package com.mylife.pacs.test.integration;

import com.mylife.pacs.application.AetApplicationService;
import com.mylife.pacs.application.DicomApplicationService;
import com.mylife.pacs.application.SyncJobConfigApplicationService;
import com.mylife.pacs.boot.PacsApplication;
import com.mylife.pacs.common.config.DicomPacsProperties;
import com.mylife.pacs.common.sync.SyncMetadataKeys;
import com.mylife.pacs.domain.model.AetNode;
import com.mylife.pacs.domain.model.AetRole;
import com.mylife.pacs.domain.model.PacsInstance;
import com.mylife.pacs.domain.model.SyncJobConfig;
import com.mylife.pacs.domain.model.SyncJobType;
import com.mylife.pacs.domain.service.InstanceQueryCriteria;
import com.mylife.pacs.domain.service.StudyQueryCriteria;
import com.mylife.pacs.infrastructure.netty.DicomServerBootstrap;
import com.mylife.pacs.infrastructure.rest.admin.SyncAdminController;
import com.mylife.pacs.infrastructure.sync.SyncExecutionRecordService;
import com.mylife.pacs.test.support.PublicSampleLibrarySupport;
import com.mylife.pacs.test.support.TestDicomObjects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PacsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PythonGeneratorScheduledPullIntegrationTest {

    private static final DateTimeFormatter DICOM_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AetApplicationService aetApplicationService;

    @Autowired
    private DicomApplicationService dicomApplicationService;

    @Autowired
    private SyncAdminController syncAdminController;

    @Autowired
    private SyncJobConfigApplicationService syncJobConfigApplicationService;

    @Autowired
    private DicomPacsProperties properties;

    @Autowired
    private DicomServerBootstrap localBootstrap;

    @Test
    void shouldPullPythonGeneratedStudyFromSimulatedUpstreamPacsAndPreserveGovernanceSignals() throws Exception {
        String upstreamAet = "PYUP" + Long.toString(System.nanoTime()).substring(0, 8);
        String today = LocalDate.now().format(DICOM_DATE);

        try (ConfigurableApplicationContext upstreamContext = new SpringApplicationBuilder(PacsApplication.class)
                .run(
                        "--spring.profiles.active=test",
                        "--server.port=0",
                        "--dicom.pacs.local-aet=" + upstreamAet,
                        "--dicom.pacs.local-port=0",
                        "--dicom.pacs.storage-dir=target\\test-storage-" + upstreamAet,
                        "--spring.datasource.url=jdbc:h2:mem:" + upstreamAet + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
                )) {
            DicomServerBootstrap upstreamBootstrap = upstreamContext.getBean(DicomServerBootstrap.class);
            DicomApplicationService upstreamDicomApplicationService = upstreamContext.getBean(DicomApplicationService.class);
            AetApplicationService upstreamAetApplicationService = upstreamContext.getBean(AetApplicationService.class);

            registerPeer(upstreamAet, upstreamBootstrap.boundPort(), "Python Generated Upstream");
            registerPeer(upstreamAetApplicationService, properties.getLocalAet(), localBootstrap.boundPort(), "Local Pull Destination");

            GeneratedStudy generatedStudy = pushStudyToUpstream(upstreamAet, upstreamBootstrap.boundPort(), today);

            assertThat(upstreamDicomApplicationService.findStudies(new StudyQueryCriteria(
                    generatedStudy.patientId(),
                    null,
                    generatedStudy.studyUid(),
                    null,
                    generatedStudy.modality(),
                    today,
                    today
            ))).hasSize(1);

            String jobName = "pygen-pull-" + Long.toString(System.nanoTime());
            syncJobConfigApplicationService.create(new SyncJobConfig(
                    null,
                    jobName,
                    SyncJobType.PULL,
                    upstreamAet,
                    properties.getLocalAet(),
                    generatedStudy.patientId(),
                    generatedStudy.modality(),
                    0,
                    true,
                    true,
                    0,
                    0,
                    0L,
                    List.of(),
                    List.of(),
                    0,
                    3,
                    false,
                    0,
                    null,
                    null,
                    null,
                    null,
                    true,
                    null,
                    null
            ));

            try {
                syncAdminController.triggerPull(jobName);
                syncAdminController.triggerPull(jobName);

                List<PacsInstance> pulledInstances = dicomApplicationService.findInstances(new InstanceQueryCriteria(
                        generatedStudy.studyUid(),
                        generatedStudy.seriesUid(),
                        generatedStudy.sopUid()
                ));
                assertThat(pulledInstances).hasSize(1);
                assertThat(pulledInstances.getFirst().extraTags()).containsEntry(SyncMetadataKeys.SOURCE_AET, upstreamAet);

                mockMvc.perform(get("/qido-rs/studies")
                                .param("StudyInstanceUID", generatedStudy.studyUid()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[0].0020000D.Value[0]").value(generatedStudy.studyUid()));

                mockMvc.perform(get("/wado-rs/studies/{study}/series/{series}/instances/{instance}",
                                generatedStudy.studyUid(),
                                generatedStudy.seriesUid(),
                                generatedStudy.sopUid()))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Content-Type", "application/dicom"))
                        .andExpect(result -> {
                            Attributes storedDataset = TestDicomObjects.readDataset(result.getResponse().getContentAsByteArray());
                            assertThat(storedDataset.getString(Tag.PatientID)).isEqualTo(generatedStudy.patientId());
                            assertThat(storedDataset.getString(Tag.StudyInstanceUID)).isEqualTo(generatedStudy.studyUid());
                            assertThat(storedDataset.getString(Tag.SeriesInstanceUID)).isEqualTo(generatedStudy.seriesUid());
                            assertThat(storedDataset.getString(Tag.SOPInstanceUID)).isEqualTo(generatedStudy.sopUid());
                        });

                mockMvc.perform(get("/api/viewer/studies")
                                .param("patientId", generatedStudy.patientId())
                                .param("modality", generatedStudy.modality()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[*].studyInstanceUid", hasItem(generatedStudy.studyUid())));

                List<SyncExecutionRecordService.SyncExecutionRecord> executions = syncAdminController.listExecutions(
                        "SUCCESS",
                        jobName,
                        null,
                        10
                );
                assertThat(executions).hasSizeGreaterThanOrEqualTo(2);
                assertThat(executions).anySatisfy(record -> {
                    assertThat(record.transferredStudyCount()).isEqualTo(1);
                    assertThat(record.transferredInstanceCount()).isEqualTo(1);
                });
                assertThat(executions).anySatisfy(record -> {
                    assertThat(record.transferredStudyCount()).isZero();
                    assertThat(record.skippedExistingCount()).isEqualTo(1);
                });
            } finally {
                syncJobConfigApplicationService.remove(jobName);
            }
        }
    }

    private void registerPeer(String aet, int port, String nodeName) {
        registerPeer(aetApplicationService, aet, port, nodeName);
    }

    private void registerPeer(AetApplicationService service, String aet, int port, String nodeName) {
        service.register(new AetNode(
                null,
                aet,
                "127.0.0.1",
                port,
                AetRole.REMOTE,
                nodeName,
                nodeName,
                true,
                null,
                null,
                null
        ));
    }

    private GeneratedStudy pushStudyToUpstream(String upstreamAet, int upstreamPort, String today) throws IOException, InterruptedException {
        Path templatePath = PublicSampleLibrarySupport.requireDownloadedSample("pydicom-test-files", "pydicom-CT_small.dcm");
        String patientId = "PYUP-" + Long.toString(System.nanoTime());
        String accessionNumber = "ACC" + Long.toString(System.nanoTime());
        String outputDirectory = resolveRepoRoot().resolve("dicom-generator").resolve("output").resolve("scheduled-pull").toString();
        String pythonExecutable = System.getProperty("python.executable", "python");

        String pythonScript = """
from app.models import DicomIdentityOverrides, GenerateJobRequest, PacsTarget
from app.service import GeneratorService

request = GenerateJobRequest(
    template_path="%s",
    output_directory="%s",
    overwrite=True,
    mode="simulate_upstream_pacs",
    identity=DicomIdentityOverrides(
        patient_id="%s",
        patient_name="Pygen^ScheduledPull",
        accession_number="%s",
        study_date="%s",
        study_time="121500",
    ),
    tag_overrides={"StudyDescription": "Python Scheduled Pull Acceptance"},
    pacs_target=PacsTarget(
        host="127.0.0.1",
        port=%d,
        called_aet="%s",
        calling_aet="PYGENUP",
    ),
)
result = GeneratorService().export(request, job_id="scheduled-pull")
instance = result.instances[0]
print("PYGEN_RESULT|" + request.identity.patient_id + "|" + instance.study_instance_uid + "|" + instance.series_instance_uid + "|" + instance.sop_instance_uid + "|" + (instance.modality or ""))
""".formatted(
                pythonString(templatePath.toString()),
                pythonString(outputDirectory),
                pythonString(patientId),
                pythonString(accessionNumber),
                pythonString(today),
                upstreamPort,
                pythonString(upstreamAet)
        );

        Path generatorRoot = resolveRepoRoot().resolve("dicom-generator");
        Path tempScript = Files.createTempFile(generatorRoot, "pygen-scheduled-pull-", ".py");
        Files.writeString(tempScript, pythonScript, StandardCharsets.UTF_8);

        String output = "";
        int exitCode = -1;
        try {
            Process process = new ProcessBuilder(pythonExecutable, tempScript.toString())
                    .directory(generatorRoot.toFile())
                    .redirectErrorStream(true)
                    .start();

            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            exitCode = process.waitFor();
        } finally {
            Files.deleteIfExists(tempScript);
        }

        assertThat(exitCode)
                .withFailMessage("Python generator failed:%n%s", output)
                .isZero();

        String resultLine = output.lines()
                .filter(line -> line.startsWith("PYGEN_RESULT|"))
                .findFirst()
                .orElse(null);
        if (resultLine == null) {
            throw new IllegalStateException("Missing generator result marker in output: " + output);
        }
        String[] parts = resultLine.split("\\|", -1);
        if (parts.length != 6) {
            throw new IllegalStateException("Unexpected generator result marker: " + resultLine);
        }
        return new GeneratedStudy(parts[1], parts[2], parts[3], parts[4], parts[5].isBlank() ? "CT" : parts[5]);
    }

    private Path resolveRepoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.isDirectory(current.resolve("sample-library"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Failed to resolve repository root");
    }

    private String pythonString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record GeneratedStudy(
            String patientId,
            String studyUid,
            String seriesUid,
            String sopUid,
            String modality
    ) {
    }
}
