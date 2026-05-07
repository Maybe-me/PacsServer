package com.mylife.pacs.domain.model;

import java.time.Instant;
import java.util.List;

public record SyncJobConfig(
        Long id,
        String jobName,
        SyncJobType jobType,
        String targetAet,
        String destinationAet,
        String patientId,
        String modality,
        Integer studyDateLookbackDays,
        Boolean preventLoopToSource,
        Boolean skipRemoteDuplicates,
        Integer maxStudiesPerRun,
        Integer maxInstancesPerRun,
        Long throttleDelayMs,
        List<String> sourceAetAllowList,
        List<String> sourceAetBlockList,
        Integer maxRetryCount,
        Integer failureThreshold,
        Boolean paused,
        Integer consecutiveFailureCount,
        SyncFailureCategory lastErrorCategory,
        String lastErrorMessage,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
