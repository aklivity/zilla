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

import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Function;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.FunctionArgument;
import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveUdfConfig;

public class RisingwaveCreateFunctionTemplateTest
{
    private static RisingwaveCreateFunctionTemplate template;

    @BeforeClass
    public static void setUp()
    {
        template = new RisingwaveCreateFunctionTemplate(List.of(
            RisingwaveUdfConfig.builder().server("http://localhost:8815").language("java").build(),
            RisingwaveUdfConfig.builder().server("http://localhost:8816").language("python").build()));
    }

    @Test
    public void shouldGenerateFunctionWithValidFunctionInfo()
    {
        Function function = new Function(
            "test_function",
            List.of(new FunctionArgument("arg1", "INT")),
            "INT",
            List.of(),
            "test_function",
            "java");
        String expectedSQL = """
            CREATE FUNCTION test_function(arg1 INT)
            RETURNS INT
            AS test_function
            LANGUAGE java
            USING LINK 'http://localhost:8815';\u0000""";

        String actualSQL = template.generate(function);

        assertEquals(expectedSQL, actualSQL);
    }

    @Test
    public void shouldGenerateFunctionWithTableReturnType()
    {
        Function functionInfo = new Function(
            "test_function",
            List.of(new FunctionArgument("arg1", "INT")),
            "INT",
            List.of(new FunctionArgument("tab1", "INT")),
            "test_function",
            "java");

        String expectedSQL = """
            CREATE FUNCTION test_function(arg1 INT)
            RETURNS TABLE (tab1 INT)
            AS test_function
            LANGUAGE java
            USING LINK 'http://localhost:8815';\u0000""";

        String actualSQL = template.generate(functionInfo);

        assertEquals(expectedSQL, actualSQL);
    }

    @Test
    public void shouldGenerateFunctionWithTableAsReturnType()
    {
        Function functionInfo = new Function(
            "test_function",
            List.of(new FunctionArgument("arg1", "INT")),
            "INT",
            List.of(),
            "test_function",
            "java");

        String expectedSQL = """
            CREATE FUNCTION test_function(arg1 INT)
            RETURNS INT
            AS test_function
            LANGUAGE java
            USING LINK 'http://localhost:8815';\u0000""";

        String actualSQL = template.generate(functionInfo);

        assertEquals(expectedSQL, actualSQL);
    }

    @Test
    public void shouldGenerateFunctionWithMultipleArguments()
    {
        Function function = new Function(
            "test_function",
            List.of(new FunctionArgument("arg1", "INT"), new FunctionArgument("arg2", "STRING")),
            "STRING",
            List.of(),
            "test_function",
            "python");

        String expectedSQL = """
            CREATE FUNCTION test_function(arg1 INT, arg2 STRING)
            RETURNS STRING
            AS test_function
            LANGUAGE python
            USING LINK 'http://localhost:8816';\u0000""";

        String actualSQL = template.generate(function);

        assertEquals(expectedSQL, actualSQL);
    }

    @Test
    public void shouldGenerateFunctionWithUnnamedArguments()
    {
        Function function = new Function(
            "test_function",
            List.of(new FunctionArgument(null, "INT"), new FunctionArgument(null, "STRING")),
            "STRING",
            List.of(),
            "test_function",
            "java");

        String expectedSQL = """
            CREATE FUNCTION test_function(INT, STRING)
            RETURNS STRING
            AS test_function
            LANGUAGE java
            USING LINK 'http://localhost:8815';\u0000""";

        String actualSQL = template.generate(function);

        assertEquals(expectedSQL, actualSQL);
    }

    @Test
    public void shouldGenerateFunctionWithEmptyArguments()
    {
        Function function = new Function(
            "test_function",
            List.of(),
            "VOID",
            List.of(),
            "test_function",
            "python");

        String expectedSQL = """
            CREATE FUNCTION test_function()
            RETURNS VOID
            AS test_function
            LANGUAGE python
            USING LINK 'http://localhost:8816';\u0000""";

        String actualSQL = template.generate(function);

        assertEquals(expectedSQL, actualSQL);
    }

}
