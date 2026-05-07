package com.mylife.pacs.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "pacs_instance", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sop_instance_uid", columnNames = "sop_instance_uid")
})
public class InstanceEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_fk", nullable = false)
    private SeriesEntity series;

    @Column(name = "sop_instance_uid", nullable = false, length = 128)
    private String sopInstanceUid;

    @Column(name = "sop_class_uid", length = 64)
    private String sopClassUid;

    @Column(name = "transfer_syntax_uid", length = 64)
    private String transferSyntaxUid;

    @Column(name = "instance_number")
    private Integer instanceNumber;

    @Column(name = "file_path", nullable = false, length = 512)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_md5", length = 32)
    private String fileMd5;

    @Column(name = "storage_type", nullable = false, length = 32)
    private String storageType;

    @Column(name = "storage_bucket", length = 255)
    private String storageBucket;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Lob
    @Column(name = "extra_tags")
    private String extraTagsJson;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SeriesEntity getSeries() {
        return series;
    }

    public void setSeries(SeriesEntity series) {
        this.series = series;
    }

    public String getSopInstanceUid() {
        return sopInstanceUid;
    }

    public void setSopInstanceUid(String sopInstanceUid) {
        this.sopInstanceUid = sopInstanceUid;
    }

    public String getSopClassUid() {
        return sopClassUid;
    }

    public void setSopClassUid(String sopClassUid) {
        this.sopClassUid = sopClassUid;
    }

    public String getTransferSyntaxUid() {
        return transferSyntaxUid;
    }

    public void setTransferSyntaxUid(String transferSyntaxUid) {
        this.transferSyntaxUid = transferSyntaxUid;
    }

    public Integer getInstanceNumber() {
        return instanceNumber;
    }

    public void setInstanceNumber(Integer instanceNumber) {
        this.instanceNumber = instanceNumber;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileMd5() {
        return fileMd5;
    }

    public void setFileMd5(String fileMd5) {
        this.fileMd5 = fileMd5;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public String getStorageBucket() {
        return storageBucket;
    }

    public void setStorageBucket(String storageBucket) {
        this.storageBucket = storageBucket;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getExtraTagsJson() {
        return extraTagsJson;
    }

    public void setExtraTagsJson(String extraTagsJson) {
        this.extraTagsJson = extraTagsJson;
    }
}
