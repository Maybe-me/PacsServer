package com.mylife.pacs.infrastructure.persistence.dialect;

import org.springframework.stereotype.Component;

@Component
public class MySqlDialectAdapter extends AbstractDatabaseDialectAdapter {

    private static final DialectCapabilities CAPABILITIES = new DialectCapabilities(true, false, false, true);

    @Override
    public String dialectType() {
        return "mysql";
    }

    @Override
    public DialectCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public String applyPagination(String baseSql, long limit, long offset) {
        return baseSql + " LIMIT " + limit + " OFFSET " + offset;
    }
}
