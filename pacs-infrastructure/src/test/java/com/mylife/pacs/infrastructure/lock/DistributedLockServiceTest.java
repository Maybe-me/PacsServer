package com.mylife.pacs.infrastructure.lock;

import com.mylife.pacs.common.config.DicomPacsProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributedLockServiceTest {

    @Test
    void shouldDelegateToConfiguredProvider() {
        FakeLockProvider databaseProvider = new FakeLockProvider("database");
        FakeLockProvider redisProvider = new FakeLockProvider("redisson");

        DicomPacsProperties properties = new DicomPacsProperties();
        properties.getSync().setLockProvider("redisson");

        DistributedLockService service = new DistributedLockService(List.of(databaseProvider, redisProvider), properties);

        assertTrue(service.tryLock("sync-pull-dispatcher", Duration.ofSeconds(30)));
        assertTrue(service.renew("sync-pull-dispatcher", Duration.ofSeconds(30)));
        service.unlock("sync-pull-dispatcher");

        assertFalse(databaseProvider.tryCalled);
        assertTrue(redisProvider.tryCalled);
        assertTrue(redisProvider.renewCalled);
        assertTrue(redisProvider.unlockCalled);
    }

    private static final class FakeLockProvider implements DistributedLockProvider {
        private final String providerType;
        private boolean tryCalled;
        private boolean renewCalled;
        private boolean unlockCalled;

        private FakeLockProvider(String providerType) {
            this.providerType = providerType;
        }

        @Override
        public String providerType() {
            return providerType;
        }

        @Override
        public boolean tryLock(String key, Duration leaseTime) {
            this.tryCalled = true;
            return true;
        }

        @Override
        public boolean renew(String key, Duration leaseTime) {
            this.renewCalled = true;
            return true;
        }

        @Override
        public void unlock(String key) {
            this.unlockCalled = true;
        }
    }
}
