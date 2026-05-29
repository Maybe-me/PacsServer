package com.mylife.pacs.infrastructure.persistence.entity;

import com.mylife.pacs.domain.model.SyncFailureCategory;
import com.mylife.pacs.domain.model.SyncJobType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "sync_job_config", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sync_job_name", columnNames = "job_name")
})
public class SyncJobConfigEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false, length = 128)
    private String jobName;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 16)
    private SyncJobType jobType;

    @Column(name = "target_aet", nullable = false, length = 64)
    private String targetAet;

    @Column(name = "destination_aet", length = 64)
    private String destinationAet;

    @Column(name = "patient_id", length = 64)
    private String patientId;

    @Column(name = "modality", length = 16)
    private String modality;

    @Column(name = "study_date_lookback_days", nullable = false)
    private Integer studyDateLookbackDays = 1;

    @Column(name = "prevent_loop_to_source", nullable = false)
    private boolean preventLoopToSource = true;

    @Column(name = "skip_remote_duplicates", nullable = false)
    private boolean skipRemoteDuplicates = true;

    @Column(name = "max_studies_per_run", nullable = false)
    private Integer maxStudiesPerRun = 0;

    @Column(name = "max_instances_per_run", nullable = false)
    private Integer maxInstancesPerRun = 0;

    @Column(name = "throttle_delay_ms", nullable = false)
    private Long throttleDelayMs = 0L;

    @Column(name = "source_aet_allow_list", columnDefinition = "TEXT")
    private String sourceAetAllowList;

    @Column(name = "source_aet_block_list", columnDefinition = "TEXT")
    private String sourceAetBlockList;

    @Column(name = "max_retry_count", nullable = false)
    private Integer maxRetryCount = 0;

    @Column(name = "failure_threshold", nullable = false)
    private Integer failureThreshold = 3;

    @Column(name = "paused", nullable = false)
    private boolean paused;

    @Column(name = "consecutive_failure_count", nullable = false)
    private Integer consecutiveFailureCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_error_category", length = 32)
    private SyncFailureCategory lastErrorCategory;

    @Column(name = "last_error_message", length = 1024)
    private String lastErrorMessage;

    @Column(name = "last_success_at")
    private java.time.Instant lastSuccessAt;

    @Column(name = "last_failure_at")
    private java.time.Instant lastFailureAt;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public SyncJobType getJobType() {
        return jobType;
    }

    public void setJobType(SyncJobType jobType) {
        this.jobType = jobType;
    }

    public String getTargetAet() {
        return targetAet;
    }

    public void setTargetAet(String targetAet) {
        this.targetAet = targetAet;
    }

    public String getDestinationAet() {
        return destinationAet;
    }

    public void setDestinationAet(String destinationAet) {
        this.destinationAet = destinationAet;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getModality() {
        return modality;
    }

    public void setModality(String modality) {
        this.modality = modality;
    }

    public Integer getStudyDateLookbackDays() {
        return studyDateLookbackDays;
    }

    public void setStudyDateLookbackDays(Integer studyDateLookbackDays) {
        this.studyDateLookbackDays = studyDateLookbackDays;
    }

    public boolean isPreventLoopToSource() {
        return preventLoopToSource;
    }

    public void setPreventLoopToSource(boolean preventLoopToSource) {
        this.preventLoopToSource = preventLoopToSource;
    }

    public boolean isSkipRemoteDuplicates() {
        return skipRemoteDuplicates;
    }

    public void setSkipRemoteDuplicates(boolean skipRemoteDuplicates) {
        this.skipRemoteDuplicates = skipRemoteDuplicates;
    }

    public Integer getMaxStudiesPerRun() {
        return maxStudiesPerRun;
    }

    public void setMaxStudiesPerRun(Integer maxStudiesPerRun) {
        this.maxStudiesPerRun = maxStudiesPerRun;
    }

    public Integer getMaxInstancesPerRun() {
        return maxInstancesPerRun;
    }

    public void setMaxInstancesPerRun(Integer maxInstancesPerRun) {
        this.maxInstancesPerRun = maxInstancesPerRun;
    }

    public Long getThrottleDelayMs() {
        return throttleDelayMs;
    }

    public void setThrottleDelayMs(Long throttleDelayMs) {
        this.throttleDelayMs = throttleDelayMs;
    }

    public String getSourceAetAllowList() {
        return sourceAetAllowList;
    }

    public void setSourceAetAllowList(String sourceAetAllowList) {
        this.sourceAetAllowList = sourceAetAllowList;
    }

    public String getSourceAetBlockList() {
        return sourceAetBlockList;
    }

    public void setSourceAetBlockList(String sourceAetBlockList) {
        this.sourceAetBlockList = sourceAetBlockList;
    }

    public Integer getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(Integer maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public Integer getFailureThreshold() {
        return failureThreshold;
    }

    public void setFailureThreshold(Integer failureThreshold) {
        this.failureThreshold = failureThreshold;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public Integer getConsecutiveFailureCount() {
        return consecutiveFailureCount;
    }

    public void setConsecutiveFailureCount(Integer consecutiveFailureCount) {
        this.consecutiveFailureCount = consecutiveFailureCount;
    }

    public SyncFailureCategory getLastErrorCategory() {
        return lastErrorCategory;
    }

    public void setLastErrorCategory(SyncFailureCategory lastErrorCategory) {
        this.lastErrorCategory = lastErrorCategory;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public java.time.Instant getLastSuccessAt() {
        return lastSuccessAt;
    }

    public void setLastSuccessAt(java.time.Instant lastSuccessAt) {
        this.lastSuccessAt = lastSuccessAt;
    }

    public java.time.Instant getLastFailureAt() {
        return lastFailureAt;
    }

    public void setLastFailureAt(java.time.Instant lastFailureAt) {
        this.lastFailureAt = lastFailureAt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
