package com.mylife.pacs.infrastructure.persistence.dialect;

import com.mylife.pacs.common.config.DicomPacsProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DatabaseDialectService {

    private final Map<String, DatabaseDialectAdapter> adapters;
    private final DicomPacsProperties properties;

    public DatabaseDialectService(List<DatabaseDialectAdapter> adapters, DicomPacsProperties properties) {
        this.adapters = indexAdapters(adapters);
        this.properties = properties;
    }

    public DatabaseDialectAdapter current() {
        String dialectKey = normalize(properties.getDbType());
        if ("postgres".equals(dialectKey) || "pg".equals(dialectKey)) {
            dialectKey = "postgresql";
        } else if ("dm".equals(dialectKey) || "dameng".equals(dialectKey) || "oceanbase".equals(dialectKey)) {
            dialectKey = "oracle-like";
        }

        DatabaseDialectAdapter adapter = adapters.get(dialectKey);
        if (adapter == null) {
            throw new IllegalStateException("Unsupported database dialect: " + properties.getDbType());
        }
        return adapter;
    }

    private Map<String, DatabaseDialectAdapter> indexAdapters(List<DatabaseDialectAdapter> adapters) {
        LinkedHashMap<String, DatabaseDialectAdapter> indexed = new LinkedHashMap<>();
        for (DatabaseDialectAdapter adapter : adapters) {
            indexed.put(normalize(adapter.dialectType()), adapter);
        }
        return Map.copyOf(indexed);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "h2";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
