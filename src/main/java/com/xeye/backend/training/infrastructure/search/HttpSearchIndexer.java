package com.xeye.backend.training.infrastructure.search;

import com.xeye.backend.shared.config.SearchProperties;
import com.xeye.backend.shared.http.CircuitBreaker;
import com.xeye.backend.shared.http.OutboundHttp;
import com.xeye.backend.training.application.command.SearchIndexCommand;
import com.xeye.backend.training.application.port.out.SearchIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Empuja una lista completada al microservicio de búsqueda:
 * {@code POST {url}/v1/lists/{listId}/index} con cabeceras {@code X-Internal-Service: backend}
 * + {@code X-Internal-Token} y un {@link SearchIndexCommand} como body JSON. Lo llama el handler
 * del outbox: un fallo (timeout, 5xx, circuito abierto) se propaga y el relé reintenta con backoff.
 */
@Component
@ConditionalOnProperty(name = "xeye.search.provider", havingValue = "http")
public class HttpSearchIndexer implements SearchIndexer {

    private static final Logger log = LoggerFactory.getLogger(HttpSearchIndexer.class);

    private final RestClient http;
    private final CircuitBreaker breaker;
    private final String internalServiceName;
    private final String internalToken;

    public HttpSearchIndexer(SearchProperties properties, @Qualifier("searchServiceBreaker") CircuitBreaker breaker) {
        // Payload grande (embeddings de toda la lista): lectura generosa.
        this.http = OutboundHttp.client(properties.url(), Duration.ofSeconds(60));
        this.breaker = breaker;
        this.internalServiceName = properties.internalServiceName();
        this.internalToken = properties.internalToken();
    }

    @Override
    public void index(SearchIndexCommand command) {
        try {
            breaker.run(() -> http.post()
                    .uri("/v1/lists/{listId}/index", command.listId())
                    .header("X-Internal-Service", internalServiceName)
                    .header("X-Internal-Token", internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(command)
                    .retrieve()
                    .toBodilessEntity());
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        log.info("Pushed list {} to search service", command.listId());
    }
}
