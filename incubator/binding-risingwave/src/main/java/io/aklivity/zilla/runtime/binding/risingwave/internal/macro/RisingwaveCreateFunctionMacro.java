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
package io.aklivity.zilla.runtime.binding.risingwave.internal.macro;

import java.util.List;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateFunction;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.FunctionArgument;
import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveUdfConfig;
import io.aklivity.zilla.runtime.binding.risingwave.internal.stream.RisingwaveCompletionCommand;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;

public class RisingwaveCreateFunctionMacro extends RisingwaveMacroBase
{
    private final String javaServer;
    private final String pythonServer;

    private final String systemSchema;
    private final String user;
    private final String sql;
    private final CreateFunction command;
    private final StringBuilder fieldBuilder;

    public RisingwaveCreateFunctionMacro(
        List<RisingwaveUdfConfig> udfs,
        String systemSchema,
        String user,
        String sql,
        CreateFunction command,
        RisingwaveMacroHandler handler)
    {
        super(sql, handler);

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
        this.systemSchema = systemSchema;
        this.user = user;
        this.sql = sql;
        this.command = command;
        this.fieldBuilder = new StringBuilder();
    }


    public RisingwaveMacroState start()
    {
        return new CreateFunctionState();
    }

    private final class CreateFunctionState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            CREATE FUNCTION %s(%s)
            RETURNS %s
            AS %s
            LANGUAGE %s
            USING LINK '%s';\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String functionName = command.name();
            String asFunction = command.asFunction();
            List<FunctionArgument> arguments = command.arguments();
            List<FunctionArgument> tables = command.tables();

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

            String language = command.language() != null ? command.language() : "java";
            String server = "python".equalsIgnoreCase(language) ? pythonServer : javaServer;

            String returnType = command.returnType();
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

            String sqlQuery = sqlFormat.formatted(functionName, funcArguments, returnType, asFunction, language, server);

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onCompletion(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doCompletion(traceId, authorization, RisingwaveCompletionCommand.CREATE_FUNCTION_COMMAND);
            return this;
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doReady(traceId, authorization, sql.length());
            return null;
        }

        @Override
        public RisingwaveMacroState onError(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doFlushProxy(traceId, authorization, flushEx);
            return this;
        }
    }
}
