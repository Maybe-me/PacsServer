package com.mylife.pacs.infrastructure.netty.message;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DicomMessages {

    private DicomMessages() {
    }

    public static DicomMessage associateRequest(String callingAet, String calledAet) {
        return new DicomMessage(
                UUID.randomUUID().toString(),
                DicomCommandType.ASSOCIATE_REQUEST,
                callingAet,
                calledAet,
                0,
                "Association request",
                Map.of(),
                null,
                List.of()
        );
    }

    public static DicomMessage associateAccept(DicomMessage request) {
        return new DicomMessage(
                request.correlationId(),
                DicomCommandType.ASSOCIATE_ACCEPT,
                request.calledAet(),
                request.callingAet(),
                0,
                "Association accepted",
                Map.of(),
                null,
                List.of()
        );
    }

    public static DicomMessage associateReject(DicomMessage request, int status, String message) {
        return new DicomMessage(
                request.correlationId(),
                DicomCommandType.ASSOCIATE_REJECT,
                request.calledAet(),
                request.callingAet(),
                status,
                message,
                Map.of(),
                null,
                List.of()
        );
    }

    public static DicomMessage cEchoRequest(String callingAet, String calledAet) {
        return new DicomMessage(
                UUID.randomUUID().toString(),
                DicomCommandType.C_ECHO_REQUEST,
                callingAet,
                calledAet,
                0,
                "C-ECHO request",
                Map.of(),
                null,
                List.of()
        );
    }

    public static DicomMessage cEchoResponse(DicomMessage request) {
        return new DicomMessage(
                request.correlationId(),
                DicomCommandType.C_ECHO_RESPONSE,
                request.calledAet(),
                request.callingAet(),
                0,
                "Success",
                Map.of(),
                null,
                List.of()
        );
    }

    public static DicomMessage cStoreRequest(
            String callingAet,
            String calledAet,
            Map<String, String> attributes,
            String payloadBase64
    ) {
        return new DicomMessage(
                UUID.randomUUID().toString(),
                DicomCommandType.C_STORE_REQUEST,
                callingAet,
                calledAet,
                0,
                "C-STORE request",
                attributes,
                payloadBase64,
                List.of()
        );
    }

    public static DicomMessage cStoreResponse(DicomMessage request, String message) {
        return new DicomMessage(
                request.correlationId(),
                DicomCommandType.C_STORE_RESPONSE,
                request.calledAet(),
                request.callingAet(),
                0,
                message,
                Map.of(),
                null,
                List.of()
        );
    }

    public static DicomMessage cFindRequest(String callingAet, String calledAet, Map<String, String> attributes) {
        return new DicomMessage(
                UUID.randomUUID().toString(),
                DicomCommandType.C_FIND_REQUEST,
                callingAet,
                calledAet,
                0,
                "C-FIND request",
                attributes,
                null,
                List.of()
        );
    }

    public static DicomMessage cFindResponse(DicomMessage request, List<Map<String, String>> results) {
        return new DicomMessage(
                request.correlationId(),
                DicomCommandType.C_FIND_RESPONSE,
                request.calledAet(),
                request.callingAet(),
                0,
                "C-FIND success",
                Map.of(),
                null,
                results
        );
    }

    public static DicomMessage cMoveRequest(String callingAet, String calledAet, Map<String, String> attributes) {
        return new DicomMessage(
                UUID.randomUUID().toString(),
                DicomCommandType.C_MOVE_REQUEST,
                callingAet,
                calledAet,
                0,
                "C-MOVE request",
                attributes,
                null,
                List.of()
        );
    }

    public static DicomMessage cMoveResponse(DicomMessage request, int movedCount) {
        return new DicomMessage(
                request.correlationId(),
                DicomCommandType.C_MOVE_RESPONSE,
                request.calledAet(),
                request.callingAet(),
                0,
                "C-MOVE success",
                Map.of("movedCount", Integer.toString(movedCount)),
                null,
                List.of()
        );
    }

    public static DicomMessage error(DicomMessage request, int status, String message) {
        return new DicomMessage(
                request == null ? UUID.randomUUID().toString() : request.correlationId(),
                DicomCommandType.ERROR,
                request == null ? null : request.calledAet(),
                request == null ? null : request.callingAet(),
                status,
                message,
                Map.of(),
                null,
                List.of()
        );
    }
}
