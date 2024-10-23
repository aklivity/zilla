/*
 * Copyright 2021-2023 Aklivity Inc
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Ignore;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Table;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.View;

public class RisingwaveCreateMaterializedViewTemplateTest
{
    private final RisingwaveCreateMaterializedViewTemplate template = new RisingwaveCreateMaterializedViewTemplate();

    @Test
    public void shouldGenerateMaterializedViewWithValidViewInfo()
    {
        View view = new View("test_view", "SELECT * FROM test_table");
        String expectedSQL = """
            CREATE MATERIALIZED VIEW IF NOT EXISTS test_view AS SELECT * FROM test_table;\u0000""";

        String actualSQL = template.generate(view);

        assertEquals(expectedSQL, actualSQL);
    }

    @Ignore("TODO")
    @Test
    public void shouldGenerateMaterializedViewWithValidTableInfo()
    {
        Table table = new Table(
            "test_table",
                  Map.of("id", "INT", "name", "STRING"),
                  Set.of("id"));
        String expectedSQL = """
            CREATE MATERIALIZED VIEW IF NOT EXISTS test_table_view AS SELECT id, name FROM test_table_source;\u0000""";

        String actualSQL = template.generate(table);

        assertEquals(expectedSQL, actualSQL);
    }

    @Test
    public void shouldGenerateMaterializedViewWithEmptyColumns()
    {
        Table table = new Table("empty_table", Map.of(), Set.of());
        String expectedSQL = """
            CREATE MATERIALIZED VIEW IF NOT EXISTS empty_table_view AS SELECT * FROM empty_table_source;\u0000""";

        String actualSQL = template.generate(table);

        assertEquals(expectedSQL, actualSQL);
    }

    @Test
    public void shouldGenerateMaterializedViewWithIncludes()
    {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "INT");
        columns.put("zilla_correlation_id", "VARCHAR");
        columns.put("zilla_identity", "VARCHAR");
        columns.put("zilla_timestamp", "TIMESTAMP");

        Table table = new Table("test_table", columns, Set.of("id"));
        String expectedSQL = "CREATE MATERIALIZED VIEW IF NOT EXISTS test_table_view AS SELECT id," +
            " COALESCE(zilla_correlation_id, zilla_correlation_id_header::varchar) as zilla_correlation_id," +
            " COALESCE(zilla_identity, zilla_identity_header::varchar) as zilla_identity," +
            " COALESCE(zilla_timestamp, zilla_timestamp_timestamp::varchar) as zilla_timestamp" +
            " FROM test_table_source;\u0000";

        String actualSQL = template.generate(table);

        assertEquals(expectedSQL, actualSQL);
    }
}
