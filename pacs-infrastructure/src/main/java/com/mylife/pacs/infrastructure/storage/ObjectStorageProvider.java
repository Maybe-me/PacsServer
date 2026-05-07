package com.mylife.pacs.infrastructure.storage;

import java.util.Map;

public interface ObjectStorageProvider {

    String storageType();

    StoredFile store(Map<String, String> attributes, byte[] payload);

    byte[] read(String storageKey);

    boolean exists(String storageKey);

    void delete(String storageKey);

    String resolveAccessPath(String storageKey);
}
