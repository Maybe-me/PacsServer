package com.mylife.pacs.infrastructure.dimse;

import com.mylife.pacs.application.DicomApplicationService;
import com.mylife.pacs.common.config.DicomPacsProperties;
import com.mylife.pacs.domain.model.PacsPatient;
import com.mylife.pacs.domain.model.PacsSeries;
import com.mylife.pacs.domain.model.PacsStudy;
import com.mylife.pacs.domain.service.PatientQueryCriteria;
import com.mylife.pacs.domain.service.SeriesQueryCriteria;
import com.mylife.pacs.domain.service.StudyQueryCriteria;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCFindSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.QueryTask;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class Dcm4cheCFindScp extends BasicCFindSCP {

    private final DicomApplicationService dicomApplicationService;
    private final DicomPacsProperties properties;

    public Dcm4cheCFindScp(DicomApplicationService dicomApplicationService, DicomPacsProperties properties) {
        super(
                UID.PatientRootQueryRetrieveInformationModelFind,
                UID.StudyRootQueryRetrieveInformationModelFind
        );
        this.dicomApplicationService = dicomApplicationService;
        this.properties = properties;
    }

    @Override
    protected QueryTask calculateMatches(Association association, PresentationContext presentationContext, Attributes request, Attributes keys)
            throws DicomServiceException {
        if (!properties.getScp().isFindEnabled()) {
            throw new DicomServiceException(Status.SOPclassNotSupported, "C-FIND SCP is disabled");
        }

        String level = keys.getString(Tag.QueryRetrieveLevel);
        if (level == null || level.isBlank()) {
            throw new DicomServiceException(Status.IdentifierDoesNotMatchSOPClass, "Missing QueryRetrieveLevel");
        }

        List<Attributes> results = switch (level.toUpperCase()) {
            case "PATIENT" -> queryPatients(keys);
            case "STUDY" -> queryStudies(keys);
            case "SERIES" -> querySeries(keys);
            default -> throw new DicomServiceException(Status.UnableToProcess, "Unsupported QueryRetrieveLevel: " + level);
        };
        return new LocalQueryTask(association, presentationContext, request, keys, results);
    }

    private List<Attributes> queryPatients(Attributes keys) {
        return dicomApplicationService.findPatients(new PatientQueryCriteria(
                        keys.getString(Tag.PatientID),
                        keys.getString(Tag.IssuerOfPatientID),
                        keys.getString(Tag.PatientName)
                ))
                .stream()
                .map(this::toPatientAttributes)
                .toList();
    }

    private List<Attributes> queryStudies(Attributes keys) {
        String[] studyDateRange = splitRange(keys.getString(Tag.StudyDate));
        return dicomApplicationService.findStudies(new StudyQueryCriteria(
                        keys.getString(Tag.PatientID),
                        keys.getString(Tag.IssuerOfPatientID),
                        keys.getString(Tag.StudyInstanceUID),
                        keys.getString(Tag.AccessionNumber),
                        firstNonBlank(keys.getString(Tag.ModalitiesInStudy), keys.getString(Tag.Modality)),
                        studyDateRange[0],
                        studyDateRange[1]
                ))
                .stream()
                .map(this::toStudyAttributes)
                .toList();
    }

    private List<Attributes> querySeries(Attributes keys) {
        return dicomApplicationService.findSeries(new SeriesQueryCriteria(
                        keys.getString(Tag.StudyInstanceUID),
                        keys.getString(Tag.SeriesInstanceUID),
                        keys.getString(Tag.Modality)
                ))
                .stream()
                .map(this::toSeriesAttributes)
                .toList();
    }

    private Attributes toPatientAttributes(PacsPatient patient) {
        Attributes attributes = new Attributes();
        attributes.setString(Tag.QueryRetrieveLevel, VR.CS, "PATIENT");
        setIfPresent(attributes, Tag.PatientID, VR.LO, patient.patientId());
        setIfPresent(attributes, Tag.IssuerOfPatientID, VR.LO, patient.issuerOfPatientId());
        setIfPresent(attributes, Tag.PatientName, VR.PN, patient.patientName());
        setIfPresent(attributes, Tag.PatientSex, VR.CS, patient.patientSex());
        setIfPresent(attributes, Tag.PatientBirthDate, VR.DA, patient.patientBirthDate());
        return attributes;
    }

    private Attributes toStudyAttributes(PacsStudy study) {
        Attributes attributes = new Attributes();
        attributes.setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
        setIfPresent(attributes, Tag.StudyInstanceUID, VR.UI, study.studyInstanceUid());
        setIfPresent(attributes, Tag.AccessionNumber, VR.SH, study.accessionNo());
        setIfPresent(attributes, Tag.StudyDate, VR.DA, study.studyDate());
        setIfPresent(attributes, Tag.StudyTime, VR.TM, study.studyTime());
        setIfPresent(attributes, Tag.StudyDescription, VR.LO, study.studyDescription());
        setIfPresent(attributes, Tag.ModalitiesInStudy, VR.CS, study.modalitiesInStudy());
        setIfPresent(attributes, Tag.ReferringPhysicianName, VR.PN, study.referringDoctor());
        attributes.setInt(Tag.NumberOfStudyRelatedSeries, VR.IS, study.numSeries());
        attributes.setInt(Tag.NumberOfStudyRelatedInstances, VR.IS, study.numInstances());
        return attributes;
    }

    private Attributes toSeriesAttributes(PacsSeries series) {
        Attributes attributes = new Attributes();
        attributes.setString(Tag.QueryRetrieveLevel, VR.CS, "SERIES");
        setIfPresent(attributes, Tag.SeriesInstanceUID, VR.UI, series.seriesInstanceUid());
        setIfPresent(attributes, Tag.Modality, VR.CS, series.modality());
        setIfPresent(attributes, Tag.SeriesDescription, VR.LO, series.seriesDescription());
        setIfPresent(attributes, Tag.BodyPartExamined, VR.CS, series.bodyPartExamined());
        if (series.seriesNumber() != null) {
            attributes.setInt(Tag.SeriesNumber, VR.IS, series.seriesNumber());
        }
        attributes.setInt(Tag.NumberOfSeriesRelatedInstances, VR.IS, series.numInstances());
        return attributes;
    }

    private void setIfPresent(Attributes attributes, int tag, VR vr, String value) {
        if (value != null && !value.isBlank()) {
            attributes.setString(tag, vr, value);
        }
    }

    private String[] splitRange(String value) {
        if (value == null || value.isBlank()) {
            return new String[]{null, null};
        }
        String[] parts = value.split("-", 2);
        String from = parts.length > 0 && !parts[0].isBlank() ? parts[0] : null;
        String to = parts.length > 1 && !parts[1].isBlank() ? parts[1] : null;
        return new String[]{from, to};
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}
