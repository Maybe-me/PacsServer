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
@Table(name = "pacs_study", uniqueConstraints = {
        @UniqueConstraint(name = "uk_study_uid", columnNames = "study_instance_uid")
})
public class StudyEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_fk", nullable = false)
    private PatientEntity patient;

    @Column(name = "study_instance_uid", nullable = false, length = 128)
    private String studyInstanceUid;

    @Column(name = "accession_no", length = 128)
    private String accessionNo;

    @Column(name = "study_date", length = 16)
    private String studyDate;

    @Column(name = "study_time", length = 16)
    private String studyTime;

    @Column(name = "study_desc", length = 256)
    private String studyDescription;

    @Column(name = "modalities_in_study", length = 128)
    private String modalitiesInStudy;

    @Column(name = "referring_dr", length = 128)
    private String referringDoctor;

    @Column(name = "num_series", nullable = false)
    private int numSeries;

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

    public PatientEntity getPatient() {
        return patient;
    }

    public void setPatient(PatientEntity patient) {
        this.patient = patient;
    }

    public String getStudyInstanceUid() {
        return studyInstanceUid;
    }

    public void setStudyInstanceUid(String studyInstanceUid) {
        this.studyInstanceUid = studyInstanceUid;
    }

    public String getAccessionNo() {
        return accessionNo;
    }

    public void setAccessionNo(String accessionNo) {
        this.accessionNo = accessionNo;
    }

    public String getStudyDate() {
        return studyDate;
    }

    public void setStudyDate(String studyDate) {
        this.studyDate = studyDate;
    }

    public String getStudyTime() {
        return studyTime;
    }

    public void setStudyTime(String studyTime) {
        this.studyTime = studyTime;
    }

    public String getStudyDescription() {
        return studyDescription;
    }

    public void setStudyDescription(String studyDescription) {
        this.studyDescription = studyDescription;
    }

    public String getModalitiesInStudy() {
        return modalitiesInStudy;
    }

    public void setModalitiesInStudy(String modalitiesInStudy) {
        this.modalitiesInStudy = modalitiesInStudy;
    }

    public String getReferringDoctor() {
        return referringDoctor;
    }

    public void setReferringDoctor(String referringDoctor) {
        this.referringDoctor = referringDoctor;
    }

    public int getNumSeries() {
        return numSeries;
    }

    public void setNumSeries(int numSeries) {
        this.numSeries = numSeries;
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
