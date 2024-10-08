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

import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.view.CreateView;

public class RisingwaveCreateTopicTemplate extends RisingwaveCommandTemplate
{
    private final String sqlFormat = """
        CREATE TOPIC IF NOT EXISTS %s (%s%s);\u0000""";
    private final String primaryKeyFormat = ", PRIMARY KEY (%s)";
    private final String fieldFormat = "%s %s, ";

    private final StringBuilder fieldBuilder = new StringBuilder();
    private final StringBuilder primaryKeyBuilder = new StringBuilder();

    public RisingwaveCreateTopicTemplate()
    {
    }

    public String generate(
        CreateTable createTable)
    {
        String topic = createTable.getTable().getName();
        String primaryKeyField = primaryKey(createTable);
        String primaryKey = primaryKeyField != null ? String.format(primaryKeyFormat, primaryKeyField) : "";

        fieldBuilder.setLength(0);

        createTable.getColumnDefinitions()
            .forEach(c -> fieldBuilder.append(
                String.format(fieldFormat, c.getColumnName(), c.getColDataType().getDataType())));

        fieldBuilder.delete(fieldBuilder.length() - 2, fieldBuilder.length());

        return String.format(sqlFormat, topic, fieldBuilder, primaryKey);
    }

    public String generate(
        RisingwaveCreateTableCommand command)
    {
        CreateTable createTable = command.createTable;
        String topic = createTable.getTable().getName();
        String primaryKeyField = primaryKey(createTable);
        String primaryKey = primaryKeyField != null ? String.format(primaryKeyFormat, primaryKeyField) : "";

        fieldBuilder.setLength(0);

        createTable.getColumnDefinitions()
            .forEach(c -> fieldBuilder.append(
                String.format(fieldFormat, c.getColumnName(), c.getColDataType().getDataType())));

        if (command.includes != null)
        {
            command.includes.forEach((k, v) -> fieldBuilder.append(
                String.format(fieldFormat, v, ZILLA_INCLUDE_TYPE_MAPPINGS.get(k))));
        }

        fieldBuilder.delete(fieldBuilder.length() - 2, fieldBuilder.length());

        return String.format(sqlFormat, topic, fieldBuilder, primaryKey);
    }

    public String generate(
        CreateView createView,
        Map<String, String> columns)
    {
        String topic = createView.getView().getName();

        primaryKeyBuilder.setLength(0);
        columns.keySet().forEach(k -> primaryKeyBuilder.append(k).append(", "));
        primaryKeyBuilder.delete(primaryKeyBuilder.length() - 2, primaryKeyBuilder.length());

        String primaryKey = String.format(primaryKeyFormat, primaryKeyBuilder);

        fieldBuilder.setLength(0);

        columns.forEach((k, v) -> fieldBuilder.append(String.format(fieldFormat, k,
            RisingwavePgsqlTypeMapping.typeName(v))));
        fieldBuilder.delete(fieldBuilder.length() - 2, fieldBuilder.length());

        return String.format(sqlFormat, topic, fieldBuilder, primaryKey);
    }
}
