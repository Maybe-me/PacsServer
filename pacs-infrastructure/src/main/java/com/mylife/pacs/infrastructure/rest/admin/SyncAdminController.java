package com.mylife.pacs.infrastructure.rest.admin;

import com.mylife.pacs.application.SyncJobConfigApplicationService;
import com.mylife.pacs.domain.model.SyncFailureCategory;
import com.mylife.pacs.domain.model.SyncJobConfig;
import com.mylife.pacs.domain.model.SyncJobType;
import com.mylife.pacs.infrastructure.sync.ScheduledDicomSyncService;
import com.mylife.pacs.infrastructure.sync.SyncExecutionRecordService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/sync")
public class SyncAdminController {

    private final ScheduledDicomSyncService scheduledDicomSyncService;
    private final SyncJobConfigApplicationService syncJobConfigApplicationService;

    public SyncAdminController(
            ScheduledDicomSyncService scheduledDicomSyncService,
            SyncJobConfigApplicationService syncJobConfigApplicationService
    ) {
        this.scheduledDicomSyncService = scheduledDicomSyncService;
        this.syncJobConfigApplicationService = syncJobConfigApplicationService;
    }

    @GetMapping("/jobs")
    public List<SyncJobConfig> listJobs() {
        return syncJobConfigApplicationService.listAll();
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public SyncJobConfig createJob(@RequestBody SyncJobRequest request) {
        return syncJobConfigApplicationService.create(request.toDomain(null, request.jobName(), null));
    }

    @PutMapping("/jobs/{jobName}")
    public SyncJobConfig updateJob(@PathVariable("jobName") String jobName, @RequestBody SyncJobRequest request) {
        SyncJobConfig existing = syncJobConfigApplicationService.findByJobName(jobName);
        return syncJobConfigApplicationService.update(request.toDomain(existing.id(), jobName, existing));
    }

    @DeleteMapping("/jobs/{jobName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteJob(@PathVariable("jobName") String jobName) {
        syncJobConfigApplicationService.remove(jobName);
    }

    @GetMapping("/executions")
    public List<SyncExecutionRecordService.SyncExecutionRecord> listExecutions(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "jobName", required = false) String jobName,
            @RequestParam(value = "errorCategory", required = false) String errorCategory,
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit
    ) {
        return scheduledDicomSyncService.listRecentExecutions(status, jobName, errorCategory, limit);
    }

    public List<SyncExecutionRecordService.SyncExecutionRecord> listExecutions() {
        return listExecutions(null, null, null, 50);
    }

    @GetMapping("/executions/summary")
    public SyncExecutionRecordService.SyncExecutionSummary executionSummary(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "jobName", required = false) String jobName,
            @RequestParam(value = "errorCategory", required = false) String errorCategory
    ) {
        return scheduledDicomSyncService.summarizeExecutions(status, jobName, errorCategory);
    }

    @PostMapping("/pull/{jobName}")
    public Map<String, Object> triggerPull(@PathVariable("jobName") String jobName) {
        ScheduledDicomSyncService.SyncJobResult result = scheduledDicomSyncService.triggerPullJob(jobName);
        return Map.of(
                "jobName", result.jobName(),
                "examinedCount", result.examinedCount(),
                "transferredStudyCount", result.transferredStudyCount(),
                "transferredInstanceCount", result.transferredInstanceCount()
        );
    }

    @PostMapping("/push/{jobName}")
    public Map<String, Object> triggerPush(@PathVariable("jobName") String jobName) {
        ScheduledDicomSyncService.SyncJobResult result = scheduledDicomSyncService.triggerPushJob(jobName);
        return Map.of(
                "jobName", result.jobName(),
                "examinedCount", result.examinedCount(),
                "transferredStudyCount", result.transferredStudyCount(),
                "transferredInstanceCount", result.transferredInstanceCount(),
                "sourceLoopSkipCount", result.sourceLoopSkipCount(),
                "duplicateSkipCount", result.duplicateSkipCount()
        );
    }

    public record SyncJobRequest(
            SyncJobType jobType,
            String jobName,
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
            Boolean enabled
    ) {
        public SyncJobConfig toDomain(Long id, String resolvedJobName, SyncJobConfig existing) {
            return new SyncJobConfig(
                    id,
                    resolvedJobName,
                    jobType,
                    targetAet,
                    destinationAet,
                    patientId,
                    modality,
                    studyDateLookbackDays == null ? 1 : studyDateLookbackDays,
                    preventLoopToSource == null ? Boolean.TRUE : preventLoopToSource,
                    skipRemoteDuplicates == null ? Boolean.TRUE : skipRemoteDuplicates,
                    maxStudiesPerRun == null ? 0 : maxStudiesPerRun,
                    maxInstancesPerRun == null ? 0 : maxInstancesPerRun,
                    throttleDelayMs == null ? 0L : throttleDelayMs,
                    sourceAetAllowList == null ? List.of() : List.copyOf(sourceAetAllowList),
                    sourceAetBlockList == null ? List.of() : List.copyOf(sourceAetBlockList),
                    maxRetryCount == null ? 0 : maxRetryCount,
                    failureThreshold == null ? 3 : failureThreshold,
                    paused != null && paused,
                    existing == null ? 0 : existing.consecutiveFailureCount(),
                    existing == null ? null : existing.lastErrorCategory(),
                    existing == null ? null : existing.lastErrorMessage(),
                    existing == null ? null : existing.lastSuccessAt(),
                    existing == null ? null : existing.lastFailureAt(),
                    enabled == null || enabled,
                    null,
                    null
            );
        }
    }
}
