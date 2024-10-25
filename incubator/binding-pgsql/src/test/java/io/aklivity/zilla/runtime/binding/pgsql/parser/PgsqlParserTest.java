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
package io.aklivity.zilla.runtime.binding.pgsql.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Alter;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Function;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Operation;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Stream;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Table;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.View;

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
        Table table = parser.parseCreateTable(sql);

        assertNotNull(table);
        assertTrue(table.primaryKeys().contains("id"));
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
        Table table = parser.parseCreateTable(sql);

        assertNotNull(table);
        assertEquals(2, table.primaryKeys().size());
        assertEquals(3, table.columns().size());
        assertTrue(table.primaryKeys().contains("id"));
        assertTrue(table.primaryKeys().contains("name"));
    }

    @Test
    public void shouldParseCreateTableName()
    {
        String sql = "CREATE TABLE test (id INT);";
        Table table = parser.parseCreateTable(sql);

        assertEquals("test", table.name());
    }

    @Test
    public void shouldParseCreateTableNameWithDoublePrecisionTypeField()
    {
        String sql = "CREATE TABLE test (id DOUBLE PRECISION);";
        Table table = parser.parseCreateTable(sql);
        assertEquals("test", table.name());
        assertEquals("DOUBLE PRECISION", table.columns().get(0).type());
    }

    @Test
    public void shouldParseCreateTableColumns()
    {
        String sql = "CREATE TABLE test (id INT, name VARCHAR(100));";
        Table table = parser.parseCreateTable(sql);

        assertEquals(2, table.columns().size());
        assertEquals("INT", table.columns().get(0).type());
        assertEquals("VARCHAR(100)", table.columns().get(1).type());
    }

    @Test
    public void shouldParseCreatreTablePrimaryKey()
    {
        String sql = "CREATE TABLE test (id INT PRIMARY KEY, name VARCHAR(100));";
        Table table = parser.parseCreateTable(sql);

        assertEquals(1, table.primaryKeys().size());
        assertTrue(table.primaryKeys().contains("id"));
    }

    @Test
    public void shouldParseCreateTableCompositePrimaryKey()
    {
        String sql = "CREATE TABLE test (id INT, name VARCHAR(100), PRIMARY KEY (id, name));";
        Table table = parser.parseCreateTable(sql);

        assertEquals(2, table.primaryKeys().size());
        assertTrue(table.primaryKeys().contains("id"));
        assertTrue(table.primaryKeys().contains("name"));
    }

    @Test
    public void shouldHandleEmptyCreateTable()
    {
        String sql = "CREATE TABLE test ();";
        Table table = parser.parseCreateTable(sql);

        assertEquals(0, table.columns().size());
        assertEquals(0, table.primaryKeys().size());
    }

    @Test
    public void shouldHandleEmptySql()
    {
        String sql = "";
        Table table = parser.parseCreateTable(sql);

        assertNotNull(table);
    }

    @Test
    public void shouldParseCreateMaterializedView()
    {
        String sql = "CREATE MATERIALIZED VIEW test_view AS SELECT * FROM test_table;";
        View view = parser.parseCreateMaterializedView(sql);

        assertNotNull(view);
        assertEquals("test_view", view.name());
        assertEquals("SELECT * FROM test_table", view.select());
    }

    @Test(expected = ParseCancellationException.class)
    public void shouldHandleEmptyCreateMaterializedView()
    {
        String sql = "CREATE MATERIALIZED VIEW test_view AS ;";
        View view = parser.parseCreateMaterializedView(sql);

        assertNotNull(view);
        assertEquals("test_view", view.name());
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
        Stream stream = parser.parseCreateStream(sql);
        assertNotNull(stream);
        assertEquals("test_stream", stream.name());
        assertEquals(2, stream.columns().size());
        assertEquals("INT", stream.columns().get("id"));
        assertEquals("VARCHAR(100)", stream.columns().get("name"));
    }

    @Test
    public void shouldParseCreateStreamIfNotExists()
    {
        String sql = "CREATE STREAM IF NOT EXISTS test_stream (id INT, name VARCHAR(100));";
        Stream stream = parser.parseCreateStream(sql);
        assertNotNull(stream);
        assertEquals("test_stream", stream.name());
        assertEquals(2, stream.columns().size());
        assertEquals("INT", stream.columns().get("id"));
        assertEquals("VARCHAR(100)", stream.columns().get("name"));
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
        Function function = parser.parseCreateFunction(sql);

        assertNotNull(function);
        assertEquals("test_function", function.name());
        assertEquals("INT", function.returnType());
    }

    @Test
    public void shouldParseCreateFunctionWithLanguage()
    {
        String sql = "CREATE FUNCTION test_function(int) RETURNS TABLE (x INT) LANGUAGE python AS 'test_function';";
        Function function = parser.parseCreateFunction(sql);

        assertNotNull(function);
        assertEquals("test_function", function.name());
        assertEquals("INT", function.returnType());
        assertEquals("python", function.language());
    }

    @Test
    public void shouldParseCreateFunctionWithStructReturnType()
    {
        String sql = "CREATE FUNCTION test_function(int) RETURNS struct<key varchar, value varchar>" +
            " LANGUAGE python AS 'test_function';";
        Function function = parser.parseCreateFunction(sql);

        assertNotNull(function);
        assertEquals("test_function", function.name());
        assertEquals("struct<key varchar, value varchar>", function.returnType());
        assertEquals("python", function.language());
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
        Table table = parser.parseCreateTable(sql);

        assertNotNull(table);
        assertEquals(2, table.columns().size());
        assertEquals("id", table.columns().get(0).name());
        assertEquals("name", table.columns().get(1).name());
    }

    @Test
    public void shouldParseCreateTableWithForeignKey()
    {
        String sql = "CREATE TABLE test (id INT, name VARCHAR(100), CONSTRAINT fk_name FOREIGN KEY (name)" +
            " REFERENCES other_table(name));";
        Table table = parser.parseCreateTable(sql);
        assertNotNull(table);
        assertEquals(2, table.columns().size());
        assertEquals("id", table.columns().get(0).name());
        assertEquals("name", table.columns().get(1).name());
    }

    @Test
    public void shouldParseCreateTableWithCheckConstraint()
    {
        String sql = "CREATE TABLE test (id INT, name VARCHAR(100), CHECK (id > 0));";
        Table table = parser.parseCreateTable(sql);

        assertNotNull(table);
        assertEquals(2, table.columns().size());
        assertEquals("id", table.columns().get(0).name());
        assertEquals("name", table.columns().get(1).name());
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
        Table table = parser.parseCreateTable(sql);

        assertNotNull(table);
        assertEquals(2, table.columns().size());
        assertEquals("INT", table.columns().get(0).type());
        assertEquals("VARCHAR(100)", table.columns().get(1).type());
    }

    @Test
    public void shouldParseCreateTableWithNotNullConstraint()
    {
        String sql = "CREATE TABLE test (id INT NOT NULL, name VARCHAR(100) NOT NULL);";
        Table table = parser.parseCreateTable(sql);

        assertNotNull(table);
        assertEquals(2, table.columns().size());
        assertEquals("id", table.columns().get(0).name());
        assertEquals("name", table.columns().get(1).name());
    }

    @Test
    public void shouldParseCreateTableWithMultipleConstraints()
    {
        String sql = "CREATE TABLE test (id INT PRIMARY KEY, name VARCHAR(100) UNIQUE, age INT CHECK (age > 0));";
        Table table = parser.parseCreateTable(sql);

        assertNotNull(table);
        assertEquals(3, table.columns().size());
        assertTrue(table.primaryKeys().contains("id"));
        assertEquals("name", table.columns().get(1).name());
        assertEquals("age", table.columns().get(2).name());
    }

    @Test
    public void shouldParseAlterTableAddColumn()
    {
        String sql = "ALTER TABLE test_table ADD COLUMN new_column INT;";
        Alter alter = parser.parseAlterTable(sql);

        assertEquals("test_table", alter.name());
        assertEquals(1, alter.alterExpressions().size());
        assertEquals(Operation.ADD, alter.alterExpressions().get(0).operation());
        assertEquals("new_column", alter.alterExpressions().get(0).columnName());
        assertEquals("INT", alter.alterExpressions().get(0).columnType());
    }

    @Test
    public void shouldParseAlterTopicAddColumn()
    {
        String sql = "ALTER TOPIC test_table ADD COLUMN new_column INT;";
        Alter alter = parser.parseAlterTable(sql);

        assertEquals("test_table", alter.name());
        assertEquals(1, alter.alterExpressions().size());
        assertEquals(Operation.ADD, alter.alterExpressions().get(0).operation());
        assertEquals("new_column", alter.alterExpressions().get(0).columnName());
        assertEquals("INT", alter.alterExpressions().get(0).columnType());
    }

    @Test
    public void shouldParseAlterTableDropColumn()
    {
        String sql = "ALTER TABLE test_table DROP COLUMN old_column;";
        Alter alter = parser.parseAlterTable(sql);

        assertEquals("test_table", alter.name());
        assertEquals(1, alter.alterExpressions().size());
        assertEquals(Operation.DROP, alter.alterExpressions().get(0).operation());
        assertEquals("old_column", alter.alterExpressions().get(0).columnName());
    }

    @Test
    public void shouldParseAlterTableModifyColumn()
    {
        String sql = "ALTER TABLE test_table ALTER COLUMN existing_column TYPE VARCHAR(100);";
        Alter alter = parser.parseAlterTable(sql);

        assertEquals("test_table", alter.name());
        assertEquals(1, alter.alterExpressions().size());
        assertEquals(Operation.MODIFY, alter.alterExpressions().get(0).operation());
        assertEquals("existing_column", alter.alterExpressions().get(0).columnName());
    }

    @Test(expected = ParseCancellationException.class)
    public void shouldHandleInvalidAlterTable()
    {
        String sql = "ALTER TABLE";
        parser.parseAlterTable(sql);
    }

    @Test
    public void shouldParseCommandForAlterTable()
    {
        String sql = "ALTER TABLE test_table ALTER COLUMN existing_column TYPE VARCHAR(100);";
        String expectedCommand = "ALTER TABLE";

        String parsedCommand = parser.parseCommand(sql);

        assertEquals(expectedCommand, parsedCommand);
    }

    @Test
    public void shouldParseCommandForCreateTopic()
    {
        String sql = "CREATE TOPIC test (id INT UNIQUE, name VARCHAR(100));";
        String expectedCommand = "CREATE TOPIC";

        String parsedCommand = parser.parseCommand(sql);

        assertEquals(expectedCommand, parsedCommand);
    }
}
