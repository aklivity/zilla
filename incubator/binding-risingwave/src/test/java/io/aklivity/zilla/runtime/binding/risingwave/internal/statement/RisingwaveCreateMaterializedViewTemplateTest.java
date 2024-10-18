package io.aklivity.zilla.runtime.binding.risingwave.internal.statement;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Ignore;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.pgsql.parser.module.TableInfo;
import io.aklivity.zilla.runtime.binding.pgsql.parser.module.ViewInfo;

public class RisingwaveCreateMaterializedViewTemplateTest
{
    private final RisingwaveCreateMaterializedViewTemplate template = new RisingwaveCreateMaterializedViewTemplate();

    @Test
    public void shouldGenerateMaterializedViewWithValidViewInfo()
    {
        ViewInfo viewInfo = new ViewInfo("test_view", "SELECT * FROM test_table");
        String expectedSQL = """
            CREATE MATERIALIZED VIEW IF NOT EXISTS test_view AS SELECT * FROM test_table;\u0000""";

        String actualSQL = template.generate(viewInfo);

        assertEquals(expectedSQL, actualSQL);
    }

    @Ignore("TODO")
    @Test
    public void shouldGenerateMaterializedViewWithValidTableInfo()
    {
        TableInfo tableInfo = new TableInfo(
            "test_table",
                  Map.of("id", "INT", "name", "STRING"),
                  Set.of("id"));
        String expectedSQL = """
            CREATE MATERIALIZED VIEW IF NOT EXISTS test_table_view AS SELECT id, name FROM test_table_source;\u0000""";

        String actualSQL = template.generate(tableInfo);

        assertEquals(expectedSQL, actualSQL);
    }

    @Test
    public void shouldGenerateMaterializedViewWithEmptyColumns()
    {
        TableInfo tableInfo = new TableInfo("empty_table", Map.of(), Set.of());
        String expectedSQL = """
            CREATE MATERIALIZED VIEW IF NOT EXISTS empty_table_view AS SELECT * FROM empty_table_source;\u0000""";

        String actualSQL = template.generate(tableInfo);

        assertEquals(expectedSQL, actualSQL);
    }

    @Test
    public void shouldGenerateMaterializedViewWithIncludes()
    {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "INT");
        columns.put("zilla_correlation_id", "VARCHAR");
        columns.put("zilla_identity", "VARCHAR");
        columns.put("timestamp", "TIMESTAMP");

        TableInfo tableInfo = new TableInfo("test_table", columns, Set.of("id"));
        String expectedSQL = "CREATE MATERIALIZED VIEW IF NOT EXISTS test_table_view AS SELECT id," +
            " COALESCE(zilla_correlation_id, zilla_correlation_id_header::varchar) as zilla_correlation_id," +
            " COALESCE(zilla_identity, zilla_identity_header::varchar) as zilla_identity, timestamp" +
            " FROM test_table_source;\u0000";

        String actualSQL = template.generate(tableInfo);

        assertEquals(expectedSQL, actualSQL);
    }
}
