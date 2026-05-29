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
@Table(name = "pacs_series", uniqueConstraints = {
        @UniqueConstraint(name = "uk_series_uid", columnNames = "series_instance_uid")
})
public class SeriesEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_fk", nullable = false)
    private StudyEntity study;

    @Column(name = "series_instance_uid", nullable = false, length = 128)
    private String seriesInstanceUid;

    @Column(name = "modality", length = 32)
    private String modality;

    @Column(name = "series_desc", length = 256)
    private String seriesDescription;

    @Column(name = "body_part_examined", length = 64)
    private String bodyPartExamined;

    @Column(name = "series_number")
    private Integer seriesNumber;

    @Column(name = "num_instances", nullable = false)
    private int numInstances;

    @Column(name = "extra_tags", columnDefinition = "TEXT")
    private String extraTagsJson;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public StudyEntity getStudy() {
        return study;
    }

    public void setStudy(StudyEntity study) {
        this.study = study;
    }

    public String getSeriesInstanceUid() {
        return seriesInstanceUid;
    }

    public void setSeriesInstanceUid(String seriesInstanceUid) {
        this.seriesInstanceUid = seriesInstanceUid;
    }

    public String getModality() {
        return modality;
    }

    public void setModality(String modality) {
        this.modality = modality;
    }

    public String getSeriesDescription() {
        return seriesDescription;
    }

    public void setSeriesDescription(String seriesDescription) {
        this.seriesDescription = seriesDescription;
    }

    public String getBodyPartExamined() {
        return bodyPartExamined;
    }

    public void setBodyPartExamined(String bodyPartExamined) {
        this.bodyPartExamined = bodyPartExamined;
    }

    public Integer getSeriesNumber() {
        return seriesNumber;
    }

    public void setSeriesNumber(Integer seriesNumber) {
        this.seriesNumber = seriesNumber;
    }

    public int getNumInstances() {
        return numInstances;
    }

    public void setNumInstances(int numInstances) {
        this.numInstances = numInstances;
    }

    public String getExtraTagsJson() {
        return extraTagsJson;
    }

    public void setExtraTagsJson(String extraTagsJson) {
        this.extraTagsJson = extraTagsJson;
    }
}
