package com.xeye.backend.search.infrastructure.query;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.xeye.backend.search.application.command.ConsoleSearchCommand;
import com.xeye.backend.search.application.port.out.SearchQueryGateway;
import com.xeye.backend.shared.config.SearchProperties;
import com.xeye.backend.shared.http.CircuitBreaker;
import com.xeye.backend.shared.http.OutboundHttp;
import com.xeye.backend.shared.exception.NotFoundException;
import com.xeye.backend.shared.exception.ServiceUnavailableException;
import com.xeye.backend.shared.exception.TooManyRequestsException;
import com.xeye.backend.shared.web.RequestId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;

/**
 * {@code POST {url}/v1/lists/{listId}/search} con {@code X-Internal-Token}. El buscador responde
 * en snake_case (su contrato público); aquí se mapea al resultado del puerto. Errores: 404 →
 * {@link NotFoundException} (la lista aún no está en su catálogo), 429 →
 * {@link TooManyRequestsException} con el {@code Retry-After} del buscador, resto/caído →
 * {@link ServiceUnavailableException}. Nunca se reenvía el cuerpo de error del buscador. Con el
 * cortacircuitos del buscador abierto responde 503 al instante, sin esperar el timeout.
 */
@Component
@ConditionalOnProperty(name = "xeye.search.provider", havingValue = "http")
public class HttpSearchQueryGateway implements SearchQueryGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpSearchQueryGateway.class);

    private final RestClient http;
    private final CircuitBreaker breaker;
    private final String internalServiceName;
    private final String internalToken;

    public HttpSearchQueryGateway(SearchProperties properties,
                                  @Qualifier("searchServiceBreaker") CircuitBreaker breaker) {
        this.http = OutboundHttp.client(properties.url(), Duration.ofSeconds(15));
        this.breaker = breaker;
        this.internalServiceName = properties.internalServiceName();
        this.internalToken = properties.internalToken();
    }

    @Override
    public SearchQueryResult search(Long listId, ConsoleSearchCommand command) {
        SearchBody body;
        // El buscador acepta el mismo X-Request-Id: sus logs y los nuestros comparten el id.
        String requestId = RequestId.current();
        try {
            body = breaker.call(() -> http.post()
                    .uri("/v1/lists/{listId}/search", listId)
                    .header("X-Internal-Service", internalServiceName)
                    .header("X-Internal-Token", internalToken)
                    .headers(h -> {
                        if (requestId != null) {
                            h.set(RequestId.HEADER, requestId);
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SearchRequest(command.searchTerm(), command.limit(), command.includeScoreBreakdown()))
                    .retrieve()
                    .body(SearchBody.class));
        } catch (CircuitBreaker.OpenCircuitException ex) {
            throw new ServiceUnavailableException("Search service is temporarily unavailable");
        } catch (RestClientResponseException ex) {
            throw translate(ex, listId);
        } catch (ResourceAccessException ex) {
            log.warn("Search service unreachable for list {}: {}", listId, ex.getMessage());
            throw new ServiceUnavailableException("Search service is not reachable");
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceUnavailableException("Search service call failed: " + ex.getMessage());
        }
        if (body == null) {
            throw new ServiceUnavailableException("Search service returned an empty response");
        }
        List<Hit> hits = body.results() == null ? List.of() : body.results().stream()
                .map(r -> new Hit(r.item(), r.score(), r.params(), r.textScore(), r.semanticScore()))
                .toList();
        List<String> reasons = body.degradationReasons() == null ? List.of() : body.degradationReasons();
        return new SearchQueryResult(hits, body.totalResults(), body.searchTerm(), body.listName(), body.durationMs(),
                Boolean.TRUE.equals(body.degraded()), reasons);
    }

    private RuntimeException translate(RestClientResponseException ex, Long listId) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == HttpStatus.NOT_FOUND) {
            return new NotFoundException("List not found in the search service");
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            long retryAfter = 60;
            String header = ex.getResponseHeaders() == null ? null : ex.getResponseHeaders().getFirst("Retry-After");
            if (header != null) {
                try {
                    retryAfter = Long.parseLong(header.trim());
                } catch (NumberFormatException ignored) {
                    // se mantiene el valor por defecto
                }
            }
            return new TooManyRequestsException(
                    "Search rate limit reached, try again in " + retryAfter + " seconds", "RATE_LIMITED", retryAfter);
        }
        log.warn("Search service answered {} for list {}", ex.getStatusCode(), listId);
        return new ServiceUnavailableException("Search service error (" + ex.getStatusCode().value() + ")");
    }

    /** Contrato interno del buscador: snake_case (mismo cuerpo que su API pública). */
    record SearchRequest(@JsonProperty("search_term") String searchTerm,
                         int limit,
                         @JsonProperty("include_score_breakdown") boolean includeScoreBreakdown) {
    }

    record SearchBody(List<ResultItem> results,
                      @JsonProperty("total_results") int totalResults,
                      @JsonProperty("search_term") String searchTerm,
                      @JsonProperty("list_name") String listName,
                      @JsonProperty("duration_ms") int durationMs,
                      Boolean degraded,
                      @JsonProperty("degradation_reasons") List<String> degradationReasons) {
    }

    record ResultItem(String item, double score, Object params,
                      @JsonProperty("text_score") Double textScore,
                      @JsonProperty("semantic_score") Double semanticScore) {
    }
}
