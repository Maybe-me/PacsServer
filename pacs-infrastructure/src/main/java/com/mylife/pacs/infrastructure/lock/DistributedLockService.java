package com.mylife.pacs.infrastructure.lock;

import com.mylife.pacs.common.config.DicomPacsProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DistributedLockService {

    private final Map<String, DistributedLockProvider> providers;
    private final DicomPacsProperties properties;

    public DistributedLockService(List<DistributedLockProvider> providers, DicomPacsProperties properties) {
        this.providers = indexProviders(providers);
        this.properties = properties;
    }

    public boolean tryLock(String key, Duration leaseTime) {
        return resolveProvider().tryLock(key, leaseTime);
    }

    public boolean renew(String key, Duration leaseTime) {
        return resolveProvider().renew(key, leaseTime);
    }

    public void unlock(String key) {
        resolveProvider().unlock(key);
    }

    private DistributedLockProvider resolveProvider() {
        String providerType = normalize(properties.getSync().getLockProvider());
        DistributedLockProvider provider = providers.get(providerType);
        if (provider == null) {
            throw new IllegalStateException("Unsupported lock provider: " + properties.getSync().getLockProvider());
        }
        return provider;
    }

    private Map<String, DistributedLockProvider> indexProviders(List<DistributedLockProvider> providers) {
        LinkedHashMap<String, DistributedLockProvider> indexed = new LinkedHashMap<>();
        for (DistributedLockProvider provider : providers) {
            indexed.put(normalize(provider.providerType()), provider);
        }
        return Map.copyOf(indexed);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "database";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
