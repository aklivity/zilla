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
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateZfunction;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.FunctionArgument;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Select;

public class SqlCreateZfunctionListener extends PostgreSqlParserBaseListener
{
    private static final String PUBLIC_SCHEMA_NAME = "public";
    private static final String SCHEMA_PATTERN = "\\.";

    private final List<FunctionArgument> returnTypes;
    private final List<FunctionArgument> arguments;
    private final TokenStream tokens;

    private final List<String> columns;

    private String from;
    private String whereClause;
    private String groupByClause;
    private String orderByClause;
    private String limitClause;
    private String offsetClause;
    private String events;

    private String schema;
    private String name;

    public SqlCreateZfunctionListener(
        TokenStream tokens)
    {
        this.returnTypes = new ArrayList<>();
        this.arguments = new ArrayList<>();
        this.columns = new ArrayList<>();

        this.tokens = tokens;
    }

    public CreateZfunction zfunction()
    {
        final Select select = new Select(
            columns,
            from,
            whereClause,
            groupByClause,
            orderByClause,
            limitClause,
            offsetClause);

        return new CreateZfunction(
            schema,
            name,
            arguments,
            returnTypes,
            select,
            events);
    }

    @Override
    public void enterRoot(
        PostgreSqlParser.RootContext ctx)
    {
        schema = null;
        name = null;
        arguments.clear();
        returnTypes.clear();

        columns.clear();
        from = null;
        whereClause = null;
        groupByClause = null;
        orderByClause = null;
        limitClause = null;
        offsetClause = null;
    }

    @Override
    public void enterCreatezfunctionstmt(
        PostgreSqlParser.CreatezfunctionstmtContext ctx)
    {
        String text = ctx.func_name().getText().replace("\"", "");
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
        String argType = tokens.getText(ctx.func_type());
        returnTypes.add(new FunctionArgument(argName, argType));
    }

    @Override
    public void enterTarget_list(
        PostgreSqlParser.Target_listContext ctx)
    {
        ctx.target_el().forEach(c -> columns.add(tokens.getText(c)));
    }

    @Override
    public void enterFrom_clause(
        PostgreSqlParser.From_clauseContext ctx)
    {
        if (ctx.from_list() != null)
        {
            from = tokens.getText(ctx.from_list());
        }
    }

    @Override
    public void enterWhere_clause(
        PostgreSqlParser.Where_clauseContext ctx)
    {
        whereClause = tokens.getText(ctx).replaceAll("(?i)\\bwhere\\s", "");
    }

    @Override
    public void enterGroup_clause(
        PostgreSqlParser.Group_clauseContext ctx)
    {
        groupByClause = tokens.getText(ctx);
    }

    @Override
    public void enterSort_clause(
        PostgreSqlParser.Sort_clauseContext ctx)
    {
        orderByClause = tokens.getText(ctx);
    }

    @Override
    public void enterLimit_clause(
        PostgreSqlParser.Limit_clauseContext ctx)
    {
        limitClause = tokens.getText(ctx);
    }

    @Override
    public void enterOffset_clause(
        PostgreSqlParser.Offset_clauseContext ctx)
    {
        offsetClause = tokens.getText(ctx);
    }

    @Override
    public void enterZfunc_with_option(
        PostgreSqlParser.Zfunc_with_optionContext ctx)
    {
        if (ctx.EVENTS() != null)
        {
            events = tokens.getText(ctx.sconst());

            if (events != null && events.length() >= 2)
            {
                events = events.substring(1, events.length() - 1);
            }
        }
    }
}
