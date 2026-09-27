package com.xeye.backend.shared.config;

import com.xeye.backend.shared.http.CircuitBreaker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Cortacircuitos compartidos por destino. El del buscador lo usan las tres puertas que hablan
 * con él (notificaciones, push del índice y playground): si el search-service está caído,
 * todas fallan rápido durante 30 s en vez de agotar timeouts una a una.
 */
@Configuration
public class OutboundHttpConfig {

    @Bean(name = "searchServiceBreaker")
    public CircuitBreaker searchServiceBreaker() {
        return new CircuitBreaker("search-service", 5, Duration.ofSeconds(30));
    }
}
