package io.aklivity.zilla.runtime.binding.risingwave.internal.statement;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.pgsql.parser.module.TableInfo;

public class RisingwaveCreateTableTemplateTest
{
    private static RisingwaveCreateTableTemplate template;

    @BeforeClass
    public static void setUp()
    {
        template = new RisingwaveCreateTableTemplate();
    }

    @Test
    public void shouldGenerateTableWithValidTableInfo()
    {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "INT");
        columns.put("name", "STRING");

        TableInfo tableInfo = new TableInfo(
            "test_table",
            columns,
            Set.of("id"));
        String expectedSQL = """
            CREATE TABLE IF NOT EXISTS test_table (id INT, name STRING, PRIMARY KEY (id));\u0000""";

        String actualSQL = template.generate(tableInfo);

        assertEquals(expectedSQL, actualSQL);
    }

    @Test
    public void shouldGenerateTableWithoutPrimaryKey()
    {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "INT");
        columns.put("name", "STRING");

        TableInfo tableInfo = new TableInfo(
            "test_table",
            columns,
            Set.of());
        String expectedSQL = """
            CREATE TABLE IF NOT EXISTS test_table (id INT, name STRING);\u0000""";

        String actualSQL = template.generate(tableInfo);

        assertEquals(expectedSQL, actualSQL);
    }

    @Ignore("TODO")
    @Test
    public void shouldGenerateTableWithMultiplePrimaryKeys()
    {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "INT");
        columns.put("name", "STRING");

        TableInfo tableInfo = new TableInfo(
            "test_table",
            columns,
            Set.of("id", "name"));
        String expectedSQL = """
            CREATE TABLE IF NOT EXISTS test_table (id INT, name STRING, PRIMARY KEY (id));\u0000""";

        String actualSQL = template.generate(tableInfo);

        assertEquals(expectedSQL, actualSQL);
    }
}
