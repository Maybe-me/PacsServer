package com.mylife.pacs.infrastructure.lock;

import java.time.Duration;

public interface DistributedLockProvider {

    String providerType();

    boolean tryLock(String key, Duration leaseTime);

    boolean renew(String key, Duration leaseTime);

    void unlock(String key);
}
