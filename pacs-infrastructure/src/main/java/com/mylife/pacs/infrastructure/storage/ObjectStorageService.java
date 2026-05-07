package com.mylife.pacs.infrastructure.storage;

import com.mylife.pacs.common.config.DicomPacsProperties;
import com.mylife.pacs.domain.model.PacsInstance;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ObjectStorageService {

    private final Map<String, ObjectStorageProvider> providers;
    private final DicomPacsProperties properties;

    public ObjectStorageService(List<ObjectStorageProvider> providers, DicomPacsProperties properties) {
        this.providers = indexProviders(providers);
        this.properties = properties;
    }

    public StoredFile store(Map<String, String> attributes, byte[] payload) {
        return resolveProvider(properties.getStorageProvider()).store(attributes, payload);
    }

    public byte[] read(PacsInstance instance) {
        return resolveProvider(resolveStorageType(instance.storageType())).read(resolveStorageKey(instance));
    }

    public boolean exists(PacsInstance instance) {
        return resolveProvider(resolveStorageType(instance.storageType())).exists(resolveStorageKey(instance));
    }

    public String resolveAccessPath(PacsInstance instance) {
        return resolveProvider(resolveStorageType(instance.storageType())).resolveAccessPath(resolveStorageKey(instance));
    }

    private Map<String, ObjectStorageProvider> indexProviders(List<ObjectStorageProvider> providers) {
        LinkedHashMap<String, ObjectStorageProvider> indexed = new LinkedHashMap<>();
        for (ObjectStorageProvider provider : providers) {
            indexed.put(normalize(provider.storageType()), provider);
        }
        return Map.copyOf(indexed);
    }

    private ObjectStorageProvider resolveProvider(String storageType) {
        String normalizedType = normalize(storageType);
        ObjectStorageProvider provider = providers.get(normalizedType);
        if (provider == null) {
            throw new IllegalStateException("Unsupported storage provider: " + storageType);
        }
        return provider;
    }

    private String resolveStorageType(String storageType) {
        if (storageType != null && !storageType.isBlank()) {
            return storageType;
        }
        return properties.getStorageProvider();
    }

    private String resolveStorageKey(PacsInstance instance) {
        String storageKey = instance.storageKey();
        if (storageKey != null && !storageKey.isBlank()) {
            return storageKey;
        }
        if (instance.filePath() != null && !instance.filePath().isBlank()) {
            return instance.filePath();
        }
        throw new IllegalStateException("Missing storage key for instance " + instance.sopInstanceUid());
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "local";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
