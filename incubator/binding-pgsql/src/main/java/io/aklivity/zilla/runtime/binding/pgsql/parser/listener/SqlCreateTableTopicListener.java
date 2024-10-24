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
import java.util.Set;

import org.agrona.collections.ObjectHashSet;
import org.antlr.v4.runtime.TokenStream;

import io.aklivity.zilla.runtime.binding.pgsql.parser.PostgreSqlParser;
import io.aklivity.zilla.runtime.binding.pgsql.parser.PostgreSqlParserBaseListener;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Table;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.TableColumn;

public class SqlCreateTableTopicListener extends PostgreSqlParserBaseListener
{
    private final List<TableColumn> columns = new ArrayList<>();
    private final Set<String> primaryKeys = new ObjectHashSet<>();
    private final TokenStream tokens;

    private String name;

    public SqlCreateTableTopicListener(
        TokenStream tokens)
    {
        this.tokens = tokens;
    }

    public Table table()
    {
        return new Table(name, columns, primaryKeys);
    }

    @Override
    public void enterRoot(
        PostgreSqlParser.RootContext ctx)
    {
        name = null;
        columns.clear();
        primaryKeys.clear();
    }

    @Override
    public void enterQualified_name(
        PostgreSqlParser.Qualified_nameContext ctx)
    {
        name = ctx.getText();
    }

    @Override
    public void enterCreatestmt(
        PostgreSqlParser.CreatestmtContext ctx)
    {
        if (ctx.opttableelementlist().tableelementlist() != null)
        {
            for (PostgreSqlParser.TableelementContext tableElement :
                ctx.opttableelementlist().tableelementlist().tableelement())
            {
                if (tableElement.columnDef() != null)
                {
                    addColumn(tableElement.columnDef());
                }
                else if (tableElement.tableconstraint() != null)
                {
                    addPrimaryKey(tableElement.tableconstraint());
                }
            }
        }
    }

    private void addColumn(
        PostgreSqlParser.ColumnDefContext columnDef)
    {
        List<String> constraints = new ArrayList<>();
        String columnName = columnDef.colid().getText();
        String dataType = tokens.getText(columnDef.typename());

        for (PostgreSqlParser.ColconstraintContext constraint :
            columnDef.colquallist().colconstraint())
        {
            if (constraint.colconstraintelem().PRIMARY() != null &&
                constraint.colconstraintelem().KEY() != null)
            {
                primaryKeys.add(columnName);
            }
            else
            {
                constraints.add(tokens.getText(constraint.colconstraintelem()));
            }
        }
        columns.add(new TableColumn(columnName, dataType, constraints));
    }

    private void addPrimaryKey(
        PostgreSqlParser.TableconstraintContext tableConstraint)
    {
        if (tableConstraint.constraintelem().PRIMARY() != null &&
            tableConstraint.constraintelem().KEY() != null)
        {
            tableConstraint.constraintelem().columnlist().columnElem().forEach(
                column -> primaryKeys.add(column.getText()));
        }
    }
}
