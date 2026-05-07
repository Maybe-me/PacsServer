package com.mylife.pacs.infrastructure.sync;

import com.mylife.pacs.common.config.DicomPacsProperties;
import com.mylife.pacs.infrastructure.lock.DistributedLockService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DicomSyncScheduler {

    private static final String PULL_LOCK = "sync-pull-dispatcher";
    private static final String PUSH_LOCK = "sync-push-dispatcher";

    private final DicomPacsProperties properties;
    private final ScheduledDicomSyncService scheduledDicomSyncService;
    private final DistributedLockService distributedLockService;
    private final AtomicBoolean pullRunning = new AtomicBoolean(false);
    private final AtomicBoolean pushRunning = new AtomicBoolean(false);

    public DicomSyncScheduler(
            DicomPacsProperties properties,
            ScheduledDicomSyncService scheduledDicomSyncService,
            DistributedLockService distributedLockService
    ) {
        this.properties = properties;
        this.scheduledDicomSyncService = scheduledDicomSyncService;
        this.distributedLockService = distributedLockService;
    }

    @Scheduled(
            fixedDelayString = "${dicom.pacs.sync.dispatcher-interval-ms:60000}",
            initialDelayString = "${dicom.pacs.sync.initial-delay-ms:30000}"
    )
    public void dispatchPullJobs() {
        if (!shouldRun(pullRunning) || !distributedLockService.tryLock(PULL_LOCK, schedulerLeaseTime())) {
            return;
        }
        try {
            scheduledDicomSyncService.runConfiguredPullJobs();
        } finally {
            distributedLockService.unlock(PULL_LOCK);
            pullRunning.set(false);
        }
    }

    @Scheduled(
            fixedDelayString = "${dicom.pacs.sync.dispatcher-interval-ms:60000}",
            initialDelayString = "${dicom.pacs.sync.initial-delay-ms:30000}"
    )
    public void dispatchPushJobs() {
        if (!shouldRun(pushRunning) || !distributedLockService.tryLock(PUSH_LOCK, schedulerLeaseTime())) {
            return;
        }
        try {
            scheduledDicomSyncService.runConfiguredPushJobs();
        } finally {
            distributedLockService.unlock(PUSH_LOCK);
            pushRunning.set(false);
        }
    }

    private boolean shouldRun(AtomicBoolean runningFlag) {
        return properties.getSync().isEnabled() && runningFlag.compareAndSet(false, true);
    }

    private Duration schedulerLeaseTime() {
        return Duration.ofMillis(Math.max(1L, properties.getSync().getLockAtMostMs()));
    }
}
