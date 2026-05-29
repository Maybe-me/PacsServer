package com.mylife.pacs.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "pacs_patient", uniqueConstraints = {
        @UniqueConstraint(name = "uk_patient_id", columnNames = {"patient_id", "issuer_of_patient_id"})
})
public class PatientEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false, length = 128)
    private String patientId;

    @Column(name = "issuer_of_patient_id", nullable = false, length = 128)
    private String issuerOfPatientId = "";

    @Column(name = "patient_name", length = 256)
    private String patientName;

    @Column(name = "patient_sex", length = 4)
    private String patientSex;

    @Column(name = "patient_birth_date", length = 32)
    private String patientBirthDate;

    @Column(name = "extra_tags", columnDefinition = "TEXT")
    private String extraTagsJson;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getIssuerOfPatientId() {
        return issuerOfPatientId;
    }

    public void setIssuerOfPatientId(String issuerOfPatientId) {
        this.issuerOfPatientId = issuerOfPatientId;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getPatientSex() {
        return patientSex;
    }

    public void setPatientSex(String patientSex) {
        this.patientSex = patientSex;
    }

    public String getPatientBirthDate() {
        return patientBirthDate;
    }

    public void setPatientBirthDate(String patientBirthDate) {
        this.patientBirthDate = patientBirthDate;
    }

    public String getExtraTagsJson() {
        return extraTagsJson;
    }

    public void setExtraTagsJson(String extraTagsJson) {
        this.extraTagsJson = extraTagsJson;
    }
}
