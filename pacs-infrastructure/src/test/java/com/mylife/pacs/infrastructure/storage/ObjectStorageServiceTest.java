package com.mylife.pacs.infrastructure.storage;

import com.mylife.pacs.common.config.DicomPacsProperties;
import com.mylife.pacs.domain.model.PacsInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ObjectStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReadUsingInstanceStorageTypeAndKey() {
        FakeProvider localProvider = new FakeProvider("local");
        FakeProvider s3Provider = new FakeProvider("s3");
        s3Provider.put("study\\series\\instance.dcm", new byte[]{1, 2, 3});

        DicomPacsProperties properties = new DicomPacsProperties();
        properties.setStorageProvider("local");

        ObjectStorageService service = new ObjectStorageService(List.of(localProvider, s3Provider), properties);

        PacsInstance instance = new PacsInstance(
                1L,
                2L,
                "1.2.3",
                "1.2.840.10008.5.1.4.1.1.2",
                "1.2.840.10008.1.2.1",
                1,
                "legacy\\path.dcm",
                3L,
                "abc",
                "s3",
                "bucket-a",
                "study\\series\\instance.dcm",
                Map.of(),
                Instant.now(),
                Instant.now()
        );

        assertArrayEquals(new byte[]{1, 2, 3}, service.read(instance));
    }

    @Test
    void shouldFallbackToLegacyFilePathWhenStorageKeyMissing() {
        FakeProvider localProvider = new FakeProvider("local");
        localProvider.put("legacy\\path.dcm", new byte[]{9, 8, 7});

        DicomPacsProperties properties = new DicomPacsProperties();
        properties.setStorageProvider("local");

        ObjectStorageService service = new ObjectStorageService(List.of(localProvider), properties);

        PacsInstance instance = new PacsInstance(
                1L,
                2L,
                "1.2.3",
                "1.2.840.10008.5.1.4.1.1.2",
                "1.2.840.10008.1.2.1",
                1,
                "legacy\\path.dcm",
                3L,
                "abc",
                null,
                null,
                null,
                Map.of(),
                Instant.now(),
                Instant.now()
        );

        assertArrayEquals(new byte[]{9, 8, 7}, service.read(instance));
    }

    @Test
    void shouldStoreUsingConfiguredDefaultProvider() {
        FakeProvider localProvider = new FakeProvider("local");
        FakeProvider s3Provider = new FakeProvider("s3");

        DicomPacsProperties properties = new DicomPacsProperties();
        properties.setStorageProvider("s3");

        ObjectStorageService service = new ObjectStorageService(List.of(localProvider, s3Provider), properties);

        StoredFile stored = service.store(Map.of("00080018", "1.2.3"), new byte[]{5, 5});

        assertEquals("s3", stored.storageType());
        assertEquals("stored-by-s3.dcm", stored.storageKey());
    }

    @Test
    void shouldStoreAndReadUsingS3Provider() {
        DicomPacsProperties properties = new DicomPacsProperties();
        properties.getS3().setBucket("test-s3-bucket");
        properties.getS3().setEndpoint("http://localhost:9000"); // Will trigger mock fallback if offline

        S3CompatibleStorageProvider s3Provider = new S3CompatibleStorageProvider(properties);
        byte[] payload = new byte[]{10, 20, 30};

        StoredFile stored = s3Provider.store(Map.of("patientId", "PAT-S3", "studyInstanceUid", "STUDY-S3", "seriesInstanceUid", "SERIES-S3", "sopInstanceUid", "SOP-S3"), payload);

        assertEquals("s3", stored.storageType());
        assertEquals("test-s3-bucket", stored.storageBucket());
        assertTrue(s3Provider.exists(stored.storageKey()));

        byte[] retrieved = s3Provider.read(stored.storageKey());
        assertArrayEquals(payload, retrieved);

        s3Provider.delete(stored.storageKey());
        assertFalse(s3Provider.exists(stored.storageKey()));
    }

    @Test
    void shouldStoreAndReadUsingFastDfsProvider() {
        DicomPacsProperties properties = new DicomPacsProperties();
        properties.getFastdfs().setGroupName("test-group");
        properties.getFastdfs().setTrackerServer("192.168.1.100:22122");

        FastDfsStorageProvider fastDfsProvider = new FastDfsStorageProvider(properties);
        byte[] payload = new byte[]{40, 50, 60, 70};

        StoredFile stored = fastDfsProvider.store(Map.of(), payload);

        assertEquals("fastdfs", stored.storageType());
        assertEquals("test-group", stored.storageBucket());
        assertTrue(stored.storageKey().startsWith("test-group/M00/00/00/"));
        assertTrue(fastDfsProvider.exists(stored.storageKey()));

        byte[] retrieved = fastDfsProvider.read(stored.storageKey());
        assertArrayEquals(payload, retrieved);

        fastDfsProvider.delete(stored.storageKey());
        assertFalse(fastDfsProvider.exists(stored.storageKey()));
    }

    @Test
    void shouldSupportDynamicProviderHotSwapping() throws IOException {
        DicomPacsProperties properties = new DicomPacsProperties();
        properties.setStorageDir(tempDir.toString());
        properties.setFilePathPattern("{patientId}/{studyInstanceUid}/{seriesInstanceUid}/{sopInstanceUid}.dcm");

        LocalFileStorage localStorage = new LocalFileStorage(properties);
        S3CompatibleStorageProvider s3Provider = new S3CompatibleStorageProvider(properties);
        FastDfsStorageProvider fastDfsProvider = new FastDfsStorageProvider(properties);

        ObjectStorageService service = new ObjectStorageService(
                List.of(localStorage, s3Provider, fastDfsProvider),
                properties
        );

        byte[] payload = new byte[]{8, 8, 8};
        Map<String, String> attrs = Map.of(
                "patientId", "PAT-SWAP",
                "studyInstanceUid", "STUDY-SWAP",
                "seriesInstanceUid", "SERIES-SWAP",
                "sopInstanceUid", "SOP-SWAP"
        );

        // 1. Swap to Local
        properties.setStorageProvider("local");
        StoredFile storedLocal = service.store(attrs, payload);
        assertEquals("local", storedLocal.storageType());
        assertTrue(Files.exists(tempDir.resolve(storedLocal.relativePath())));

        // 2. Swap to S3
        properties.setStorageProvider("s3");
        StoredFile storedS3 = service.store(attrs, payload);
        assertEquals("s3", storedS3.storageType());

        // 3. Swap to FastDFS
        properties.setStorageProvider("fastdfs");
        StoredFile storedFast = service.store(attrs, payload);
        assertEquals("fastdfs", storedFast.storageType());
    }

    private static final class FakeProvider implements ObjectStorageProvider {
        private final String type;
        private final java.util.Map<String, byte[]> objects = new java.util.LinkedHashMap<>();

        private FakeProvider(String type) {
            this.type = type;
        }

        @Override
        public String storageType() {
            return type;
        }

        @Override
        public StoredFile store(Map<String, String> attributes, byte[] payload) {
            String key = "stored-by-" + type + ".dcm";
            objects.put(key, payload);
            return new StoredFile(Path.of(key), key, type, null, key, payload.length, "md5");
        }

        @Override
        public byte[] read(String storageKey) {
            return objects.get(storageKey);
        }

        @Override
        public boolean exists(String storageKey) {
            return objects.containsKey(storageKey);
        }

        @Override
        public void delete(String storageKey) {
            objects.remove(storageKey);
        }

        @Override
        public String resolveAccessPath(String storageKey) {
            return storageKey;
        }

        private void put(String key, byte[] payload) {
            objects.put(key, payload);
        }
    }
}
