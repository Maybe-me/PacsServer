package com.mylife.pacs.test.integration;

import com.mylife.pacs.application.AetApplicationService;
import com.mylife.pacs.application.DicomApplicationService;
import com.mylife.pacs.boot.PacsApplication;
import com.mylife.pacs.common.config.DicomPacsProperties;
import com.mylife.pacs.domain.model.AetNode;
import com.mylife.pacs.domain.model.AetRole;
import com.mylife.pacs.domain.model.PacsInstance;
import com.mylife.pacs.domain.model.PacsStudy;
import com.mylife.pacs.domain.service.InstanceQueryCriteria;
import com.mylife.pacs.domain.service.StudyQueryCriteria;
import com.mylife.pacs.infrastructure.netty.DicomClientBootstrap;
import com.mylife.pacs.infrastructure.netty.DicomServerBootstrap;
import com.mylife.pacs.infrastructure.netty.message.DicomCommandType;
import com.mylife.pacs.infrastructure.netty.message.DicomMessage;
import com.mylife.pacs.test.support.TestDicomObjects;
import org.dcm4che3.data.UID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PacsApplication.class)
@ActiveProfiles("test")
class DicomTransportIntegrationTest {

    @Autowired
    private DicomServerBootstrap dicomServerBootstrap;

    @Autowired
    private DicomClientBootstrap dicomClientBootstrap;

    @Autowired
    private DicomApplicationService dicomApplicationService;

    @Autowired
    private AetApplicationService aetApplicationService;

    @Test
    void shouldRespondToCEchoThroughNettyTransport() {
        boolean result = dicomClientBootstrap.echo("127.0.0.1", dicomServerBootstrap.boundPort(), "REMOTE_ECHO", "MY_PACS");

        assertThat(result).isTrue();
    }

    @Test
    void shouldStoreAndIndexInstanceThroughCStoreTransport() {
        String patientId = "P-" + System.nanoTime();
        String studyUid = "1.2.826.0.1." + System.nanoTime();
        String seriesUid = studyUid + ".1";
        String sopUid = seriesUid + ".1";

        DicomMessage response = dicomClientBootstrap.store(
                "127.0.0.1",
                dicomServerBootstrap.boundPort(),
                "REMOTE_STORE",
                "MY_PACS",
                Map.ofEntries(
                        Map.entry("00100020", patientId),
                        Map.entry("00100021", "LOCAL"),
                        Map.entry("00100010", "Transport^Patient"),
                        Map.entry("00100040", "M"),
                        Map.entry("00100030", "19810101"),
                        Map.entry("0020000D", studyUid),
                        Map.entry("00080050", "ACC-" + patientId),
                        Map.entry("00080020", "20260428"),
                        Map.entry("00080030", "094500"),
                        Map.entry("00081030", "Netty Transport Study"),
                        Map.entry("00080061", "MR"),
                        Map.entry("00080090", "Dr.Transport"),
                        Map.entry("0020000E", seriesUid),
                        Map.entry("00080060", "MR"),
                        Map.entry("0008103E", "Transport Series"),
                        Map.entry("00180015", "BRAIN"),
                        Map.entry("00200011", "2"),
                        Map.entry("00080018", sopUid),
                        Map.entry("00080016", "1.2.840.10008.5.1.4.1.1.4"),
                        Map.entry("00020010", "1.2.840.10008.1.2.1"),
                        Map.entry("00200013", "8")
                ),
                TestDicomObjects.createDicomBytes(Map.ofEntries(
                        Map.entry("00100020", patientId),
                        Map.entry("00100021", "LOCAL"),
                        Map.entry("00100010", "Transport^Patient"),
                        Map.entry("00100040", "M"),
                        Map.entry("00100030", "19810101"),
                        Map.entry("0020000D", studyUid),
                        Map.entry("00080050", "ACC-" + patientId),
                        Map.entry("00080020", "20260428"),
                        Map.entry("00080030", "094500"),
                        Map.entry("00081030", "Netty Transport Study"),
                        Map.entry("00080061", "MR"),
                        Map.entry("00080090", "Dr.Transport"),
                        Map.entry("0020000E", seriesUid),
                        Map.entry("00080060", "MR"),
                        Map.entry("0008103E", "Transport Series"),
                        Map.entry("00180015", "BRAIN"),
                        Map.entry("00200011", "2"),
                        Map.entry("00080018", sopUid),
                        Map.entry("00080016", "1.2.840.10008.5.1.4.1.1.4"),
                        Map.entry("00020010", "1.2.840.10008.1.2.1"),
                        Map.entry("00200013", "8")
                ), "transport-pixel-data")
        );

        List<PacsStudy> studies = dicomApplicationService.findStudies(
                new StudyQueryCriteria(patientId, "LOCAL", studyUid, null, "MR", null, null)
        );
        List<PacsInstance> instances = dicomApplicationService.findInstances(
                new InstanceQueryCriteria(studyUid, seriesUid, sopUid)
        );

        assertThat(response.commandType()).isEqualTo(DicomCommandType.C_STORE_RESPONSE);
        assertThat(response.status()).isZero();
        assertThat(studies).hasSize(1);
        assertThat(studies.getFirst().numSeries()).isEqualTo(1);
        assertThat(studies.getFirst().numInstances()).isEqualTo(1);
        assertThat(instances).hasSize(1);
        assertThat(instances.getFirst().filePath()).contains(patientId);
    }

    @Test
    void shouldStoreAndIndexImplicitVrLittleEndianInstanceThroughCStoreTransport() {
        String patientId = "I-" + System.nanoTime();
        String studyUid = "1.2.826.0.7." + System.nanoTime();
        String seriesUid = studyUid + ".1";
        String sopUid = seriesUid + ".1";

        DicomMessage response = dicomClientBootstrap.store(
                "127.0.0.1",
                dicomServerBootstrap.boundPort(),
                "RIMPLICIT",
                "MY_PACS",
                Map.ofEntries(
                        Map.entry("00100020", patientId),
                        Map.entry("00100021", "LOCAL"),
                        Map.entry("00100010", "Implicit^Patient"),
                        Map.entry("0020000D", studyUid),
                        Map.entry("00080050", "ACC-" + patientId),
                        Map.entry("00080020", "20260428"),
                        Map.entry("00081030", "Implicit Transfer Syntax Study"),
                        Map.entry("00080061", "MR"),
                        Map.entry("0020000E", seriesUid),
                        Map.entry("00080060", "MR"),
                        Map.entry("0008103E", "Implicit Series"),
                        Map.entry("00080018", sopUid),
                        Map.entry("00080016", "1.2.840.10008.5.1.4.1.1.4"),
                        Map.entry("00020010", "1.2.840.10008.1.2"),
                        Map.entry("00200013", "1")
                ),
                TestDicomObjects.createDicomBytes(Map.ofEntries(
                        Map.entry("00100020", patientId),
                        Map.entry("00100021", "LOCAL"),
                        Map.entry("00100010", "Implicit^Patient"),
                        Map.entry("0020000D", studyUid),
                        Map.entry("00080050", "ACC-" + patientId),
                        Map.entry("00080020", "20260428"),
                        Map.entry("00081030", "Implicit Transfer Syntax Study"),
                        Map.entry("00080061", "MR"),
                        Map.entry("0020000E", seriesUid),
                        Map.entry("00080060", "MR"),
                        Map.entry("0008103E", "Implicit Series"),
                        Map.entry("00080018", sopUid),
                        Map.entry("00080016", "1.2.840.10008.5.1.4.1.1.4"),
                        Map.entry("00020010", "1.2.840.10008.1.2"),
                        Map.entry("00200013", "1")
                ), "implicit-pixel-data")
        );

        List<PacsInstance> instances = dicomApplicationService.findInstances(
                new InstanceQueryCriteria(studyUid, seriesUid, sopUid)
        );

        assertThat(response.commandType()).isEqualTo(DicomCommandType.C_STORE_RESPONSE);
        assertThat(response.status()).isZero();
        assertThat(instances).hasSize(1);
        assertThat(instances.getFirst().transferSyntaxUid()).isEqualTo("1.2.840.10008.1.2");
    }

    @Test
    void shouldReturnDuplicateCommentWhenStoringSameInstanceTwice() {
        String patientId = "D-" + System.nanoTime();
        String studyUid = "1.2.826.0.8." + System.nanoTime();
        String seriesUid = studyUid + ".1";
        String sopUid = seriesUid + ".1";
        Map<String, String> attributes = Map.ofEntries(
                Map.entry("00100020", patientId),
                Map.entry("00100021", "LOCAL"),
                Map.entry("00100010", "Duplicate^Patient"),
                Map.entry("0020000D", studyUid),
                Map.entry("00080050", "ACC-" + patientId),
                Map.entry("00080020", "20260428"),
                Map.entry("00081030", "Duplicate Study"),
                Map.entry("00080061", "MR"),
                Map.entry("0020000E", seriesUid),
                Map.entry("00080060", "MR"),
                Map.entry("0008103E", "Duplicate Series"),
                Map.entry("00080018", sopUid),
                Map.entry("00080016", "1.2.840.10008.5.1.4.1.1.4"),
                Map.entry("00020010", "1.2.840.10008.1.2"),
                Map.entry("00200013", "1")
        );
        byte[] payload = TestDicomObjects.createDicomBytes(attributes, "duplicate-pixel-data");

        DicomMessage firstResponse = dicomClientBootstrap.store(
                "127.0.0.1",
                dicomServerBootstrap.boundPort(),
                "RDUP001",
                "MY_PACS",
                attributes,
                payload
        );
        DicomMessage duplicateResponse = dicomClientBootstrap.store(
                "127.0.0.1",
                dicomServerBootstrap.boundPort(),
                "RDUP002",
                "MY_PACS",
                attributes,
                payload
        );

        assertThat(firstResponse.status()).isZero();
        assertThat(duplicateResponse.commandType()).isEqualTo(DicomCommandType.ERROR);
        assertThat(duplicateResponse.status()).isEqualTo(org.dcm4che3.net.Status.ProcessingFailure);
        assertThat(duplicateResponse.message()).contains("Duplicate SOP Instance UID");
        assertThat(duplicateResponse.attributes()).containsKey("errorComment");
    }

    @Test
    void shouldStoreDigitalMammographyInstanceThroughCStoreTransport() {
        String patientId = "MG-" + System.nanoTime();
        String studyUid = "1.2.826.0.9." + System.nanoTime();
        String seriesUid = studyUid + ".1";
        String sopUid = seriesUid + ".1";

        DicomMessage response = dicomClientBootstrap.store(
                "127.0.0.1",
                dicomServerBootstrap.boundPort(),
                "RMAMMO1",
                "MY_PACS",
                Map.ofEntries(
                        Map.entry("00100020", patientId),
                        Map.entry("00100010", "Mammo^Patient"),
                        Map.entry("0020000D", studyUid),
                        Map.entry("0020000E", seriesUid),
                        Map.entry("00080018", sopUid),
                        Map.entry("00080016", UID.DigitalMammographyXRayImageStorageForPresentation),
                        Map.entry("00020010", UID.ExplicitVRLittleEndian)
                ),
                TestDicomObjects.createDicomBytes(Map.ofEntries(
                        Map.entry("00100020", patientId),
                        Map.entry("00100010", "Mammo^Patient"),
                        Map.entry("0020000D", studyUid),
                        Map.entry("0020000E", seriesUid),
                        Map.entry("00080018", sopUid),
                        Map.entry("00080016", UID.DigitalMammographyXRayImageStorageForPresentation),
                        Map.entry("00020010", UID.ExplicitVRLittleEndian)
                ), "mammo-pixel-data")
        );

        List<PacsInstance> instances = dicomApplicationService.findInstances(
                new InstanceQueryCriteria(studyUid, seriesUid, sopUid)
        );

        assertThat(response.commandType()).isEqualTo(DicomCommandType.C_STORE_RESPONSE);
        assertThat(response.status()).isZero();
        assertThat(instances).hasSize(1);
        assertThat(instances.getFirst().sopClassUid()).isEqualTo(UID.DigitalMammographyXRayImageStorageForPresentation);
    }

    @Test
    void shouldStoreInstanceWithoutPatientIdUsingGeneratedFallback() {
        String studyUid = "1.2.826.0.10." + System.nanoTime();
        String seriesUid = studyUid + ".1";
        String sopUid = seriesUid + ".1";

        DicomMessage response = dicomClientBootstrap.store(
                "127.0.0.1",
                dicomServerBootstrap.boundPort(),
                "RNOID01",
                "MY_PACS",
                Map.ofEntries(
                        Map.entry("00100010", "Anonymous^Patient"),
                        Map.entry("0020000D", studyUid),
                        Map.entry("0020000E", seriesUid),
                        Map.entry("00080018", sopUid),
                        Map.entry("00080016", UID.CTImageStorage),
                        Map.entry("00020010", UID.ExplicitVRLittleEndian)
                ),
                TestDicomObjects.createDicomBytes(Map.ofEntries(
                        Map.entry("00100010", "Anonymous^Patient"),
                        Map.entry("0020000D", studyUid),
                        Map.entry("0020000E", seriesUid),
                        Map.entry("00080018", sopUid),
                        Map.entry("00080016", UID.CTImageStorage),
                        Map.entry("00020010", UID.ExplicitVRLittleEndian)
                ), "anonymous-pixel-data")
        );

        List<PacsInstance> instances = dicomApplicationService.findInstances(
                new InstanceQueryCriteria(studyUid, seriesUid, sopUid)
        );

        assertThat(response.commandType()).isEqualTo(DicomCommandType.C_STORE_RESPONSE);
        assertThat(response.status()).isZero();
        assertThat(instances).hasSize(1);
        assertThat(instances.getFirst().filePath()).contains("AUTO-");
    }

    @Test
    void shouldStoreInstanceWithoutStudyAndSeriesUsingGeneratedFallback() {
        String sopUid = "1.2.826.0.11." + System.nanoTime();

        DicomMessage response = dicomClientBootstrap.store(
                "127.0.0.1",
                dicomServerBootstrap.boundPort(),
                "RAUTGEN",
                "MY_PACS",
                Map.ofEntries(
                        Map.entry("00100010", "Fallback^Patient"),
                        Map.entry("00080018", sopUid),
                        Map.entry("00080016", UID.SecondaryCaptureImageStorage),
                        Map.entry("00020010", UID.ExplicitVRLittleEndian)
                ),
                TestDicomObjects.createDicomBytes(Map.ofEntries(
                        Map.entry("00100010", "Fallback^Patient"),
                        Map.entry("00080018", sopUid),
                        Map.entry("00080016", UID.SecondaryCaptureImageStorage),
                        Map.entry("00020010", UID.ExplicitVRLittleEndian)
                ), "generated-uid-pixel-data")
        );

        List<PacsStudy> studies = dicomApplicationService.findStudies(
                new StudyQueryCriteria(null, null, null, null, null, null, null)
        );
        List<PacsInstance> instances = dicomApplicationService.findInstances(
                new InstanceQueryCriteria(null, null, sopUid)
        );

        assertThat(response.commandType()).isEqualTo(DicomCommandType.C_STORE_RESPONSE);
        assertThat(response.status()).isZero();
        assertThat(instances).hasSize(1);
        assertThat(instances.getFirst().filePath()).contains("AUTO-");
        assertThat(studies).isNotEmpty();
    }

    @Test
    void shouldQueryStudiesThroughCFindTransport() {
        String patientId = "Q-" + System.nanoTime();
        String studyUid = "1.2.826.0.2." + System.nanoTime();
        String seriesUid = studyUid + ".1";
        String sopUid = seriesUid + ".1";

        dicomClientBootstrap.store(
                "127.0.0.1",
                dicomServerBootstrap.boundPort(),
                "RQRYSEED",
                "MY_PACS",
                Map.ofEntries(
                        Map.entry("00100020", patientId),
                        Map.entry("00100021", "LOCAL"),
                        Map.entry("00100010", "Query^Patient"),
                        Map.entry("0020000D", studyUid),
                        Map.entry("00080050", "ACC-" + patientId),
                        Map.entry("00080020", "20260428"),
                        Map.entry("00081030", "Query Study"),
                        Map.entry("00080061", "CT"),
                        Map.entry("0020000E", seriesUid),
                        Map.entry("00080060", "CT"),
                        Map.entry("0008103E", "Query Series"),
                        Map.entry("00080018", sopUid),
                        Map.entry("00080016", "1.2.840.10008.5.1.4.1.1.2"),
                        Map.entry("00020010", "1.2.840.10008.1.2.1")
                ),
                TestDicomObjects.createDicomBytes(Map.ofEntries(
                        Map.entry("00100020", patientId),
                        Map.entry("00100021", "LOCAL"),
                        Map.entry("00100010", "Query^Patient"),
                        Map.entry("0020000D", studyUid),
                        Map.entry("00080050", "ACC-" + patientId),
                        Map.entry("00080020", "20260428"),
                        Map.entry("00081030", "Query Study"),
                        Map.entry("00080061", "CT"),
                        Map.entry("0020000E", seriesUid),
                        Map.entry("00080060", "CT"),
                        Map.entry("0008103E", "Query Series"),
                        Map.entry("00080018", sopUid),
                        Map.entry("00080016", "1.2.840.10008.5.1.4.1.1.2"),
                        Map.entry("00020010", "1.2.840.10008.1.2.1")
                ), "query-pixel-data")
        );

        DicomMessage response = dicomClientBootstrap.find(
                "127.0.0.1",
                dicomServerBootstrap.boundPort(),
                "REMOTE_FIND",
                "MY_PACS",
                Map.of(
                        "QueryRetrieveLevel", "STUDY",
                        "00100020", patientId,
                        "00100021", "LOCAL",
                        "00080060", "CT"
                )
        );

        assertThat(response.commandType()).isEqualTo(DicomCommandType.C_FIND_RESPONSE);
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().getFirst()).containsEntry("0020000D", studyUid);
        assertThat(response.results().getFirst()).containsEntry("00081030", "Query Study");
    }

    @Test
    void shouldForwardInstancesToMoveDestination() {
        String patientId = "M-" + System.nanoTime();
        String studyUid = "1.2.826.0.3." + System.nanoTime();
        String seriesUid = studyUid + ".1";
        String sopUid = seriesUid + ".1";
        String destinationAet = "RDEST" + Long.toString(System.nanoTime()).substring(0, 8);

        try (ConfigurableApplicationContext remoteContext = new SpringApplicationBuilder(PacsApplication.class)
                .run(
                        "--spring.profiles.active=test",
                        "--server.port=0",
                        "--dicom.pacs.local-aet=" + destinationAet,
                        "--dicom.pacs.local-port=0",
                        "--dicom.pacs.storage-dir=target\\test-storage-" + destinationAet,
                        "--spring.datasource.url=jdbc:h2:mem:" + destinationAet + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
                )) {
            DicomServerBootstrap remoteBootstrap = remoteContext.getBean(DicomServerBootstrap.class);
            DicomApplicationService remoteDicomApplicationService = remoteContext.getBean(DicomApplicationService.class);
            DicomPacsProperties remoteProperties = remoteContext.getBean(DicomPacsProperties.class);

            assertThat(remoteProperties.getLocalAet()).isEqualTo(destinationAet);

            aetApplicationService.register(new AetNode(
                    null,
                    remoteProperties.getLocalAet(),
                    "127.0.0.1",
                    remoteBootstrap.boundPort(),
                    AetRole.REMOTE,
                    "Remote Destination",
                    "Move target",
                    true,
                    null,
                    null,
                    null
            ));

            dicomClientBootstrap.store(
                    "127.0.0.1",
                    dicomServerBootstrap.boundPort(),
                    "REMOTE_MOVE_SEED",
                    "MY_PACS",
                    Map.ofEntries(
                            Map.entry("00100020", patientId),
                            Map.entry("00100021", "LOCAL"),
                            Map.entry("00100010", "Move^Patient"),
                            Map.entry("0020000D", studyUid),
                            Map.entry("00080050", "ACC-" + patientId),
                            Map.entry("00080020", "20260428"),
                            Map.entry("00081030", "Move Study"),
                            Map.entry("00080061", "CT"),
                            Map.entry("0020000E", seriesUid),
                            Map.entry("00080060", "CT"),
                            Map.entry("0008103E", "Move Series"),
                            Map.entry("00080018", sopUid),
                            Map.entry("00080016", "1.2.840.10008.5.1.4.1.1.2"),
                            Map.entry("00020010", "1.2.840.10008.1.2.1")
                    ),
                    TestDicomObjects.createDicomBytes(Map.ofEntries(
                            Map.entry("00100020", patientId),
                            Map.entry("00100021", "LOCAL"),
                            Map.entry("00100010", "Move^Patient"),
                            Map.entry("0020000D", studyUid),
                            Map.entry("00080050", "ACC-" + patientId),
                            Map.entry("00080020", "20260428"),
                            Map.entry("00081030", "Move Study"),
                            Map.entry("00080061", "CT"),
                            Map.entry("0020000E", seriesUid),
                            Map.entry("00080060", "CT"),
                            Map.entry("0008103E", "Move Series"),
                            Map.entry("00080018", sopUid),
                            Map.entry("00080016", "1.2.840.10008.5.1.4.1.1.2"),
                            Map.entry("00020010", "1.2.840.10008.1.2.1")
                    ), "move-pixel-data")
            );

            DicomMessage moveResponse = dicomClientBootstrap.move(
                    "127.0.0.1",
                    dicomServerBootstrap.boundPort(),
                    "RMVCLIENT",
                    "MY_PACS",
                    Map.of(
                            "MoveDestination", remoteProperties.getLocalAet(),
                            "0020000D", studyUid
                    )
            );

            List<PacsInstance> remoteInstances = remoteDicomApplicationService.findInstances(
                    new InstanceQueryCriteria(studyUid, seriesUid, sopUid)
            );

            assertThat(moveResponse.commandType())
                    .withFailMessage("Unexpected move response: %s / %s", moveResponse.commandType(), moveResponse.message())
                    .isEqualTo(DicomCommandType.C_MOVE_RESPONSE);
            assertThat(moveResponse.attributes()).containsEntry("movedCount", "1");
            assertThat(remoteInstances).hasSize(1);
            assertThat(remoteInstances.getFirst().sopInstanceUid()).isEqualTo(sopUid);
        }
    }
}
