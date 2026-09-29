package com.xeye.backend.contracts;

import com.xeye.backend.search.infrastructure.web.dto.BootstrapResponse;
import com.xeye.backend.search.infrastructure.web.dto.ListSearchDataResponse;
import com.xeye.backend.shared.security.WebhookTokens;
import com.xeye.backend.training.application.command.SearchIndexCommand;
import com.xeye.backend.training.application.command.TrainingLaunchCommand;
import com.xeye.backend.training.domain.model.TrainingOption;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Lo que este backend ENVÍA a los otros servicios, comparado campo a campo con las fixtures
 * canónicas: el job al worker, el push de índice, el bootstrap y los datos de lista al buscador.
 * Si un record cambia de nombre de campo, esto rompe aquí y no en el worker o en el buscador.
 */
class OutboundContractsTest {

    private static final String EMBEDDINGS = ContractFixtures.tree("search-index-push.json")
            .get("embeddingsData").asString();
    private static final String MODEL = ContractFixtures.tree("search-index-push.json").get("model").asString();
    private static final String ENRICHMENT_101 = ContractFixtures.tree("search-index-push.json")
            .get("elements").get(0).get("generatedDescription").asString();
    private static final String ENRICHMENT_102 = ContractFixtures.tree("search-index-push.json")
            .get("elements").get(1).get("generatedDescription").asString();

    @Test
    void trainingJobMatchesTheWorkerContract() {
        TrainingLaunchCommand command = new TrainingLaunchCommand(42L, 7L, 3L,
                "https://hooks.xeye.es/webhooks/training-update",
                WebhookTokens.issue("it-webhook-secret-0123456789abcdef0123456789", 42),
                new TrainingLaunchCommand.ListPayload(7L, "Herramientas", "Catálogo de ferretería", true),
                List.of(
                        new TrainingLaunchCommand.ElementPayload(101L, "martillo de carpintero", "Mango de madera, 500 g",
                                null, false),
                        new TrainingLaunchCommand.ElementPayload(102L, "destornillador de estrella", null, ENRICHMENT_102,
                                false)),
                List.of(new TrainingOption("train_all", true),
                        new TrainingOption("embedding_model", "paraphrase-multilingual-MiniLM-L12-v2"),
                        new TrainingOption("force_enrich", false)));

        assertEquals(ContractFixtures.tree("training-job.json"), ContractFixtures.normalize(command));
    }

    @Test
    void searchIndexPushMatchesTheSearchServiceContract() {
        SearchIndexCommand command = new SearchIndexCommand(7L, 3L, "Herramientas", true, EMBEDDINGS, MODEL,
                List.of(101L, 102L),
                List.of(
                        new SearchIndexCommand.Element(101L, "martillo de carpintero", "{\"precio\":12.5,\"stock\":8}",
                                "Mango de madera, 500 g", ENRICHMENT_101),
                        new SearchIndexCommand.Element(102L, "destornillador de estrella", null, null, ENRICHMENT_102)));

        assertEquals(ContractFixtures.tree("search-index-push.json"), ContractFixtures.normalize(command));
    }

    @Test
    void bootstrapMatchesTheSearchServiceContract() {
        JsonNode expected = ContractFixtures.tree("search-bootstrap.json");
        BootstrapResponse response = new BootstrapResponse(
                List.of(new BootstrapResponse.ApiKeyEntry(1L, 3L, expected.get("apiKeys").get(0).get("keyHash").asString())),
                null,
                List.of(new BootstrapResponse.ListEntry(7L, 3L, "Herramientas", true),
                        new BootstrapResponse.ListEntry(8L, 3L, "Privada", false)),
                null,
                List.of("paraphrase-multilingual-MiniLM-L12-v2", "paraphrase-multilingual-mpnet-base-v2"),
                List.of(new BootstrapResponse.UserLimitEntry(3L, 120)));

        assertEquals(expected, ContractFixtures.normalize(response));
    }

    @Test
    void keysetPageMatchesTheSearchServiceContract() {
        BootstrapResponse.KeysetPage<BootstrapResponse.ListEntry> page = new BootstrapResponse.KeysetPage<>(
                List.of(new BootstrapResponse.ListEntry(9L, 4L, "Otra", true)), null);

        assertEquals(ContractFixtures.tree("search-bootstrap-page.json"), ContractFixtures.normalize(page));
    }

    @Test
    void listDataMatchesTheSearchServiceContract() {
        ListSearchDataResponse response = new ListSearchDataResponse(7L, 3L, "Herramientas", true, MODEL, EMBEDDINGS,
                List.of(101L, 102L),
                List.of(
                        new ListSearchDataResponse.ElementEntry(101L, "martillo de carpintero",
                                "{\"precio\":12.5,\"stock\":8}", "Mango de madera, 500 g"),
                        new ListSearchDataResponse.ElementEntry(102L, "destornillador de estrella", null, null)));

        assertEquals(ContractFixtures.tree("search-list-data.json"), ContractFixtures.normalize(response));
    }
}
