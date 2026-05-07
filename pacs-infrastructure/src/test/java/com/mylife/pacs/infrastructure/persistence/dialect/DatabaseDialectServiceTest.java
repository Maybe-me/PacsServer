package com.mylife.pacs.infrastructure.persistence.dialect;

import com.mylife.pacs.common.config.DicomPacsProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseDialectServiceTest {

    private final List<DatabaseDialectAdapter> adapters = List.of(
            new H2DialectAdapter(),
            new PostgresDialectAdapter(),
            new MySqlDialectAdapter(),
            new OracleLikeDialectAdapter()
    );

    @Test
    void shouldResolvePostgresAliasAndExposeCapabilities() {
        DicomPacsProperties properties = new DicomPacsProperties();
        properties.setDbType("pg");

        DatabaseDialectService service = new DatabaseDialectService(adapters, properties);
        DatabaseDialectAdapter adapter = service.current();

        assertEquals("postgresql", adapter.dialectType());
        assertTrue(adapter.capabilities().supportsLimitOffset());
        assertTrue(adapter.capabilities().supportsMergeUpsert());
        assertTrue(adapter.capabilities().supportsJsonColumns());
    }

    @Test
    void shouldGenerateOracleLikePagination() {
        DicomPacsProperties properties = new DicomPacsProperties();
        properties.setDbType("dameng");

        DatabaseDialectService service = new DatabaseDialectService(adapters, properties);
        String sql = service.current().applyPagination("SELECT * FROM pacs_instance ORDER BY id", 50, 100);

        assertEquals("SELECT * FROM pacs_instance ORDER BY id OFFSET 100 ROWS FETCH NEXT 50 ROWS ONLY", sql);
        assertTrue(service.current().capabilities().supportsOffsetFetch());
        assertFalse(service.current().capabilities().supportsLimitOffset());
    }

    @Test
    void shouldGenerateMysqlPagination() {
        DicomPacsProperties properties = new DicomPacsProperties();
        properties.setDbType("mysql");

        DatabaseDialectService service = new DatabaseDialectService(adapters, properties);
        String sql = service.current().applyPagination("SELECT * FROM pacs_instance ORDER BY id", 20, 40);

        assertEquals("SELECT * FROM pacs_instance ORDER BY id LIMIT 20 OFFSET 40", sql);
        assertFalse(service.current().capabilities().supportsMergeUpsert());
    }
}
