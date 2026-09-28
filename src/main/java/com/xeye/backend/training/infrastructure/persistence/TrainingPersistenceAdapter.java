package com.xeye.backend.training.infrastructure.persistence;

import com.xeye.backend.shared.paging.Page;
import com.xeye.backend.shared.paging.Paging;
import com.xeye.backend.training.application.port.out.TrainingRepository;
import com.xeye.backend.training.domain.model.Training;
import com.xeye.backend.training.domain.model.TrainingCost;
import com.xeye.backend.training.domain.model.TrainingOption;
import com.xeye.backend.training.domain.model.TrainingStatus;
import com.xeye.backend.training.domain.model.TrainingTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class TrainingPersistenceAdapter implements TrainingRepository {

    private static final TypeReference<List<TrainingOption>> OPTION_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<Long>> ID_LIST = new TypeReference<>() {
    };
    private static final List<String> LAUNCHED_STATUSES = Arrays.stream(TrainingStatus.values())
            .filter(TrainingStatus::isLaunched)
            .map(TrainingStatus::value)
            .toList();
    private static final List<String> IN_PROGRESS_STATUSES = Arrays.stream(TrainingStatus.values())
            .filter(TrainingStatus::isInProgress)
            .map(TrainingStatus::value)
            .toList();

    private final TrainingJpaRepository jpa;
    private final TrainingEmbeddingsJpaRepository embeddings;
    private final ObjectMapper objectMapper;

    public TrainingPersistenceAdapter(TrainingJpaRepository jpa, TrainingEmbeddingsJpaRepository embeddings,
                                      ObjectMapper objectMapper) {
        this.jpa = jpa;
        this.embeddings = embeddings;
        this.objectMapper = objectMapper;
    }

    @Override
    public Page<Training> findByListIdAndUserId(Long listId, Long userId, Paging paging) {
        var result = jpa.findByListIdAndUserIdOrderByIdDesc(listId, userId,
                PageRequest.of(paging.pageNumber(), paging.limit()));
        return new Page<>(result.getContent().stream().map(this::toDomain).toList(),
                result.getTotalElements(), paging.offset(), paging.limit());
    }

    @Override
    public Optional<Training> findByIdAndUserId(Long id, Long userId) {
        return jpa.findByIdAndUserId(id, userId).map(this::toDomain);
    }

    @Override
    public Optional<Training> findById(Long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Training> findInUseByListId(Long listId) {
        return jpa.findFirstByListIdAndInUseTrueOrderByIdDesc(listId).map(this::toDomain);
    }

    @Override
    public Optional<Training> findPendingByListId(Long listId) {
        return jpa.findFirstByListIdAndStatus(listId, TrainingStatus.PENDING.value()).map(this::toDomain);
    }

    @Override
    public List<Training> findPendingByUserId(Long userId) {
        return jpa.findByUserIdAndStatusOrderByIdDesc(userId, TrainingStatus.PENDING.value())
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Training> findLaunchedWithHeartbeatBefore(Instant cutoff) {
        return jpa.findStalled(LAUNCHED_STATUSES, cutoff).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsInProgressByListId(Long listId) {
        return jpa.existsByListIdAndStatusIn(listId, IN_PROGRESS_STATUSES);
    }

    @Override
    public long countLaunched() {
        return jpa.countByStatusIn(LAUNCHED_STATUSES);
    }

    @Override
    public long countByStatus(TrainingStatus status) {
        return jpa.countByStatusIn(List.of(status.value()));
    }

    @Override
    public Map<Long, Long> countLaunchedByUser() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : jpa.countByUserIdWhereStatusIn(LAUNCHED_STATUSES)) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    @Override
    public List<Training> findQueued() {
        return jpa.findByStatusOrderByIdAsc(TrainingStatus.QUEUED.value()).stream().map(this::toDomain).toList();
    }

    @Override
    public int queuePosition(Long trainingId) {
        return (int) jpa.countByStatusAndIdLessThan(TrainingStatus.QUEUED.value(), trainingId) + 1;
    }

    @Override
    public Training save(Training training) {
        return toDomain(jpa.save(toEntity(training)));
    }

    @Override
    public void clearInUseForList(Long listId) {
        jpa.clearInUseForList(listId);
    }

    @Override
    public Optional<String> findEmbeddings(Long trainingId) {
        return embeddings.findById(trainingId).map(TrainingEmbeddingsJpaEntity::getEmbeddingsData);
    }

    @Override
    public void saveEmbeddings(Long trainingId, String embeddingsData) {
        embeddings.save(new TrainingEmbeddingsJpaEntity(trainingId, embeddingsData));
    }

    @Override
    @Transactional
    public int deleteTerminalNotInUseBefore(Instant cutoff, int batchSize) {
        return jpa.deleteTerminalNotInUseBefore(cutoff, batchSize);
    }

    private Training toDomain(TrainingJpaEntity entity) {
        return new Training(
                entity.getId(),
                entity.getListId(),
                entity.getUserId(),
                entity.getInstanceId(),
                TrainingStatus.fromString(entity.getStatus()),
                readJson(entity.getOptions(), OPTION_LIST),
                readJson(entity.getElementIds(), ID_LIST),
                entity.getDescribedCount(),
                entity.isHasEmbeddings(),
                entity.getModel(),
                readJson(entity.getTime(), TrainingTime.class),
                readJson(entity.getCost(), TrainingCost.class),
                entity.getError(),
                entity.isInUse(),
                entity.getLastHeartbeatAt(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private TrainingJpaEntity toEntity(Training training) {
        TrainingJpaEntity entity = new TrainingJpaEntity();
        entity.setId(training.id());
        entity.setListId(training.listId());
        entity.setUserId(training.userId());
        entity.setInstanceId(training.instanceId());
        entity.setStatus(training.status().value());
        entity.setOptions(writeJson(training.options()));
        entity.setElementIds(writeJson(training.elementIds()));
        entity.setDescribedCount(training.describedCount());
        entity.setHasEmbeddings(training.hasEmbeddings());
        entity.setModel(training.model());
        entity.setTime(writeJson(training.time()));
        entity.setCost(writeJson(training.cost()));
        entity.setError(training.error());
        entity.setInUse(training.inUse());
        entity.setLastHeartbeatAt(training.lastHeartbeatAt());
        // Ver ListMapper: una fila existente sin versión conocida se asume 0 (409 si es vieja).
        entity.setVersion(training.id() == null ? null : training.version() == null ? 0L : training.version());
        return entity;
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize training field", ex);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not deserialize training field", ex);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not deserialize training field", ex);
        }
    }
}
