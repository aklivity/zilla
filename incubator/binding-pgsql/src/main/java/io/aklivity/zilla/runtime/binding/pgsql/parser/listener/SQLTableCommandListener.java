package io.aklivity.zilla.runtime.binding.pgsql.parser.listener;

import java.util.Map;
import java.util.Set;

import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.collections.ObjectHashSet;

import io.aklivity.zilla.runtime.binding.pgsql.parser.PostgreSQLParser;
import io.aklivity.zilla.runtime.binding.pgsql.parser.PostgreSQLParserBaseListener;

public class SQLTableCommandListener extends PostgreSQLParserBaseListener
{
    private String tableName;
    private final Map<String, String> columns = new Object2ObjectHashMap<>();
    private final Set<String> primaryKeys = new ObjectHashSet<>();

    public TableInfo tableInfo()
    {
        return new TableInfo(tableName, columns, primaryKeys);
    }

    @Override   
    public void enterQualified_name(
        PostgreSQLParser.Qualified_nameContext ctx)
    {
        tableName = ctx.getText();s
    }

    @Override
    public void enterCreatestmt(
        PostgreSQLParser.CreatestmtContext ctx)
    {
        columns.clear();
        primaryKeys.clear();

        for (PostgreSQLParser.TableelementContext tableElement : ctx.opttableelementlist().tableelementlist().tableelement())
        {
            if (tableElement.columnDef() != null)
            {
                String columnName = tableElement.columnDef().colid().getText();
                String dataType = tableElement.columnDef().typename().getText();
                columns.put(columnName, dataType);

                for (PostgreSQLParser.ColconstraintContext constraint : tableElement.columnDef().colquallist().colconstraint())
                {
                    if (constraint.colconstraintelem().PRIMARY() != null &&
                        constraint.colconstraintelem().KEY() != null)
                    {
                        primaryKeys.add(columnName);
                    }
                }
            }
            else if (tableElement.tableconstraint() != null)
            {
                if (tableElement.tableconstraint().constraintelem().PRIMARY() != null &&
                    tableElement.tableconstraint().constraintelem().KEY() != null)
                {
                    tableElement.tableconstraint().constraintelem().columnlist().columnElem().forEach(
                        column -> primaryKeys.add(column.getText()));
                }
            }
        }
    }

    public record TableInfo(
        String tableName,
        Map<String, String> columns,
        Set<String> primaryKeys
    )
    {
    }
}
