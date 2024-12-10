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
import java.util.List;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateTable;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.TableColumn;

public class RisingwaveCreateCreateTableTemplateTest
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
        List<TableColumn> columns = new ArrayList<>();
        columns.add(new TableColumn("id", "INT", List.of()));
        columns.add(new TableColumn("name", "STRING", List.of()));

        CreateTable createTable = new CreateTable(
            "public",
            "test_table",
            columns,
            Set.of("id"));
        String expectedSQL = """
            CREATE TABLE IF NOT EXISTS test_table (id INT, name STRING, PRIMARY KEY (id));\u0000""";

        String actualSQL = template.generate(createTable);

        assertEquals(expectedSQL, actualSQL);
    }

    @Test
    public void shouldGenerateTableWithoutPrimaryKey()
    {
        List<TableColumn> columns = new ArrayList<>();
        columns.add(new TableColumn("id", "INT", List.of()));
        columns.add(new TableColumn("name", "STRING", List.of()));

        CreateTable createTable = new CreateTable(
            "public",
            "test_table",
            columns,
            Set.of());
        String expectedSQL = """
            CREATE TABLE IF NOT EXISTS test_table (id INT, name STRING);\u0000""";

        String actualSQL = template.generate(createTable);

        assertEquals(expectedSQL, actualSQL);
    }

    @Ignore("TODO")
    @Test
    public void shouldGenerateTableWithMultiplePrimaryKeys()
    {
        List<TableColumn> columns = new ArrayList<>();
        columns.add(new TableColumn("id", "INT", List.of()));
        columns.add(new TableColumn("name", "STRING", List.of()));

        CreateTable createTable = new CreateTable(
            "public",
            "test_table",
            columns,
            Set.of("id", "name"));
        String expectedSQL = """
            CREATE TABLE IF NOT EXISTS test_table (id INT, name STRING, PRIMARY KEY (id));\u0000""";

        String actualSQL = template.generate(createTable);

        assertEquals(expectedSQL, actualSQL);
    }
}
