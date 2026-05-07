package com.mylife.pacs.infrastructure.storage;

import com.mylife.pacs.common.config.DicomPacsProperties;
import com.mylife.pacs.common.constant.DicomTagConst;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

@Component
public class LocalFileStorage implements ObjectStorageProvider {

    private final DicomPacsProperties properties;

    public LocalFileStorage(DicomPacsProperties properties) {
        this.properties = properties;
    }

    @Override
    public String storageType() {
        return "local";
    }

    @Override
    public StoredFile store(Map<String, String> attributes, byte[] payload) {
        try {
            Path root = Path.of(properties.getStorageDir());
            Files.createDirectories(root);

            String relative = resolveRelativePath(attributes);
            Path absolute = root.resolve(relative);
            if (absolute.getParent() != null) {
                Files.createDirectories(absolute.getParent());
            }
            Files.write(absolute, payload, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            String storageKey = relative.replace('/', '\\');
            return new StoredFile(absolute, storageKey, storageType(), null, storageKey, payload.length, md5(payload));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store incoming file", exception);
        }
    }

    @Override
    public byte[] read(String storageKey) {
        try {
            return Files.readAllBytes(Path.of(properties.getStorageDir()).resolve(storageKey));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read stored file: " + storageKey, exception);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.exists(Path.of(properties.getStorageDir()).resolve(storageKey));
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(Path.of(properties.getStorageDir()).resolve(storageKey));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete stored file: " + storageKey, exception);
        }
    }

    @Override
    public String resolveAccessPath(String storageKey) {
        return Path.of(properties.getStorageDir()).resolve(storageKey).toString();
    }

    private String resolveRelativePath(Map<String, String> attributes) {
        String pattern = properties.getFilePathPattern();
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String token = "{" + entry.getKey() + "}";
            if (pattern.contains(token)) {
                pattern = pattern.replace(token, sanitize(entry.getValue()));
            }
        }
        pattern = pattern.replace("{" + DicomTagConst.PATIENT_ID + "}", sanitize(find(attributes, "patientId", DicomTagConst.PATIENT_ID, "UNKNOWN_PATIENT")));
        pattern = pattern.replace("{" + DicomTagConst.STUDY_INSTANCE_UID + "}", sanitize(find(attributes, "studyInstanceUid", DicomTagConst.STUDY_INSTANCE_UID, "UNKNOWN_STUDY")));
        pattern = pattern.replace("{" + DicomTagConst.SERIES_INSTANCE_UID + "}", sanitize(find(attributes, "seriesInstanceUid", DicomTagConst.SERIES_INSTANCE_UID, "UNKNOWN_SERIES")));
        pattern = pattern.replace("{" + DicomTagConst.SOP_INSTANCE_UID + "}", sanitize(find(attributes, "sopInstanceUid", DicomTagConst.SOP_INSTANCE_UID, "UNKNOWN_INSTANCE")));
        return pattern.replace('\\', '/');
    }

    private String find(Map<String, String> attributes, String alias, String tag, String fallback) {
        String value = attributes.get(alias);
        if (value == null || value.isBlank()) {
            value = attributes.get(tag);
        }
        return value == null || value.isBlank() ? fallback : value;
    }

    private String sanitize(String value) {
        return value == null || value.isBlank()
                ? "unknown"
                : value.replaceAll("[\\\\/:*?\"<>|]", "_");
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
