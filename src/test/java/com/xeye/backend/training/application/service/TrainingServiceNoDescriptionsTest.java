package com.xeye.backend.training.application.service;

import com.xeye.backend.element.application.port.in.ElementQueryPort;
import com.xeye.backend.element.domain.model.Element;
import com.xeye.backend.list.application.port.in.ListQueryPort;
import com.xeye.backend.list.domain.model.ItemList;
import com.xeye.backend.training.application.command.TrainingLaunchCommand;
import com.xeye.backend.training.application.port.in.TrainingUseCases.CostEstimate;
import com.xeye.backend.training.application.port.out.SearchIndexer;
import com.xeye.backend.training.application.port.out.TrainingRepository;
import com.xeye.backend.training.config.TrainingProperties;
import com.xeye.backend.training.domain.model.Training;
import com.xeye.backend.training.domain.model.TrainingOption;
import com.xeye.backend.training.domain.model.TrainingStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Entrenar sin descripciones IA ({@code noDescriptions}): el worker recibe strategy=embeddings_only
 * con force_enrich a false (gana a regenerar) y la estimación no cobra descripciones.
 */
class TrainingServiceNoDescriptionsTest {

    private static final long USER_ID = 7L;
    private static final long LIST_ID = 3L;
    private static final long TRAINING_ID = 11L;
    private static final double FIXED_PRICE = 1.5;

    private final TrainingRepository trainings = mock(TrainingRepository.class);
    private final ListQueryPort lists = mock(ListQueryPort.class);
    private final ElementQueryPort elements = mock(ElementQueryPort.class);
    private final SearchIndexer searchIndexer = mock(SearchIndexer.class);

    private TrainingService service() {
        TrainingProperties properties = new TrainingProperties(
                "mock", "secret", "http://localhost:8000", 0,
                List.of("model-a", "model-b"), 30, 5,
                new TrainingProperties.Pricing(FIXED_PRICE, 0.0057), null, null);
        return new TrainingService(trainings, lists, elements, searchIndexer, properties);
    }

    private void givenALaunchableListWithAnUnenrichedElement() {
        when(lists.findById(LIST_ID)).thenReturn(Optional.of(
                new ItemList(LIST_ID, "list", "desc", false, USER_ID, null, null)));
        // Sin generatedDescription: un lanzamiento normal pagaría su descripción LLM.
        when(elements.findByListId(LIST_ID)).thenReturn(List.of(
                new Element(21L, LIST_ID, "text", null, "una descripción", null, true, null, null)));
    }

    @Test
    void prepareLaunchSendsEmbeddingsOnlyStrategyAndOverridesForceEnrich() {
        when(trainings.findByIdAndUserId(TRAINING_ID, USER_ID)).thenReturn(Optional.of(
                new Training(TRAINING_ID, LIST_ID, USER_ID, null, TrainingStatus.PENDING, null,
                        null, null, null, null, null, null, null, false, null, null)));
        when(trainings.existsRunningByListId(LIST_ID)).thenReturn(false);
        when(trainings.countRunning()).thenReturn(0L);
        givenALaunchableListWithAnUnenrichedElement();

        TrainingLaunchCommand command = service()
                .prepareLaunch(TRAINING_ID, USER_ID, null, true, true);

        assertTrue(command.options().contains(new TrainingOption("strategy", "embeddings_only")));
        // noDescriptions gana a regenerateDescriptions.
        assertTrue(command.options().contains(new TrainingOption("force_enrich", false)));
    }

    @Test
    void estimateChargesNoDescriptionsWhenTrainingWithoutThem() {
        givenALaunchableListWithAnUnenrichedElement();

        CostEstimate estimate = service().estimateCost(USER_ID, LIST_ID, true, true);

        assertEquals(0, estimate.descriptionsToGenerate());
        assertEquals(0.0, estimate.enrichment());
        assertEquals(FIXED_PRICE, estimate.total());
    }
}
