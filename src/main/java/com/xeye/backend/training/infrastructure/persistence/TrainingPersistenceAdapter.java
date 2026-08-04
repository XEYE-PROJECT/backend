package com.xeye.backend.training.infrastructure.persistence;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.xeye.backend.training.application.port.out.TrainingRepository;
import com.xeye.backend.training.domain.model.Training;
import com.xeye.backend.training.domain.model.TrainingCost;
import com.xeye.backend.training.domain.model.TrainingOption;
import com.xeye.backend.training.domain.model.TrainingStatus;
import com.xeye.backend.training.domain.model.TrainingTime;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
public class TrainingPersistenceAdapter implements TrainingRepository {

    private static final TypeReference<List<TrainingOption>> OPTION_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<Long>> ID_LIST = new TypeReference<>() {
    };
    private static final List<String> RUNNING_STATUSES = Arrays.stream(TrainingStatus.values())
            .filter(TrainingStatus::isRunning)
            .map(TrainingStatus::value)
            .toList();

    private final TrainingJpaRepository jpa;
    private final ObjectMapper objectMapper;

    public TrainingPersistenceAdapter(TrainingJpaRepository jpa, ObjectMapper objectMapper) {
        this.jpa = jpa;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Training> findByListIdAndUserId(Long listId, Long userId) {
        return jpa.findByListIdAndUserIdOrderByIdDesc(listId, userId).stream().map(this::toDomain).toList();
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
    public List<Training> findRunningUpdatedBefore(Instant cutoff) {
        return jpa.findByStatusInAndUpdatedAtBefore(RUNNING_STATUSES, cutoff)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsRunningByListId(Long listId) {
        return jpa.existsByListIdAndStatusIn(listId, RUNNING_STATUSES);
    }

    @Override
    public long countRunning() {
        return jpa.countByStatusIn(RUNNING_STATUSES);
    }

    @Override
    public Training save(Training training) {
        return toDomain(jpa.save(toEntity(training)));
    }

    @Override
    public void clearInUseForList(Long listId) {
        jpa.clearInUseForList(listId);
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
                entity.getEmbeddingsData(),
                entity.getModel(),
                readJson(entity.getTime(), TrainingTime.class),
                readJson(entity.getCost(), TrainingCost.class),
                entity.getError(),
                entity.isInUse(),
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
        entity.setEmbeddingsData(training.embeddingsData());
        entity.setModel(training.model());
        entity.setTime(writeJson(training.time()));
        entity.setCost(writeJson(training.cost()));
        entity.setError(training.error());
        entity.setInUse(training.inUse());
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
