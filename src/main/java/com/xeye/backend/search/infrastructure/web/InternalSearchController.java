package com.xeye.backend.search.infrastructure.web;

import com.xeye.backend.apikey.application.port.in.ApiKeyQueryPort;
import com.xeye.backend.apikey.domain.model.ApiKey;
import com.xeye.backend.element.application.port.in.ElementQueryPort;
import com.xeye.backend.list.application.port.in.ListQueryPort;
import com.xeye.backend.list.domain.model.ItemList;
import com.xeye.backend.search.application.command.RecordSearchCommand;
import com.xeye.backend.search.application.port.in.SearchLogUseCases;
import com.xeye.backend.search.infrastructure.web.dto.BootstrapResponse;
import com.xeye.backend.search.infrastructure.web.dto.IngestAck;
import com.xeye.backend.search.infrastructure.web.dto.IngestSearchLogsRequest;
import com.xeye.backend.search.infrastructure.web.dto.ListSearchDataResponse;
import com.xeye.backend.shared.exception.NotFoundException;
import com.xeye.backend.training.application.port.in.TrainingQueryPort;
import com.xeye.backend.training.domain.model.Training;
import com.xeye.backend.user.application.port.in.UserQueryPort;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * API servidor-a-servidor para el microservicio de búsqueda. Sin JWT: {@code SecurityConfig}
 * exige en {@code /internal/**} el secreto compartido {@code X-Internal-Token} vía
 * {@code SharedSecretAuthenticationFilter} (mismo mecanismo que el webhook de training), así
 * que aquí ya solo llegan llamadas autenticadas del search-service.
 */
@RestController
@RequestMapping("/internal/search")
public class InternalSearchController {

    static final int DEFAULT_PAGE = 1000;
    static final int MAX_PAGE = 5000;

    private final ApiKeyQueryPort apiKeys;
    private final ListQueryPort lists;
    private final ElementQueryPort elements;
    private final TrainingQueryPort trainings;
    private final UserQueryPort users;
    private final SearchLogUseCases searchLogs;
    private final ObjectMapper objectMapper;

    public InternalSearchController(ApiKeyQueryPort apiKeys, ListQueryPort lists, ElementQueryPort elements,
                                    TrainingQueryPort trainings, UserQueryPort users, SearchLogUseCases searchLogs,
                                    ObjectMapper objectMapper) {
        this.apiKeys = apiKeys;
        this.lists = lists;
        this.elements = elements;
        this.trainings = trainings;
        this.users = users;
        this.searchLogs = searchLogs;
        this.objectMapper = objectMapper;
    }

    /**
     * Snapshot de arranque: primera página (id ascendente, {@code ?limit=} hasta 5000) de hashes
     * de api keys y de metadatos de listas, más modelos de embedding y cupos por usuario. Si
     * {@code apiKeysNextAfterId}/{@code listsNextAfterId} no son nulos, el buscador sigue con los
     * endpoints por clave de abajo. Nunca viaja una key en claro.
     */
    @GetMapping("/bootstrap")
    public BootstrapResponse bootstrap(@RequestParam(required = false) Integer limit) {
        int page = pageSize(limit);
        BootstrapResponse.KeysetPage<BootstrapResponse.ApiKeyEntry> keys = apiKeyPage(0, page);
        BootstrapResponse.KeysetPage<BootstrapResponse.ListEntry> allLists = listPage(0, page);
        List<BootstrapResponse.UserLimitEntry> limits = users.findSearchRateLimits().stream()
                .map(l -> new BootstrapResponse.UserLimitEntry(l.userId(), l.rateLimitPerMinute()))
                .toList();
        return new BootstrapResponse(keys.items(), keys.nextAfterId(), allLists.items(), allLists.nextAfterId(),
                trainings.availableEmbeddingModels(), limits);
    }

    /** Página por clave de hashes de api keys: {@code ?afterId=&limit=}. */
    @GetMapping("/api-keys")
    public BootstrapResponse.KeysetPage<BootstrapResponse.ApiKeyEntry> apiKeys(
            @RequestParam(defaultValue = "0") long afterId, @RequestParam(required = false) Integer limit) {
        return apiKeyPage(afterId, pageSize(limit));
    }

    /** Página por clave de metadatos de listas: {@code ?afterId=&limit=}. */
    @GetMapping("/lists")
    public BootstrapResponse.KeysetPage<BootstrapResponse.ListEntry> lists(
            @RequestParam(defaultValue = "0") long afterId, @RequestParam(required = false) Integer limit) {
        return listPage(afterId, pageSize(limit));
    }

    /** Datos de búsqueda completos de una lista — la carga perezosa equivalente al push al completar un training. */
    @GetMapping("/lists/{listId}")
    public ListSearchDataResponse listData(@PathVariable Long listId) {
        ItemList list = lists.findById(listId)
                .orElseThrow(() -> new NotFoundException("List not found: " + listId));
        List<ListSearchDataResponse.ElementEntry> elementEntries = elements.findByListId(listId).stream()
                .map(e -> new ListSearchDataResponse.ElementEntry(e.id(), e.text(), e.params(), e.description()))
                .toList();
        Training inUse = trainings.findInUseByListId(listId).orElse(null);
        String embeddings = inUse == null ? null : trainings.findEmbeddingsData(inUse.id()).orElse(null);
        return new ListSearchDataResponse(
                list.id(), list.userId(), list.name(), list.isPublic(),
                inUse == null ? null : inUse.model(),
                embeddings,
                inUse == null ? null : inUse.elementIds(),
                elementEntries);
    }

    /** Ingesta por lotes de logs de búsqueda (fire-and-forget en el lado de búsqueda). */
    @PostMapping("/logs")
    public IngestAck ingestLogs(@Valid @RequestBody IngestSearchLogsRequest request) {
        List<RecordSearchCommand> commands = request.logs().stream().map(this::toCommand).toList();
        return new IngestAck(true, searchLogs.recordAll(commands));
    }

    private BootstrapResponse.KeysetPage<BootstrapResponse.ApiKeyEntry> apiKeyPage(long afterId, int page) {
        List<ApiKey> keys = apiKeys.findAfterId(afterId, page);
        List<BootstrapResponse.ApiKeyEntry> items = keys.stream()
                .map(key -> new BootstrapResponse.ApiKeyEntry(key.id(), key.userId(), key.keyHash()))
                .toList();
        return new BootstrapResponse.KeysetPage<>(items, keys.size() < page ? null : keys.get(keys.size() - 1).id());
    }

    private BootstrapResponse.KeysetPage<BootstrapResponse.ListEntry> listPage(long afterId, int page) {
        List<ItemList> found = lists.findAfterId(afterId, page);
        List<BootstrapResponse.ListEntry> items = found.stream()
                .map(list -> new BootstrapResponse.ListEntry(list.id(), list.userId(), list.name(), list.isPublic()))
                .toList();
        return new BootstrapResponse.KeysetPage<>(items, found.size() < page ? null : found.get(found.size() - 1).id());
    }

    private static int pageSize(Integer limit) {
        return limit == null ? DEFAULT_PAGE : Math.max(1, Math.min(limit, MAX_PAGE));
    }

    private RecordSearchCommand toCommand(IngestSearchLogsRequest.Entry entry) {
        return new RecordSearchCommand(
                entry.userId(), entry.apiKeyId(), entry.listId(), entry.listName(),
                entry.endpoint(), entry.searchTerm(), entry.totalResults(), entry.durationMs(),
                entry.session(),
                entry.results() == null ? null : objectMapper.writeValueAsString(entry.results()),
                entry.searchedAt());
    }
}
