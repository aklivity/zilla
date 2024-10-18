package io.aklivity.zilla.runtime.binding.risingwave.internal.statement;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.pgsql.parser.module.StreamInfo;
import io.aklivity.zilla.runtime.binding.pgsql.parser.module.TableInfo;

public class RisingwaveCreateSourceTemplateTest
{
    private static RisingwaveCreateSourceTemplate template;

    @BeforeClass
    public static void setUp()
    {
        template = new RisingwaveCreateSourceTemplate("localhost:9092", "http://localhost:8081", 1627846260000L);
    }

    @Test
    public void shouldGenerateStreamSourceWithValidStreamInfo()
    {
        StreamInfo streamInfo = new StreamInfo("test_stream", Map.of("id", "INT", "name", "STRING"));
        String expectedSQL = """
            CREATE SOURCE IF NOT EXISTS test_stream (*)
            WITH (
               connector='kafka',
               properties.bootstrap.server='localhost:9092',
               topic='test_db.test_stream',
               scan.startup.mode='latest',
               scan.startup.timestamp.millis='1627846260000'
            ) FORMAT PLAIN ENCODE AVRO (
               schema.registry = 'http://localhost:8081'
            );\u0000""";

        String actualSQL = template.generateStreamSource("test_db", streamInfo);

        assertEquals(expectedSQL, actualSQL);
    }

    @Test
    public void shouldGenerateTableSourceWithValidTableInfoAndIncludes()
    {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "INT");
        columns.put("zilla_correlation_id", "VARCHAR");
        columns.put("zilla_identity", "VARCHAR");
        columns.put("timestamp", "TIMESTAMP");

        TableInfo tableInfo = new TableInfo(
            "test_table", columns, Set.of("id"));
        String expectedSQL = """
            CREATE SOURCE IF NOT EXISTS test_table_source (*)
            INCLUDE header 'zilla:correlation-id' AS zilla_zilla_correlation_id_header
            INCLUDE header 'zilla:identity' AS zilla_zilla_identity_header
            INCLUDE timestamp AS TIMESTAMP
            WITH (
               connector='kafka',
               properties.bootstrap.server='localhost:9092',
               topic='test_db.test_table',
               scan.startup.mode='latest',
               scan.startup.timestamp.millis='1627846260000'
            ) FORMAT PLAIN ENCODE AVRO (
               schema.registry = 'http://localhost:8081'
            );\u0000""";

        String actualSQL = template.generateTableSource("test_db", tableInfo);

        assertEquals(expectedSQL, actualSQL);
    }

    @Test
    public void shouldGenerateStreamSourceWithEmptyColumnsReturnsSQLWithoutIncludes()
    {
        StreamInfo streamInfo = new StreamInfo("empty_stream", Map.of());
        String expectedSQL = """
            CREATE SOURCE IF NOT EXISTS empty_stream (*)
            WITH (
               connector='kafka',
               properties.bootstrap.server='localhost:9092',
               topic='test_db.empty_stream',
               scan.startup.mode='latest',
               scan.startup.timestamp.millis='1627846260000'
            ) FORMAT PLAIN ENCODE AVRO (
               schema.registry = 'http://localhost:8081'
            );\u0000""";

        String actualSQL = template.generateStreamSource("test_db", streamInfo);

        assertEquals(expectedSQL, actualSQL);
    }

    @Test
    public void shouldGenerateTableSourceWithEmptyColumnsAndWithoutIncludes()
    {
        TableInfo tableInfo = new TableInfo("empty_table", Map.of(), Set.of());
        String expectedSQL = """
            CREATE SOURCE IF NOT EXISTS empty_table_source (*)
            WITH (
               connector='kafka',
               properties.bootstrap.server='localhost:9092',
               topic='test_db.empty_table',
               scan.startup.mode='latest',
               scan.startup.timestamp.millis='1627846260000'
            ) FORMAT PLAIN ENCODE AVRO (
               schema.registry = 'http://localhost:8081'
            );\u0000""";

        String actualSQL = template.generateTableSource("test_db", tableInfo);

        assertEquals(expectedSQL, actualSQL);
    }
}
