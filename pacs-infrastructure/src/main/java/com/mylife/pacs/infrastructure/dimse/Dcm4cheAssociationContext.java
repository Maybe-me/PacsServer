package com.mylife.pacs.infrastructure.dimse;

import org.dcm4che3.net.Association;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

public final class Dcm4cheAssociationContext implements AutoCloseable {

    private final Association association;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutorService;

    public Dcm4cheAssociationContext(
            Association association,
            ExecutorService executorService,
            ScheduledExecutorService scheduledExecutorService
    ) {
        this.association = association;
        this.executorService = executorService;
        this.scheduledExecutorService = scheduledExecutorService;
    }

    public Association association() {
        return association;
    }

    @Override
    public void close() {
        try {
            association.release();
        } catch (IOException exception) {
            association.abort();
        } finally {
            executorService.shutdownNow();
            scheduledExecutorService.shutdownNow();
        }
    }
}
