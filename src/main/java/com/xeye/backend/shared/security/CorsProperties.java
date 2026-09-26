package com.xeye.backend.shared.security;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** Vincula {@code xeye.cors.*}. Sin orígenes configurados la app no arranca. */
@Validated
@ConfigurationProperties(prefix = "xeye.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {
}
