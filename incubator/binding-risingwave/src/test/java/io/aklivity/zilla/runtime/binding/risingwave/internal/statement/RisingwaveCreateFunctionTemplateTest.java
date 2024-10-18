package io.aklivity.zilla.runtime.binding.risingwave.internal.statement;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.pgsql.parser.module.FunctionArgument;
import io.aklivity.zilla.runtime.binding.pgsql.parser.module.FunctionInfo;
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
        FunctionInfo functionInfo = new FunctionInfo(
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
    public void shouldGenerateFunctionWithTableReturnType()
    {
        FunctionInfo functionInfo = new FunctionInfo(
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
        FunctionInfo functionInfo = new FunctionInfo(
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
        FunctionInfo functionInfo = new FunctionInfo(
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

        String actualSQL = template.generate(functionInfo);

        assertEquals(expectedSQL, actualSQL);
    }

    @Test
    public void shouldGenerateFunctionWithUnnamedArguments()
    {
        FunctionInfo functionInfo = new FunctionInfo(
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

        String actualSQL = template.generate(functionInfo);

        assertEquals(expectedSQL, actualSQL);
    }

    @Test
    public void shouldGenerateFunctionWithEmptyArguments()
    {
        FunctionInfo functionInfo = new FunctionInfo(
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

        String actualSQL = template.generate(functionInfo);

        assertEquals(expectedSQL, actualSQL);
    }

}
