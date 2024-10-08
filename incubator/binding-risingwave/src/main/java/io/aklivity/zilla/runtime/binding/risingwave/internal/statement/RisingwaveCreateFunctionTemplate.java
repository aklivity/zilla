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

import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveUdfConfig;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.function.CreateFunction;

public class RisingwaveCreateFunctionTemplate extends RisingwaveCommandTemplate
{
    private static final List<String> KEYWORDS = List.of("LANGUAGE", "AS", "USING");
    private final String sqlFormat = """
        CREATE FUNCTION %s(%s)
        RETURNS %s
        AS %s
        LANGUAGE %s
        USING LINK '%s';\u0000""";

    private final String java;
    private final String python;

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

        this.java = javaServer;
        this.python = pythonServer;
    }

    public String generate(
        Statement statement)
    {
        CreateFunction createFunction = (CreateFunction) statement;
        List<String> parts = createFunction.getFunctionDeclarationParts();

        String functionName = parts.get(0);

        int paramStartIndex = parts.indexOf("(");
        int paramEndIndex = parts.indexOf(")");
        String parameters = paramStartIndex >= 0 && paramEndIndex > paramStartIndex
            ? String.join(" ", parts.subList(paramStartIndex + 1, paramEndIndex))
            : "";

        int returnsIndex = parts.indexOf("RETURNS");
        String returnType = returnsIndex >= 0 ? parts.get(returnsIndex + 1) : "";

        if (returnsIndex >= 0)
        {
            int nextKeywordIndex = findNextKeywordIndex(parts, returnsIndex + 1);
            returnType = nextKeywordIndex >= 0
                ? String.join(" ", parts.subList(returnsIndex + 1, nextKeywordIndex))
                : String.join(" ", parts.subList(returnsIndex + 1, parts.size()));
        }

        int asIndex = parts.indexOf("AS");
        String body = asIndex >= 0 ? parts.get(asIndex + 1) : "";

        int langIndex = parts.indexOf("LANGUAGE");
        String language = langIndex >= 0 ? parts.get(langIndex + 1) : "java";

        return sqlFormat.formatted(functionName, parameters, returnType.trim(), body, language,
            "python".equalsIgnoreCase(language) ? python : java);
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
