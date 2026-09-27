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
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Entrenar sin descripciones IA ({@code noDescriptions}): el worker recibe strategy=embeddings_only
 * con force_enrich a false (gana a regenerar), la estimación no cobra descripciones y el precio
 * fijado al lanzar tampoco.
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
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    private TrainingService service() {
        TrainingProperties properties = new TrainingProperties(
                "mock", "secret", "http://localhost:8000", 0,
                List.of("model-a", "model-b"), 30, 5, 1, 90,
                new TrainingProperties.Pricing(FIXED_PRICE, 0.0057), null, null);
        return new TrainingService(trainings, lists, elements, searchIndexer, properties, events);
    }

    private void givenALaunchableListWithAnUnenrichedElement() {
        when(lists.findById(LIST_ID)).thenReturn(Optional.of(
                new ItemList(LIST_ID, "list", "desc", false, USER_ID, null, null)));
        // Sin generatedDescription: un lanzamiento normal pagaría su descripción LLM.
        when(elements.findByListId(LIST_ID)).thenReturn(List.of(
                new Element(21L, LIST_ID, "text", null, "una descripción", null, true, null, null)));
        when(elements.countByListId(LIST_ID)).thenReturn(1L);
    }

    @Test
    void enqueueSendsEmbeddingsOnlyStrategyAndOverridesForceEnrich() {
        Training pending = TrainingServiceLaunchGuardTest.training(TRAINING_ID, LIST_ID, USER_ID, TrainingStatus.PENDING);
        when(trainings.findByIdAndUserId(TRAINING_ID, USER_ID)).thenReturn(Optional.of(pending));
        when(trainings.existsInProgressByListId(LIST_ID)).thenReturn(false);
        when(trainings.save(any())).thenAnswer(inv -> inv.getArgument(0));
        givenALaunchableListWithAnUnenrichedElement();

        Training queued = service().enqueue(TRAINING_ID, USER_ID, null, true, true);

        assertTrue(queued.options().contains(new TrainingOption("strategy", "embeddings_only")));
        // noDescriptions gana a regenerateDescriptions.
        assertTrue(queued.options().contains(new TrainingOption("force_enrich", false)));

        // Y al despachar, el precio fijado no incluye descripciones.
        when(trainings.findById(TRAINING_ID)).thenReturn(Optional.of(queued));
        TrainingLaunchCommand command = service().prepareLaunch(TRAINING_ID);
        assertEquals(TRAINING_ID, command.trainingId());
        assertEquals(FIXED_PRICE, queued.cost().total());
        assertEquals(0.0, queued.cost().enrichment());
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
