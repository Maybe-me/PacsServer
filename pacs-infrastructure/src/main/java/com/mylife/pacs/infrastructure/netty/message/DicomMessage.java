package com.mylife.pacs.infrastructure.netty.message;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record DicomMessage(
        String correlationId,
        DicomCommandType commandType,
        String callingAet,
        String calledAet,
        int status,
        String message,
        Map<String, String> attributes,
        String payloadBase64,
        List<Map<String, String>> results
) {
    public DicomMessage {
        attributes = attributes == null ? Collections.emptyMap() : Map.copyOf(attributes);
        results = results == null ? List.of() : List.copyOf(results);
    }
}
