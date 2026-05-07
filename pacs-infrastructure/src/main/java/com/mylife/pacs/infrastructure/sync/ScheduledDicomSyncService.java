package com.mylife.pacs.infrastructure.sync;

import com.mylife.pacs.application.DicomApplicationService;
import com.mylife.pacs.application.RemoteDicomApplicationService;
import com.mylife.pacs.application.SyncJobConfigApplicationService;
import com.mylife.pacs.common.config.DicomPacsProperties;
import com.mylife.pacs.domain.model.PacsStudy;
import com.mylife.pacs.domain.model.SyncFailureCategory;
import com.mylife.pacs.domain.model.SyncJobConfig;
import com.mylife.pacs.domain.model.SyncJobType;
import com.mylife.pacs.domain.service.StudyQueryCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ScheduledDicomSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledDicomSyncService.class);
    private static final DateTimeFormatter DICOM_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final DicomPacsProperties properties;
    private final RemoteDicomApplicationService remoteDicomApplicationService;
    private final DicomApplicationService dicomApplicationService;
    private final RemoteDicomPushService remoteDicomPushService;
    private final SyncExecutionRecordService syncExecutionRecordService;
    private final SyncJobConfigApplicationService syncJobConfigApplicationService;

    public ScheduledDicomSyncService(
            DicomPacsProperties properties,
            RemoteDicomApplicationService remoteDicomApplicationService,
            DicomApplicationService dicomApplicationService,
            RemoteDicomPushService remoteDicomPushService,
            SyncExecutionRecordService syncExecutionRecordService,
            SyncJobConfigApplicationService syncJobConfigApplicationService
    ) {
        this.properties = properties;
        this.remoteDicomApplicationService = remoteDicomApplicationService;
        this.dicomApplicationService = dicomApplicationService;
        this.remoteDicomPushService = remoteDicomPushService;
        this.syncExecutionRecordService = syncExecutionRecordService;
        this.syncJobConfigApplicationService = syncJobConfigApplicationService;
    }

    public List<SyncJobResult> runConfiguredPullJobs() {
        if (!properties.getSync().isEnabled()) {
            return List.of();
        }
        List<SyncJobResult> results = new java.util.ArrayList<>();
        List<DicomPacsProperties.Sync.PullJob> jobs = configuredPullJobs();
        for (DicomPacsProperties.Sync.PullJob job : jobs) {
            try {
                results.add(executePullJob(job, findPersistedJobConfig(job.getJobName()).orElse(null)));
            } catch (Exception exception) {
                LOGGER.error("Scheduled pull job {} failed: {}", job.getJobName(), exception.getMessage(), exception);
            }
        }
        return List.copyOf(results);
    }

    public List<SyncJobResult> runConfiguredPushJobs() {
        if (!properties.getSync().isEnabled()) {
            return List.of();
        }
        List<SyncJobResult> results = new java.util.ArrayList<>();
        List<DicomPacsProperties.Sync.PushJob> jobs = configuredPushJobs();
        for (DicomPacsProperties.Sync.PushJob job : jobs) {
            try {
                results.add(executePushJob(job, findPersistedJobConfig(job.getJobName()).orElse(null)));
            } catch (Exception exception) {
                LOGGER.error("Scheduled push job {} failed: {}", job.getJobName(), exception.getMessage(), exception);
            }
        }
        return List.copyOf(results);
    }

    public SyncJobResult triggerPullJob(String jobName) {
        return executePullJob(findPersistedPullJob(jobName).orElseGet(() -> findPullJob(jobName)), findPersistedJobConfig(jobName).orElse(null));
    }

    public SyncJobResult triggerPushJob(String jobName) {
        return executePushJob(findPersistedPushJob(jobName).orElseGet(() -> findPushJob(jobName)), findPersistedJobConfig(jobName).orElse(null));
    }

    public List<SyncExecutionRecordService.SyncExecutionRecord> listRecentExecutions() {
        return syncExecutionRecordService.listRecent();
    }

    public List<SyncExecutionRecordService.SyncExecutionRecord> listRecentExecutions(String status, String jobName, String errorCategory, int limit) {
        return syncExecutionRecordService.listRecent(new SyncExecutionRecordService.SyncExecutionFilter(status, jobName, errorCategory, limit));
    }

    public SyncExecutionRecordService.SyncExecutionSummary summarizeExecutions(String status, String jobName, String errorCategory) {
        return syncExecutionRecordService.summarize(new SyncExecutionRecordService.SyncExecutionFilter(status, jobName, errorCategory, 200));
    }

    public List<SyncJobConfig> listPersistedJobs() {
        return syncJobConfigApplicationService.listAll();
    }

    public SyncJobResult runPullJob(DicomPacsProperties.Sync.PullJob job) {
        require(job.getTargetAet(), "pull targetAet");
        List<Map<String, String>> remoteStudies = remoteDicomApplicationService.findStudies(
                job.getTargetAet(),
                properties.getLocalAet(),
                buildRemoteFindCriteria(job.getPatientId(), job.getModality(), job.getStudyDateLookbackDays())
        );

        int skippedExisting = 0;
        int movedStudies = 0;
        int movedInstances = 0;
        int examinedStudies = 0;
        for (Map<String, String> remoteStudy : remoteStudies) {
            if (reachedStudyLimit(examinedStudies, job.getMaxStudiesPerRun())) {
                break;
            }
            String studyInstanceUid = firstNonBlank(remoteStudy.get("0020000D"), remoteStudy.get("studyInstanceUid"));
            if (studyInstanceUid == null) {
                continue;
            }
            if (reachedInstanceLimit(movedInstances, job.getMaxInstancesPerRun(), remoteStudy)) {
                break;
            }
            examinedStudies++;
            if (!dicomApplicationService.findStudies(new StudyQueryCriteria(
                    null,
                    null,
                    studyInstanceUid,
                    null,
                    null,
                    null,
                    null
            )).isEmpty()) {
                skippedExisting++;
                continue;
            }
            movedInstances += remoteDicomApplicationService.pullFromRemote(
                    job.getTargetAet(),
                    properties.getLocalAet(),
                    Map.of("0020000D", studyInstanceUid),
                    firstNonBlank(job.getDestinationAet(), properties.getLocalAet())
            );
            movedStudies++;
            sleepIfNeeded(job.getThrottleDelayMs());
        }

        SyncJobResult result = new SyncJobResult(job.getJobName(), examinedStudies, movedStudies, movedInstances, skippedExisting, 0, 0);
        LOGGER.info("Scheduled pull job {} completed: examined={}, movedStudies={}, movedInstances={}, skippedExisting={}",
                result.jobName(), result.examinedCount(), result.transferredStudyCount(), result.transferredInstanceCount(), result.skippedExistingCount());
        return result;
    }

    public SyncJobResult runPushJob(DicomPacsProperties.Sync.PushJob job) {
        require(job.getTargetAet(), "push targetAet");
        List<PacsStudy> localStudies = dicomApplicationService.findStudies(new StudyQueryCriteria(
                job.getPatientId(),
                null,
                null,
                null,
                job.getModality(),
                dateRange(job.getStudyDateLookbackDays())[0],
                dateRange(job.getStudyDateLookbackDays())[1]
        ));

        int pushedStudies = 0;
        int pushedInstances = 0;
        int duplicateSkips = 0;
        int sourceLoopSkips = 0;
        int examinedStudies = 0;
        for (PacsStudy localStudy : localStudies) {
            if (reachedStudyLimit(examinedStudies, job.getMaxStudiesPerRun())) {
                break;
            }
            if (job.getMaxInstancesPerRun() > 0 && pushedInstances >= job.getMaxInstancesPerRun()) {
                break;
            }
            examinedStudies++;
            RemoteDicomPushService.PushResult pushResult = remoteDicomPushService.pushToRemote(
                    job.getTargetAet(),
                    Map.of("0020000D", localStudy.studyInstanceUid()),
                    new RemoteDicomPushService.PushOptions(
                            job.isSkipRemoteDuplicates(),
                            job.isPreventLoopToSource(),
                            remainingInstanceBudget(job.getMaxInstancesPerRun(), pushedInstances),
                            job.getSourceAetAllowList(),
                            job.getSourceAetBlockList()
                    )
            );
            if (pushResult.pushedCount() > 0) {
                pushedStudies++;
            }
            pushedInstances += pushResult.pushedCount();
            duplicateSkips += pushResult.duplicateSkipCount();
            sourceLoopSkips += pushResult.sourceLoopSkipCount();
            sleepIfNeeded(job.getThrottleDelayMs());
        }

        SyncJobResult result = new SyncJobResult(job.getJobName(), examinedStudies, pushedStudies, pushedInstances, 0, duplicateSkips, sourceLoopSkips);
        LOGGER.info("Scheduled push job {} completed: examined={}, pushedStudies={}, pushedInstances={}, duplicateSkips={}, sourceLoopSkips={}",
                result.jobName(), result.examinedCount(), result.transferredStudyCount(), result.transferredInstanceCount(),
                result.duplicateSkipCount(), result.sourceLoopSkipCount());
        return result;
    }

    private SyncJobResult executePullJob(DicomPacsProperties.Sync.PullJob job, SyncJobConfig persistedJob) {
        Instant startedAt = Instant.now();
        Map<String, String> criteria = Map.of(
                "patientId", nullToEmpty(job.getPatientId()),
                "modality", nullToEmpty(job.getModality()),
                "studyDateLookbackDays", Integer.toString(job.getStudyDateLookbackDays()),
                "destinationAet", nullToEmpty(job.getDestinationAet()),
                "maxStudiesPerRun", Integer.toString(job.getMaxStudiesPerRun()),
                "maxInstancesPerRun", Integer.toString(job.getMaxInstancesPerRun()),
                "throttleDelayMs", Long.toString(job.getThrottleDelayMs())
        );
        return executeWithGovernance(job.getJobName(), "PULL", job.getTargetAet(), criteria, persistedJob, () -> runPullJob(job), startedAt);
    }

    private SyncJobResult executePushJob(DicomPacsProperties.Sync.PushJob job, SyncJobConfig persistedJob) {
        Instant startedAt = Instant.now();
        Map<String, String> criteria = Map.of(
                "patientId", nullToEmpty(job.getPatientId()),
                "modality", nullToEmpty(job.getModality()),
                "studyDateLookbackDays", Integer.toString(job.getStudyDateLookbackDays()),
                "preventLoopToSource", Boolean.toString(job.isPreventLoopToSource()),
                "skipRemoteDuplicates", Boolean.toString(job.isSkipRemoteDuplicates()),
                "maxStudiesPerRun", Integer.toString(job.getMaxStudiesPerRun()),
                "maxInstancesPerRun", Integer.toString(job.getMaxInstancesPerRun()),
                "throttleDelayMs", Long.toString(job.getThrottleDelayMs()),
                "sourceAetAllowList", String.join(",", Optional.ofNullable(job.getSourceAetAllowList()).orElse(List.of())),
                "sourceAetBlockList", String.join(",", Optional.ofNullable(job.getSourceAetBlockList()).orElse(List.of()))
        );
        return executeWithGovernance(job.getJobName(), "PUSH", job.getTargetAet(), criteria, persistedJob, () -> runPushJob(job), startedAt);
    }

    private DicomPacsProperties.Sync.PullJob findPullJob(String jobName) {
        return properties.getSync().getPullJobs().stream()
                .filter(job -> job.getJobName().equals(jobName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown pull job: " + jobName));
    }

    private DicomPacsProperties.Sync.PushJob findPushJob(String jobName) {
        return properties.getSync().getPushJobs().stream()
                .filter(job -> job.getJobName().equals(jobName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown push job: " + jobName));
    }

    private java.util.Optional<DicomPacsProperties.Sync.PullJob> findPersistedPullJob(String jobName) {
        return syncJobConfigApplicationService.listAll().stream()
                .filter(job -> job.jobType() == SyncJobType.PULL)
                .filter(job -> job.jobName().equals(jobName))
                .findFirst()
                .map(this::toPullJob);
    }

    private java.util.Optional<DicomPacsProperties.Sync.PushJob> findPersistedPushJob(String jobName) {
        return syncJobConfigApplicationService.listAll().stream()
                .filter(job -> job.jobType() == SyncJobType.PUSH)
                .filter(job -> job.jobName().equals(jobName))
                .findFirst()
                .map(this::toPushJob);
    }

    private java.util.Optional<SyncJobConfig> findPersistedJobConfig(String jobName) {
        return syncJobConfigApplicationService.listAll().stream()
                .filter(job -> job.jobName().equals(jobName))
                .findFirst();
    }

    private List<DicomPacsProperties.Sync.PullJob> configuredPullJobs() {
        List<SyncJobConfig> persistedJobs = syncJobConfigApplicationService.listEnabled().stream()
                .filter(job -> job.jobType() == SyncJobType.PULL)
                .filter(job -> !Boolean.TRUE.equals(job.paused()))
                .toList();
        if (!persistedJobs.isEmpty()) {
            return persistedJobs.stream().map(this::toPullJob).toList();
        }
        return properties.getSync().getPullJobs().stream()
                .filter(DicomPacsProperties.Sync.PullJob::isEnabled)
                .toList();
    }

    private List<DicomPacsProperties.Sync.PushJob> configuredPushJobs() {
        List<SyncJobConfig> persistedJobs = syncJobConfigApplicationService.listEnabled().stream()
                .filter(job -> job.jobType() == SyncJobType.PUSH)
                .filter(job -> !Boolean.TRUE.equals(job.paused()))
                .toList();
        if (!persistedJobs.isEmpty()) {
            return persistedJobs.stream().map(this::toPushJob).toList();
        }
        return properties.getSync().getPushJobs().stream()
                .filter(DicomPacsProperties.Sync.PushJob::isEnabled)
                .toList();
    }

    private DicomPacsProperties.Sync.PullJob toPullJob(SyncJobConfig job) {
        DicomPacsProperties.Sync.PullJob mapped = new DicomPacsProperties.Sync.PullJob();
        mapped.setJobName(job.jobName());
        mapped.setEnabled(job.enabled());
        mapped.setTargetAet(job.targetAet());
        mapped.setDestinationAet(job.destinationAet());
        mapped.setPatientId(job.patientId());
        mapped.setModality(job.modality());
        mapped.setStudyDateLookbackDays(job.studyDateLookbackDays() == null ? 1 : job.studyDateLookbackDays());
        mapped.setMaxStudiesPerRun(job.maxStudiesPerRun() == null ? 0 : job.maxStudiesPerRun());
        mapped.setMaxInstancesPerRun(job.maxInstancesPerRun() == null ? 0 : job.maxInstancesPerRun());
        mapped.setThrottleDelayMs(job.throttleDelayMs() == null ? 0L : job.throttleDelayMs());
        mapped.setEnabled(job.enabled());
        return mapped;
    }

    private DicomPacsProperties.Sync.PushJob toPushJob(SyncJobConfig job) {
        DicomPacsProperties.Sync.PushJob mapped = new DicomPacsProperties.Sync.PushJob();
        mapped.setJobName(job.jobName());
        mapped.setEnabled(job.enabled());
        mapped.setTargetAet(job.targetAet());
        mapped.setPatientId(job.patientId());
        mapped.setModality(job.modality());
        mapped.setStudyDateLookbackDays(job.studyDateLookbackDays() == null ? 1 : job.studyDateLookbackDays());
        mapped.setPreventLoopToSource(job.preventLoopToSource() == null || job.preventLoopToSource());
        mapped.setSkipRemoteDuplicates(job.skipRemoteDuplicates() == null || job.skipRemoteDuplicates());
        mapped.setMaxStudiesPerRun(job.maxStudiesPerRun() == null ? 0 : job.maxStudiesPerRun());
        mapped.setMaxInstancesPerRun(job.maxInstancesPerRun() == null ? 0 : job.maxInstancesPerRun());
        mapped.setThrottleDelayMs(job.throttleDelayMs() == null ? 0L : job.throttleDelayMs());
        mapped.setSourceAetAllowList(job.sourceAetAllowList());
        mapped.setSourceAetBlockList(job.sourceAetBlockList());
        return mapped;
    }

    private SyncJobResult executeWithGovernance(
            String jobName,
            String jobType,
            String targetAet,
            Map<String, String> criteria,
            SyncJobConfig persistedJob,
            JobExecution execution,
            Instant startedAt
    ) {
        int maxRetries = persistedJob == null || persistedJob.maxRetryCount() == null ? 0 : Math.max(0, persistedJob.maxRetryCount());
        Exception lastException = null;
        SyncFailureCategory lastCategory = null;
        int attemptsUsed = 0;

        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            attemptsUsed = attempt;
            try {
                SyncJobResult result = execution.run();
                if (persistedJob != null) {
                    syncJobConfigApplicationService.update(onSuccess(persistedJob, Instant.now()));
                }
                syncExecutionRecordService.recordSuccess(jobName, jobType, targetAet, criteria, startedAt, Instant.now(), attempt, result);
                return result;
            } catch (Exception exception) {
                lastException = exception;
                lastCategory = classifyFailure(exception);
                boolean retryable = isRetryable(lastCategory);
                if (attempt <= maxRetries && retryable) {
                    LOGGER.warn("Sync job {} attempt {}/{} failed with {}: {}", jobName, attempt, maxRetries + 1, lastCategory, exception.getMessage());
                    continue;
                }
                break;
            }
        }

        if (persistedJob != null && lastException != null) {
            syncJobConfigApplicationService.update(onFailure(persistedJob, lastCategory, lastException.getMessage(), Instant.now()));
        }
        syncExecutionRecordService.recordFailure(
                jobName,
                jobType,
                targetAet,
                criteria,
                startedAt,
                Instant.now(),
                attemptsUsed,
                lastCategory,
                lastException == null ? new IllegalStateException("Unknown sync failure") : lastException
        );
        throw new IllegalStateException(lastException == null ? "Unknown sync failure" : lastException.getMessage(), lastException);
    }

    private SyncJobConfig onSuccess(SyncJobConfig job, Instant timestamp) {
        return new SyncJobConfig(
                job.id(),
                job.jobName(),
                job.jobType(),
                job.targetAet(),
                job.destinationAet(),
                job.patientId(),
                job.modality(),
                job.studyDateLookbackDays(),
                job.preventLoopToSource(),
                job.skipRemoteDuplicates(),
                job.maxStudiesPerRun(),
                job.maxInstancesPerRun(),
                job.throttleDelayMs(),
                job.sourceAetAllowList(),
                job.sourceAetBlockList(),
                job.maxRetryCount(),
                job.failureThreshold(),
                false,
                0,
                null,
                null,
                timestamp,
                job.lastFailureAt(),
                job.enabled(),
                job.createdAt(),
                job.updatedAt()
        );
    }

    private SyncJobConfig onFailure(SyncJobConfig job, SyncFailureCategory category, String message, Instant timestamp) {
        int failureCount = (job.consecutiveFailureCount() == null ? 0 : job.consecutiveFailureCount()) + 1;
        int threshold = job.failureThreshold() == null ? 3 : Math.max(1, job.failureThreshold());
        boolean paused = failureCount >= threshold;
        return new SyncJobConfig(
                job.id(),
                job.jobName(),
                job.jobType(),
                job.targetAet(),
                job.destinationAet(),
                job.patientId(),
                job.modality(),
                job.studyDateLookbackDays(),
                job.preventLoopToSource(),
                job.skipRemoteDuplicates(),
                job.maxStudiesPerRun(),
                job.maxInstancesPerRun(),
                job.throttleDelayMs(),
                job.sourceAetAllowList(),
                job.sourceAetBlockList(),
                job.maxRetryCount(),
                job.failureThreshold(),
                paused,
                failureCount,
                category,
                truncate(message, 1024),
                job.lastSuccessAt(),
                timestamp,
                job.enabled(),
                job.createdAt(),
                job.updatedAt()
        );
    }

    private SyncFailureCategory classifyFailure(Exception exception) {
        String message = fullErrorMessage(exception);
        String lower = message.toLowerCase();
        if (lower.contains("unknown sync job") || lower.contains("missing scheduled sync configuration")) {
            return SyncFailureCategory.CONFIGURATION;
        }
        if (lower.contains("refused") || lower.contains("timeout") || lower.contains("timed out") || lower.contains("connect")) {
            return SyncFailureCategory.CONNECTIVITY;
        }
        if (lower.contains("presentation context") || lower.contains("eofexception") || lower.contains("bad dicom") || lower.contains("malformed")) {
            return SyncFailureCategory.DATA;
        }
        if (lower.contains("duplicate sop instance uid") || lower.contains("c-find failed") || lower.contains("c-move failed") || lower.contains("c-store failed")) {
            return SyncFailureCategory.REMOTE_DICOM;
        }
        return SyncFailureCategory.UNKNOWN;
    }

    private String fullErrorMessage(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append(" | ");
                }
                builder.append(current.getMessage());
            }
            current = current.getCause();
        }
        return builder.toString();
    }

    private boolean isRetryable(SyncFailureCategory category) {
        return category == SyncFailureCategory.CONNECTIVITY || category == SyncFailureCategory.REMOTE_DICOM || category == SyncFailureCategory.UNKNOWN;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private Map<String, String> buildRemoteFindCriteria(String patientId, String modality, int lookbackDays) {
        LinkedHashMap<String, String> criteria = new LinkedHashMap<>();
        if (patientId != null && !patientId.isBlank()) {
            criteria.put("00100020", patientId);
        }
        if (modality != null && !modality.isBlank()) {
            criteria.put("00080061", modality);
        }
        String[] dateRange = dateRange(lookbackDays);
        criteria.put("00080020", dateRange[0] + "-" + dateRange[1]);
        return Map.copyOf(criteria);
    }

    private String[] dateRange(int lookbackDays) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(Math.max(0, lookbackDays));
        return new String[]{from.format(DICOM_DATE), today.format(DICOM_DATE)};
    }

    private boolean reachedStudyLimit(int examinedStudies, int maxStudiesPerRun) {
        return maxStudiesPerRun > 0 && examinedStudies >= maxStudiesPerRun;
    }

    private boolean reachedInstanceLimit(int transferredInstances, int maxInstancesPerRun, Map<String, String> remoteStudy) {
        if (maxInstancesPerRun <= 0) {
            return false;
        }
        Integer instanceCount = parseInteger(firstNonBlank(remoteStudy.get("00201208"), remoteStudy.get("numInstances")));
        return transferredInstances >= maxInstancesPerRun
                || (instanceCount != null && transferredInstances + instanceCount > maxInstancesPerRun);
    }

    private int remainingInstanceBudget(int maxInstancesPerRun, int transferredInstances) {
        if (maxInstancesPerRun <= 0) {
            return 0;
        }
        return Math.max(0, maxInstancesPerRun - transferredInstances);
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void sleepIfNeeded(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Scheduled sync throttling interrupted", exception);
        }
    }

    private void require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing scheduled sync configuration: " + label);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public record SyncJobResult(
            String jobName,
            int examinedCount,
            int transferredStudyCount,
            int transferredInstanceCount,
            int skippedExistingCount,
            int duplicateSkipCount,
            int sourceLoopSkipCount
    ) {
    }

    @FunctionalInterface
    private interface JobExecution {
        SyncJobResult run();
    }
}
