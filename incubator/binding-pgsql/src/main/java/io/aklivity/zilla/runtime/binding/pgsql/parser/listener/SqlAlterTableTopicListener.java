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
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Alter;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.AlterExpression;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Operation;

public class SqlAlterTableTopicListener extends PostgreSqlParserBaseListener
{
    private final TokenStream tokens;

    private String name;
    private final List<AlterExpression> alterExpressions = new ArrayList<>();

    public SqlAlterTableTopicListener(
        TokenStream tokens)
    {
        this.tokens = tokens;
    }

    public Alter alter()
    {
        return new Alter(name, alterExpressions);
    }

    @Override
    public void enterRoot(
        PostgreSqlParser.RootContext ctx)
    {
        name = null;
        alterExpressions.clear();
    }

    @Override
    public void enterQualified_name(
        PostgreSqlParser.Qualified_nameContext ctx)
    {
        name = ctx.getText();
    }

    @Override
    public void enterAlter_table_cmds(
        PostgreSqlParser.Alter_table_cmdsContext ctx)
    {
        for (PostgreSqlParser.Alter_table_cmdContext alterTableCmdCtx : ctx.alter_table_cmd())
        {
            if (alterTableCmdCtx.ADD_P() != null)
            {
                alterExpressions.add(new AlterExpression(
                    Operation.ADD,
                    alterTableCmdCtx.columnDef().colid().getText(),
                    alterTableCmdCtx.columnDef().typename().getText()
                ));
            }
            else if (alterTableCmdCtx.DROP() != null)
            {
                alterTableCmdCtx.colid().forEach(colidCtxs -> alterExpressions.add(
                    new AlterExpression(
                        Operation.DROP,
                        colidCtxs.identifier().getText(),
                        null
                )));
            }
            else if (alterTableCmdCtx.ALTER() != null)
            {
                alterTableCmdCtx.colid().forEach(colidCtxs -> alterExpressions.add(
                    new AlterExpression(
                        Operation.MODIFY,
                        colidCtxs.getText(),
                        alterTableCmdCtx.typename() != null ? alterTableCmdCtx.typename().getText() : null
                    )));
            }
        }
    }

}
