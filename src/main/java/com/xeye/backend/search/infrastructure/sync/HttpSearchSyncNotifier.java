package com.xeye.backend.search.infrastructure.sync;

import com.xeye.backend.search.application.port.out.SearchSyncNotifier;
import com.xeye.backend.shared.config.SearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Envía las notificaciones de cambio a la API interna del microservicio de búsqueda
 * ({@code /v1/...}), autenticadas con el {@code X-Internal-Token} compartido. Los fallos ya
 * son no fatales para quien llama; el timeout corto evita que un servicio muerto atasque
 * el executor asíncrono.
 */
@Component
@ConditionalOnProperty(name = "xeye.search.provider", havingValue = "http")
public class HttpSearchSyncNotifier implements SearchSyncNotifier {

    private static final Logger log = LoggerFactory.getLogger(HttpSearchSyncNotifier.class);

    private final RestClient http;
    private final String internalServiceName;
    private final String internalToken;

    public HttpSearchSyncNotifier(SearchProperties properties) {
        // HTTP/1.1 plano: el upgrade h2c por defecto del cliente del JDK hace que uvicorn descarte el body.
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder()
                .baseUrl(properties.url())
                .requestFactory(requestFactory)
                .build();
        this.internalServiceName = properties.internalServiceName();
        this.internalToken = properties.internalToken();
    }

    @Override
    public void listMetaChanged(Long listId, Long userId, String name, boolean isPublic) {
        http.put()
                .uri("/v1/lists/{listId}/meta", listId)
                .headers(this::internalHeaders)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ListMetaPayload(userId, name, isPublic))
                .retrieve()
                .toBodilessEntity();
        log.debug("Notified search: list {} meta changed", listId);
    }

    @Override
    public void listDeleted(Long listId) {
        http.delete()
                .uri("/v1/lists/{listId}", listId)
                .headers(this::internalHeaders)
                .retrieve()
                .toBodilessEntity();
        log.debug("Notified search: list {} deleted", listId);
    }

    @Override
    public void listDataInvalidated(Long listId) {
        http.post()
                .uri("/v1/lists/{listId}/invalidate", listId)
                .headers(this::internalHeaders)
                .retrieve()
                .toBodilessEntity();
        log.debug("Notified search: list {} data invalidated", listId);
    }

    @Override
    public void apiKeyCreated(Long apiKeyId, Long userId, String apiKey) {
        http.put()
                .uri("/v1/api-keys/{id}", apiKeyId)
                .headers(this::internalHeaders)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiKeyPayload(userId, apiKey))
                .retrieve()
                .toBodilessEntity();
        log.debug("Notified search: api key {} created", apiKeyId);
    }

    @Override
    public void apiKeyDeleted(Long apiKeyId) {
        http.delete()
                .uri("/v1/api-keys/{id}", apiKeyId)
                .headers(this::internalHeaders)
                .retrieve()
                .toBodilessEntity();
        log.debug("Notified search: api key {} deleted", apiKeyId);
    }

    @Override
    public void userDeleted(Long userId) {
        http.delete()
                .uri("/v1/users/{userId}", userId)
                .headers(this::internalHeaders)
                .retrieve()
                .toBodilessEntity();
        log.debug("Notified search: user {} deleted", userId);
    }

    private void internalHeaders(org.springframework.http.HttpHeaders headers) {
        headers.set("X-Internal-Service", internalServiceName);
        headers.set("X-Internal-Token", internalToken);
    }

    record ListMetaPayload(Long userId, String name, boolean isPublic) {
    }

    record ApiKeyPayload(Long userId, String apiKey) {
    }
}
