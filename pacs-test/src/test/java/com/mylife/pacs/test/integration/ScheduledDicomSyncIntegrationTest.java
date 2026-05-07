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
import com.mylife.pacs.domain.model.SyncFailureCategory;
import com.mylife.pacs.domain.model.SyncJobConfig;
import com.mylife.pacs.domain.model.SyncJobType;
import com.mylife.pacs.domain.service.InstanceQueryCriteria;
import com.mylife.pacs.domain.service.StudyQueryCriteria;
import com.mylife.pacs.infrastructure.netty.DicomClientBootstrap;
import com.mylife.pacs.infrastructure.netty.DicomServerBootstrap;
import com.mylife.pacs.infrastructure.netty.message.DicomCommandType;
import com.mylife.pacs.infrastructure.netty.message.DicomMessage;
import com.mylife.pacs.infrastructure.lock.DatabaseLockProvider;
import com.mylife.pacs.infrastructure.persistence.dialect.DatabaseDialectService;
import com.mylife.pacs.infrastructure.rest.admin.SyncAdminController;
import com.mylife.pacs.infrastructure.sync.ScheduledDicomSyncService;
import com.mylife.pacs.infrastructure.sync.SyncExecutionRecordService;
import com.mylife.pacs.test.support.TestDicomObjects;
import org.dcm4che3.data.UID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = PacsApplication.class)
@ActiveProfiles("test")
class ScheduledDicomSyncIntegrationTest {

    private static final DateTimeFormatter DICOM_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    @Autowired
    private AetApplicationService aetApplicationService;

    @Autowired
    private DicomApplicationService dicomApplicationService;

    @Autowired
    private DicomClientBootstrap dicomClientBootstrap;

    @Autowired
    private DicomServerBootstrap localBootstrap;

    @Autowired
    private DicomPacsProperties properties;

    @Autowired
    private ScheduledDicomSyncService scheduledDicomSyncService;

    @Autowired
    private SyncAdminController syncAdminController;

    @Autowired
    private SyncJobConfigApplicationService syncJobConfigApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseDialectService databaseDialectService;

    @Test
    void shouldPushLocalStudyThroughScheduledJob() {
        String remoteAet = "SPUSH" + Long.toString(System.nanoTime()).substring(0, 8);
        String today = LocalDate.now().format(DICOM_DATE);

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
            DicomApplicationService remoteDicomApplicationService = remoteContext.getBean(DicomApplicationService.class);

            registerPeer(aetApplicationService, remoteAet, remoteBootstrap.boundPort(), "Scheduled Push Remote");

            StudySeed localSeed = seedStudy(
                    dicomClientBootstrap,
                    localBootstrap.boundPort(),
                    properties.getLocalAet(),
                    "SYNC_PUSH",
                    UID.CTImageStorage,
                    "CT",
                    today
            );

            DicomPacsProperties.Sync.PushJob pushJob = new DicomPacsProperties.Sync.PushJob();
            pushJob.setJobName("push-local-study");
            pushJob.setTargetAet(remoteAet);
            pushJob.setStudyDateLookbackDays(0);
            pushJob.setPatientId(localSeed.patientId());
            pushJob.setModality("CT");

            ScheduledDicomSyncService.SyncJobResult result = scheduledDicomSyncService.runPushJob(pushJob);

            assertThat(result.transferredStudyCount()).isEqualTo(1);
            assertThat(result.transferredInstanceCount()).isEqualTo(1);
            assertThat(remoteDicomApplicationService.findStudies(new StudyQueryCriteria(
                    null,
                    null,
                    localSeed.studyUid(),
                    null,
                    null,
                    null,
                    null
            ))).hasSize(1);
        }
    }

    @Test
    void shouldPreventLoopedPushBackToSourceAfterScheduledPull() {
        String remoteAet = "SLOOP" + Long.toString(System.nanoTime()).substring(0, 8);
        String today = LocalDate.now().format(DICOM_DATE);

        try (ConfigurableApplicationContext remoteContext = new SpringApplicationBuilder(PacsApplication.class)
                .run(
                        "--spring.profiles.active=test",
                        "--server.port=0",
                        "--dicom.pacs.local-aet=" + remoteAet,
                        "--dicom.pacs.local-port=0",
                        "--dicom.pacs.storage-dir=target\\test-storage-" + remoteAet,
                        "--spring.datasource.url=jdbc:h2:mem:" + remoteAet + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
                )) {
            DicomApplicationService remoteDicomApplicationService = remoteContext.getBean(DicomApplicationService.class);
            DicomPacsProperties remoteProperties = remoteContext.getBean(DicomPacsProperties.class);
            AetApplicationService remoteAetApplicationService = remoteContext.getBean(AetApplicationService.class);
            ScheduledDicomSyncService remoteScheduledDicomSyncService = remoteContext.getBean(ScheduledDicomSyncService.class);

            registerPeer(aetApplicationService, remoteAet, remoteContext.getBean(DicomServerBootstrap.class).boundPort(), "Loop Remote");
            registerPeer(remoteAetApplicationService, properties.getLocalAet(), localBootstrap.boundPort(), "Loop Local");

            StudySeed localSeed = seedStudy(
                    dicomClientBootstrap,
                    localBootstrap.boundPort(),
                    properties.getLocalAet(),
                    "SYNC_PULL",
                    UID.MRImageStorage,
                    "MR",
                    today
            );

            DicomPacsProperties.Sync.PullJob pullJob = new DicomPacsProperties.Sync.PullJob();
            pullJob.setJobName("pull-from-a");
            pullJob.setTargetAet(properties.getLocalAet());
            pullJob.setDestinationAet(remoteProperties.getLocalAet());
            pullJob.setStudyDateLookbackDays(0);
            pullJob.setPatientId(localSeed.patientId());
            pullJob.setModality("MR");

            ScheduledDicomSyncService.SyncJobResult pullResult = remoteScheduledDicomSyncService.runPullJob(pullJob);
            List<PacsInstance> pulledInstances = remoteDicomApplicationService.findInstances(new InstanceQueryCriteria(
                    localSeed.studyUid(),
                    localSeed.seriesUid(),
                    localSeed.sopUid()
            ));

            DicomPacsProperties.Sync.PushJob pushJob = new DicomPacsProperties.Sync.PushJob();
            pushJob.setJobName("push-back-to-source");
            pushJob.setTargetAet(properties.getLocalAet());
            pushJob.setStudyDateLookbackDays(0);
            pushJob.setPatientId(localSeed.patientId());
            pushJob.setModality("MR");
            pushJob.setPreventLoopToSource(true);

            ScheduledDicomSyncService.SyncJobResult pushResult = remoteScheduledDicomSyncService.runPushJob(pushJob);

            assertThat(pullResult.transferredStudyCount()).isEqualTo(1);
            assertThat(pullResult.transferredInstanceCount()).isEqualTo(1);
            assertThat(pulledInstances).hasSize(1);
            assertThat(pulledInstances.getFirst().extraTags()).containsEntry(SyncMetadataKeys.SOURCE_AET, properties.getLocalAet());
            assertThat(pushResult.transferredInstanceCount()).isZero();
            assertThat(pushResult.sourceLoopSkipCount()).isEqualTo(1);
            assertThat(dicomApplicationService.findInstances(new InstanceQueryCriteria(
                    localSeed.studyUid(),
                    localSeed.seriesUid(),
                    localSeed.sopUid()
            ))).hasSize(1);
        }
    }

    @Test
    void shouldRecordExecutionForTriggeredPushJob() {
        String remoteAet = "SREC" + Long.toString(System.nanoTime()).substring(0, 8);
        String today = LocalDate.now().format(DICOM_DATE);

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
            DicomApplicationService remoteDicomApplicationService = remoteContext.getBean(DicomApplicationService.class);

            registerPeer(aetApplicationService, remoteAet, remoteBootstrap.boundPort(), "Execution Remote");
            StudySeed localSeed = seedStudy(
                    dicomClientBootstrap,
                    localBootstrap.boundPort(),
                    properties.getLocalAet(),
                    "SYNC_RECORD",
                    UID.CTImageStorage,
                    "CT",
                    today
            );
            try {
                syncJobConfigApplicationService.create(new SyncJobConfig(
                        null,
                        "recorded-push-job",
                        SyncJobType.PUSH,
                        remoteAet,
                        null,
                        localSeed.patientId(),
                        "CT",
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

                Map<String, Object> triggerResult = syncAdminController.triggerPush("recorded-push-job");
                List<SyncJobConfig> jobs = syncAdminController.listJobs();
                List<SyncExecutionRecordService.SyncExecutionRecord> executions = syncAdminController.listExecutions();

                assertThat(triggerResult).containsEntry("jobName", "recorded-push-job");
                assertThat(triggerResult).containsEntry("transferredInstanceCount", 1);
                assertThat(jobs).extracting(SyncJobConfig::jobName).contains("recorded-push-job");
                assertThat(remoteDicomApplicationService.findStudies(new StudyQueryCriteria(
                        null,
                        null,
                        localSeed.studyUid(),
                        null,
                        null,
                        null,
                        null
                ))).hasSize(1);
                assertThat(executions).isNotEmpty();
                assertThat(executions.getFirst().jobName()).isEqualTo("recorded-push-job");
                assertThat(executions.getFirst().status()).isEqualTo("SUCCESS");
                assertThat(executions.getFirst().targetAet()).isEqualTo(remoteAet);
                assertThat(executions.getFirst().transferredInstanceCount()).isEqualTo(1);
            } finally {
                syncJobConfigApplicationService.remove("recorded-push-job");
            }
        }
    }

    @Test
    void shouldRetryAndPauseFailedPersistedPushJob() {
        String offlineAet = "SOFF" + Long.toString(System.nanoTime()).substring(0, 8);
        String today = LocalDate.now().format(DICOM_DATE);

        aetApplicationService.register(new AetNode(
                null,
                offlineAet,
                "127.0.0.1",
                29999,
                AetRole.REMOTE,
                "Offline PACS",
                "Offline PACS",
                true,
                null,
                null,
                null
        ));

        StudySeed localSeed = seedStudy(
                dicomClientBootstrap,
                localBootstrap.boundPort(),
                properties.getLocalAet(),
                "SYNC_FAIL",
                UID.CTImageStorage,
                "CT",
                today
        );

        try {
            syncJobConfigApplicationService.create(new SyncJobConfig(
                    null,
                    "failing-push-job",
                    SyncJobType.PUSH,
                    offlineAet,
                    null,
                    localSeed.patientId(),
                    "CT",
                    0,
                    true,
                    true,
                    0,
                    0,
                    0L,
                    List.of(),
                    List.of(),
                    1,
                    1,
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

            assertThatThrownBy(() -> syncAdminController.triggerPush("failing-push-job"))
                    .isInstanceOf(IllegalStateException.class);

            SyncJobConfig jobConfig = syncJobConfigApplicationService.findByJobName("failing-push-job");
            SyncExecutionRecordService.SyncExecutionRecord execution = syncAdminController.listExecutions().stream()
                    .filter(item -> item.jobName().equals("failing-push-job"))
                    .findFirst()
                    .orElseThrow();

            assertThat(jobConfig.paused()).isTrue();
            assertThat(jobConfig.consecutiveFailureCount()).isEqualTo(1);
            assertThat(jobConfig.lastErrorCategory()).isEqualTo(SyncFailureCategory.CONNECTIVITY);
            assertThat(jobConfig.lastFailureAt()).isNotNull();
            assertThat(execution.status()).isEqualTo("FAILED");
            assertThat(execution.attemptCount()).isEqualTo(2);
            assertThat(execution.errorCategory()).isEqualTo(SyncFailureCategory.CONNECTIVITY.name());
        } finally {
            syncJobConfigApplicationService.remove("failing-push-job");
        }
    }

    @Test
    void shouldFilterExecutionHistoryAndProvideSummary() {
        String remoteAet = "SFIL" + Long.toString(System.nanoTime()).substring(0, 8);
        String offlineAet = "SERR" + Long.toString(System.nanoTime()).substring(0, 8);
        String today = LocalDate.now().format(DICOM_DATE);

        aetApplicationService.register(new AetNode(
                null,
                offlineAet,
                "127.0.0.1",
                29998,
                AetRole.REMOTE,
                "Offline Filter PACS",
                "Offline Filter PACS",
                true,
                null,
                null,
                null
        ));

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
            registerPeer(aetApplicationService, remoteAet, remoteBootstrap.boundPort(), "Filter Remote");

            StudySeed localSeed = seedStudy(
                    dicomClientBootstrap,
                    localBootstrap.boundPort(),
                    properties.getLocalAet(),
                    "SYNC_FILTER",
                    UID.CTImageStorage,
                    "CT",
                    today
            );

            syncJobConfigApplicationService.create(new SyncJobConfig(
                    null,
                    "filter-success-job",
                    SyncJobType.PUSH,
                    remoteAet,
                    null,
                    localSeed.patientId(),
                    "CT",
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

            syncJobConfigApplicationService.create(new SyncJobConfig(
                    null,
                    "filter-failure-job",
                    SyncJobType.PUSH,
                    offlineAet,
                    null,
                    localSeed.patientId(),
                    "CT",
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
                syncAdminController.triggerPush("filter-success-job");
                assertThatThrownBy(() -> syncAdminController.triggerPush("filter-failure-job"))
                        .isInstanceOf(IllegalStateException.class);

                List<SyncExecutionRecordService.SyncExecutionRecord> filtered = syncAdminController.listExecutions(
                        "FAILED",
                        "filter-failure",
                        SyncFailureCategory.CONNECTIVITY.name(),
                        20
                );
                SyncExecutionRecordService.SyncExecutionSummary summary = syncAdminController.executionSummary(
                        null,
                        "filter-",
                        null
                );

                assertThat(filtered).hasSize(1);
                assertThat(filtered.getFirst().jobName()).isEqualTo("filter-failure-job");
                assertThat(summary.totalCount()).isGreaterThanOrEqualTo(2);
                assertThat(summary.statusCounts()).containsEntry("SUCCESS", 1L);
                assertThat(summary.statusCounts()).containsEntry("FAILED", 1L);
                assertThat(summary.errorCategoryCounts()).containsEntry(SyncFailureCategory.CONNECTIVITY.name(), 1L);
            } finally {
                syncJobConfigApplicationService.remove("filter-success-job");
                syncJobConfigApplicationService.remove("filter-failure-job");
            }
        }
    }

    @Test
    void shouldApplyPushSourceAetRules() {
        String remoteAet = "SRULE" + Long.toString(System.nanoTime()).substring(0, 8);
        String today = LocalDate.now().format(DICOM_DATE);

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
            DicomApplicationService remoteDicomApplicationService = remoteContext.getBean(DicomApplicationService.class);
            registerPeer(aetApplicationService, remoteAet, remoteBootstrap.boundPort(), "Rules Remote");

            StudySeed allowedStudy = seedStudy(
                    dicomClientBootstrap,
                    localBootstrap.boundPort(),
                    properties.getLocalAet(),
                    "ALLOW_SRC",
                    UID.CTImageStorage,
                    "CT",
                    today
            );
            StudySeed blockedStudy = seedStudy(
                    dicomClientBootstrap,
                    localBootstrap.boundPort(),
                    properties.getLocalAet(),
                    "BLOCK_SRC",
                    UID.CTImageStorage,
                    "CT",
                    today
            );

            DicomPacsProperties.Sync.PushJob pushJob = new DicomPacsProperties.Sync.PushJob();
            pushJob.setJobName("push-with-rules");
            pushJob.setTargetAet(remoteAet);
            pushJob.setStudyDateLookbackDays(0);
            pushJob.setModality("CT");
            pushJob.setSourceAetAllowList(List.of("ALLOW_SRC"));
            pushJob.setSourceAetBlockList(List.of("BLOCK_SRC"));

            ScheduledDicomSyncService.SyncJobResult result = scheduledDicomSyncService.runPushJob(pushJob);

            assertThat(result.transferredStudyCount()).isEqualTo(1);
            assertThat(result.sourceLoopSkipCount()).isGreaterThanOrEqualTo(1);
            assertThat(remoteDicomApplicationService.findStudies(new StudyQueryCriteria(
                    null,
                    null,
                    allowedStudy.studyUid(),
                    null,
                    null,
                    null,
                    null
            ))).hasSize(1);
            assertThat(remoteDicomApplicationService.findStudies(new StudyQueryCriteria(
                    null,
                    null,
                    blockedStudy.studyUid(),
                    null,
                    null,
                    null,
                    null
            ))).isEmpty();
        }
    }

    @Test
    void shouldLimitStudiesPerScheduledPushRun() {
        String remoteAet = "SLIM" + Long.toString(System.nanoTime()).substring(0, 8);
        String today = LocalDate.now().format(DICOM_DATE);
        String patientId = "LIMIT-PAT-" + Long.toString(System.nanoTime());

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
            DicomApplicationService remoteDicomApplicationService = remoteContext.getBean(DicomApplicationService.class);
            registerPeer(aetApplicationService, remoteAet, remoteBootstrap.boundPort(), "Limit Remote");

            seedStudy(
                    dicomClientBootstrap,
                    localBootstrap.boundPort(),
                    properties.getLocalAet(),
                    "SYNC_LIMIT_A",
                    UID.CTImageStorage,
                    "DX",
                    today,
                    patientId
            );
            seedStudy(
                    dicomClientBootstrap,
                    localBootstrap.boundPort(),
                    properties.getLocalAet(),
                    "SYNC_LIMIT_B",
                    UID.CTImageStorage,
                    "DX",
                    today,
                    patientId
            );

            DicomPacsProperties.Sync.PushJob pushJob = new DicomPacsProperties.Sync.PushJob();
            pushJob.setJobName("push-with-study-limit");
            pushJob.setTargetAet(remoteAet);
            pushJob.setPatientId(patientId);
            pushJob.setModality("DX");
            pushJob.setStudyDateLookbackDays(0);
            pushJob.setMaxStudiesPerRun(1);

            ScheduledDicomSyncService.SyncJobResult result = scheduledDicomSyncService.runPushJob(pushJob);

            assertThat(result.examinedCount()).isEqualTo(1);
            assertThat(result.transferredStudyCount()).isEqualTo(1);
            assertThat(remoteDicomApplicationService.findStudies(new StudyQueryCriteria(
                    patientId,
                    null,
                    null,
                    null,
                    "DX",
                    today,
                    today
            ))).hasSize(1);
        }
    }

    @Test
    void shouldUseDatabaseSchedulerLockAcrossOwners() {
        String lockName = "test-lock-" + Long.toString(System.nanoTime());
        jdbcTemplate.update(
                "INSERT INTO sync_scheduler_lock(lock_name, locked_until, locked_at, locked_by) VALUES (?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)",
                lockName,
                "bootstrap"
        );
        DatabaseLockProvider ownerA = new DatabaseLockProvider(jdbcTemplate, databaseDialectService, "owner-a", 60_000L);
        DatabaseLockProvider ownerB = new DatabaseLockProvider(jdbcTemplate, databaseDialectService, "owner-b", 60_000L);

        assertThat(ownerA.tryLock(lockName, Duration.ofSeconds(60))).isTrue();
        assertThat(ownerB.tryLock(lockName, Duration.ofSeconds(60))).isFalse();

        ownerA.unlock(lockName);

        assertThat(ownerB.tryLock(lockName, Duration.ofSeconds(60))).isTrue();
        ownerB.unlock(lockName);
    }

    @Test
    void shouldRenewDatabaseSchedulerLockForCurrentOwner() {
        String lockName = "test-renew-lock-" + Long.toString(System.nanoTime());
        jdbcTemplate.update(
                "INSERT INTO sync_scheduler_lock(lock_name, locked_until, locked_at, locked_by) VALUES (?, TIMESTAMP '1970-01-01 00:00:00', TIMESTAMP '1970-01-01 00:00:00', ?)",
                lockName,
                "bootstrap"
        );
        DatabaseLockProvider ownerA = new DatabaseLockProvider(jdbcTemplate, databaseDialectService, "owner-a", 1_000L);
        DatabaseLockProvider ownerB = new DatabaseLockProvider(jdbcTemplate, databaseDialectService, "owner-b", 1_000L);

        assertThat(ownerA.tryLock(lockName, Duration.ofSeconds(1))).isTrue();
        assertThat(ownerA.renew(lockName, Duration.ofSeconds(30))).isTrue();
        assertThat(ownerB.tryLock(lockName, Duration.ofSeconds(1))).isFalse();

        ownerA.unlock(lockName);
        assertThat(ownerB.tryLock(lockName, Duration.ofSeconds(1))).isTrue();
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

    private StudySeed seedStudy(
            DicomClientBootstrap clientBootstrap,
            int port,
            String calledAet,
            String callingAet,
            String sopClassUid,
            String modality,
            String studyDate
    ) {
        return seedStudy(clientBootstrap, port, calledAet, callingAet, sopClassUid, modality, studyDate, null);
    }

    private StudySeed seedStudy(
            DicomClientBootstrap clientBootstrap,
            int port,
            String calledAet,
            String callingAet,
            String sopClassUid,
            String modality,
            String studyDate,
            String patientIdOverride
    ) {
        String uniqueSuffix = Long.toString(System.nanoTime());
        String patientId = patientIdOverride == null ? callingAet + "-PAT-" + uniqueSuffix : patientIdOverride;
        String studyUid = "1.2.826.0.30." + uniqueSuffix;
        String seriesUid = studyUid + ".1";
        String sopUid = seriesUid + ".1";
        Map<String, String> attributes = Map.ofEntries(
                Map.entry("00100020", patientId),
                Map.entry("00100021", callingAet),
                Map.entry("00100010", callingAet + "^Patient"),
                Map.entry("0020000D", studyUid),
                Map.entry("00080050", "ACC-" + uniqueSuffix),
                Map.entry("00080020", studyDate),
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
        return new StudySeed(patientId, studyUid, seriesUid, sopUid);
    }

    private record StudySeed(String patientId, String studyUid, String seriesUid, String sopUid) {
    }
}
