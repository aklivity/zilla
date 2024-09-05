package io.aklivity.zilla.runtime.binding.risingwave.internal.statement;

import java.util.List;

import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.table.Index;

public abstract class StatementGenerator
{
    protected final StringBuilder builder = new StringBuilder();

    public String getPrimaryKey(
        CreateTable statement)
    {
        String primaryKey = null;

        final List<Index> indexes = statement.getIndexes();

        match:
        for (Index index : indexes)
        {
            if ("PRIMARY KEY".equalsIgnoreCase(index.getType()))
            {
                final List<Index.ColumnParams> primaryKeyColumns = index.getColumns();
                primaryKey = primaryKeyColumns.get(0).columnName;
                break match;
            }
        }

        return primaryKey;
    }
}
