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

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.pgsql.parser.listener.SqlTableCommandListener;

public class PgsqlTableParserTest
{
    private PgsqlParser parser;

    @Before
    public void setUp()
    {
        parser = new PgsqlParser();
    }

    @Test
    public void shouldParseWithPrimaryKeySQL()
    {
        String sql = "CREATE TABLE test (id INT PRIMARY KEY, name VARCHAR(100));";
        SqlTableCommandListener.TableInfo tableInfo = parser.parseTable(sql);
        assertNotNull(tableInfo);
        assertTrue(tableInfo.primaryKeys().contains("id"));
    }

    @Test
    public void shouldParseWithPrimaryKeysSQL()
    {
        String sql = """
            CREATE TABLE example_table (
                id INT PRIMARY KEY,
                name VARCHAR(100),
                age INT,
                PRIMARY KEY (id, name)
            );""";
        SqlTableCommandListener.TableInfo command = parser.parseTable(sql);
        assertNotNull(command);
        assertEquals(2, command.primaryKeys().size());
        assertEquals(3, command.columns().size());
        assertTrue(command.primaryKeys().contains("id"));
        assertTrue(command.primaryKeys().contains("name"));
    }

    @Test
    public void shouldHandleEmptySQL()
    {
        String sql = "";
        SqlTableCommandListener.TableInfo command = parser.parseTable(sql);
        assertNotNull(command);
    }

    @Test
    public void shouldHandleInvalidSQL()
    {
        String sql = "INVALID SQL";
        try
        {
            parser.parseTable(sql);
            assertTrue("Expected an exception to be thrown", false);
        }
        catch (Exception e)
        {
            assertTrue(true);
        }
    }
}
