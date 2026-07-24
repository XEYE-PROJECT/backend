package com.xeye.backend.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Vincula {@code xeye.cors.*}. */
@ConfigurationProperties(prefix = "xeye.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
