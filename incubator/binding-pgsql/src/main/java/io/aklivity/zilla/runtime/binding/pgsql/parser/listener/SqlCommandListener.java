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
    public void enterCreateztstmt(
        PostgreSqlParser.CreateztstmtContext ctx)
    {
        command = "CREATE %s".formatted(ctx.optztable_type().getText().toUpperCase());
    }

    @Override
    public void enterAltertablestmt(
        PostgreSqlParser.AltertablestmtContext ctx)
    {
        if (ctx.ALTER() != null && ctx.ZTABLE() != null)
        {
            command = "ALTER ZTABLE";
        }
        else if (ctx.ALTER() != null && ctx.TOPIC() != null)
        {
            command = "ALTER TOPIC";
        }
    }

    @Override
    public void enterCreatezviewstmt(
        PostgreSqlParser.CreatezviewstmtContext ctx)
    {
        command = "CREATE ZVIEW";
    }

    @Override
    public void enterShowstmt(
        PostgreSqlParser.ShowstmtContext ctx)
    {
        String type = tokens.getText(ctx.show_object_type_name());
        command = "SHOW %s".formatted(type.toUpperCase());
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
    public void enterCreatezfunctionstmt(
        PostgreSqlParser.CreatezfunctionstmtContext ctx)
    {
        command = "CREATE ZFUNCTION";
    }

    @Override
    public void enterDropstmt(
        PostgreSqlParser.DropstmtContext ctx)
    {
        command = "DROP %s".formatted(tokens.getText(ctx.object_type_any_name()).toUpperCase());
    }
}
