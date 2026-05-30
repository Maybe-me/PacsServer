package com.mylife.pacs.infrastructure.storage;

import com.mylife.pacs.common.config.DicomPacsProperties;
import com.mylife.pacs.common.constant.DicomTagConst;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class S3CompatibleStorageProvider implements ObjectStorageProvider {

    private final DicomPacsProperties properties;
    private final ConcurrentHashMap<String, byte[]> mockStorage = new ConcurrentHashMap<>();
    private S3Client s3Client;
    private boolean mockMode = false;

    public S3CompatibleStorageProvider(DicomPacsProperties properties) {
        this.properties = properties;
        initializeClient();
    }

    private synchronized void initializeClient() {
        if (s3Client != null || mockMode) {
            return;
        }
        try {
            DicomPacsProperties.S3 s3Config = properties.getS3();
            software.amazon.awssdk.auth.credentials.AwsCredentialsProvider credentialsProvider;
            software.amazon.awssdk.core.client.config.ClientOverrideConfiguration overrideConfig = null;
            if (s3Config.getAccessKey() == null || s3Config.getAccessKey().isBlank()
                    || "anonymous".equalsIgnoreCase(s3Config.getAccessKey())
                    || "none".equalsIgnoreCase(s3Config.getAccessKey())) {
                credentialsProvider = software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider.create();
                overrideConfig = software.amazon.awssdk.core.client.config.ClientOverrideConfiguration.builder()
                        .putAdvancedOption(
                                software.amazon.awssdk.core.client.config.SdkAdvancedClientOption.SIGNER,
                                new software.amazon.awssdk.core.signer.NoOpSigner())
                        .build();
            } else {
                credentialsProvider = StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3Config.getAccessKey(), s3Config.getSecretKey()));
            }

            software.amazon.awssdk.services.s3.S3ClientBuilder builder = S3Client.builder()
                    .endpointOverride(URI.create(s3Config.getEndpoint()))
                    .credentialsProvider(credentialsProvider)
                    .region(Region.of(s3Config.getRegion()))
                    .serviceConfiguration(b -> b.pathStyleAccessEnabled(s3Config.isPathStyleAccess()));

            if (overrideConfig != null) {
                builder.overrideConfiguration(overrideConfig);
            }

            this.s3Client = builder.build();
            
            // Try to check/create bucket to ensure seamless out-of-the-box experience
            String bucket = s3Config.getBucket();
            try {
                try {
                    s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
                } catch (NoSuchBucketException | NoSuchKeyException e) {
                    s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                    System.out.println("[S3 Storage] Created missing bucket: " + bucket);
                }
            } catch (Exception bucketEx) {
                System.err.println("[S3 Storage] Failed to check or create bucket (continuing anyway): " + bucketEx.getMessage());
            }
        } catch (Exception exception) {
            System.err.println("[S3 Storage] Failed to initialize official S3 Client, falling back to mock mode. Reason: " + exception.getMessage());
            this.mockMode = true;
        }
    }

    @Override
    public String storageType() {
        return "s3";
    }

    @Override
    public StoredFile store(Map<String, String> attributes, byte[] payload) {
        String relative = resolveRelativePath(attributes);
        String storageKey = relative.replace('\\', '/');
        String bucket = properties.getS3().getBucket();

        initializeClient();

        if (!mockMode) {
            try {
                s3Client.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(storageKey)
                        .build(), RequestBody.fromBytes(payload));
            } catch (Exception exception) {
                System.err.println("[S3 Storage] S3 putObject failed. Key: " + storageKey + ", Error: " + exception.getMessage());
                throw new IllegalStateException("Failed to write to S3 storage", exception);
            }
        } else {
            mockStorage.put(bucket + "/" + storageKey, payload);
        }

        return new StoredFile(null, storageKey, storageType(), bucket, storageKey, payload.length, md5(payload));
    }

    @Override
    public byte[] read(String storageKey) {
        String bucket = properties.getS3().getBucket();
        String normalizedKey = storageKey.replace('\\', '/');

        initializeClient();

        if (!mockMode) {
            try {
                ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(normalizedKey)
                        .build());
                return objectBytes.asByteArray();
            } catch (NoSuchKeyException exception) {
                System.err.println("[S3 Storage] S3 Key not found (404): " + normalizedKey);
                throw exception;
            } catch (Exception exception) {
                System.err.println("[S3 Storage] S3 getObject failed. Key: " + normalizedKey + ", Error: " + exception.getMessage());
                throw new IllegalStateException("Failed to read S3 storage object", exception);
            }
        }

        byte[] mockData = mockStorage.get(bucket + "/" + normalizedKey);
        if (mockData != null) {
            return mockData;
        }

        throw new IllegalStateException("Failed to read stored S3 object: " + normalizedKey);
    }

    @Override
    public boolean exists(String storageKey) {
        String bucket = properties.getS3().getBucket();
        String normalizedKey = storageKey.replace('\\', '/');

        initializeClient();

        if (!mockMode) {
            try {
                s3Client.headObject(HeadObjectRequest.builder()
                        .bucket(bucket)
                        .key(normalizedKey)
                        .build());
                return true;
            } catch (NoSuchKeyException exception) {
                return false;
            } catch (Exception exception) {
                System.err.println("[S3 Storage] S3 headObject failed. Key: " + normalizedKey + ", Error: " + exception.getMessage());
                return false;
            }
        }

        return mockStorage.containsKey(bucket + "/" + normalizedKey);
    }

    @Override
    public void delete(String storageKey) {
        String bucket = properties.getS3().getBucket();
        String normalizedKey = storageKey.replace('\\', '/');

        initializeClient();

        if (!mockMode) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(normalizedKey)
                        .build());
                return;
            } catch (NoSuchKeyException exception) {
                System.err.println("[S3 Storage] S3 Key to delete not found: " + normalizedKey);
            } catch (Exception exception) {
                System.err.println("[S3 Storage] S3 deleteObject failed. Key: " + normalizedKey + ", Error: " + exception.getMessage());
                throw new IllegalStateException("Failed to delete S3 storage object", exception);
            }
        } else {
            mockStorage.remove(bucket + "/" + normalizedKey);
        }
    }

    @Override
    public String resolveAccessPath(String storageKey) {
        String bucket = properties.getS3().getBucket();
        String normalizedKey = storageKey.replace('\\', '/');
        return properties.getS3().getEndpoint() + "/" + bucket + "/" + normalizedKey;
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
