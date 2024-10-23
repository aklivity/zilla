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
package io.aklivity.zilla.runtime.binding.pgsql.parser.listener;

import org.antlr.v4.runtime.TokenStream;

import io.aklivity.zilla.runtime.binding.pgsql.parser.PostgreSqlParser;
import io.aklivity.zilla.runtime.binding.pgsql.parser.PostgreSqlParserBaseListener;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.View;

public class SqlCreateMaterializedViewListener extends PostgreSqlParserBaseListener
{
    private final TokenStream tokens;

    private String name;
    private String select;

    public SqlCreateMaterializedViewListener(
        TokenStream tokens)
    {
        this.tokens = tokens;
    }

    public View view()
    {
        return new View(name, select);
    }

    @Override
    public void enterRoot(
        PostgreSqlParser.RootContext ctx)
    {
        name = null;
        select = null;
    }

    @Override
    public void enterCreatematviewstmt(
        PostgreSqlParser.CreatematviewstmtContext ctx)
    {
        name = ctx.create_mv_target().qualified_name().getText();
    }

    @Override
    public void enterSelectstmt(
        PostgreSqlParser.SelectstmtContext ctx)
    {
        select = tokens.getText(ctx);
    }
}
