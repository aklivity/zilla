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
package io.aklivity.zilla.runtime.binding.pgsql.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.pgsql.parser.module.TableInfo;
import io.aklivity.zilla.runtime.binding.pgsql.parser.module.ViewInfo;

public class PgsqlParserTest
{
    private PgsqlParser parser;

    @Before
    public void setUp()
    {
        parser = new PgsqlParser();
    }

    @Test
    public void shouldParseWithPrimaryKeySql()
    {
        String sql = "CREATE TABLE test (id INT PRIMARY KEY, name VARCHAR(100));";
        TableInfo tableInfo = parser.parseCreateTable(sql);
        assertNotNull(tableInfo);
        assertTrue(tableInfo.primaryKeys().contains("id"));
    }

    @Test
    public void shouldCreateParseWithPrimaryKeysSql()
    {
        String sql = """
            CREATE TABLE example_table (
                id INT PRIMARY KEY,
                name VARCHAR(100),
                age INT,
                PRIMARY KEY (id, name)
            );""";
        TableInfo tableInfo = parser.parseCreateTable(sql);
        assertNotNull(tableInfo);
        assertEquals(2, tableInfo.primaryKeys().size());
        assertEquals(3, tableInfo.columns().size());
        assertTrue(tableInfo.primaryKeys().contains("id"));
        assertTrue(tableInfo.primaryKeys().contains("name"));
    }

    @Test
    public void shouldParseCreateTableName()
    {
        String sql = "CREATE TABLE test (id INT);";
        TableInfo tableInfo = parser.parseCreateTable(sql);
        assertEquals("test", tableInfo.name());
    }

    @Test
    public void shouldParseCreateTableColumns()
    {
        String sql = "CREATE TABLE test (id INT, name VARCHAR(100));";
        TableInfo tableInfo = parser.parseCreateTable(sql);
        assertEquals(2, tableInfo.columns().size());
        assertEquals("INT", tableInfo.columns().get("id"));
        assertEquals("VARCHAR(100)", tableInfo.columns().get("name"));
    }

    @Test
    public void shouldParseCreatreTablePrimaryKey()
    {
        String sql = "CREATE TABLE test (id INT PRIMARY KEY, name VARCHAR(100));";
        TableInfo tableInfo = parser.parseCreateTable(sql);
        assertEquals(1, tableInfo.primaryKeys().size());
        assertTrue(tableInfo.primaryKeys().contains("id"));
    }

    @Test
    public void shouldParseCreateTableCompositePrimaryKey()
    {
        String sql = "CREATE TABLE test (id INT, name VARCHAR(100), PRIMARY KEY (id, name));";
        TableInfo tableInfo = parser.parseCreateTable(sql);
        assertEquals(2, tableInfo.primaryKeys().size());
        assertTrue(tableInfo.primaryKeys().contains("id"));
        assertTrue(tableInfo.primaryKeys().contains("name"));
    }

    @Test
    public void shouldHandleEmptyCreateTable()
    {
        String sql = "CREATE TABLE test ();";
        TableInfo tableInfo = parser.parseCreateTable(sql);
        assertEquals(0, tableInfo.columns().size());
        assertEquals(0, tableInfo.primaryKeys().size());
    }

    @Test
    public void shouldHandleEmptySql()
    {
        String sql = "";
        TableInfo tableInfo = parser.parseCreateTable(sql);
        assertNotNull(tableInfo);
    }

    @Test
    public void shouldParseCreateMaterializedView()
    {
        String sql = "CREATE MATERIALIZED VIEW test_view AS SELECT * FROM test_table;";
        ViewInfo viewInfo = parser.parseCreateMaterializedView(sql);
        assertNotNull(viewInfo);
        assertEquals("test_view", viewInfo.name());
    }

    @Test(expected = ParseCancellationException.class)
    public void shouldHandleEmptyCreateMaterializedView()
    {
        String sql = "CREATE MATERIALIZED VIEW test_view AS ;";
        ViewInfo viewInfo = parser.parseCreateMaterializedView(sql);
        assertNotNull(viewInfo);
        assertEquals("test_view", viewInfo.name());
    }

    @Test(expected = ParseCancellationException.class)
    public void shouldHandleInvalidCreateMaterializedView()
    {
        String sql = "CREATE MATERIALIZED VIEW test_view";
        parser.parseCreateMaterializedView(sql);
    }

    @Test(expected = ParseCancellationException.class)
    public void shouldHandleInvalidCreateTable()
    {
        String sql = "CREATE TABLE test";
        parser.parseCreateTable(sql);
    }

    @Test
    public void shouldParseDropSingleTable()
    {
        String sql = "DROP TABLE test_table;";
        Set<String> drops = parser.parseDrop(sql);
        assertEquals(1, drops.size());
        assertTrue(drops.contains("test_table"));
    }

    @Test
    public void shouldParseDropMultipleTables()
    {
        String sql = "DROP TABLE table1, table2;";
        Set<String> drops = parser.parseDrop(sql);
        assertEquals(2, drops.size());
        assertTrue(drops.contains("table1"));
        assertTrue(drops.contains("table2"));
    }

    @Test(expected = ParseCancellationException.class)
    public void shouldHandleEmptyDropStatement()
    {
        String sql = "DROP TABLE;";
        Set<String> drops = parser.parseDrop(sql);
        assertEquals(0, drops.size());
    }

    @Test
    public void shouldParseDropView()
    {
        String sql = "DROP VIEW test_view;";
        Set<String> drops = parser.parseDrop(sql);
        assertEquals(1, drops.size());
        assertTrue(drops.contains("test_view"));
    }

    @Test
    public void shouldParseDropMaterializedView()
    {
        String sql = "DROP MATERIALIZED VIEW test_materialized_view;";
        Set<String> drops = parser.parseDrop(sql);
        assertEquals(1, drops.size());
        assertTrue(drops.contains("test_materialized_view"));
    }

}
