package com.mylife.pacs.domain.service;

import com.mylife.pacs.common.exception.PacsException;
import com.mylife.pacs.domain.model.PacsInstance;
import com.mylife.pacs.domain.model.PacsPatient;
import com.mylife.pacs.domain.model.PacsSeries;
import com.mylife.pacs.domain.model.PacsStudy;
import com.mylife.pacs.domain.repository.PacsInstanceRepository;
import com.mylife.pacs.domain.repository.PacsPatientRepository;
import com.mylife.pacs.domain.repository.PacsSeriesRepository;
import com.mylife.pacs.domain.repository.PacsStudyRepository;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DefaultDicomStoreDomainService implements DicomStoreDomainService {

    private final PacsPatientRepository patientRepository;
    private final PacsStudyRepository studyRepository;
    private final PacsSeriesRepository seriesRepository;
    private final PacsInstanceRepository instanceRepository;

    public DefaultDicomStoreDomainService(
            PacsPatientRepository patientRepository,
            PacsStudyRepository studyRepository,
            PacsSeriesRepository seriesRepository,
            PacsInstanceRepository instanceRepository
    ) {
        this.patientRepository = patientRepository;
        this.studyRepository = studyRepository;
        this.seriesRepository = seriesRepository;
        this.instanceRepository = instanceRepository;
    }

    @Override
    public StoreResult store(StoreDicomRequest request) {
        require(request.patientId(), "patientId");
        require(request.studyInstanceUid(), "studyInstanceUid");
        require(request.seriesInstanceUid(), "seriesInstanceUid");
        require(request.sopInstanceUid(), "sopInstanceUid");
        require(request.filePath(), "filePath");

        PacsPatient patient = patientRepository.findByPatientKey(request.patientId(), request.issuerOfPatientId())
                .map(existing -> mergePatient(existing, request))
                .orElse(new PacsPatient(
                        null,
                        request.patientId(),
                        request.issuerOfPatientId(),
                        request.patientName(),
                        request.patientSex(),
                        request.patientBirthDate(),
                        request.patientExtraTags(),
                        null,
                        null
                ));
        patient = patientRepository.save(patient);

        PacsStudy study;
        Optional<PacsStudy> existingStudy = studyRepository.findByStudyInstanceUid(request.studyInstanceUid());
        if (existingStudy.isPresent()) {
            study = mergeStudy(existingStudy.get(), request, patient.id());
        } else {
            study = new PacsStudy(
                    null,
                    patient.id(),
                    request.studyInstanceUid(),
                    request.accessionNo(),
                    request.studyDate(),
                    request.studyTime(),
                    request.studyDescription(),
                    request.modalitiesInStudy(),
                    request.referringDoctor(),
                    0,
                    0,
                    request.studyExtraTags(),
                    null,
                    null
            );
        }
        study = studyRepository.save(study);

        PacsSeries series;
        Optional<PacsSeries> existingSeries = seriesRepository.findBySeriesInstanceUid(request.seriesInstanceUid());
        if (existingSeries.isPresent()) {
            series = mergeSeries(existingSeries.get(), request, study.id());
        } else {
            series = new PacsSeries(
                    null,
                    study.id(),
                    request.seriesInstanceUid(),
                    request.modality(),
                    request.seriesDescription(),
                    request.bodyPartExamined(),
                    request.seriesNumber(),
                    0,
                    request.seriesExtraTags(),
                    null,
                    null
            );
        }
        boolean isNewSeries = series.id() == null;
        series = seriesRepository.save(series);

        if (instanceRepository.findBySopInstanceUid(request.sopInstanceUid()).isPresent()) {
            throw new PacsException("Duplicate SOP Instance UID: " + request.sopInstanceUid());
        }

        PacsInstance instance = instanceRepository.save(new PacsInstance(
                null,
                series.id(),
                request.sopInstanceUid(),
                request.sopClassUid(),
                request.transferSyntaxUid(),
                request.instanceNumber(),
                request.filePath(),
                request.fileSize(),
                request.fileMd5(),
                coalesce(request.storageType(), "local"),
                request.storageBucket(),
                coalesce(request.storageKey(), request.filePath()),
                request.instanceExtraTags(),
                null,
                null
        ));

        PacsSeries updatedSeries = seriesRepository.save(new PacsSeries(
                series.id(),
                series.studyFk(),
                series.seriesInstanceUid(),
                series.modality(),
                series.seriesDescription(),
                series.bodyPartExamined(),
                series.seriesNumber(),
                series.numInstances() + 1,
                series.extraTags(),
                series.createdAt(),
                series.updatedAt()
        ));

        PacsStudy updatedStudy = studyRepository.save(new PacsStudy(
                study.id(),
                study.patientFk(),
                study.studyInstanceUid(),
                study.accessionNo(),
                study.studyDate(),
                study.studyTime(),
                study.studyDescription(),
                combineModalities(study.modalitiesInStudy(), request.modality()),
                study.referringDoctor(),
                study.numSeries() + (isNewSeries ? 1 : 0),
                study.numInstances() + 1,
                study.extraTags(),
                study.createdAt(),
                study.updatedAt()
        ));

        return new StoreResult(patient, updatedStudy, updatedSeries, instance);
    }

    private PacsPatient mergePatient(PacsPatient existing, StoreDicomRequest request) {
        return new PacsPatient(
                existing.id(),
                existing.patientId(),
                request.issuerOfPatientId(),
                coalesce(request.patientName(), existing.patientName()),
                coalesce(request.patientSex(), existing.patientSex()),
                coalesce(request.patientBirthDate(), existing.patientBirthDate()),
                mergeTags(existing.extraTags(), request.patientExtraTags()),
                existing.createdAt(),
                existing.updatedAt()
        );
    }

    private PacsStudy mergeStudy(PacsStudy existing, StoreDicomRequest request, Long patientId) {
        return new PacsStudy(
                existing.id(),
                patientId,
                existing.studyInstanceUid(),
                coalesce(request.accessionNo(), existing.accessionNo()),
                coalesce(request.studyDate(), existing.studyDate()),
                coalesce(request.studyTime(), existing.studyTime()),
                coalesce(request.studyDescription(), existing.studyDescription()),
                combineModalities(existing.modalitiesInStudy(), request.modality()),
                coalesce(request.referringDoctor(), existing.referringDoctor()),
                existing.numSeries(),
                existing.numInstances(),
                mergeTags(existing.extraTags(), request.studyExtraTags()),
                existing.createdAt(),
                existing.updatedAt()
        );
    }

    private PacsSeries mergeSeries(PacsSeries existing, StoreDicomRequest request, Long studyId) {
        return new PacsSeries(
                existing.id(),
                studyId,
                existing.seriesInstanceUid(),
                coalesce(request.modality(), existing.modality()),
                coalesce(request.seriesDescription(), existing.seriesDescription()),
                coalesce(request.bodyPartExamined(), existing.bodyPartExamined()),
                request.seriesNumber() != null ? request.seriesNumber() : existing.seriesNumber(),
                existing.numInstances(),
                mergeTags(existing.extraTags(), request.seriesExtraTags()),
                existing.createdAt(),
                existing.updatedAt()
        );
    }

    private String combineModalities(String existing, String incoming) {
        Set<String> modalities = new LinkedHashSet<>();
        if (existing != null && !existing.isBlank()) {
            for (String value : existing.split("\\\\")) {
                if (!value.isBlank()) {
                    modalities.add(value);
                }
            }
        }
        if (incoming != null && !incoming.isBlank()) {
            modalities.add(incoming);
        }
        return String.join("\\", modalities);
    }

    private Map<String, String> mergeTags(Map<String, String> existing, Map<String, String> incoming) {
        java.util.LinkedHashMap<String, String> merged = new java.util.LinkedHashMap<>();
        if (existing != null) {
            merged.putAll(existing);
        }
        if (incoming != null) {
            merged.putAll(incoming);
        }
        return Map.copyOf(merged);
    }

    private String coalesce(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private void require(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new PacsException("Missing required field: " + fieldName);
        }
    }
}
