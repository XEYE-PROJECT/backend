package com.xeye.backend.shared.security;

import com.xeye.backend.shared.web.ApiError;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

/**
 * Seguridad sin estado: contraseñas BCrypt, filtro JWT bearer para los usuarios y dos filtros
 * de secreto compartido para las llamadas servidor-a-servidor ({@code /webhooks/**} del
 * worker de training, {@code /internal/**} del search-service), que exigen su rol de servicio.
 * Solo {@code /auth/**} y {@code /actuator/health} son públicos; todo lo demás exige JWT.
 */
@Configuration
public class SecurityConfig {

    static final String ROLE_TRAINING_WORKER = "TRAINING_WORKER";
    static final String ROLE_SEARCH_SERVICE = "SEARCH_SERVICE";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, ObjectMapper objectMapper,
                                                   // Por clave y no por los records de cada módulo: shared no
                                                   // depende de training (dependencias acíclicas entre módulos).
                                                   @Value("${xeye.training.webhook-secret}") String webhookSecret,
                                                   @Value("${xeye.search.internal-token}") String internalToken)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/actuator/health").permitAll()
                        // Sin JWT: los autentica SharedSecretAuthenticationFilter con su cabecera.
                        .requestMatchers("/webhooks/**").hasRole(ROLE_TRAINING_WORKER)
                        .requestMatchers("/internal/**").hasRole(ROLE_SEARCH_SERVICE)
                        .anyRequest().authenticated())
                .exceptionHandling(eh -> eh.authenticationEntryPoint((request, response, ex) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    ApiError body = ApiError.of(HttpStatus.UNAUTHORIZED.value(),
                            HttpStatus.UNAUTHORIZED.getReasonPhrase(), "Authentication required");
                    objectMapper.writeValue(response.getOutputStream(), body);
                }))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService),
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
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
