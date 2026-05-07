package com.mylife.pacs.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mylife.pacs.common.exception.PacsException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class JsonUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private JsonUtil() {
    }

    public static String writeMap(Map<String, String> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value == null ? Collections.emptyMap() : value);
        } catch (JsonProcessingException exception) {
            throw new PacsException("Failed to serialize extra tags");
        }
    }

    public static Map<String, String> readMap(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(value, STRING_MAP);
        } catch (JsonProcessingException exception) {
            throw new PacsException("Failed to deserialize extra tags");
        }
    }

    public static String writeList(List<String> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value == null ? Collections.emptyList() : value);
        } catch (JsonProcessingException exception) {
            throw new PacsException("Failed to serialize string list");
        }
    }

    public static List<String> readList(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(value, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new PacsException("Failed to deserialize string list");
        }
    }
}
