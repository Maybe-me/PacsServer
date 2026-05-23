package com.mylife.pacs.infrastructure.storage;

import com.mylife.pacs.common.config.DicomPacsProperties;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FastDfsStorageProvider implements ObjectStorageProvider {

    private final DicomPacsProperties properties;
    private final ConcurrentHashMap<String, byte[]> mockStorage = new ConcurrentHashMap<>();

    public FastDfsStorageProvider(DicomPacsProperties properties) {
        this.properties = properties;
    }

    @Override
    public String storageType() {
        return "fastdfs";
    }

    @Override
    public StoredFile store(Map<String, String> attributes, byte[] payload) {
        String md5 = md5(payload);
        String group = properties.getFastdfs().getGroupName();
        // FastDFS typically generates a path resembling: group1/M00/00/00/wKhQA1...dcm
        String storageKey = group + "/M00/00/00/" + md5 + ".dcm";

        // Store in simulated FastDFS storage
        mockStorage.put(storageKey, payload);

        return new StoredFile(null, storageKey, storageType(), group, storageKey, payload.length, md5);
    }

    @Override
    public byte[] read(String storageKey) {
        String normalizedKey = storageKey.replace('\\', '/');
        byte[] payload = mockStorage.get(normalizedKey);
        if (payload == null) {
            throw new IllegalStateException("FastDFS object not found for key: " + normalizedKey);
        }
        return payload;
    }

    @Override
    public boolean exists(String storageKey) {
        String normalizedKey = storageKey.replace('\\', '/');
        return mockStorage.containsKey(normalizedKey);
    }

    @Override
    public void delete(String storageKey) {
        String normalizedKey = storageKey.replace('\\', '/');
        mockStorage.remove(normalizedKey);
    }

    @Override
    public String resolveAccessPath(String storageKey) {
        String normalizedKey = storageKey.replace('\\', '/');
        return "http://" + properties.getFastdfs().getTrackerServer() + "/" + normalizedKey;
    }

    private String md5(byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(payload));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 algorithm unavailable", exception);
        }
    }
}
