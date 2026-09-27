package com.xeye.backend.shared.http;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Fábrica de {@link RestClient} para las llamadas salientes del backend: siempre con timeout
 * de conexión y de lectura (una dependencia colgada nunca bloquea un hilo indefinidamente) y
 * en HTTP/1.1 plano (el upgrade h2c por defecto del cliente del JDK hace que uvicorn descarte
 * el body).
 */
public final class OutboundHttp {

    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    private OutboundHttp() {
    }

    public static RestClient client(String baseUrl, Duration readTimeout) {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /** Fallo de red/timeout o respuesta 5xx: merece reintento. Un 4xx nunca. */
    public static boolean isTransient(Exception ex) {
        if (ex instanceof ResourceAccessException) {
            return true;
        }
        if (ex instanceof RestClientResponseException response) {
            return response.getStatusCode().is5xxServerError();
        }
        return false;
    }
}
