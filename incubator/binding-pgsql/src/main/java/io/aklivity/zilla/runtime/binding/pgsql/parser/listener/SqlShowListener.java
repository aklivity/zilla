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
package io.aklivity.zilla.runtime.binding.pgsql.parser.listener;

import org.antlr.v4.runtime.TokenStream;

import io.aklivity.zilla.runtime.binding.pgsql.parser.PostgreSqlParser;
import io.aklivity.zilla.runtime.binding.pgsql.parser.PostgreSqlParserBaseListener;

public class SqlShowListener extends PostgreSqlParserBaseListener
{
    private final TokenStream tokens;

    private String type;

    public SqlShowListener(
        TokenStream tokens)
    {
        this.tokens = tokens;
    }

    public String type()
    {
        return type;
    }

    @Override
    public void enterRoot(
        PostgreSqlParser.RootContext ctx)
    {
        this.type = null;
    }

    @Override
    public void enterShowstmt(
        PostgreSqlParser.ShowstmtContext ctx)
    {
        this.type = tokens.getText(ctx.show_object_type_name());
    }
}
