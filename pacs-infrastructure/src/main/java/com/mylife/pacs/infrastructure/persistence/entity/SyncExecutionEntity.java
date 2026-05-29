package com.mylife.pacs.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "sync_execution")
public class SyncExecutionEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false, length = 128)
    private String jobName;

    @Column(name = "job_type", nullable = false, length = 16)
    private String jobType;

    @Column(name = "target_aet", nullable = false, length = 64)
    private String targetAet;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "examined_count", nullable = false)
    private int examinedCount;

    @Column(name = "transferred_study_count", nullable = false)
    private int transferredStudyCount;

    @Column(name = "transferred_instance_count", nullable = false)
    private int transferredInstanceCount;

    @Column(name = "skipped_existing_count", nullable = false)
    private int skippedExistingCount;

    @Column(name = "duplicate_skip_count", nullable = false)
    private int duplicateSkipCount;

    @Column(name = "source_loop_skip_count", nullable = false)
    private int sourceLoopSkipCount;

    @Column(name = "criteria_json", columnDefinition = "TEXT")
    private String criteriaJson;

    @Column(name = "error_category", length = 32)
    private String errorCategory;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

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

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public String getTargetAet() {
        return targetAet;
    }

    public void setTargetAet(String targetAet) {
        this.targetAet = targetAet;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getExaminedCount() {
        return examinedCount;
    }

    public void setExaminedCount(int examinedCount) {
        this.examinedCount = examinedCount;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public int getTransferredStudyCount() {
        return transferredStudyCount;
    }

    public void setTransferredStudyCount(int transferredStudyCount) {
        this.transferredStudyCount = transferredStudyCount;
    }

    public int getTransferredInstanceCount() {
        return transferredInstanceCount;
    }

    public void setTransferredInstanceCount(int transferredInstanceCount) {
        this.transferredInstanceCount = transferredInstanceCount;
    }

    public int getSkippedExistingCount() {
        return skippedExistingCount;
    }

    public void setSkippedExistingCount(int skippedExistingCount) {
        this.skippedExistingCount = skippedExistingCount;
    }

    public int getDuplicateSkipCount() {
        return duplicateSkipCount;
    }

    public void setDuplicateSkipCount(int duplicateSkipCount) {
        this.duplicateSkipCount = duplicateSkipCount;
    }

    public int getSourceLoopSkipCount() {
        return sourceLoopSkipCount;
    }

    public void setSourceLoopSkipCount(int sourceLoopSkipCount) {
        this.sourceLoopSkipCount = sourceLoopSkipCount;
    }

    public String getCriteriaJson() {
        return criteriaJson;
    }

    public void setCriteriaJson(String criteriaJson) {
        this.criteriaJson = criteriaJson;
    }

    public String getErrorCategory() {
        return errorCategory;
    }

    public void setErrorCategory(String errorCategory) {
        this.errorCategory = errorCategory;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
