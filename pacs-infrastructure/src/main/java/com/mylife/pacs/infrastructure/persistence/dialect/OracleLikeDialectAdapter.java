package com.mylife.pacs.infrastructure.persistence.dialect;

import org.springframework.stereotype.Component;

@Component
public class OracleLikeDialectAdapter extends AbstractDatabaseDialectAdapter {

    private static final DialectCapabilities CAPABILITIES = new DialectCapabilities(false, true, true, false);

    @Override
    public String dialectType() {
        return "oracle-like";
    }

    @Override
    public DialectCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public String applyPagination(String baseSql, long limit, long offset) {
        return baseSql + " OFFSET " + offset + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
    }
}
