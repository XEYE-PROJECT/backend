package com.xeye.backend.shared.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Vincula {@code xeye.jwt.*}. El secreto necesita >= 32 bytes para HS256; se valida al arrancar. */
@Validated
@ConfigurationProperties(prefix = "xeye.jwt")
public record JwtProperties(@NotBlank @Size(min = 32) String secret,
                            @Positive long expirationMinutes,
                            @NotBlank String issuer) {
}
