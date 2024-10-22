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

public class SqlCommandListener extends PostgreSqlParserBaseListener
{
    private String command = "";

    private final TokenStream tokens;

    public SqlCommandListener(
        TokenStream tokens)
    {
        this.tokens = tokens;
    }

    public String command()
    {
        return command;
    }

    @Override
    public void enterRoot(
        PostgreSqlParser.RootContext ctx)
    {
        command = "";
    }

    @Override
    public void enterCreatestmt(
        PostgreSqlParser.CreatestmtContext ctx)
    {
        command = "CREATE %s".formatted(ctx.opttable_type().getText());
    }

    @Override
    public void enterCreatestreamstmt(
        PostgreSqlParser.CreatestreamstmtContext ctx)
    {
        command = "CREATE STREAM";
    }

    @Override
    public void enterCreatematviewstmt(
        PostgreSqlParser.CreatematviewstmtContext ctx)
    {
        command = "CREATE MATERIALIZED VIEW";
    }

    @Override
    public void enterCreatefunctionstmt(
        PostgreSqlParser.CreatefunctionstmtContext ctx)
    {
        String functionBody = ctx.getText();

        if (!functionBody.contains("$$"))
        {
            command = "CREATE FUNCTION";
        }
    }

    @Override
    public void enterDropstmt(
        PostgreSqlParser.DropstmtContext ctx)
    {
        command = "DROP %s".formatted(tokens.getText(ctx.object_type_any_name()));
    }
}
