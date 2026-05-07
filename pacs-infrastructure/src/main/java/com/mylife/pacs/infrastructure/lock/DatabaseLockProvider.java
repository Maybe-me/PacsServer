package com.mylife.pacs.infrastructure.lock;

import com.mylife.pacs.common.config.DicomPacsProperties;
import com.mylife.pacs.infrastructure.persistence.dialect.DatabaseDialectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

@Component
public class DatabaseLockProvider implements DistributedLockProvider {

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseDialectService databaseDialectService;
    private final String ownerId;
    private final long defaultLockAtMostMs;

    @Autowired
    public DatabaseLockProvider(JdbcTemplate jdbcTemplate, DatabaseDialectService databaseDialectService, DicomPacsProperties properties) {
        this(jdbcTemplate, databaseDialectService, resolveOwnerId(), properties.getSync().getLockAtMostMs());
    }

    public DatabaseLockProvider(JdbcTemplate jdbcTemplate, DatabaseDialectService databaseDialectService, String ownerId, long lockAtMostMs) {
        this.jdbcTemplate = jdbcTemplate;
        this.databaseDialectService = databaseDialectService;
        this.ownerId = ownerId;
        this.defaultLockAtMostMs = Math.max(1L, lockAtMostMs);
    }

    @Override
    public String providerType() {
        return "database";
    }

    @Override
    public boolean tryLock(String lockName, Duration leaseTime) {
        Instant now = Instant.now();
        Instant lockedUntil = now.plusMillis(resolveLeaseTimeMs(leaseTime));
        int updated = jdbcTemplate.update(
                databaseDialectService.current().schedulerLockTryLockSql(),
                Timestamp.from(lockedUntil),
                Timestamp.from(now),
                ownerId,
                lockName,
                Timestamp.from(now)
        );
        return updated > 0;
    }

    @Override
    public boolean renew(String lockName, Duration leaseTime) {
        Instant now = Instant.now();
        Instant lockedUntil = now.plusMillis(resolveLeaseTimeMs(leaseTime));
        int updated = jdbcTemplate.update(
                databaseDialectService.current().schedulerLockRenewSql(),
                Timestamp.from(lockedUntil),
                Timestamp.from(now),
                ownerId,
                lockName,
                ownerId
        );
        return updated > 0;
    }

    @Override
    public void unlock(String lockName) {
        Instant now = Instant.now();
        Instant expiredAt = now.minusMillis(1L);
        jdbcTemplate.update(
                databaseDialectService.current().schedulerLockUnlockSql(),
                Timestamp.from(expiredAt),
                Timestamp.from(now),
                ownerId,
                lockName,
                ownerId
        );
    }

    private long resolveLeaseTimeMs(Duration leaseTime) {
        if (leaseTime == null || leaseTime.isZero() || leaseTime.isNegative()) {
            return defaultLockAtMostMs;
        }
        return Math.max(1L, leaseTime.toMillis());
    }

    private static String resolveOwnerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + ":" + ManagementFactory.getRuntimeMXBean().getName();
        } catch (UnknownHostException exception) {
            return ManagementFactory.getRuntimeMXBean().getName();
        }
    }
}
