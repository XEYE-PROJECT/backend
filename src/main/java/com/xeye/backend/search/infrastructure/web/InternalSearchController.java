package com.xeye.backend.search.infrastructure.web;

import com.xeye.backend.apikey.application.port.in.ApiKeyQueryPort;
import com.xeye.backend.element.application.port.in.ElementQueryPort;
import com.xeye.backend.list.application.port.in.ListQueryPort;
import com.xeye.backend.list.domain.model.ItemList;
import com.xeye.backend.search.application.command.RecordSearchCommand;
import com.xeye.backend.search.application.port.in.SearchLogUseCases;
import com.xeye.backend.search.infrastructure.web.dto.BootstrapResponse;
import com.xeye.backend.search.infrastructure.web.dto.IngestAck;
import com.xeye.backend.search.infrastructure.web.dto.IngestSearchLogsRequest;
import com.xeye.backend.search.infrastructure.web.dto.ListSearchDataResponse;
import com.xeye.backend.shared.config.SearchProperties;
import com.xeye.backend.shared.exception.ForbiddenException;
import com.xeye.backend.shared.exception.NotFoundException;
import com.xeye.backend.training.application.port.in.TrainingQueryPort;
import com.xeye.backend.training.domain.model.Training;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * API servidor-a-servidor para el microservicio de búsqueda. Sin JWT: la ruta es pública en
 * {@code SecurityConfig} y se protege aquí con el secreto compartido {@code X-Internal-Token}
 * (mismo patrón que el webhook de training).
 */
@RestController
@RequestMapping("/internal/search")
public class InternalSearchController {

    private final ApiKeyQueryPort apiKeys;
    private final ListQueryPort lists;
    private final ElementQueryPort elements;
    private final TrainingQueryPort trainings;
    private final SearchLogUseCases searchLogs;
    private final ObjectMapper objectMapper;
    private final String internalToken;

    public InternalSearchController(ApiKeyQueryPort apiKeys, ListQueryPort lists, ElementQueryPort elements,
                                    TrainingQueryPort trainings, SearchLogUseCases searchLogs,
                                    ObjectMapper objectMapper, SearchProperties properties) {
        this.apiKeys = apiKeys;
        this.lists = lists;
        this.elements = elements;
        this.trainings = trainings;
        this.searchLogs = searchLogs;
        this.objectMapper = objectMapper;
        this.internalToken = properties.internalToken();
    }

    /** Snapshot completo de arranque: api keys en claro + metadatos de listas (sin elementos ni embeddings). */
    @GetMapping("/bootstrap")
    public BootstrapResponse bootstrap(@RequestHeader(value = "X-Internal-Token", required = false) String token) {
        requireInternalToken(token);
        List<BootstrapResponse.ApiKeyEntry> keys = apiKeys.findAll().stream()
                .map(key -> new BootstrapResponse.ApiKeyEntry(key.id(), key.userId(), key.apiKey()))
                .toList();
        List<BootstrapResponse.ListEntry> allLists = lists.findAll().stream()
                .map(list -> new BootstrapResponse.ListEntry(list.id(), list.userId(), list.name(), list.isPublic()))
                .toList();
        return new BootstrapResponse(keys, allLists, trainings.availableEmbeddingModels());
    }

    /** Datos de búsqueda completos de una lista — la carga perezosa equivalente al push al completar un training. */
    @GetMapping("/lists/{listId}")
    public ListSearchDataResponse listData(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                           @PathVariable Long listId) {
        requireInternalToken(token);
        ItemList list = lists.findById(listId)
                .orElseThrow(() -> new NotFoundException("List not found: " + listId));
        List<ListSearchDataResponse.ElementEntry> elementEntries = elements.findByListId(listId).stream()
                .map(e -> new ListSearchDataResponse.ElementEntry(e.id(), e.text(), e.params(), e.description()))
                .toList();
        Training inUse = trainings.findInUseByListId(listId).orElse(null);
        return new ListSearchDataResponse(
                list.id(), list.userId(), list.name(), list.isPublic(),
                inUse == null ? null : inUse.model(),
                inUse == null ? null : inUse.embeddingsData(),
                inUse == null ? null : inUse.elementIds(),
                elementEntries);
    }

    /** Ingesta por lotes de logs de búsqueda (fire-and-forget en el lado de búsqueda). */
    @PostMapping("/logs")
    public IngestAck ingestLogs(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                @RequestBody IngestSearchLogsRequest request) {
        requireInternalToken(token);
        List<RecordSearchCommand> commands = request.logs() == null ? List.of()
                : request.logs().stream().map(this::toCommand).toList();
        return new IngestAck(true, searchLogs.recordAll(commands));
    }

    private RecordSearchCommand toCommand(IngestSearchLogsRequest.Entry entry) {
        return new RecordSearchCommand(
                entry.userId(), entry.apiKeyId(), entry.listId(), entry.listName(),
                entry.endpoint(), entry.searchTerm(), entry.totalResults(), entry.durationMs(),
                entry.session(),
                entry.results() == null ? null : objectMapper.writeValueAsString(entry.results()),
                entry.searchedAt());
    }

    private void requireInternalToken(String token) {
        // Fallar cerrado: un token configurado en blanco lo rechaza todo en vez de permitirlo
        // (un despliegue mal configurado no debe exponer api keys en claro).
        if (internalToken == null || internalToken.isBlank() || !internalToken.equals(token)) {
            throw new ForbiddenException("Invalid internal token");
        }
    }
}
