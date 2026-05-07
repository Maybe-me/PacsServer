package com.mylife.pacs.infrastructure.persistence.dialect;

abstract class AbstractDatabaseDialectAdapter implements DatabaseDialectAdapter {

    @Override
    public String schedulerLockTryLockSql() {
        return """
                UPDATE sync_scheduler_lock
                   SET locked_until = ?, locked_at = ?, locked_by = ?
                 WHERE lock_name = ?
                   AND (locked_until IS NULL OR locked_until <= ?)
                """;
    }

    @Override
    public String schedulerLockRenewSql() {
        return """
                UPDATE sync_scheduler_lock
                   SET locked_until = ?, locked_at = ?, locked_by = ?
                 WHERE lock_name = ?
                   AND locked_by = ?
                """;
    }

    @Override
    public String schedulerLockUnlockSql() {
        return """
                UPDATE sync_scheduler_lock
                   SET locked_until = ?, locked_at = ?, locked_by = ?
                 WHERE lock_name = ?
                   AND locked_by = ?
                """;
    }
}
