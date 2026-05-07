package com.mylife.pacs.infrastructure.netty;

import com.mylife.pacs.common.config.DicomPacsProperties;
import com.mylife.pacs.infrastructure.dimse.Dcm4cheAssociationContext;
import com.mylife.pacs.infrastructure.dimse.Dcm4cheAssociationFactory;
import com.mylife.pacs.infrastructure.dimse.Dcm4cheAttributesBridge;
import com.mylife.pacs.infrastructure.dimse.PreparedDicomObject;
import com.mylife.pacs.infrastructure.netty.message.DicomCommandType;
import com.mylife.pacs.infrastructure.netty.message.DicomMessage;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.DataWriterAdapter;
import org.dcm4che3.net.DimseRSP;
import org.dcm4che3.net.Priority;
import org.dcm4che3.net.Status;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

@Component
public class DicomClientBootstrap {

    private final DicomPacsProperties properties;
    private final Dcm4cheAssociationFactory associationFactory;

    public DicomClientBootstrap(DicomPacsProperties properties, Dcm4cheAssociationFactory associationFactory) {
        this.properties = properties;
        this.associationFactory = associationFactory;
    }

    public boolean echo(String host, int port, String callingAet, String calledAet) {
        try (Dcm4cheAssociationContext associationContext = associationFactory.openAssociation(
                callingAet,
                host,
                port,
                calledAet,
                associateRQ -> associateRQ.addPresentationContextFor(UID.Verification, UID.ImplicitVRLittleEndian)
        )) {
            Attributes command = awaitFinalCommand(associationContext.association().cecho());
            return command != null && command.getInt(Tag.Status, Status.ProcessingFailure) == Status.Success;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to perform remote C-ECHO", exception);
        }
    }

    public DicomMessage store(
            String host,
            int port,
            String callingAet,
            String calledAet,
            Map<String, String> attributes,
            byte[] payload
    ) {
        PreparedDicomObject prepared = PreparedDicomObject.fromPayload(attributes, payload);
        try (Dcm4cheAssociationContext associationContext = associationFactory.openAssociation(
                callingAet,
                host,
                port,
                calledAet,
                associateRQ -> associateRQ.addPresentationContextFor(prepared.sopClassUid(), prepared.transferSyntaxUid())
        )) {
            Attributes command = awaitFinalCommand(associationContext.association().cstore(
                    prepared.sopClassUid(),
                    prepared.sopInstanceUid(),
                    Priority.NORMAL,
                    DataWriterAdapter.forAttributes(prepared.dataset()),
                    prepared.transferSyntaxUid()
            ));
            int status = command == null ? Status.ProcessingFailure : command.getInt(Tag.Status, Status.ProcessingFailure);
            return response(
                    status == Status.Success ? DicomCommandType.C_STORE_RESPONSE : DicomCommandType.ERROR,
                    calledAet,
                    callingAet,
                    status,
                    responseAttributes(command, Map.of()),
                    java.util.List.of(),
                    commandMessage(command, status)
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to perform remote C-STORE", exception);
        }
    }

    public DicomMessage find(
            String host,
            int port,
            String callingAet,
            String calledAet,
            Map<String, String> attributes
    ) {
        Attributes queryAttributes = Dcm4cheAttributesBridge.toQueryAttributes(attributes);
        String level = queryAttributes.getString(Tag.QueryRetrieveLevel, "STUDY");
        String sopClassUid = "PATIENT".equalsIgnoreCase(level)
                ? UID.PatientRootQueryRetrieveInformationModelFind
                : UID.StudyRootQueryRetrieveInformationModelFind;
        try (Dcm4cheAssociationContext associationContext = associationFactory.openAssociation(
                callingAet,
                host,
                port,
                calledAet,
                associateRQ -> associateRQ.addPresentationContextFor(sopClassUid, UID.ImplicitVRLittleEndian)
        )) {
            DimseRSP response = associationContext.association().cfind(
                    sopClassUid,
                    Priority.NORMAL,
                    queryAttributes,
                    UID.ImplicitVRLittleEndian,
                    associationContext.association().nextMessageID()
            );
            ArrayList<Map<String, String>> results = new ArrayList<>();
            Attributes command = null;
            while (response.next()) {
                command = response.getCommand();
                int status = command.getInt(Tag.Status, Status.ProcessingFailure);
                if (Status.isPending(status) && response.getDataset() != null) {
                    results.add(Dcm4cheAttributesBridge.toAttributeMap(response.getDataset()));
                }
            }
            int status = command == null ? Status.ProcessingFailure : command.getInt(Tag.Status, Status.ProcessingFailure);
            return response(
                    status == Status.Success ? DicomCommandType.C_FIND_RESPONSE : DicomCommandType.ERROR,
                    calledAet,
                    callingAet,
                    status,
                    responseAttributes(command, Map.of()),
                    results,
                    commandMessage(command, status)
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to perform remote C-FIND", exception);
        }
    }

    public DicomMessage move(
            String host,
            int port,
            String callingAet,
            String calledAet,
            Map<String, String> attributes
    ) {
        Attributes queryAttributes = Dcm4cheAttributesBridge.toQueryAttributes(attributes);
        String destinationAet = firstNonBlank(
                attributes.get("MoveDestination"),
                attributes.get("00000600"),
                properties.getLocalAet()
        );
        try (Dcm4cheAssociationContext associationContext = associationFactory.openAssociation(
                callingAet,
                host,
                port,
                calledAet,
                associateRQ -> associateRQ.addPresentationContextFor(UID.StudyRootQueryRetrieveInformationModelMove, UID.ImplicitVRLittleEndian)
        )) {
            Attributes command = awaitFinalCommand(associationContext.association().cmove(
                    UID.StudyRootQueryRetrieveInformationModelMove,
                    Priority.NORMAL,
                    queryAttributes,
                    UID.ImplicitVRLittleEndian,
                    destinationAet
            ));
            int status = command == null ? Status.ProcessingFailure : command.getInt(Tag.Status, Status.ProcessingFailure);
            int movedCount = command == null ? 0 : command.getInt(Tag.NumberOfCompletedSuboperations, 0);
            return response(
                    status == Status.Success ? DicomCommandType.C_MOVE_RESPONSE : DicomCommandType.ERROR,
                    calledAet,
                    callingAet,
                    status,
                    responseAttributes(command, Map.of("movedCount", Integer.toString(movedCount))),
                    java.util.List.of(),
                    commandMessage(command, status)
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to perform remote C-MOVE", exception);
        }
    }

    private Attributes awaitFinalCommand(DimseRSP response) {
        try {
            Attributes command = null;
            while (response.next()) {
                command = response.getCommand();
            }
            return command;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed while waiting for DIMSE response", exception);
        }
    }

    private DicomMessage response(
            DicomCommandType commandType,
            String callingAet,
            String calledAet,
            int status,
            Map<String, String> attributes,
            java.util.List<Map<String, String>> results,
            String message
    ) {
        return new DicomMessage(
                UUID.randomUUID().toString(),
                commandType,
                callingAet,
                calledAet,
                status,
                message,
                attributes,
                null,
                results
        );
    }

    private Map<String, String> responseAttributes(Attributes command, Map<String, String> baseAttributes) {
        java.util.LinkedHashMap<String, String> attributes = new java.util.LinkedHashMap<>(baseAttributes);
        if (command != null) {
            String errorComment = command.getString(Tag.ErrorComment);
            if (errorComment != null && !errorComment.isBlank()) {
                attributes.put("errorComment", errorComment);
            }
        }
        return Map.copyOf(attributes);
    }

    private String commandMessage(Attributes command, int status) {
        if (command != null) {
            String errorComment = command.getString(Tag.ErrorComment);
            if (errorComment != null && !errorComment.isBlank()) {
                return errorComment;
            }
        }
        return statusMessage(status);
    }

    private String statusMessage(int status) {
        return switch (status) {
            case Status.Success -> "Success";
            case Status.MoveDestinationUnknown -> "Move destination unknown";
            case Status.UnableToProcess -> "Unable to process";
            case Status.ProcessingFailure -> "Processing failure";
            default -> "DICOM status: 0x" + Integer.toHexString(status);
        };
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
