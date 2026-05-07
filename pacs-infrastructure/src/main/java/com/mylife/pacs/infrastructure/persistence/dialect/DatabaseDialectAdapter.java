package com.mylife.pacs.infrastructure.persistence.dialect;

public interface DatabaseDialectAdapter {

    String dialectType();

    DialectCapabilities capabilities();

    String applyPagination(String baseSql, long limit, long offset);

    String schedulerLockTryLockSql();

    String schedulerLockRenewSql();

    String schedulerLockUnlockSql();
}
