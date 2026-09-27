package com.xeye.backend.search.infrastructure.sync;

import com.xeye.backend.search.application.port.out.SearchSyncNotifier;
import com.xeye.backend.shared.config.SearchProperties;
import com.xeye.backend.shared.http.CircuitBreaker;
import com.xeye.backend.shared.http.OutboundHttp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Envía las notificaciones de cambio a la API interna del microservicio de búsqueda
 * ({@code /v1/...}), autenticadas con el {@code X-Internal-Token} compartido. Lo llama el
 * handler del outbox: un fallo (timeout, 5xx, circuito abierto) se propaga y el relé
 * reintenta con backoff; con el cortacircuitos abierto ni se intenta la conexión.
 */
@Component
@ConditionalOnProperty(name = "xeye.search.provider", havingValue = "http")
public class HttpSearchSyncNotifier implements SearchSyncNotifier {

    private static final Logger log = LoggerFactory.getLogger(HttpSearchSyncNotifier.class);

    private final RestClient http;
    private final CircuitBreaker breaker;
    private final String internalServiceName;
    private final String internalToken;

    public HttpSearchSyncNotifier(SearchProperties properties,
                                  @Qualifier("searchServiceBreaker") CircuitBreaker breaker) {
        this.http = OutboundHttp.client(properties.url(), Duration.ofSeconds(10));
        this.breaker = breaker;
        this.internalServiceName = properties.internalServiceName();
        this.internalToken = properties.internalToken();
    }

    /** Toda llamada pasa por el cortacircuitos; las excepciones comprobadas no existen en RestClient. */
    private void guarded(CircuitBreaker.Runnable call) {
        try {
            breaker.run(call);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public void listMetaChanged(Long listId, Long userId, String name, boolean isPublic) {
        guarded(() -> http.put()
                        .uri("/v1/lists/{listId}/meta", listId)
                        .headers(this::internalHeaders)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new ListMetaPayload(userId, name, isPublic))
                        .retrieve()
                        .toBodilessEntity());
        log.debug("Notified search: list {} meta changed", listId);
    }

    @Override
    public void listDeleted(Long listId) {
        guarded(() -> http.delete()
                        .uri("/v1/lists/{listId}", listId)
                        .headers(this::internalHeaders)
                        .retrieve()
                        .toBodilessEntity());
        log.debug("Notified search: list {} deleted", listId);
    }

    @Override
    public void listDataInvalidated(Long listId) {
        guarded(() -> http.post()
                        .uri("/v1/lists/{listId}/invalidate", listId)
                        .headers(this::internalHeaders)
                        .retrieve()
                        .toBodilessEntity());
        log.debug("Notified search: list {} data invalidated", listId);
    }

    @Override
    public void apiKeyCreated(Long apiKeyId, Long userId, String keyHash) {
        guarded(() -> http.put()
                        .uri("/v1/api-keys/{id}", apiKeyId)
                        .headers(this::internalHeaders)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new ApiKeyPayload(userId, keyHash))
                        .retrieve()
                        .toBodilessEntity());
        log.debug("Notified search: api key {} created", apiKeyId);
    }

    @Override
    public void apiKeyDeleted(Long apiKeyId) {
        guarded(() -> http.delete()
                        .uri("/v1/api-keys/{id}", apiKeyId)
                        .headers(this::internalHeaders)
                        .retrieve()
                        .toBodilessEntity());
        log.debug("Notified search: api key {} deleted", apiKeyId);
    }

    @Override
    public void userSearchLimitChanged(Long userId, Integer rateLimitPerMinute) {
        guarded(() -> http.put()
                        .uri("/v1/users/{userId}/limits", userId)
                        .headers(this::internalHeaders)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new UserLimitsPayload(rateLimitPerMinute))
                        .retrieve()
                        .toBodilessEntity());
        log.debug("Notified search: user {} search limit changed to {}", userId, rateLimitPerMinute);
    }

    @Override
    public void userDeleted(Long userId) {
        guarded(() -> http.delete()
                        .uri("/v1/users/{userId}", userId)
                        .headers(this::internalHeaders)
                        .retrieve()
                        .toBodilessEntity());
        log.debug("Notified search: user {} deleted", userId);
    }

    private void internalHeaders(org.springframework.http.HttpHeaders headers) {
        headers.set("X-Internal-Service", internalServiceName);
        headers.set("X-Internal-Token", internalToken);
    }

    record ListMetaPayload(Long userId, String name, boolean isPublic) {
    }

    /** Solo el hash SHA-256: el search-service nunca recibe claves en claro. */
    record ApiKeyPayload(Long userId, String keyHash) {
    }

    /** {@code null} se serializa explícitamente (Jackson incluye nulls): "vuelve al por defecto". */
    record UserLimitsPayload(Integer rateLimitPerMinute) {
    }
}
