package com.mylife.pacs.infrastructure.sync;

import com.mylife.pacs.domain.model.SyncFailureCategory;
import com.mylife.pacs.common.util.JsonUtil;
import com.mylife.pacs.infrastructure.persistence.entity.SyncExecutionEntity;
import com.mylife.pacs.infrastructure.persistence.springdata.SyncExecutionEntityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@Transactional
public class SyncExecutionRecordService {

    private final SyncExecutionEntityRepository repository;

    public SyncExecutionRecordService(SyncExecutionEntityRepository repository) {
        this.repository = repository;
    }

    public SyncExecutionRecord recordSuccess(
            String jobName,
            String jobType,
            String targetAet,
            Map<String, String> criteria,
            Instant startedAt,
            Instant finishedAt,
            int attemptCount,
            ScheduledDicomSyncService.SyncJobResult result
    ) {
        return save(jobName, jobType, targetAet, "SUCCESS", criteria, attemptCount, null, null, startedAt, finishedAt, result);
    }

    public SyncExecutionRecord recordFailure(
            String jobName,
            String jobType,
            String targetAet,
            Map<String, String> criteria,
            Instant startedAt,
            Instant finishedAt,
            int attemptCount,
            SyncFailureCategory errorCategory,
            Exception exception
    ) {
        return save(
                jobName,
                jobType,
                targetAet,
                "FAILED",
                criteria,
                attemptCount,
                errorCategory == null ? null : errorCategory.name(),
                exception.getMessage(),
                startedAt,
                finishedAt,
                new ScheduledDicomSyncService.SyncJobResult(jobName, 0, 0, 0, 0, 0, 0)
        );
    }

    @Transactional(readOnly = true)
    public List<SyncExecutionRecord> listRecent() {
        return listRecent(new SyncExecutionFilter(null, null, null, 50));
    }

    @Transactional(readOnly = true)
    public List<SyncExecutionRecord> listRecent(SyncExecutionFilter filter) {
        SyncExecutionFilter normalized = filter == null ? new SyncExecutionFilter(null, null, null, 50) : filter.normalize();
        return repository.findTop200ByOrderByStartedAtDesc().stream()
                .map(this::toRecord)
                .filter(record -> matches(record, normalized))
                .limit(normalized.limit())
                .toList();
    }

    @Transactional(readOnly = true)
    public SyncExecutionSummary summarize(SyncExecutionFilter filter) {
        List<SyncExecutionRecord> records = listRecent(filter == null ? new SyncExecutionFilter(null, null, null, 200) : filter.withDefaultLimit(200));
        long successCount = records.stream().filter(record -> "SUCCESS".equalsIgnoreCase(record.status())).count();
        long failedCount = records.stream().filter(record -> "FAILED".equalsIgnoreCase(record.status())).count();
        return new SyncExecutionSummary(
                records.size(),
                successCount,
                failedCount,
                groupCounts(records, SyncExecutionRecord::status),
                groupCounts(records, SyncExecutionRecord::jobName),
                groupCounts(records, record -> record.errorCategory() == null || record.errorCategory().isBlank() ? "NONE" : record.errorCategory())
        );
    }

    private SyncExecutionRecord save(
            String jobName,
            String jobType,
            String targetAet,
            String status,
            Map<String, String> criteria,
            int attemptCount,
            String errorCategory,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt,
            ScheduledDicomSyncService.SyncJobResult result
    ) {
        SyncExecutionEntity entity = new SyncExecutionEntity();
        entity.setJobName(jobName);
        entity.setJobType(jobType);
        entity.setTargetAet(targetAet);
        entity.setStatus(status);
        entity.setAttemptCount(attemptCount);
        entity.setExaminedCount(result.examinedCount());
        entity.setTransferredStudyCount(result.transferredStudyCount());
        entity.setTransferredInstanceCount(result.transferredInstanceCount());
        entity.setSkippedExistingCount(result.skippedExistingCount());
        entity.setDuplicateSkipCount(result.duplicateSkipCount());
        entity.setSourceLoopSkipCount(result.sourceLoopSkipCount());
        entity.setCriteriaJson(criteria == null ? null : JsonUtil.writeMap(criteria));
        entity.setErrorCategory(errorCategory);
        entity.setErrorMessage(errorMessage);
        entity.setStartedAt(startedAt);
        entity.setFinishedAt(finishedAt);
        return toRecord(repository.save(entity));
    }

    private SyncExecutionRecord toRecord(SyncExecutionEntity entity) {
        return new SyncExecutionRecord(
                entity.getId(),
                entity.getJobName(),
                entity.getJobType(),
                entity.getTargetAet(),
                entity.getStatus(),
                entity.getAttemptCount(),
                entity.getExaminedCount(),
                entity.getTransferredStudyCount(),
                entity.getTransferredInstanceCount(),
                entity.getSkippedExistingCount(),
                entity.getDuplicateSkipCount(),
                entity.getSourceLoopSkipCount(),
                JsonUtil.readMap(entity.getCriteriaJson()),
                entity.getErrorCategory(),
                entity.getErrorMessage(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private boolean matches(SyncExecutionRecord record, SyncExecutionFilter filter) {
        return matchesExact(record.status(), filter.status())
                && matchesContains(record.jobName(), filter.jobName())
                && matchesExact(record.errorCategory(), filter.errorCategory());
    }

    private boolean matchesExact(String actual, String expected) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return actual != null && actual.equalsIgnoreCase(expected.trim());
    }

    private boolean matchesContains(String actual, String expected) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return actual != null && actual.toLowerCase().contains(expected.trim().toLowerCase());
    }

    private Map<String, Long> groupCounts(List<SyncExecutionRecord> records, Function<SyncExecutionRecord, String> classifier) {
        LinkedHashMap<String, Long> counts = new LinkedHashMap<>();
        records.stream()
                .map(classifier)
                .forEach(key -> counts.merge(key, 1L, Long::sum));
        return Map.copyOf(counts);
    }

    public record SyncExecutionRecord(
            Long id,
            String jobName,
            String jobType,
            String targetAet,
            String status,
            int attemptCount,
            int examinedCount,
            int transferredStudyCount,
            int transferredInstanceCount,
            int skippedExistingCount,
            int duplicateSkipCount,
            int sourceLoopSkipCount,
            Map<String, String> criteria,
            String errorCategory,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record SyncExecutionFilter(
            String status,
            String jobName,
            String errorCategory,
            int limit
    ) {
        SyncExecutionFilter normalize() {
            return new SyncExecutionFilter(status, jobName, errorCategory, limit <= 0 ? 50 : Math.min(limit, 200));
        }

        SyncExecutionFilter withDefaultLimit(int defaultLimit) {
            return new SyncExecutionFilter(status, jobName, errorCategory, limit <= 0 ? defaultLimit : limit).normalize();
        }
    }

    public record SyncExecutionSummary(
            long totalCount,
            long successCount,
            long failedCount,
            Map<String, Long> statusCounts,
            Map<String, Long> jobCounts,
            Map<String, Long> errorCategoryCounts
    ) {
    }
}
