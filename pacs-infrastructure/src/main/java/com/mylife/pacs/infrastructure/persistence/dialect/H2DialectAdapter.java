package com.mylife.pacs.infrastructure.persistence.dialect;

import org.springframework.stereotype.Component;

@Component
public class H2DialectAdapter extends AbstractDatabaseDialectAdapter {

    private static final DialectCapabilities CAPABILITIES = new DialectCapabilities(true, false, true, false);

    @Override
    public String dialectType() {
        return "h2";
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
