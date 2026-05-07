package com.mylife.pacs.infrastructure.storage;

import com.mylife.pacs.common.config.DicomPacsProperties;
import com.mylife.pacs.domain.model.PacsInstance;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ObjectStorageServiceTest {

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
