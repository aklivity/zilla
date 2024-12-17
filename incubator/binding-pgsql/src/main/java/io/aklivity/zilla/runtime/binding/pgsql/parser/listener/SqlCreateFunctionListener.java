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

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.TokenStream;

import io.aklivity.zilla.runtime.binding.pgsql.parser.PostgreSqlParser;
import io.aklivity.zilla.runtime.binding.pgsql.parser.PostgreSqlParserBaseListener;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateFunction;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.FunctionArgument;

public class SqlCreateFunctionListener extends PostgreSqlParserBaseListener
{
    private static final String PUBLIC_SCHEMA_NAME = "public";
    private static final String SCHEMA_PATTERN = "\\.";

    private final List<FunctionArgument> tables;
    private final List<FunctionArgument> arguments;
    private final TokenStream tokens;

    private String schema;
    private String name;
    private String returnType;
    private String asFunction;
    private String language;

    public SqlCreateFunctionListener(
        TokenStream tokens)
    {
        this.tables = new ArrayList<>();
        this.arguments = new ArrayList<>();
        this.tokens = tokens;
    }

    public CreateFunction function()
    {
        return new CreateFunction(schema, name, arguments, returnType, tables, asFunction, language);
    }

    @Override
    public void enterRoot(
        PostgreSqlParser.RootContext ctx)
    {
        schema = null;
        name = null;
        returnType = null;
        asFunction = null;
        language = null;
        arguments.clear();
        tables.clear();
    }

    @Override
    public void enterCreatefunctionstmt(
        PostgreSqlParser.CreatefunctionstmtContext ctx)
    {
        String text = ctx.func_name().getText();
        String[] split = text.split(SCHEMA_PATTERN);
        schema = split.length > 1 ? split[0] : PUBLIC_SCHEMA_NAME;
        name = split.length > 1 ? split[1] : text;
    }

    @Override
    public void enterFunc_arg(
        PostgreSqlParser.Func_argContext ctx)
    {
        String argName = ctx.param_name() != null ? ctx.param_name().getText() : null;
        String argType = tokens.getText(ctx.func_type());
        arguments.add(new FunctionArgument(argName, argType));
    }

    @Override
    public void enterTable_func_column(
        PostgreSqlParser.Table_func_columnContext ctx)
    {
        String argName = ctx.param_name() != null ? ctx.param_name().getText() : null;
        String argType = ctx.func_type().getText();
        tables.add(new FunctionArgument(argName, argType));
    }

    @Override
    public void enterFunc_type(
        PostgreSqlParser.Func_typeContext ctx)
    {
        returnType = tokens.getText(ctx.typename());
    }

    @Override
    public void enterFunc_as(
        PostgreSqlParser.Func_asContext ctx)
    {
        asFunction = ctx.getText();
    }

    @Override
    public void enterNonreservedword_or_sconst(
        PostgreSqlParser.Nonreservedword_or_sconstContext ctx)
    {
        language = ctx.getText();
    }
}
