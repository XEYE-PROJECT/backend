package com.xeye.backend.shared.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Vincula {@code xeye.auth.*}: URL pública de la consola (enlaces de los emails y retorno del SSO),
 * verificación de email, admins de arranque, límites por IP, CAPTCHA, comprobación de contraseñas
 * filtradas y proveedores SSO. Compartido porque lo usan {@code SecurityConfig} y el módulo {@code user}.
 */
@Validated
@ConfigurationProperties(prefix = "xeye.auth")
public record AuthProperties(
        @NotBlank String frontendUrl,
        @DefaultValue("true") boolean requireEmailVerification,
        /** Emails que se promocionan a admin al iniciar sesión (bootstrap del primer admin). */
        @DefaultValue("") List<String> adminEmails,
        @DefaultValue("XEYE") String mfaIssuer,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue Captcha captcha,
        @DefaultValue Password password,
        @DefaultValue Sso sso) {

    /** Peticiones por minuto y por IP; {@code <= 0} desactiva ese límite. */
    public record RateLimit(@DefaultValue("10") int loginPerMinute,
                            @DefaultValue("5") int registerPerMinute,
                            @DefaultValue("5") int passwordResetPerMinute) {
    }

    /** {@code none} (por defecto) o {@code turnstile} (Cloudflare; exige secret y site-key). */
    public record Captcha(@DefaultValue("none") String provider,
                          @DefaultValue("") String siteKey,
                          @DefaultValue("") String secret) {
        public boolean enabled() {
            return !"none".equalsIgnoreCase(provider);
        }
    }

    /** {@code hibp} (Have I Been Pwned, k-anonimato, falla abierto) o {@code none}. */
    public record Password(@DefaultValue("hibp") String breachCheck) {
    }

    public record Sso(@DefaultValue Provider google, @DefaultValue Provider microsoft) {

        public record Provider(@DefaultValue("") String clientId,
                               @DefaultValue("") String clientSecret,
                               /** Solo Microsoft: {@code common}, {@code organizations} o un tenant id. */
                               @DefaultValue("common") String tenant) {
            public boolean enabled() {
                return !clientId.isBlank() && !clientSecret.isBlank();
            }
        }
    }
}
