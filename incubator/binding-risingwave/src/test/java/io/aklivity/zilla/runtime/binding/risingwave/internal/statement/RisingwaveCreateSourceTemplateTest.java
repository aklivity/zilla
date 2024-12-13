/*
 * Copyright 2021-2024 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.risingwave.internal.statement;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateStream;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateTable;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.TableColumn;

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
        CreateStream createStream = new CreateStream("public", "test_stream", Map.of("id", "INT", "name", "STRING"));
        String expectedSQL = """
            CREATE SOURCE IF NOT EXISTS test_stream (*)
            WITH (
               connector='kafka',
               properties.bootstrap.server='localhost:9092',
               topic='public.test_stream',
               scan.startup.mode='latest',
               scan.startup.timestamp.millis='1627846260000'
            ) FORMAT PLAIN ENCODE AVRO (
               schema.registry = 'http://localhost:8081'
            );\u0000""";

        String actualSQL = template.generateStreamSource(createStream);

        assertEquals(expectedSQL, actualSQL);
    }

    @Test
    public void shouldGenerateTableSourceWithValidTableInfoAndIncludes()
    {
        List<TableColumn> columns = new ArrayList<>();
        columns.add(new TableColumn("id", "INT", List.of()));
        columns.add(new TableColumn("zilla_correlation_id", "VARCHAR", List.of()));
        columns.add(new TableColumn("zilla_identity", "VARCHAR", List.of()));
        columns.add(new TableColumn("zilla_timestamp", "TIMESTAMP", List.of()));

        CreateTable createTable = new CreateTable(
            "public", "test_table", columns, Set.of("id"));
        String expectedSQL = """
            CREATE SOURCE IF NOT EXISTS test_table_source (*)
            INCLUDE header 'zilla:correlation-id' AS zilla_correlation_id_header
            INCLUDE header 'zilla:identity' AS zilla_identity_header
            INCLUDE timestamp AS zilla_timestamp_timestamp
            WITH (
               connector='kafka',
               properties.bootstrap.server='localhost:9092',
               topic='public.test_table',
               scan.startup.mode='latest',
               scan.startup.timestamp.millis='1627846260000'
            ) FORMAT PLAIN ENCODE AVRO (
               schema.registry = 'http://localhost:8081'
            );\u0000""";

        String actualSQL = template.generateTableSource(createTable);

        assertEquals(expectedSQL, actualSQL);
    }

    @Test
    public void shouldGenerateStreamSourceWithEmptyColumnsReturnsSQLWithoutIncludes()
    {
        CreateStream createStream = new CreateStream("public", "empty_stream", Map.of());
        String expectedSQL = """
            CREATE SOURCE IF NOT EXISTS empty_stream (*)
            WITH (
               connector='kafka',
               properties.bootstrap.server='localhost:9092',
               topic='public.empty_stream',
               scan.startup.mode='latest',
               scan.startup.timestamp.millis='1627846260000'
            ) FORMAT PLAIN ENCODE AVRO (
               schema.registry = 'http://localhost:8081'
            );\u0000""";

        String actualSQL = template.generateStreamSource(createStream);

        assertEquals(expectedSQL, actualSQL);
    }

    @Test
    public void shouldGenerateStreamSourceWithEmptyColumnsReturnsSQLWithIncludes()
    {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "INT");
        columns.put("zilla_correlation_id", "VARCHAR");
        columns.put("zilla_identity", "VARCHAR");
        columns.put("zilla_timestamp", "TIMESTAMP");

        String expectedSQL = """
            CREATE SOURCE IF NOT EXISTS include_stream (*)
            INCLUDE header 'zilla:correlation-id' AS zilla_correlation_id
            INCLUDE header 'zilla:identity' AS zilla_identity
            INCLUDE timestamp AS zilla_timestamp
            WITH (
               connector='kafka',
               properties.bootstrap.server='localhost:9092',
               topic='public.include_stream',
               scan.startup.mode='latest',
               scan.startup.timestamp.millis='1627846260000'
            ) FORMAT PLAIN ENCODE AVRO (
               schema.registry = 'http://localhost:8081'
            );\u0000""";
        CreateStream createStream = new CreateStream("public", "include_stream", columns);

        String actualSQL = template.generateStreamSource(createStream);

        assertEquals(expectedSQL, actualSQL);
    }

    @Test
    public void shouldGenerateTableSourceWithEmptyColumnsAndWithoutIncludes()
    {
        CreateTable createTable = new CreateTable("public", "empty_table", List.of(), Set.of());
        String expectedSQL = """
            CREATE SOURCE IF NOT EXISTS empty_table_source (*)
            WITH (
               connector='kafka',
               properties.bootstrap.server='localhost:9092',
               topic='public.empty_table',
               scan.startup.mode='latest',
               scan.startup.timestamp.millis='1627846260000'
            ) FORMAT PLAIN ENCODE AVRO (
               schema.registry = 'http://localhost:8081'
            );\u0000""";

        String actualSQL = template.generateTableSource(createTable);

        assertEquals(expectedSQL, actualSQL);
    }
}
