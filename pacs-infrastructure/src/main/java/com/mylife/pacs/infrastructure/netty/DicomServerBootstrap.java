package com.mylife.pacs.infrastructure.netty;

import com.mylife.pacs.common.config.DicomPacsProperties;
import com.mylife.pacs.infrastructure.dimse.Dcm4cheCFindScp;
import com.mylife.pacs.infrastructure.dimse.Dcm4cheCMoveScp;
import com.mylife.pacs.infrastructure.dimse.Dcm4cheCStoreScp;
import com.mylife.pacs.infrastructure.dimse.StorageSopClasses;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.service.BasicCEchoSCP;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Component
public class DicomServerBootstrap implements SmartLifecycle {

    private static final String[] TRANSFER_SYNTAXES = {
            UID.ImplicitVRLittleEndian,
            UID.ExplicitVRLittleEndian,
            UID.DeflatedExplicitVRLittleEndian,
            UID.ExplicitVRBigEndian,
            UID.JPEGBaseline8Bit,
            UID.JPEGExtended12Bit,
            UID.JPEGLossless,
            UID.JPEGLosslessSV1,
            UID.JPEGLSLossless,
            UID.JPEGLSNearLossless,
            UID.JPEG2000Lossless,
            UID.JPEG2000,
            UID.HTJ2KLossless,
            UID.HTJ2KLosslessRPCL,
            UID.HTJ2K,
            UID.RLELossless,
            UID.MPEG2MPML,
            UID.MPEG2MPMLF,
            UID.MPEG2MPHL,
            UID.MPEG2MPHLF,
            UID.MPEG4HP41,
            UID.MPEG4HP41F,
            UID.MPEG4HP41BD,
            UID.MPEG4HP41BDF,
            UID.MPEG4HP422D,
            UID.MPEG4HP422DF,
            UID.MPEG4HP423D,
            UID.MPEG4HP423DF,
            UID.MPEG4HP42STEREO,
            UID.MPEG4HP42STEREOF,
            UID.HEVCMP51,
            UID.HEVCM10P51
    };

    private final DicomPacsProperties properties;
    private final Dcm4cheCStoreScp cStoreScp;
    private final Dcm4cheCFindScp cFindScp;
    private final Dcm4cheCMoveScp cMoveScp;

    private volatile boolean running;
    private Device device;
    private Connection connection;
    private ExecutorService executorService;
    private ScheduledExecutorService scheduledExecutorService;

    public DicomServerBootstrap(
            DicomPacsProperties properties,
            Dcm4cheCStoreScp cStoreScp,
            Dcm4cheCFindScp cFindScp,
            Dcm4cheCMoveScp cMoveScp
    ) {
        this.properties = properties;
        this.cStoreScp = cStoreScp;
        this.cFindScp = cFindScp;
        this.cMoveScp = cMoveScp;
    }

    @Override
    public synchronized void start() {
        if (running || !properties.getScp().isEnabled()) {
            return;
        }

        executorService = Executors.newVirtualThreadPerTaskExecutor();
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

        try {
            device = new Device(properties.getLocalAet());
            device.setExecutor(executorService);
            device.setScheduledExecutor(scheduledExecutorService);

            connection = new Connection();
            connection.setHostname("0.0.0.0");
            connection.setPort(resolveListenPort(properties.getLocalPort()));
            connection.setConnectTimeout(properties.getScu().getConnectTimeout());
            connection.setRequestTimeout(properties.getScu().getConnectTimeout());
            connection.setResponseTimeout(properties.getScu().getConnectTimeout() * 2);
            connection.setRetrieveTimeout(properties.getScu().getConnectTimeout() * 6);
            connection.setIdleTimeout((int) Math.min(Integer.MAX_VALUE, properties.getIdleTimeout()));
            connection.setSendBufferSize(1_048_576);
            connection.setReceiveBufferSize(1_048_576);

            ApplicationEntity applicationEntity = new ApplicationEntity(properties.getLocalAet());
            applicationEntity.setAssociationAcceptor(true);
            applicationEntity.addConnection(connection);

            if (!properties.getSecurity().getAetWhitelist().isEmpty()) {
                applicationEntity.setAcceptedCallingAETitles(properties.getSecurity().getAetWhitelist().toArray(String[]::new));
            }

            applicationEntity.addTransferCapability(new TransferCapability(
                    "verification-scp",
                    UID.Verification,
                    TransferCapability.Role.SCP,
                    UID.ImplicitVRLittleEndian,
                    UID.ExplicitVRLittleEndian
            ));
            applicationEntity.addTransferCapability(new TransferCapability(
                    "patient-root-find-scp",
                    UID.PatientRootQueryRetrieveInformationModelFind,
                    TransferCapability.Role.SCP,
                    UID.ImplicitVRLittleEndian,
                    UID.ExplicitVRLittleEndian
            ));
            applicationEntity.addTransferCapability(new TransferCapability(
                    "study-root-find-scp",
                    UID.StudyRootQueryRetrieveInformationModelFind,
                    TransferCapability.Role.SCP,
                    UID.ImplicitVRLittleEndian,
                    UID.ExplicitVRLittleEndian
            ));
            applicationEntity.addTransferCapability(new TransferCapability(
                    "study-root-move-scp",
                    UID.StudyRootQueryRetrieveInformationModelMove,
                    TransferCapability.Role.SCP,
                    UID.ImplicitVRLittleEndian,
                    UID.ExplicitVRLittleEndian
            ));
            for (String sopClassUid : StorageSopClasses.all()) {
                applicationEntity.addTransferCapability(new TransferCapability(
                        "store-scp-" + sopClassUid,
                        sopClassUid,
                        TransferCapability.Role.SCP,
                        TRANSFER_SYNTAXES
                ));
            }

            DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
            serviceRegistry.addDicomService(new BasicCEchoSCP());
            serviceRegistry.addDicomService(cStoreScp);
            serviceRegistry.addDicomService(cFindScp);
            serviceRegistry.addDicomService(cMoveScp);
            device.setDimseRQHandler(serviceRegistry);

            device.addConnection(connection);
            device.addApplicationEntity(applicationEntity);
            device.bindConnections();
            running = true;
        } catch (java.io.IOException | GeneralSecurityException exception) {
            if (scheduledExecutorService != null) {
                scheduledExecutorService.shutdownNow();
            }
            if (executorService != null) {
                executorService.shutdownNow();
            }
            throw new IllegalStateException("Failed to start DICOM server", exception);
        }
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        try {
            if (device != null) {
                device.unbindConnections();
            }
        } finally {
            if (scheduledExecutorService != null) {
                scheduledExecutorService.shutdownNow();
            }
            if (executorService != null) {
                executorService.shutdownNow();
            }
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    public int boundPort() {
        if (connection == null || connection.getListener() == null || !(connection.getListener().getEndPoint() instanceof InetSocketAddress address)) {
            throw new IllegalStateException("DICOM server is not running");
        }
        return address.getPort();
    }

    private int resolveListenPort(int configuredPort) {
        if (configuredPort > 0) {
            return configuredPort;
        }
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to allocate an ephemeral DICOM port", exception);
        }
    }
}
