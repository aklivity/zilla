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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Alter;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateFunction;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateZfunction;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateZtable;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateZview;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Drop;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Operation;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Select;

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
        CreateZtable createZtable = parser.parseCreateTable(sql);

        assertNotNull(createZtable);
        assertTrue(createZtable.primaryKeys().contains("id"));
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

        CreateZtable table = parser.parseCreateTable(sql);

        assertNotNull(table);
        assertEquals(2, table.primaryKeys().size());
        assertEquals(5, table.columns().size());
        assertTrue(table.primaryKeys().contains("id"));
        assertTrue(table.primaryKeys().contains("name"));
    }

    @Test
    public void shouldCreateZtableParseWithDoubleQuotedName()
    {
        String sql = """
            CREATE ZTABLE "example_table" (
                id INT,
                name VARCHAR(100),
                age INT,
                PRIMARY KEY (id, name)
            );""";

        CreateZtable table = parser.parseCreateTable(sql);

        assertNotNull(table);
        assertEquals("example_table", table.name());
        assertEquals(2, table.primaryKeys().size());
        assertEquals(3, table.columns().size());
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
        CreateZtable createZtable = parser.parseCreateTable(sql);

        assertNotNull(createZtable);
        assertEquals(2, createZtable.primaryKeys().size());
        assertEquals(3, createZtable.columns().size());
        assertTrue(createZtable.primaryKeys().contains("id"));
        assertTrue(createZtable.primaryKeys().contains("name"));
    }

    @Test
    public void shouldCreateTableName()
    {
        String sql = "CREATE ZTABLE test (id INT);";
        CreateZtable createZtable = parser.parseCreateTable(sql);

        assertEquals("test", createZtable.name());
    }

    @Test
    public void shouldCreateZtableNameWithDoublePrecisionTypeField()
    {
        String sql = "CREATE ZTABLE test (id DOUBLE PRECISION);";
        CreateZtable table = parser.parseCreateTable(sql);
        assertEquals("test", table.name());
        assertEquals("DOUBLE PRECISION", table.columns().get(0).type());
    }

    @Test
    public void shouldCreateTableColumns()
    {
        String sql = "CREATE ZTABLE test (id INT, name VARCHAR(100));";
        CreateZtable table = parser.parseCreateTable(sql);

        assertEquals(2, table.columns().size());
        assertEquals("INT", table.columns().get(0).type());
        assertEquals("VARCHAR(100)", table.columns().get(1).type());
    }


    @Test
    public void shouldParseCreateZtableCompositePrimaryKey()
    {
        String sql = "CREATE ZTABLE test (id INT, name VARCHAR(100), PRIMARY KEY (id, name));";
        CreateZtable table = parser.parseCreateTable(sql);

        assertEquals(2, table.primaryKeys().size());
        assertTrue(table.primaryKeys().contains("id"));
        assertTrue(table.primaryKeys().contains("name"));
    }

    @Test
    public void shouldHandleEmptyCreateZtable()
    {
        String sql = "CREATE ZTABLE test ();";
        CreateZtable createZtable = parser.parseCreateTable(sql);

        assertEquals(0, createZtable.columns().size());
        assertEquals(0, createZtable.primaryKeys().size());
    }

    @Test
    public void shouldHandleEmptySql()
    {
        String sql = "";
        CreateZtable createZtable = parser.parseCreateTable(sql);

        assertNotNull(createZtable);
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

    @Test
    public void shouldHandleEmptyCreateZView()
    {
        String sql = "CREATE ZVIEW test_view AS ;";
        CreateZview createZview = parser.parseCreateZView(sql);

        assertNotNull(createZview);
        assertNull(createZview.name());
    }

    @Test
    public void shouldHandleInvalidCreateZView()
    {
        String sql = "CREATE ZVIEW test_view";
        CreateZview createZview = parser.parseCreateZView(sql);

        assertNull(createZview.name());
    }

    @Test
    public void shouldHandleInvalidZCreateZtable()
    {
        String sql = "CREATE ZTABLE test";
        CreateZtable createZtable = parser.parseCreateTable(sql);

        assertNull(createZtable.name());
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

    @Test
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
    public void shouldCreateFunctionWithLanguage()
    {
        String sql = "CREATE FUNCTION test_function(int) RETURNS TABLE (x INT) LANGUAGE python AS 'test_function';";
        CreateFunction function = parser.parseCreateFunction(sql);

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
        CreateFunction function = parser.parseCreateFunction(sql);

        assertNotNull(function);
        assertEquals("test_function", function.name());
        assertEquals("struct<key varchar, value varchar>", function.returnType());
        assertEquals("python", function.language());
    }

    @Test
    public void shouldHandleInvalidCreateFunction()
    {
        String sql = "CREATE FUNCTION test_function()";
        CreateFunction function = parser.parseCreateFunction(sql);

        assertNull(function.name());
    }

    @Test
    public void shouldCreateZtableWithUniqueConstraint()
    {
        String sql = "CREATE ZTABLE test (id INT UNIQUE, name VARCHAR(100));";
        CreateZtable createZtable = parser.parseCreateTable(sql);

        assertNotNull(createZtable);
        assertEquals(2, createZtable.columns().size());
        assertEquals("id", createZtable.columns().get(0).name());
        assertEquals("name", createZtable.columns().get(1).name());
    }

    @Test
    public void shouldParseCreateZtableWithCheckConstraint()
    {
        String sql = "CREATE ZTABLE test (id INT, name VARCHAR(100), CHECK (id > 0));";
        CreateZtable createZtable = parser.parseCreateTable(sql);

        assertNotNull(createZtable);
        assertEquals(2, createZtable.columns().size());
        assertEquals("id", createZtable.columns().get(0).name());
        assertEquals("name", createZtable.columns().get(1).name());
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
        String sql = "CREATE ZTABLE test (id INT DEFAULT 0, name VARCHAR(100));";
        CreateZtable createZtable = parser.parseCreateTable(sql);

        assertNotNull(createZtable);
        assertEquals(2, createZtable.columns().size());
        assertEquals("INT", createZtable.columns().get(0).type());
        assertEquals("VARCHAR(100)", createZtable.columns().get(1).type());
    }

    @Test
    public void shouldCreateZtableWithNotNullConstraint()
    {
        String sql = "CREATE ZTABLE test (id INT NOT NULL, name VARCHAR(100) NOT NULL);";
        CreateZtable createZtable = parser.parseCreateTable(sql);

        assertNotNull(createZtable);
        assertEquals(2, createZtable.columns().size());
        assertEquals("id", createZtable.columns().get(0).name());
        assertEquals("name", createZtable.columns().get(1).name());
    }

    @Test
    public void shouldCreateZtableWithMultipleConstraints()
    {
        String sql = "CREATE ZTABLE test (id INT PRIMARY KEY, name VARCHAR(100) UNIQUE, age INT CHECK (age > 0));";
        CreateZtable createZtable = parser.parseCreateTable(sql);

        assertNotNull(createZtable);
        assertEquals(3, createZtable.columns().size());
        assertTrue(createZtable.primaryKeys().contains("id"));
        assertEquals("name", createZtable.columns().get(1).name());
        assertEquals("age", createZtable.columns().get(2).name());
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

    @Test
    public void shouldHandleInvalidAlterZtable()
    {
        String sql = "ALTER ZTABLE";
        Alter alter = parser.parseAlterTable(sql);

        assertNull(alter.name());
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

    @Test
    public void shouldParseCreateZfunctionWithTableReturnType()
    {
        String sql = """
            CREATE ZFUNCTION send_payment_handler(
               user_id VARCHAR,
               request_id VARCHAR,
               amount DOUBLE PRECISION,
               notes VARCHAR)
            RETURNS TABLE(
               event VARCHAR,
               user_id VARCHAR,
               request_id VARCHAR,
               amount DOUBLE PRECISION,
               notes VARCHAR)
            LANGUAGE SQL AS $$
               SELECT
                  CASE
                    WHEN balance >= args.amount THEN 'PaymentSent'
                    ELSE 'PaymentDeclined'
                  END AS event,
                 args.user_id,
                 args.request_id,
                 args.amount,
                 args.notes
               FROM balance b WHERE b.user_id = args.user_id;
            $$
            WITH(
                EVENTS = 'app_events'
            );
            """;
        CreateZfunction function = parser.parseCreateZfunction(sql);
        assertNotNull(function);

        assertEquals("send_payment_handler", function.name());
        assertEquals(4, function.arguments().size());
        assertEquals("user_id", function.arguments().get(0).name());
        assertEquals("VARCHAR", function.arguments().get(0).type());
        assertEquals("request_id", function.arguments().get(1).name());
        assertEquals("VARCHAR", function.arguments().get(1).type());

        assertEquals(5, function.returnTypes().size());
        assertEquals("event", function.returnTypes().get(0).name());
        assertEquals("VARCHAR", function.returnTypes().get(0).type());
        assertEquals("user_id", function.returnTypes().get(1).name());
        assertEquals("VARCHAR", function.returnTypes().get(1).type());
        assertEquals("request_id", function.returnTypes().get(2).name());
        assertEquals("VARCHAR", function.returnTypes().get(2).type());

        Select select = function.select();
        assertNotNull(select);
        assertEquals(5, select.columns().size());
        assertEquals("CASE\n" +
            "        WHEN balance >= args.amount THEN 'PaymentSent'\n" +
            "        ELSE 'PaymentDeclined'\n" +
            "      END AS event", select.columns().get(0));
        assertEquals("args.user_id", select.columns().get(1));
        assertEquals("args.request_id", select.columns().get(2));
        assertEquals("balance b", select.from());
        assertEquals("b.user_id = args.user_id", select.whereClause());

        assertEquals("app_events", function.events());
    }

    @Test
    public void shouldDropSingleZfunction()
    {
        String sql = "DROP ZFUNCTION test_function;";

        List<Drop> drops = parser.parseDrop(sql);

        assertEquals(1, drops.size());
        assertEquals("public", drops.get(0).schema());
        assertEquals("test_function", drops.get(0).name());
    }
}
