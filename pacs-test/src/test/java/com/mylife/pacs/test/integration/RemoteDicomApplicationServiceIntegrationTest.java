package com.mylife.pacs.test.integration;

import com.mylife.pacs.application.AetApplicationService;
import com.mylife.pacs.application.DicomApplicationService;
import com.mylife.pacs.application.RemoteDicomApplicationService;
import com.mylife.pacs.boot.PacsApplication;
import com.mylife.pacs.common.config.DicomPacsProperties;
import com.mylife.pacs.domain.model.AetNode;
import com.mylife.pacs.domain.model.AetRole;
import com.mylife.pacs.domain.service.InstanceQueryCriteria;
import com.mylife.pacs.domain.service.StudyQueryCriteria;
import com.mylife.pacs.infrastructure.netty.DicomClientBootstrap;
import com.mylife.pacs.infrastructure.netty.DicomServerBootstrap;
import com.mylife.pacs.infrastructure.netty.message.DicomCommandType;
import com.mylife.pacs.infrastructure.netty.message.DicomMessage;
import com.mylife.pacs.infrastructure.rest.admin.DicomOperationsController;
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
class RemoteDicomApplicationServiceIntegrationTest {

    @Autowired
    private RemoteDicomApplicationService remoteDicomApplicationService;

    @Autowired
    private AetApplicationService aetApplicationService;

    @Autowired
    private DicomClientBootstrap dicomClientBootstrap;

    @Autowired
    private DicomServerBootstrap localBootstrap;

    @Autowired
    private DicomApplicationService dicomApplicationService;

    @Autowired
    private DicomOperationsController dicomOperationsController;

    @Autowired
    private DicomPacsProperties properties;

    @Test
    void shouldFindAndPullFromRemoteNode() {
        String remoteAet = "RSRC" + Long.toString(System.nanoTime()).substring(0, 8);
        String patientId = "REMOTE-PATIENT-" + System.nanoTime();
        String studyUid = "1.2.826.0.6." + System.nanoTime();
        String seriesUid = studyUid + ".1";
        String sopUid = seriesUid + ".1";

        try (ConfigurableApplicationContext remoteContext = new SpringApplicationBuilder(PacsApplication.class)
                .run(
                        "--spring.profiles.active=test",
                        "--server.port=0",
                        "--dicom.pacs.local-aet=" + remoteAet,
                        "--dicom.pacs.local-port=0",
                        "--dicom.pacs.storage-dir=target\\test-storage-" + remoteAet,
                        "--spring.datasource.url=jdbc:h2:mem:" + remoteAet + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
                )) {
            DicomServerBootstrap remoteBootstrap = remoteContext.getBean(DicomServerBootstrap.class);
            DicomPacsProperties remoteProperties = remoteContext.getBean(DicomPacsProperties.class);
            AetApplicationService remoteAetApplicationService = remoteContext.getBean(AetApplicationService.class);

            aetApplicationService.register(new AetNode(
                    null,
                    remoteProperties.getLocalAet(),
                    "127.0.0.1",
                    remoteBootstrap.boundPort(),
                    AetRole.REMOTE,
                    "Remote Source",
                    "Remote source node",
                    true,
                    null,
                    null,
                    null
            ));
            remoteAetApplicationService.register(new AetNode(
                    null,
                    properties.getLocalAet(),
                    "127.0.0.1",
                    localBootstrap.boundPort(),
                    AetRole.REMOTE,
                    "Local Destination",
                    "Local PACS destination",
                    true,
                    null,
                    null,
                    null
            ));

            dicomClientBootstrap.store(
                    "127.0.0.1",
                    remoteBootstrap.boundPort(),
                    "REMOTE_SEEDER",
                    remoteProperties.getLocalAet(),
                    Map.ofEntries(
                            Map.entry("00100020", patientId),
                            Map.entry("00100021", "REMOTE"),
                            Map.entry("00100010", "Remote^Patient"),
                            Map.entry("0020000D", studyUid),
                            Map.entry("00080050", "ACC-" + patientId),
                            Map.entry("00080020", "20260428"),
                            Map.entry("00081030", "Remote Study"),
                            Map.entry("00080061", "CT"),
                            Map.entry("0020000E", seriesUid),
                            Map.entry("00080060", "CT"),
                            Map.entry("0008103E", "Remote Series"),
                            Map.entry("00080018", sopUid),
                            Map.entry("00080016", "1.2.840.10008.5.1.4.1.1.2"),
                            Map.entry("00020010", "1.2.840.10008.1.2.1")
                    ),
                    TestDicomObjects.createDicomBytes(Map.ofEntries(
                            Map.entry("00100020", patientId),
                            Map.entry("00100021", "REMOTE"),
                            Map.entry("00100010", "Remote^Patient"),
                            Map.entry("0020000D", studyUid),
                            Map.entry("00080050", "ACC-" + patientId),
                            Map.entry("00080020", "20260428"),
                            Map.entry("00081030", "Remote Study"),
                            Map.entry("00080061", "CT"),
                            Map.entry("0020000E", seriesUid),
                            Map.entry("00080060", "CT"),
                            Map.entry("0008103E", "Remote Series"),
                            Map.entry("00080018", sopUid),
                            Map.entry("00080016", "1.2.840.10008.5.1.4.1.1.2"),
                            Map.entry("00020010", "1.2.840.10008.1.2.1")
                    ), "remote-pixel-data")
            );

            List<Map<String, String>> remoteStudies = remoteDicomApplicationService.findStudies(
                    remoteProperties.getLocalAet(),
                    properties.getLocalAet(),
                    Map.of("00100020", patientId)
            );

            int movedCount = remoteDicomApplicationService.pullFromRemote(
                    remoteProperties.getLocalAet(),
                    properties.getLocalAet(),
                    Map.of("0020000D", studyUid),
                    properties.getLocalAet()
            );

            assertThat(remoteStudies).hasSize(1);
            assertThat(remoteStudies.getFirst()).containsEntry("0020000D", studyUid);
            assertThat(movedCount).isEqualTo(1);
        }
    }

    @Test
    void shouldSupportBidirectionalPushAndPullAcrossTwoPacsNodes() {
        String remoteAet = "RBID" + Long.toString(System.nanoTime()).substring(0, 8);

        try (ConfigurableApplicationContext remoteContext = new SpringApplicationBuilder(PacsApplication.class)
                .run(
                        "--spring.profiles.active=test",
                        "--server.port=0",
                        "--dicom.pacs.local-aet=" + remoteAet,
                        "--dicom.pacs.local-port=0",
                        "--dicom.pacs.storage-dir=target\\test-storage-" + remoteAet,
                        "--spring.datasource.url=jdbc:h2:mem:" + remoteAet + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
                )) {
            DicomServerBootstrap remoteBootstrap = remoteContext.getBean(DicomServerBootstrap.class);
            DicomPacsProperties remoteProperties = remoteContext.getBean(DicomPacsProperties.class);
            AetApplicationService remoteAetApplicationService = remoteContext.getBean(AetApplicationService.class);
            DicomClientBootstrap remoteDicomClientBootstrap = remoteContext.getBean(DicomClientBootstrap.class);
            DicomApplicationService remoteDicomApplicationService = remoteContext.getBean(DicomApplicationService.class);
            DicomOperationsController remoteDicomOperationsController = remoteContext.getBean(DicomOperationsController.class);

            registerPeer(aetApplicationService, remoteProperties.getLocalAet(), remoteBootstrap.boundPort(), "Remote PACS B");
            registerPeer(remoteAetApplicationService, properties.getLocalAet(), localBootstrap.boundPort(), "Local PACS A");

            StudySeed localPushSeed = storeStudy(
                    dicomClientBootstrap,
                    localBootstrap.boundPort(),
                    properties.getLocalAet(),
                    "A_PUSH",
                    UID.CTImageStorage,
                    "CT"
            );
            StudySeed localPullSeed = storeStudy(
                    dicomClientBootstrap,
                    localBootstrap.boundPort(),
                    properties.getLocalAet(),
                    "A_PULL",
                    UID.SecondaryCaptureImageStorage,
                    "OT"
            );
            StudySeed remotePushSeed = storeStudy(
                    remoteDicomClientBootstrap,
                    remoteBootstrap.boundPort(),
                    remoteProperties.getLocalAet(),
                    "B_PUSH",
                    UID.MRImageStorage,
                    "MR"
            );
            StudySeed remotePullSeed = storeStudy(
                    remoteDicomClientBootstrap,
                    remoteBootstrap.boundPort(),
                    remoteProperties.getLocalAet(),
                    "B_PULL",
                    UID.EnhancedCTImageStorage,
                    "CT"
            );

            assertThat(dicomOperationsController.echo(
                    new DicomOperationsController.TargetAetRequest(remoteProperties.getLocalAet())
            )).containsEntry("reachable", true);
            assertThat(remoteDicomOperationsController.echo(
                    new DicomOperationsController.TargetAetRequest(properties.getLocalAet())
            )).containsEntry("reachable", true);

            assertThat(dicomOperationsController.pushToRemote(
                    new DicomOperationsController.RemotePushRequest(
                            remoteProperties.getLocalAet(),
                            Map.of("0020000D", localPushSeed.studyUid())
                    )
            )).containsEntry("pushedCount", 1);
            assertThat(remoteDicomOperationsController.pushToRemote(
                    new DicomOperationsController.RemotePushRequest(
                            properties.getLocalAet(),
                            Map.of("0020000D", remotePushSeed.studyUid())
                    )
            )).containsEntry("pushedCount", 1);
            assertThat(dicomOperationsController.pullFromRemote(
                    new DicomOperationsController.RemotePullRequest(
                            remoteProperties.getLocalAet(),
                            Map.of("0020000D", remotePullSeed.studyUid()),
                            properties.getLocalAet()
                    )
            )).containsEntry("movedCount", 1);
            assertThat(remoteDicomOperationsController.pullFromRemote(
                    new DicomOperationsController.RemotePullRequest(
                            properties.getLocalAet(),
                            Map.of("0020000D", localPullSeed.studyUid()),
                            remoteProperties.getLocalAet()
                    )
            )).containsEntry("movedCount", 1);

            assertNodeContainsStudies(dicomApplicationService, localPushSeed, localPullSeed, remotePushSeed, remotePullSeed);
            assertNodeContainsStudies(remoteDicomApplicationService, localPushSeed, localPullSeed, remotePushSeed, remotePullSeed);
        }
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

    private StudySeed storeStudy(
            DicomClientBootstrap clientBootstrap,
            int port,
            String calledAet,
            String callingAet,
            String sopClassUid,
            String modality
    ) {
        String uniqueSuffix = Long.toString(System.nanoTime());
        String patientId = callingAet + "-PAT-" + uniqueSuffix;
        String studyUid = "1.2.826.0.20." + uniqueSuffix;
        String seriesUid = studyUid + ".1";
        String sopUid = seriesUid + ".1";
        Map<String, String> attributes = Map.ofEntries(
                Map.entry("00100020", patientId),
                Map.entry("00100021", callingAet),
                Map.entry("00100010", callingAet + "^Patient"),
                Map.entry("0020000D", studyUid),
                Map.entry("00080050", "ACC-" + uniqueSuffix),
                Map.entry("00080020", "20260428"),
                Map.entry("00081030", callingAet + " Study"),
                Map.entry("00080061", modality),
                Map.entry("0020000E", seriesUid),
                Map.entry("00080060", modality),
                Map.entry("0008103E", callingAet + " Series"),
                Map.entry("00080018", sopUid),
                Map.entry("00080016", sopClassUid),
                Map.entry("00020010", UID.ExplicitVRLittleEndian),
                Map.entry("00200013", "1")
        );

        DicomMessage response = clientBootstrap.store(
                "127.0.0.1",
                port,
                callingAet,
                calledAet,
                attributes,
                TestDicomObjects.createDicomBytes(attributes, callingAet + "-pixel-data")
        );

        assertThat(response.commandType()).isEqualTo(DicomCommandType.C_STORE_RESPONSE);
        assertThat(response.status()).isZero();
        return new StudySeed(studyUid, seriesUid, sopUid);
    }

    private void assertNodeContainsStudies(DicomApplicationService service, StudySeed... expectedStudies) {
        for (StudySeed expectedStudy : expectedStudies) {
            assertThat(service.findStudies(new StudyQueryCriteria(
                    null,
                    null,
                    expectedStudy.studyUid(),
                    null,
                    null,
                    null,
                    null
            ))).hasSize(1);
            assertThat(service.findInstances(new InstanceQueryCriteria(
                    expectedStudy.studyUid(),
                    expectedStudy.seriesUid(),
                    expectedStudy.sopUid()
            ))).hasSize(1);
        }
    }

    private record StudySeed(String studyUid, String seriesUid, String sopUid) {
    }
}
