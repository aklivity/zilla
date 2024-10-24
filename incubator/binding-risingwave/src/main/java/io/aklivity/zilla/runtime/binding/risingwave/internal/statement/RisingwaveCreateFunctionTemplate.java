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

import java.util.List;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Function;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.FunctionArgument;
import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveUdfConfig;

public class RisingwaveCreateFunctionTemplate extends RisingwaveCommandTemplate
{
    private final String sqlFormat = """
        CREATE FUNCTION %s(%s)
        RETURNS %s
        AS %s
        LANGUAGE %s
        USING LINK '%s';\u0000""";

    private final String javaServer;
    private final String pythonServer;

    public RisingwaveCreateFunctionTemplate(
        List<RisingwaveUdfConfig> udfs)
    {
        String javaServer = null;
        String pythonServer = null;

        if (udfs != null && !udfs.isEmpty())
        {
            for (RisingwaveUdfConfig udf : udfs)
            {
                if (udf.language.equalsIgnoreCase("java"))
                {
                    javaServer = udf.server;
                }
                else if (udf.language.equalsIgnoreCase("python"))
                {
                    pythonServer = udf.server;
                }
            }
        }

        this.javaServer = javaServer;
        this.pythonServer = pythonServer;
    }

    public String generate(
        Function function)
    {
        String functionName = function.name();
        String asFunction = function.asFunction();
        List<FunctionArgument> arguments = function.arguments();
        List<FunctionArgument> tables = function.tables();

        fieldBuilder.setLength(0);

        arguments
            .forEach(arg -> fieldBuilder.append(
                arg.name() != null
                    ? "%s %s, ".formatted(arg.name(), arg.type())
                    : "%s, ".formatted(arg.type())));

        if (!arguments.isEmpty())
        {
            fieldBuilder.delete(fieldBuilder.length() - 2, fieldBuilder.length());
        }
        String funcArguments = fieldBuilder.toString();

        String language = function.language() != null ? function.language() : "java";
        String server = "python".equalsIgnoreCase(language) ? pythonServer : javaServer;

        String returnType = function.returnType();
        if (!tables.isEmpty())
        {
            fieldBuilder.setLength(0);
            fieldBuilder.append("TABLE (");
            tables.forEach(arg -> fieldBuilder.append(
                arg.name() != null
                    ? "%s %s, ".formatted(arg.name(), arg.type())
                    : "%s, ".formatted(arg.type())));
            fieldBuilder.delete(fieldBuilder.length() - 2, fieldBuilder.length());
            fieldBuilder.append(")");

            returnType = fieldBuilder.toString();
        }

        return sqlFormat.formatted(functionName, funcArguments, returnType, asFunction, language, server);
    }
}
