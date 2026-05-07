package com.mylife.pacs.infrastructure.persistence.dialect;

public record DialectCapabilities(
        boolean supportsLimitOffset,
        boolean supportsOffsetFetch,
        boolean supportsMergeUpsert,
        boolean supportsJsonColumns
) {
}
