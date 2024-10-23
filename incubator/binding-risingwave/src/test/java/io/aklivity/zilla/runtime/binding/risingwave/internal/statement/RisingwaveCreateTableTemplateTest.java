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

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Table;

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

        Table table = new Table(
            "test_table",
            columns,
            Set.of("id"));
        String expectedSQL = """
            CREATE TABLE IF NOT EXISTS test_table (id INT, name STRING, PRIMARY KEY (id));\u0000""";

        String actualSQL = template.generate(table);

        assertEquals(expectedSQL, actualSQL);
    }

    @Test
    public void shouldGenerateTableWithoutPrimaryKey()
    {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "INT");
        columns.put("name", "STRING");

        Table table = new Table(
            "test_table",
            columns,
            Set.of());
        String expectedSQL = """
            CREATE TABLE IF NOT EXISTS test_table (id INT, name STRING);\u0000""";

        String actualSQL = template.generate(table);

        assertEquals(expectedSQL, actualSQL);
    }

    @Ignore("TODO")
    @Test
    public void shouldGenerateTableWithMultiplePrimaryKeys()
    {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "INT");
        columns.put("name", "STRING");

        Table table = new Table(
            "test_table",
            columns,
            Set.of("id", "name"));
        String expectedSQL = """
            CREATE TABLE IF NOT EXISTS test_table (id INT, name STRING, PRIMARY KEY (id));\u0000""";

        String actualSQL = template.generate(table);

        assertEquals(expectedSQL, actualSQL);
    }
}
