package com.xeye.backend.user.infrastructure.sso;

import com.xeye.backend.shared.config.AuthProperties;
import com.xeye.backend.shared.exception.BadRequestException;
import com.xeye.backend.user.application.command.SsoIdentity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.http.HttpClient;
import java.security.Key;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Cliente OIDC mínimo (authorization code, cliente confidencial) para Google y Microsoft: lee el
 * documento de descubrimiento, construye la URL de autorización, canjea el {@code code} y valida
 * el {@code id_token} (firma vía JWKS, emisor, audiencia, nonce). Sin librerías extra: jjwt ya
 * parsea JWKS. El SSO se activa por proveedor cuando hay client id + secret configurados.
 */
@Component
public class OidcClient {

    private static final Logger log = LoggerFactory.getLogger(OidcClient.class);
    private static final String GOOGLE = "google";
    private static final String MICROSOFT = "microsoft";
    /** Tenant de las cuentas personales de Microsoft: sus emails están verificados por Microsoft. */
    private static final String MS_CONSUMERS_TENANT = "9188040d-6c67-4c5b-b112-36a304b66dad";
    private static final Pattern MS_ISSUER = Pattern.compile("https://login\\.microsoftonline\\.com/([0-9a-f-]{36})/v2\\.0");

    private final AuthProperties props;
    private final RestClient http;
    private final Map<String, Discovery> discoveries = new ConcurrentHashMap<>();
    private final Map<String, JwkSet> jwks = new ConcurrentHashMap<>();

    public OidcClient(AuthProperties props) {
        this.props = props;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(requestFactory).build();
    }

    public List<String> enabledProviders() {
        return List.of(GOOGLE, MICROSOFT).stream().filter(this::isEnabled).toList();
    }

    public boolean isEnabled(String provider) {
        return switch (provider) {
            case GOOGLE -> props.sso().google().enabled();
            case MICROSOFT -> props.sso().microsoft().enabled();
            default -> false;
        };
    }

    public String authorizationUrl(String provider, String redirectUri, String state, String nonce) {
        AuthProperties.Sso.Provider cfg = config(provider);
        return UriComponentsBuilder.fromUriString(discovery(provider).authorizationEndpoint())
                .queryParam("client_id", cfg.clientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .queryParam("nonce", nonce)
                .queryParam("prompt", "select_account")
                .build().encode().toUriString();
    }

    /** Canjea el código y devuelve la identidad del {@code id_token} validado. */
    @SuppressWarnings("unchecked")
    public SsoIdentity exchange(String provider, String code, String redirectUri, String expectedNonce) {
        AuthProperties.Sso.Provider cfg = config(provider);
        Discovery discovery = discovery(provider);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("client_id", cfg.clientId());
        form.add("client_secret", cfg.clientSecret());
        Map<String, Object> tokens;
        try {
            tokens = http.post().uri(discovery.tokenEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(Map.class);
        } catch (RuntimeException e) {
            log.warn("SSO {} token exchange failed: {}", provider, e.getMessage());
            throw new BadRequestException("Could not complete the sign-in with " + provider, "SSO_EXCHANGE_FAILED");
        }
        Object idToken = tokens == null ? null : tokens.get("id_token");
        if (!(idToken instanceof String)) {
            throw new BadRequestException("The identity provider returned no id_token", "SSO_EXCHANGE_FAILED");
        }
        Claims claims = verifyIdToken(provider, (String) idToken, cfg.clientId(), expectedNonce);
        return toIdentity(provider, claims);
    }

    private Claims verifyIdToken(String provider, String idToken, String clientId, String expectedNonce) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .keyLocator(new JwksLocator(provider))
                    .build()
                    .parseSignedClaims(idToken)
                    .getPayload();
        } catch (JwtException e) {
            log.warn("SSO {} id_token rejected: {}", provider, e.getMessage());
            throw new BadRequestException("Invalid identity token", "SSO_TOKEN_INVALID");
        }
        if (!claims.getAudience().contains(clientId)) {
            throw new BadRequestException("Identity token audience mismatch", "SSO_TOKEN_INVALID");
        }
        if (!isValidIssuer(provider, claims.getIssuer())) {
            throw new BadRequestException("Identity token issuer mismatch", "SSO_TOKEN_INVALID");
        }
        if (expectedNonce != null && !expectedNonce.equals(claims.get("nonce", String.class))) {
            throw new BadRequestException("Identity token nonce mismatch", "SSO_TOKEN_INVALID");
        }
        return claims;
    }

    private boolean isValidIssuer(String provider, String issuer) {
        if (issuer == null) {
            return false;
        }
        if (GOOGLE.equals(provider)) {
            return issuer.equals("https://accounts.google.com") || issuer.equals("accounts.google.com");
        }
        var m = MS_ISSUER.matcher(issuer);
        if (!m.matches()) {
            return false;
        }
        String tenant = props.sso().microsoft().tenant();
        return "common".equals(tenant) || "organizations".equals(tenant) || "consumers".equals(tenant)
                || tenant.equalsIgnoreCase(m.group(1));
    }

    private SsoIdentity toIdentity(String provider, Claims claims) {
        String email = claims.get("email", String.class);
        if (email == null && MICROSOFT.equals(provider)) {
            email = claims.get("preferred_username", String.class);
        }
        if (email == null || !email.contains("@")) {
            throw new BadRequestException("The identity provider did not share an email", "SSO_NO_EMAIL");
        }
        boolean verified;
        if (GOOGLE.equals(provider)) {
            verified = truthy(claims.get("email_verified"));
        } else {
            // Microsoft: verificado si es cuenta personal o si el tenant expone el claim opcional xms_edov.
            String tid = claims.get("tid", String.class);
            verified = MS_CONSUMERS_TENANT.equalsIgnoreCase(tid)
                    || truthy(claims.get("xms_edov")) || truthy(claims.get("email_verified"));
        }
        String given = claims.get("given_name", String.class);
        String family = claims.get("family_name", String.class);
        if (given == null) {
            String name = Optional.ofNullable(claims.get("name", String.class)).orElse(email.split("@")[0]);
            String[] parts = name.trim().split("\\s+", 2);
            given = parts[0];
            family = family == null && parts.length > 1 ? parts[1] : family;
        }
        return new SsoIdentity(provider, claims.getSubject(), email.toLowerCase(Locale.ROOT), verified, given, family);
    }

    /** Los proveedores serializan a veces los booleanos como cadenas ("true"). */
    private static boolean truthy(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private AuthProperties.Sso.Provider config(String provider) {
        if (!isEnabled(provider)) {
            throw new BadRequestException("Unknown or disabled SSO provider: " + provider, "SSO_PROVIDER_DISABLED");
        }
        return GOOGLE.equals(provider) ? props.sso().google() : props.sso().microsoft();
    }

    @SuppressWarnings("unchecked")
    private Discovery discovery(String provider) {
        return discoveries.computeIfAbsent(provider, p -> {
            String url = GOOGLE.equals(p)
                    ? "https://accounts.google.com/.well-known/openid-configuration"
                    : "https://login.microsoftonline.com/" + props.sso().microsoft().tenant() + "/v2.0/.well-known/openid-configuration";
            Map<String, Object> doc = http.get().uri(url).retrieve().body(Map.class);
            if (doc == null) {
                throw new IllegalStateException("Empty OIDC discovery document for " + p);
            }
            return new Discovery((String) doc.get("authorization_endpoint"), (String) doc.get("token_endpoint"),
                    (String) doc.get("jwks_uri"));
        });
    }

    private JwkSet fetchJwks(String provider) {
        String body = http.get().uri(discovery(provider).jwksUri()).retrieve().body(String.class);
        return Jwks.setParser().build().parse(body);
    }

    /** Localiza la clave del {@code kid} del token; si no está cacheada, recarga el JWKS (rotación). */
    private final class JwksLocator extends LocatorAdapter<Key> {
        private final String provider;

        JwksLocator(String provider) {
            this.provider = provider;
        }

        @Override
        protected Key locate(ProtectedHeader header) {
            String kid = header.getKeyId();
            Key key = find(jwks.computeIfAbsent(provider, OidcClient.this::fetchJwks), kid);
            if (key == null) {
                JwkSet refreshed = fetchJwks(provider);
                jwks.put(provider, refreshed);
                key = find(refreshed, kid);
            }
            if (key == null) {
                throw new JwtException("No JWK found for kid " + kid);
            }
            return key;
        }

        private Key find(JwkSet set, String kid) {
            for (Jwk<?> jwk : set.getKeys()) {
                if (kid == null || kid.equals(jwk.getId())) {
                    return jwk.toKey();
                }
            }
            return null;
        }
    }

    private record Discovery(String authorizationEndpoint, String tokenEndpoint, String jwksUri) {
    }
}
