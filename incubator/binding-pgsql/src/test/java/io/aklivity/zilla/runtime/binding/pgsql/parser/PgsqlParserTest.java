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

import java.util.List;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.FunctionInfo;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.StreamInfo;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.TableInfo;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.ViewInfo;

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
    public void shouldParseCreateTableNameWithDoublePrecisionTypeField()
    {
        String sql = "CREATE TABLE test (id DOUBLE PRECISION);";
        TableInfo tableInfo = parser.parseCreateTable(sql);
        assertEquals("test", tableInfo.name());
        assertEquals("DOUBLE PRECISION", tableInfo.columns().get("id"));
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
        assertEquals("SELECT * FROM test_table", viewInfo.select());
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
        List<String> drops = parser.parseDrop(sql);
        assertEquals(1, drops.size());
        assertTrue(drops.contains("test_table"));
    }

    @Test
    public void shouldParseDropMultipleTables()
    {
        String sql = "DROP TABLE table1, table2;";
        List<String> drops = parser.parseDrop(sql);
        assertEquals(2, drops.size());
        assertTrue(drops.contains("table1"));
        assertTrue(drops.contains("table2"));
    }

    @Test(expected = ParseCancellationException.class)
    public void shouldHandleEmptyDropStatement()
    {
        String sql = "DROP TABLE;";
        List<String> drops = parser.parseDrop(sql);
        assertEquals(0, drops.size());
    }

    @Test
    public void shouldParseDropView()
    {
        String sql = "DROP VIEW test_view;";
        List<String> drops = parser.parseDrop(sql);
        assertEquals(1, drops.size());
        assertTrue(drops.contains("test_view"));
    }

    @Test
    public void shouldParseDropMaterializedView()
    {
        String sql = "DROP MATERIALIZED VIEW test_materialized_view;";
        List<String> drops = parser.parseDrop(sql);
        assertEquals(1, drops.size());
        assertTrue(drops.contains("test_materialized_view"));
    }

    @Test
    public void shouldParseCreateStream()
    {
        String sql = "CREATE STREAM test_stream (id INT, name VARCHAR(100));";
        StreamInfo streamInfo = parser.parseCreateStream(sql);
        assertNotNull(streamInfo);
        assertEquals("test_stream", streamInfo.name());
        assertEquals(2, streamInfo.columns().size());
        assertEquals("INT", streamInfo.columns().get("id"));
        assertEquals("VARCHAR(100)", streamInfo.columns().get("name"));
    }

    @Test
    public void shouldParseCreateStreamIfNotExists()
    {
        String sql = "CREATE STREAM IF NOT EXISTS test_stream (id INT, name VARCHAR(100));";
        StreamInfo streamInfo = parser.parseCreateStream(sql);
        assertNotNull(streamInfo);
        assertEquals("test_stream", streamInfo.name());
        assertEquals(2, streamInfo.columns().size());
        assertEquals("INT", streamInfo.columns().get("id"));
        assertEquals("VARCHAR(100)", streamInfo.columns().get("name"));
    }

    @Test(expected = ParseCancellationException.class)
    public void shouldHandleInvalidCreateStream()
    {
        String sql = "CREATE STREAM test_stream";
        parser.parseCreateStream(sql);
    }

    @Test
    public void shouldParseCreateFunction()
    {
        String sql = "CREATE FUNCTION test_function() RETURNS INT AS $$ BEGIN RETURN 1; END $$ LANGUAGE plpgsql;";
        FunctionInfo functionInfo = parser.parseCreateFunction(sql);
        assertNotNull(functionInfo);
        assertEquals("test_function", functionInfo.name());
        assertEquals("INT", functionInfo.returnType());
    }

    @Test
    public void shouldParseCreateFunctionWithLanguage()
    {
        String sql = "CREATE FUNCTION test_function(int) RETURNS TABLE (x INT) LANGUAGE python AS 'test_function';";
        FunctionInfo functionInfo = parser.parseCreateFunction(sql);
        assertNotNull(functionInfo);
        assertEquals("test_function", functionInfo.name());
        assertEquals("INT", functionInfo.returnType());
        assertEquals("python", functionInfo.language());
    }

    @Test
    public void shouldParseCreateFunctionWithStructReturnType()
    {
        String sql = "CREATE FUNCTION test_function(int) RETURNS struct<key varchar, value varchar>" +
            " LANGUAGE python AS 'test_function';";
        FunctionInfo functionInfo = parser.parseCreateFunction(sql);
        assertNotNull(functionInfo);
        assertEquals("test_function", functionInfo.name());
        assertEquals("struct<key varchar, value varchar>", functionInfo.returnType());
        assertEquals("python", functionInfo.language());
    }

    @Test(expected = ParseCancellationException.class)
    public void shouldHandleInvalidCreateFunction()
    {
        String sql = "CREATE FUNCTION test_function()";
        parser.parseCreateFunction(sql);
    }

    @Test
    public void shouldParseCreateTableWithUniqueConstraint()
    {
        String sql = "CREATE TABLE test (id INT UNIQUE, name VARCHAR(100));";
        TableInfo tableInfo = parser.parseCreateTable(sql);
        assertNotNull(tableInfo);
        assertEquals(2, tableInfo.columns().size());
        assertTrue(tableInfo.columns().containsKey("id"));
        assertTrue(tableInfo.columns().containsKey("name"));
    }

    @Test
    public void shouldParseCreateTableWithForeignKey()
    {
        String sql = "CREATE TABLE test (id INT, name VARCHAR(100), CONSTRAINT fk_name FOREIGN KEY (name)" +
            " REFERENCES other_table(name));";
        TableInfo tableInfo = parser.parseCreateTable(sql);
        assertNotNull(tableInfo);
        assertEquals(2, tableInfo.columns().size());
        assertTrue(tableInfo.columns().containsKey("id"));
        assertTrue(tableInfo.columns().containsKey("name"));
    }

    @Test
    public void shouldParseCreateTableWithCheckConstraint()
    {
        String sql = "CREATE TABLE test (id INT, name VARCHAR(100), CHECK (id > 0));";
        TableInfo tableInfo = parser.parseCreateTable(sql);
        assertNotNull(tableInfo);
        assertEquals(2, tableInfo.columns().size());
        assertTrue(tableInfo.columns().containsKey("id"));
        assertTrue(tableInfo.columns().containsKey("name"));
    }

    @Test
    public void shouldHandleInvalidCreateTableWithMissingColumns()
    {
        String sql = "CREATE TABLE test ();";
        parser.parseCreateTable(sql);
    }

    @Test
    public void shouldParseCreateTableWithDefaultValues()
    {
        String sql = "CREATE TABLE test (id INT DEFAULT 0, name VARCHAR(100) DEFAULT 'unknown');";
        TableInfo tableInfo = parser.parseCreateTable(sql);
        assertNotNull(tableInfo);
        assertEquals(2, tableInfo.columns().size());
        assertEquals("INT", tableInfo.columns().get("id"));
        assertEquals("VARCHAR(100)", tableInfo.columns().get("name"));
    }

    @Test
    public void shouldParseCreateTableWithNotNullConstraint()
    {
        String sql = "CREATE TABLE test (id INT NOT NULL, name VARCHAR(100) NOT NULL);";
        TableInfo tableInfo = parser.parseCreateTable(sql);
        assertNotNull(tableInfo);
        assertEquals(2, tableInfo.columns().size());
        assertTrue(tableInfo.columns().containsKey("id"));
        assertTrue(tableInfo.columns().containsKey("name"));
    }

    @Test
    public void shouldParseCreateTableWithMultipleConstraints()
    {
        String sql = "CREATE TABLE test (id INT PRIMARY KEY, name VARCHAR(100) UNIQUE, age INT CHECK (age > 0));";
        TableInfo tableInfo = parser.parseCreateTable(sql);
        assertNotNull(tableInfo);
        assertEquals(3, tableInfo.columns().size());
        assertTrue(tableInfo.primaryKeys().contains("id"));
        assertTrue(tableInfo.columns().containsKey("name"));
        assertTrue(tableInfo.columns().containsKey("age"));
    }

}
