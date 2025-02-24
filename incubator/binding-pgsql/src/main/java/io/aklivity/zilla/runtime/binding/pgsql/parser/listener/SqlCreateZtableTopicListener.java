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
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateZtable;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.ZtableColumn;

public class SqlCreateZtableTopicListener extends PostgreSqlParserBaseListener
{
    private static final String PUBLIC_SCHEMA_NAME = "public";
    private static final String SCHEMA_PATTERN = "\\.";

    private final List<ZtableColumn> columns;
    private final Set<String> primaryKeys;
    private final TokenStream tokens;

    private String schema;
    private String name;

    public SqlCreateZtableTopicListener(
        TokenStream tokens)
    {
        this.primaryKeys = new ObjectHashSet<>();
        this.columns = new ArrayList<>();
        this.tokens = tokens;
    }

    public CreateZtable table()
    {
        return new CreateZtable(schema, name, columns, primaryKeys);
    }

    @Override
    public void enterRoot(
        PostgreSqlParser.RootContext ctx)
    {
        schema = null;
        name = null;
        columns.clear();
        primaryKeys.clear();
    }

    @Override
    public void enterQualified_name(
        PostgreSqlParser.Qualified_nameContext ctx)
    {
        String text = ctx.getText().replace("\"", "");
        String[] split = text.split(SCHEMA_PATTERN);
        schema = split.length > 1 ? split[0] : PUBLIC_SCHEMA_NAME;
        name = split.length > 1 ? split[1] : text;
    }

    @Override
    public void enterCreateztstmt(
        PostgreSqlParser.CreateztstmtContext ctx)
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
                constraints.add(tokens.getText(constraint.colconstraintelem()).toUpperCase());
            }
        }
        columns.add(new ZtableColumn(columnName, dataType, constraints));
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
