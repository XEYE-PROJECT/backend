package com.xeye.backend.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Vincula {@code xeye.jwt.*}. El secreto necesita >= 32 bytes para HS256. */
@ConfigurationProperties(prefix = "xeye.jwt")
public record JwtProperties(String secret, long expirationMinutes, String issuer) {
}
