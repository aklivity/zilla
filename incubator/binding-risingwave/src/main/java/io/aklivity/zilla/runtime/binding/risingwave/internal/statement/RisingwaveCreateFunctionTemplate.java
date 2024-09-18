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
    private final String sqlFormat = """
        CREATE FUNCTION %s(%s)
        RETURNS %s
        AS %s
        LANGUAGE %s
        USING LINK '%s';\u0000""";

    private final RisingwaveUdfConfig udf;

    public RisingwaveCreateFunctionTemplate(
        RisingwaveUdfConfig udf)
    {
        this.udf = udf;
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

        if ("TABLE".equalsIgnoreCase(returnType))
        {
            int tableStartIndex = -1;
            int tableEndIndex = -1;
            for (int i = returnsIndex; i < parts.size(); i++)
            {
                if (parts.get(i).equals("("))
                {
                    tableStartIndex = i;
                }
                else if (parts.get(i).equals(")"))
                {
                    tableEndIndex = i;
                    break;
                }
            }

            if (tableStartIndex >= 0 && tableEndIndex > tableStartIndex)
            {
                String tableDefinition = String.join(" ", parts.subList(tableStartIndex + 1, tableEndIndex));
                returnType += " (" + tableDefinition + ")";
            }
        }

        int asIndex = parts.indexOf("AS");
        String body = asIndex >= 0 ? parts.get(asIndex + 1) : "";

        return sqlFormat.formatted(functionName, parameters, returnType, body, udf.language, udf.server);
    }
}
