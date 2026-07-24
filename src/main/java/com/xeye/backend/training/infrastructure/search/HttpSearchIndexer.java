package com.xeye.backend.training.infrastructure.search;

import com.xeye.backend.shared.config.SearchProperties;
import com.xeye.backend.training.application.command.SearchIndexCommand;
import com.xeye.backend.training.application.port.out.SearchIndexer;
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
 * Empuja una lista completada al microservicio de búsqueda:
 * {@code POST {url}/v1/lists/{listId}/index} con cabeceras {@code X-Internal-Service: backend}
 * + {@code X-Internal-Token} y un {@link SearchIndexCommand} como body JSON.
 */
@Component
@ConditionalOnProperty(name = "xeye.search.provider", havingValue = "http")
public class HttpSearchIndexer implements SearchIndexer {

    private static final Logger log = LoggerFactory.getLogger(HttpSearchIndexer.class);

    private final RestClient http;
    private final String internalServiceName;
    private final String internalToken;

    public HttpSearchIndexer(SearchProperties properties) {
        // HTTP/1.1 plano: el upgrade h2c por defecto del cliente del JDK hace que uvicorn descarte el body.
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder()
                .baseUrl(properties.url())
                .requestFactory(requestFactory)
                .build();
        this.internalServiceName = properties.internalServiceName();
        this.internalToken = properties.internalToken();
    }

    @Override
    public void index(SearchIndexCommand command) {
        http.post()
                .uri("/v1/lists/{listId}/index", command.listId())
                .header("X-Internal-Service", internalServiceName)
                .header("X-Internal-Token", internalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(command)
                .retrieve()
                .toBodilessEntity();
        log.info("Pushed list {} to search service", command.listId());
    }
}
