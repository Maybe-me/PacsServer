package com.mylife.pacs.infrastructure.dimse;

import com.mylife.pacs.common.config.DicomPacsProperties;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.IncompatibleConnectionException;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

@Component
public class Dcm4cheAssociationFactory {

    private final DicomPacsProperties properties;

    public Dcm4cheAssociationFactory(DicomPacsProperties properties) {
        this.properties = properties;
    }

    public Dcm4cheAssociationContext openAssociation(
            String callingAet,
            String host,
            int port,
            String calledAet,
            Consumer<AAssociateRQ> requestCustomizer
    ) {
        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(threadFactory("dcm4che-scheduler"));
        try {
            Device device = new Device("dicom-scu-" + UUID.randomUUID());
            device.setExecutor(executorService);
            device.setScheduledExecutor(scheduledExecutorService);

            Connection localConnection = new Connection();
            localConnection.setHostname("0.0.0.0");
            localConnection.setPort(Connection.NOT_LISTENING);
            localConnection.setConnectTimeout(properties.getScu().getConnectTimeout());
            localConnection.setRequestTimeout(properties.getScu().getConnectTimeout());
            localConnection.setResponseTimeout(properties.getScu().getConnectTimeout() * 2);
            localConnection.setIdleTimeout((int) Math.min(Integer.MAX_VALUE, properties.getIdleTimeout()));

            Connection remoteConnection = new Connection();
            remoteConnection.setHostname(host);
            remoteConnection.setPort(port);
            remoteConnection.setConnectTimeout(properties.getScu().getConnectTimeout());
            remoteConnection.setRequestTimeout(properties.getScu().getConnectTimeout());
            remoteConnection.setResponseTimeout(properties.getScu().getConnectTimeout() * 2);
            remoteConnection.setRetrieveTimeout(properties.getScu().getConnectTimeout() * 6);
            remoteConnection.setIdleTimeout((int) Math.min(Integer.MAX_VALUE, properties.getIdleTimeout()));

            ApplicationEntity applicationEntity = new ApplicationEntity(callingAet);
            applicationEntity.setAssociationInitiator(true);
            applicationEntity.addConnection(localConnection);

            device.addConnection(localConnection);
            device.addApplicationEntity(applicationEntity);

            AAssociateRQ associateRQ = new AAssociateRQ();
            associateRQ.setCallingAET(callingAet);
            associateRQ.setCalledAET(calledAet);
            requestCustomizer.accept(associateRQ);

            Association association = applicationEntity.connect(localConnection, remoteConnection, associateRQ);
            return new Dcm4cheAssociationContext(association, executorService, scheduledExecutorService);
        } catch (InterruptedException exception) {
            executorService.shutdownNow();
            scheduledExecutorService.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while opening DIMSE association", exception);
        } catch (GeneralSecurityException | java.io.IOException | IncompatibleConnectionException exception) {
            executorService.shutdownNow();
            scheduledExecutorService.shutdownNow();
            throw new IllegalStateException("Failed to open DIMSE association", exception);
        }
    }

    private ThreadFactory threadFactory(String prefix) {
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + "-" + thread.threadId());
            thread.setDaemon(true);
            return thread;
        };
    }
}
