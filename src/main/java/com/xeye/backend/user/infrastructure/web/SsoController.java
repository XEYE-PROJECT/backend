package com.xeye.backend.user.infrastructure.web;

import com.xeye.backend.shared.config.AuthProperties;
import com.xeye.backend.shared.exception.BadRequestException;
import com.xeye.backend.shared.exception.DomainException;
import com.xeye.backend.shared.security.JwtService;
import com.xeye.backend.user.application.command.LoginOutcome;
import com.xeye.backend.user.application.command.SsoIdentity;
import com.xeye.backend.user.application.port.in.AuthUseCases;
import com.xeye.backend.user.infrastructure.sso.OidcClient;
import com.xeye.backend.user.infrastructure.sso.SsoExchangeStore;
import com.xeye.backend.user.infrastructure.web.dto.LoginResponse;
import com.xeye.backend.user.infrastructure.web.dto.TokenRequest;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

/**
 * SSO OIDC (Google, Microsoft) con authorization code en el backend:
 * {@code GET /auth/sso/{provider}} redirige al proveedor; el proveedor vuelve a
 * {@code /auth/sso/{provider}/callback}; se canjea el código, se valida el id_token y se
 * redirige a la consola con un código de un solo uso que {@code POST /auth/sso/exchange}
 * convierte en la sesión. El {@code state} es un JWT firmado (propósito {@code sso_state}) con
 * el nonce, así no hace falta estado en servidor.
 */
@RestController
@RequestMapping("/auth/sso")
public class SsoController {

    private static final Logger log = LoggerFactory.getLogger(SsoController.class);
    private static final long STATE_TTL_SECONDS = 10 * 60;

    private final AuthUseCases auth;
    private final OidcClient oidc;
    private final SsoExchangeStore exchanges;
    private final JwtService jwt;
    private final String frontendUrl;
    private final String backendUrl;

    public SsoController(AuthUseCases auth, OidcClient oidc, SsoExchangeStore exchanges, JwtService jwt,
                         AuthProperties props, @Value("${xeye.backend.public-url}") String backendUrl) {
        this.auth = auth;
        this.oidc = oidc;
        this.exchanges = exchanges;
        this.jwt = jwt;
        this.frontendUrl = props.frontendUrl().replaceAll("/+$", "");
        this.backendUrl = backendUrl.replaceAll("/+$", "");
    }

    @GetMapping("/{provider}")
    public ResponseEntity<Void> start(@PathVariable String provider,
                                      @RequestParam(defaultValue = "es") String locale) {
        if (!oidc.isEnabled(provider)) {
            throw new BadRequestException("Unknown or disabled SSO provider: " + provider, "SSO_PROVIDER_DISABLED");
        }
        String nonce = UUID.randomUUID().toString();
        String state = jwt.generateSpecial(JwtService.PURPOSE_SSO_STATE, provider,
                Map.of("nonce", nonce, "locale", locale), STATE_TTL_SECONDS);
        String url = oidc.authorizationUrl(provider, redirectUri(provider), state, nonce);
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, url).build();
    }

    @GetMapping("/{provider}/callback")
    public ResponseEntity<Void> callback(@PathVariable String provider,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error,
                                         HttpServletRequest http) {
        if (error != null || code == null || state == null) {
            return redirectToFrontend("error", error == null ? "SSO_CANCELLED" : error);
        }
        try {
            JwtService.ParsedToken parsedState = jwt.parse(state, JwtService.PURPOSE_SSO_STATE);
            if (!provider.equals(parsedState.subject())) {
                return redirectToFrontend("error", "SSO_STATE_INVALID");
            }
            String nonce = parsedState.claims().get("nonce", String.class);
            String locale = parsedState.claims().get("locale", String.class);
            SsoIdentity identity = oidc.exchange(provider, code, redirectUri(provider), nonce);
            LoginOutcome outcome = auth.loginWithSso(identity, locale, http.getRemoteAddr());
            return redirectToFrontend("code", exchanges.put(outcome));
        } catch (JwtException e) {
            return redirectToFrontend("error", "SSO_STATE_INVALID");
        } catch (DomainException e) {
            return redirectToFrontend("error", e.code() == null ? "SSO_FAILED" : e.code());
        } catch (RuntimeException e) {
            log.error("SSO callback failed for {}", provider, e);
            return redirectToFrontend("error", "SSO_FAILED");
        }
    }

    @PostMapping("/exchange")
    public LoginResponse exchange(@Valid @RequestBody TokenRequest request) {
        return LoginResponse.of(exchanges.take(request.token())
                .orElseThrow(() -> new BadRequestException("Invalid or expired sign-in code", "SSO_CODE_INVALID")));
    }

    private String redirectUri(String provider) {
        return backendUrl + "/auth/sso/" + provider + "/callback";
    }

    private ResponseEntity<Void> redirectToFrontend(String param, String value) {
        String url = UriComponentsBuilder.fromUriString(frontendUrl + "/sso/callback")
                .queryParam(param, value).build().encode().toUriString();
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, url).build();
    }
}
