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
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultDicomStoreDomainServiceTest {

    private final InMemoryPatientRepository patientRepository = new InMemoryPatientRepository();
    private final InMemoryStudyRepository studyRepository = new InMemoryStudyRepository();
    private final InMemorySeriesRepository seriesRepository = new InMemorySeriesRepository();
    private final InMemoryInstanceRepository instanceRepository = new InMemoryInstanceRepository();

    private final DefaultDicomStoreDomainService service = new DefaultDicomStoreDomainService(
            patientRepository,
            studyRepository,
            seriesRepository,
            instanceRepository
    );

    @Test
    void shouldUpsertHierarchyAndIncrementCounters() {
        StoreResult result = service.store(baseRequest());

        assertThat(result.patient().id()).isNotNull();
        assertThat(result.study().numSeries()).isEqualTo(1);
        assertThat(result.study().numInstances()).isEqualTo(1);
        assertThat(result.series().numInstances()).isEqualTo(1);
    }

    @Test
    void shouldRejectDuplicateSopInstanceUid() {
        service.store(baseRequest());

        assertThatThrownBy(() -> service.store(baseRequest()))
                .isInstanceOf(PacsException.class)
                .hasMessageContaining("Duplicate SOP Instance UID");
    }

    private StoreDicomRequest baseRequest() {
        return new StoreDicomRequest(
                "P001",
                "",
                "Demo Patient",
                "F",
                "19900101",
                Map.of(),
                "1.2.3.4",
                "ACC-1",
                "20260428",
                "090000",
                "CT Study",
                "CT",
                "Dr.A",
                Map.of(),
                "1.2.3.4.1",
                "CT",
                "Axial",
                "CHEST",
                1,
                Map.of(),
                "1.2.3.4.1.1",
                "1.2.840.10008.5.1.4.1.1.2",
                "1.2.840.10008.1.2.1",
                1,
                "P001\\1.2.3.4\\1.2.3.4.1\\1.2.3.4.1.1.dcm",
                1024L,
                "abcdefabcdefabcdefabcdefabcdefab",
                "local",
                null,
                "P001\\1.2.3.4\\1.2.3.4.1\\1.2.3.4.1.1.dcm",
                Map.of()
        );
    }

    private static final class InMemoryPatientRepository implements PacsPatientRepository {
        private final AtomicLong ids = new AtomicLong();
        private final Map<Long, PacsPatient> store = new LinkedHashMap<>();

        @Override
        public Optional<PacsPatient> findByPatientKey(String patientId, String issuerOfPatientId) {
            return store.values().stream()
                    .filter(patient -> patient.patientId().equals(patientId) && patient.issuerOfPatientId().equals(issuerOfPatientId))
                    .findFirst();
        }

        @Override
        public PacsPatient save(PacsPatient patient) {
            Long id = patient.id() == null ? ids.incrementAndGet() : patient.id();
            PacsPatient saved = new PacsPatient(id, patient.patientId(), patient.issuerOfPatientId(), patient.patientName(),
                    patient.patientSex(), patient.patientBirthDate(), patient.extraTags(), Instant.now(), Instant.now());
            store.put(id, saved);
            return saved;
        }

        @Override
        public List<PacsPatient> search(PatientQueryCriteria criteria) {
            return store.values().stream().toList();
        }
    }

    private static final class InMemoryStudyRepository implements PacsStudyRepository {
        private final AtomicLong ids = new AtomicLong();
        private final Map<Long, PacsStudy> store = new LinkedHashMap<>();

        @Override
        public Optional<PacsStudy> findByStudyInstanceUid(String studyInstanceUid) {
            return store.values().stream().filter(study -> study.studyInstanceUid().equals(studyInstanceUid)).findFirst();
        }

        @Override
        public PacsStudy save(PacsStudy study) {
            Long id = study.id() == null ? ids.incrementAndGet() : study.id();
            PacsStudy saved = new PacsStudy(id, study.patientFk(), study.studyInstanceUid(), study.accessionNo(),
                    study.studyDate(), study.studyTime(), study.studyDescription(), study.modalitiesInStudy(),
                    study.referringDoctor(), study.numSeries(), study.numInstances(), study.extraTags(), Instant.now(), Instant.now());
            store.put(id, saved);
            return saved;
        }

        @Override
        public List<PacsStudy> search(StudyQueryCriteria criteria) {
            return store.values().stream().toList();
        }
    }

    private static final class InMemorySeriesRepository implements PacsSeriesRepository {
        private final AtomicLong ids = new AtomicLong();
        private final Map<Long, PacsSeries> store = new LinkedHashMap<>();

        @Override
        public Optional<PacsSeries> findBySeriesInstanceUid(String seriesInstanceUid) {
            return store.values().stream().filter(series -> series.seriesInstanceUid().equals(seriesInstanceUid)).findFirst();
        }

        @Override
        public PacsSeries save(PacsSeries series) {
            Long id = series.id() == null ? ids.incrementAndGet() : series.id();
            PacsSeries saved = new PacsSeries(id, series.studyFk(), series.seriesInstanceUid(), series.modality(),
                    series.seriesDescription(), series.bodyPartExamined(), series.seriesNumber(), series.numInstances(),
                    series.extraTags(), Instant.now(), Instant.now());
            store.put(id, saved);
            return saved;
        }

        @Override
        public List<PacsSeries> search(SeriesQueryCriteria criteria) {
            return store.values().stream().toList();
        }
    }

    private static final class InMemoryInstanceRepository implements PacsInstanceRepository {
        private final AtomicLong ids = new AtomicLong();
        private final Map<Long, PacsInstance> store = new LinkedHashMap<>();

        @Override
        public Optional<PacsInstance> findBySopInstanceUid(String sopInstanceUid) {
            return store.values().stream().filter(instance -> instance.sopInstanceUid().equals(sopInstanceUid)).findFirst();
        }

        @Override
        public PacsInstance save(PacsInstance instance) {
            Long id = instance.id() == null ? ids.incrementAndGet() : instance.id();
            PacsInstance saved = new PacsInstance(id, instance.seriesFk(), instance.sopInstanceUid(), instance.sopClassUid(),
                    instance.transferSyntaxUid(), instance.instanceNumber(), instance.filePath(), instance.fileSize(),
                    instance.fileMd5(), instance.storageType(), instance.storageBucket(), instance.storageKey(),
                    instance.extraTags(), Instant.now(), Instant.now());
            store.put(id, saved);
            return saved;
        }

        @Override
        public List<PacsInstance> search(InstanceQueryCriteria criteria) {
            return store.values().stream().toList();
        }
    }
}
