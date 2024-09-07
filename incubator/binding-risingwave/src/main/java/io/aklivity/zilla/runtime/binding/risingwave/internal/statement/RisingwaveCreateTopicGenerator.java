/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.risingwave.internal.statement;

import java.util.Map;

import org.agrona.collections.Object2ObjectHashMap;

import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.table.CreateTable;

public class RisingwaveCreateTopicGenerator extends CommandGenerator
{
    private final Map<String, String> fields;

    private String topic;
    private String primaryKey;

    public RisingwaveCreateTopicGenerator()
    {
        this.fields = new Object2ObjectHashMap<>();
    }

    public String generate(
        Statement statement)
    {
        CreateTable createTable = (CreateTable) statement;
        topic = createTable.getTable().getName();
        primaryKey = getPrimaryKey(createTable);
        createTable.getColumnDefinitions()
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
        builder.append("\u0000");

        return builder.toString();
    }
}
