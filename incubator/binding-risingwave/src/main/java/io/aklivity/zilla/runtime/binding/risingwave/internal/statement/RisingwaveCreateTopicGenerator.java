package io.aklivity.zilla.runtime.binding.risingwave.internal.statement;

import java.util.Map;

import org.agrona.collections.Object2ObjectHashMap;

import net.sf.jsqlparser.statement.create.table.CreateTable;

public class RisingwaveCreateTopicGenerator extends StatementGenerator
{
    private final Map<String, String> fields;

    private String topic;
    private String primaryKey;

    public RisingwaveCreateTopicGenerator()
    {
        this.fields = new Object2ObjectHashMap<>();
    }

    public String generate(
        CreateTable statement)
    {
        topic = statement.getTable().getName();
        primaryKey = getPrimaryKey(statement);
        statement.getColumnDefinitions()
            .forEach(c -> fields.put(c.getColumnName(), c.getColDataType().getDataType()));

        return format();
    }

    private String format()
    {
        builder.setLength(0);

        builder.append("CREATE TOPIC IF NOT EXISTS ");
        builder.append(topic);
        builder.append(" (");

        int i = 0;
        for (Map.Entry<String, String> field : fields.entrySet())
        {
            builder.append(field.getKey());
            builder.append(" ");
            builder.append(field.getValue());

            if (i < fields.size() - 1)
            {
                builder.append(", ");
            }
            i++;
        }

        builder.append(", PRIMARY KEY (");
        builder.append(primaryKey);
        builder.append("));");

        return builder.toString();
    }
}
