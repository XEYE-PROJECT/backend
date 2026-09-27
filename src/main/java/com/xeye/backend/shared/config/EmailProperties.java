package com.xeye.backend.shared.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Vincula {@code xeye.email.*}. {@code provider}: {@code log} (imprime el email y sus enlaces en
 * el log; dev), {@code smtp} (buzón SMTP, p. ej. IONOS: exige {@code spring.mail.*}) o
 * {@code resend} (API HTTP de Resend, exige {@code resendApiKey}). {@code from} es el remitente
 * (debe ser un buzón/dominio autorizado por el proveedor) y {@code replyTo} el buzón de contacto.
 */
@Validated
@ConfigurationProperties(prefix = "xeye.email")
public record EmailProperties(@NotBlank String provider,
                              @NotBlank String from,
                              @DefaultValue("") String replyTo,
                              @DefaultValue("") String resendApiKey) {
}
