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

import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.function.CreateFunction;

public class RisingwaveCreateFunctionTemplate extends RisingwaveCommandTemplate
{
    private final String sqlFormat = """
        CREATE FUNCTION IF NOT EXISTS IF NOT EXISTS %s(%s)
        RETURNS %s
        LANGUAGE %s
        AS %s
        USING LINK '%s';
        \u0000""";

    private final String link;

    public RisingwaveCreateFunctionTemplate(
        String link)
    {
        this.link = link;
    }

    public String generate(
        Statement statement)
    {
        CreateFunction createFunction = (CreateFunction) statement;

        return null; //sqlFormat.formatted(name, parameters, returnType, lang, as, link);
    }
}
