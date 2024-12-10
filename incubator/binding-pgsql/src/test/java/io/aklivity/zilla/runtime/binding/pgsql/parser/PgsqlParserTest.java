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
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateStream;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateTable;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateZview;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Drop;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Function;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Operation;

public class PgsqlParserTest
{
    private PgsqlParser parser;

    @Before
    public void setUp()
    {
        parser = new PgsqlParser();
    }

    @Test
    public void shouldCreateZtableWithPrimaryKey()
    {
        String sql = "CREATE ZTABLE test (id INT PRIMARY KEY, name VARCHAR(100));";
        CreateTable createTable = parser.parseCreateTable(sql);

        assertNotNull(createTable);
        assertTrue(createTable.primaryKeys().contains("id"));
    }

    @Test
    public void shouldCreateZtableParseWithGeneratedAsAlwaysSql()
    {
        String sql = """
            CREATE ZTABLE example_table (
                id INT,
                name VARCHAR(100),
                age INT,
                owner_id VARCHAR GENERATED ALWAYS AS IDENTITY,
                created_at TIMESTAMP GENERATED ALWAYS AS NOW,
                PRIMARY KEY (id, name)
            );""";
        CreateTable table = parser.parseCreateTable(sql);

        assertNotNull(table);
        assertEquals(2, table.primaryKeys().size());
        assertEquals(5, table.columns().size());
        assertTrue(table.primaryKeys().contains("id"));
        assertTrue(table.primaryKeys().contains("name"));
    }

    @Test
    public void shouldCreateZtableWithPrimaryKeyAsAggregate()
    {
        String sql = """
            CREATE ZTABLE example_table (
                id INT PRIMARY KEY,
                name VARCHAR(100),
                age INT,
                PRIMARY KEY (id, name)
            );""";
        CreateTable createTable = parser.parseCreateTable(sql);

        assertNotNull(createTable);
        assertEquals(2, createTable.primaryKeys().size());
        assertEquals(3, createTable.columns().size());
        assertTrue(createTable.primaryKeys().contains("id"));
        assertTrue(createTable.primaryKeys().contains("name"));
    }

    @Test
    public void shouldCreateTableName()
    {
        String sql = "CREATE ZTABLE test (id INT);";
        CreateTable createTable = parser.parseCreateTable(sql);

        assertEquals("test", createTable.name());
    }

    @Test
    public void shouldCreateZtableNameWithDoublePrecisionTypeField()
    {
        String sql = "CREATE ZTABLE test (id DOUBLE PRECISION);";
        CreateTable table = parser.parseCreateTable(sql);
        assertEquals("test", table.name());
        assertEquals("DOUBLE PRECISION", table.columns().get(0).type());
    }

    @Test
    public void shouldCreateTableColumns()
    {
        String sql = "CREATE ZTABLE test (id INT, name VARCHAR(100));";
        CreateTable table = parser.parseCreateTable(sql);

        assertEquals(2, table.columns().size());
        assertEquals("INT", table.columns().get(0).type());
        assertEquals("VARCHAR(100)", table.columns().get(1).type());
    }


    @Test
    public void shouldParseCreateZtableCompositePrimaryKey()
    {
        String sql = "CREATE ZTABLE test (id INT, name VARCHAR(100), PRIMARY KEY (id, name));";
        CreateTable table = parser.parseCreateTable(sql);

        assertEquals(2, table.primaryKeys().size());
        assertTrue(table.primaryKeys().contains("id"));
        assertTrue(table.primaryKeys().contains("name"));
    }

    @Test
    public void shouldHandleEmptyCreateZtable()
    {
        String sql = "CREATE ZTABLE test ();";
        CreateTable createTable = parser.parseCreateTable(sql);

        assertEquals(0, createTable.columns().size());
        assertEquals(0, createTable.primaryKeys().size());
    }

    @Test
    public void shouldHandleEmptySql()
    {
        String sql = "";
        CreateTable createTable = parser.parseCreateTable(sql);

        assertNotNull(createTable);
    }

    @Test
    public void shouldCreateZview()
    {
        String sql = "CREATE ZVIEW test_view AS SELECT * FROM test_table;";
        CreateZview createZview = parser.parseCreateZView(sql);

        assertNotNull(createZview);
        assertEquals("test_view", createZview.name());
        assertEquals("SELECT * FROM test_table", createZview.select());
    }

    @Test(expected = ParseCancellationException.class)
    public void shouldHandleEmptyCreateZView()
    {
        String sql = "CREATE ZVIEW test_view AS ;";
        CreateZview createZview = parser.parseCreateZView(sql);

        assertNotNull(createZview);
        assertEquals("test_view", createZview.name());
    }

    @Test(expected = ParseCancellationException.class)
    public void shouldHandleInvalidCreateZView()
    {
        String sql = "CREATE ZVIEW test_view";
        parser.parseCreateZView(sql);
    }

    @Test(expected = ParseCancellationException.class)
    public void shouldHandleInvalidZCreateZtable()
    {
        String sql = "CREATE ZTABLE test";
        parser.parseCreateTable(sql);
    }

    @Test
    public void shouldDropSingleZtable()
    {
        String sql = "DROP ZTABLE test_table;";
        List<Drop> drops = parser.parseDrop(sql);

        assertEquals(1, drops.size());
        assertEquals("public", drops.get(0).schema());
        assertEquals("test_table", drops.get(0).name());
    }

    @Test
    public void shouldDropMultipleZtables()
    {
        String sql = "DROP ZTABLE table1, table2;";
        List<Drop> drops = parser.parseDrop(sql);

        assertEquals(2, drops.size());
        assertEquals("public", drops.get(0).schema());
        assertEquals("table1", drops.get(0).name());
        assertEquals("public", drops.get(1).schema());
        assertEquals("table2", drops.get(1).name());
    }

    @Test(expected = ParseCancellationException.class)
    public void shouldDropZtable()
    {
        String sql = "DROP ZTABLE;";
        List<Drop> drops = parser.parseDrop(sql);

        assertEquals(0, drops.size());
    }

    @Test
    public void shouldDropView()
    {
        String sql = "DROP VIEW test_view;";
        List<Drop> drops = parser.parseDrop(sql);

        assertEquals(1, drops.size());
        assertEquals("public", drops.get(0).schema());
        assertEquals("test_view", drops.get(0).name());
    }

    @Test
    public void shouldDropZview()
    {
        String sql = "DROP ZVIEW test_materialized_view;";
        List<Drop> drops = parser.parseDrop(sql);

        assertEquals(1, drops.size());
        assertTrue(drops.get(0).name().equals("test_materialized_view"));
    }

    @Test
    public void shouldCreateStream()
    {
        String sql = "CREATE STREAM test_stream (id INT, name VARCHAR(100));";
        CreateStream createStream = parser.parseCreateStream(sql);
        assertNotNull(createStream);
        assertEquals("test_stream", createStream.name());
        assertEquals(2, createStream.columns().size());
        assertEquals("INT", createStream.columns().get("id"));
        assertEquals("VARCHAR(100)", createStream.columns().get("name"));
    }

    @Test
    public void shouldCreateStreamIfNotExists()
    {
        String sql = "CREATE STREAM IF NOT EXISTS test_stream (id INT, name VARCHAR(100));";
        CreateStream createStream = parser.parseCreateStream(sql);
        assertNotNull(createStream);
        assertEquals("test_stream", createStream.name());
        assertEquals(2, createStream.columns().size());
        assertEquals("INT", createStream.columns().get("id"));
        assertEquals("VARCHAR(100)", createStream.columns().get("name"));
    }

    @Test(expected = ParseCancellationException.class)
    public void shouldHandleInvalidCreateStream()
    {
        String sql = "CREATE STREAM test_stream";
        parser.parseCreateStream(sql);
    }

    @Test
    public void shouldCreateFunction()
    {
        String sql = "CREATE FUNCTION test_function() RETURNS INT AS $$ BEGIN RETURN 1; END $$ LANGUAGE plpgsql;";
        Function function = parser.parseCreateFunction(sql);

        assertNotNull(function);
        assertEquals("test_function", function.name());
        assertEquals("INT", function.returnType());
    }

    @Test
    public void shouldCreateFunctionWithLanguage()
    {
        String sql = "CREATE FUNCTION test_function(int) RETURNS TABLE (x INT) LANGUAGE python AS 'test_function';";
        Function function = parser.parseCreateFunction(sql);

        assertNotNull(function);
        assertEquals("test_function", function.name());
        assertEquals("INT", function.returnType());
        assertEquals("python", function.language());
    }

    @Test
    public void shouldCreateFunctionWithStructReturnType()
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
    public void shouldCreateZtableWithUniqueConstraint()
    {
        String sql = "CREATE ZTABLE test (id INT UNIQUE, name VARCHAR(100));";
        CreateTable createTable = parser.parseCreateTable(sql);

        assertNotNull(createTable);
        assertEquals(2, createTable.columns().size());
        assertEquals("id", createTable.columns().get(0).name());
        assertEquals("name", createTable.columns().get(1).name());
    }

    @Test
    public void shouldParseCreateZtableWithCheckConstraint()
    {
        String sql = "CREATE ZTABLE test (id INT, name VARCHAR(100), CHECK (id > 0));";
        CreateTable createTable = parser.parseCreateTable(sql);

        assertNotNull(createTable);
        assertEquals(2, createTable.columns().size());
        assertEquals("id", createTable.columns().get(0).name());
        assertEquals("name", createTable.columns().get(1).name());
    }

    @Test
    public void shouldHandleInvalidCreateTableWithMissingColumns()
    {
        String sql = "CREATE ZTABLE test ();";
        parser.parseCreateTable(sql);
    }

    @Test
    public void shouldCreateZtableWithDefaultValues()
    {
        String sql = "CREATE ZTABLE test (id INT DEFAULT 0, name VARCHAR(100) DEFAULT 'unknown');";
        CreateTable createTable = parser.parseCreateTable(sql);

        assertNotNull(createTable);
        assertEquals(2, createTable.columns().size());
        assertEquals("INT", createTable.columns().get(0).type());
        assertEquals("VARCHAR(100)", createTable.columns().get(1).type());
    }

    @Test
    public void shouldCreateZtableWithNotNullConstraint()
    {
        String sql = "CREATE ZTABLE test (id INT NOT NULL, name VARCHAR(100) NOT NULL);";
        CreateTable createTable = parser.parseCreateTable(sql);

        assertNotNull(createTable);
        assertEquals(2, createTable.columns().size());
        assertEquals("id", createTable.columns().get(0).name());
        assertEquals("name", createTable.columns().get(1).name());
    }

    @Test
    public void shouldCreateZtableWithMultipleConstraints()
    {
        String sql = "CREATE ZTABLE test (id INT PRIMARY KEY, name VARCHAR(100) UNIQUE, age INT CHECK (age > 0));";
        CreateTable createTable = parser.parseCreateTable(sql);

        assertNotNull(createTable);
        assertEquals(3, createTable.columns().size());
        assertTrue(createTable.primaryKeys().contains("id"));
        assertEquals("name", createTable.columns().get(1).name());
        assertEquals("age", createTable.columns().get(2).name());
    }

    @Test
    public void shouldAlterZtableAddColumn()
    {
        String sql = "ALTER ZTABLE test_table ADD COLUMN new_column INT;";
        Alter alter = parser.parseAlterTable(sql);

        assertEquals("test_table", alter.name());
        assertEquals(1, alter.expressions().size());
        assertEquals(Operation.ADD, alter.expressions().get(0).operation());
        assertEquals("new_column", alter.expressions().get(0).columnName());
        assertEquals("INT", alter.expressions().get(0).columnType());
    }

    @Test
    public void shouldAlterTopicAddColumn()
    {
        String sql = "ALTER TOPIC test_table ADD COLUMN new_column INT;";
        Alter alter = parser.parseAlterTable(sql);

        assertEquals("test_table", alter.name());
        assertEquals(1, alter.expressions().size());
        assertEquals(Operation.ADD, alter.expressions().get(0).operation());
        assertEquals("new_column", alter.expressions().get(0).columnName());
        assertEquals("INT", alter.expressions().get(0).columnType());
    }

    @Test
    public void shouldAlterZtableDropColumn()
    {
        String sql = "ALTER ZTABLE test_table DROP COLUMN old_column;";
        Alter alter = parser.parseAlterTable(sql);

        assertEquals("test_table", alter.name());
        assertEquals(1, alter.expressions().size());
        assertEquals(Operation.DROP, alter.expressions().get(0).operation());
        assertEquals("old_column", alter.expressions().get(0).columnName());
    }

    @Test
    public void shouldAlterZtableModifyColumn()
    {
        String sql = "ALTER ZTABLE test_table ALTER COLUMN existing_column TYPE VARCHAR(100);";
        Alter alter = parser.parseAlterTable(sql);

        assertEquals("test_table", alter.name());
        assertEquals(1, alter.expressions().size());
        assertEquals(Operation.MODIFY, alter.expressions().get(0).operation());
        assertEquals("existing_column", alter.expressions().get(0).columnName());
    }

    @Test(expected = ParseCancellationException.class)
    public void shouldHandleInvalidAlterZtable()
    {
        String sql = "ALTER ZTABLE";
        parser.parseAlterTable(sql);
    }

    @Test
    public void shouldDetectAlterCommand()
    {
        String sql = "ALTER ZTABLE test_table ALTER COLUMN existing_column TYPE VARCHAR(100);";
        String expectedCommand = "ALTER ZTABLE";

        String parsedCommand = parser.parseCommand(sql);

        assertEquals(expectedCommand, parsedCommand);
    }

    @Test
    public void shouldDetectCreateTopicCommand()
    {
        String sql = "CREATE TOPIC test (id INT UNIQUE, name VARCHAR(100));";
        String expectedCommand = "CREATE TOPIC";

        String parsedCommand = parser.parseCommand(sql);

        assertEquals(expectedCommand, parsedCommand);
    }

    @Test
    public void shouldDetectAlterStreamAddColumn()
    {
        String sql = "ALTER STREAM test_stream ADD COLUMN new_column INT;";
        Alter alter = parser.parseAlterStream(sql);

        assertEquals("test_stream", alter.name());
        assertEquals(1, alter.expressions().size());
        assertEquals(Operation.ADD, alter.expressions().get(0).operation());
        assertEquals("new_column", alter.expressions().get(0).columnName());
        assertEquals("INT", alter.expressions().get(0).columnType());
    }

    @Test
    public void shouldAlterStreamDropColumn()
    {
        String sql = "ALTER STREAM test_stream DROP COLUMN old_column;";
        Alter alter = parser.parseAlterStream(sql);

        assertEquals("test_stream", alter.name());
        assertEquals(1, alter.expressions().size());
        assertEquals(Operation.DROP, alter.expressions().get(0).operation());
        assertEquals("old_column", alter.expressions().get(0).columnName());
    }

    @Test(expected = ParseCancellationException.class)
    public void shouldHandleInvalidAlterStream()
    {
        String sql = "ALTER STREAM";
        parser.parseAlterStream(sql);
    }

    @Test
    public void shouldAlterStreamModifyColumn()
    {
        String sql = "ALTER STREAM test_stream ALTER COLUMN existing_column TYPE VARCHAR(100);";
        Alter alter = parser.parseAlterStream(sql);

        assertEquals("test_stream", alter.name());
        assertEquals(1, alter.expressions().size());
        assertEquals(Operation.MODIFY, alter.expressions().get(0).operation());
        assertEquals("existing_column", alter.expressions().get(0).columnName());
        assertEquals("VARCHAR(100)", alter.expressions().get(0).columnType());
    }

    @Test
    public void shouldShowZviews()
    {
        String sql = "SHOW ZVIEWS;";
        String type = parser.parseShow(sql);

        assertEquals("ZVIEWS", type);
    }

    @Test
    public void shouldParseShowZtables()
    {
        String sql = "SHOW ZTABLES;";
        String type = parser.parseShow(sql);

        assertEquals("ZTABLES", type);
    }
}
