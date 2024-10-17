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

import io.aklivity.zilla.runtime.binding.pgsql.parser.module.FunctionInfo;
import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveUdfConfig;

public class RisingwaveCreateFunctionTemplate extends RisingwaveCommandTemplate
{
    private static final List<String> KEYWORDS = List.of("LANGUAGE", "AS", "USING");
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
        FunctionInfo functionInfo)
    {
        String functionName = functionInfo.name();

        List<String> parameters = functionInfo.arguments().stream()
            .map(arg -> arg.name() != null ? "%s %s".formatted(arg.name(), arg.type()) : arg.type())
            .toList();

        String language = functionInfo.language();
        String server = "python".equalsIgnoreCase(language) ? pythonServer : javaServer;
        String returnType = functionInfo.returnType();

        return sqlFormat.formatted(functionName, parameters, returnType, "", language, server);
    }

    private int findNextKeywordIndex(
        List<String> parts,
        int startIndex)
    {
        int endIndex = -1;
        for (int index = startIndex; index < parts.size(); index++)
        {
            if (KEYWORDS.contains(parts.get(index).toUpperCase()))
            {
                endIndex = index;
                break;
            }
        }
        return endIndex;
    }
}
