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
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.View;

public class SqlCreateZviewListener extends PostgreSqlParserBaseListener
{
    private static final String PUBLIC_SCHEMA_NAME = "public";
    private static final String SCHEMA_PATTERN = "\\.";

    private final TokenStream tokens;

    private String schema;
    private String name;
    private String select;

    public SqlCreateZviewListener(
        TokenStream tokens)
    {
        this.tokens = tokens;
    }

    public View view()
    {
        return new View(schema, name, select);
    }

    @Override
    public void enterRoot(
        PostgreSqlParser.RootContext ctx)
    {
        schema = null;
        name = null;
        select = null;
    }

    @Override
    public void enterCreatezviewstmt(
        PostgreSqlParser.CreatezviewstmtContext ctx)
    {
        String text = ctx.create_mv_target().qualified_name().getText();
        String[] split = text.split(SCHEMA_PATTERN);
        schema = split.length > 1 ? split[0] : PUBLIC_SCHEMA_NAME;
        name = split.length > 1 ? split[1] : text;
    }

    @Override
    public void enterSelectstmt(
        PostgreSqlParser.SelectstmtContext ctx)
    {
        select = tokens.getText(ctx);
    }
}
