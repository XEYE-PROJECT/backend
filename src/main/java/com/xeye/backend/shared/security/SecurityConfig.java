package com.xeye.backend.shared.security;

import com.xeye.backend.shared.config.AuthProperties;
import com.xeye.backend.shared.web.ApiError;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Seguridad sin estado: contraseñas BCrypt, filtro JWT bearer para los usuarios (con revocación
 * por jti y versión de sesión), rate limit por IP en los endpoints públicos de autenticación y dos
 * filtros de secreto compartido para las llamadas servidor-a-servidor ({@code /webhooks/**} del
 * worker de training, {@code /internal/**} del search-service), que exigen su rol de servicio.
 * Públicos: {@code /auth/**} (salvo logout) y {@code /actuator/health} (+ liveness/readiness); {@code /admin/**} exige
 * ROLE_ADMIN (además del {@code @PreAuthorize} de cada controlador); todo lo demás exige JWT.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    static final String ROLE_TRAINING_WORKER = "TRAINING_WORKER";
    static final String ROLE_SEARCH_SERVICE = "SEARCH_SERVICE";
    static final String ROLE_ADMIN = "ADMIN";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                                   TokenRevocationRegistry revocations, ObjectMapper objectMapper,
                                                   AuthProperties authProps,
                                                   // Por clave y no por los records de cada módulo: shared no
                                                   // depende de training (dependencias acíclicas entre módulos).
                                                   @Value("${xeye.training.webhook-secret}") String webhookSecret,
                                                   @Value("${xeye.search.internal-token}") String internalToken)
            throws Exception {
        AuthProperties.RateLimit limits = authProps.rateLimit();
        Map<String, InMemoryRateLimiter> limiters = Map.of(
                "/auth/login", new InMemoryRateLimiter(limits.loginPerMinute(), 60),
                "/auth/mfa", new InMemoryRateLimiter(limits.loginPerMinute(), 60),
                "/auth/register", new InMemoryRateLimiter(limits.registerPerMinute(), 60),
                "/auth/resend-verification", new InMemoryRateLimiter(limits.registerPerMinute(), 60),
                "/auth/forgot-password", new InMemoryRateLimiter(limits.passwordResetPerMinute(), 60),
                "/auth/reset-password", new InMemoryRateLimiter(limits.passwordResetPerMinute(), 60),
                "/auth/verify-email", new InMemoryRateLimiter(limits.passwordResetPerMinute(), 60),
                "/auth/sso/exchange", new InMemoryRateLimiter(limits.loginPerMinute(), 60));

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/logout", "/auth/logout-all").authenticated()
                        .requestMatchers("/auth/**", "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/admin/**").hasRole(ROLE_ADMIN)
                        // Sin JWT: los autentica SharedSecretAuthenticationFilter con su cabecera.
                        .requestMatchers("/webhooks/**").hasRole(ROLE_TRAINING_WORKER)
                        .requestMatchers("/internal/**").hasRole(ROLE_SEARCH_SERVICE)
                        .anyRequest().authenticated())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            ApiError body = ApiError.of(HttpStatus.UNAUTHORIZED.value(),
                                    HttpStatus.UNAUTHORIZED.getReasonPhrase(), "Authentication required", "UNAUTHENTICATED");
                            objectMapper.writeValue(response.getOutputStream(), body);
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            ApiError body = ApiError.of(HttpStatus.FORBIDDEN.value(),
                                    HttpStatus.FORBIDDEN.getReasonPhrase(), "Insufficient permissions", "FORBIDDEN");
                            objectMapper.writeValue(response.getOutputStream(), body);
                        }))
                .addFilterBefore(new AuthRateLimitFilter(limiters, objectMapper),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, revocations),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new SharedSecretAuthenticationFilter(
                                PathPatternRequestMatcher.withDefaults().matcher("/webhooks/**"),
                                "X-Webhook-Token", webhookSecret, new ServicePrincipal("training-worker"),
                                ROLE_TRAINING_WORKER, "Invalid webhook token", objectMapper),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new SharedSecretAuthenticationFilter(
                                PathPatternRequestMatcher.withDefaults().matcher("/internal/**"),
                                "X-Internal-Token", internalToken, new ServicePrincipal("search-service"),
                                ROLE_SEARCH_SERVICE, "Invalid internal token", objectMapper),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Retry-After"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
