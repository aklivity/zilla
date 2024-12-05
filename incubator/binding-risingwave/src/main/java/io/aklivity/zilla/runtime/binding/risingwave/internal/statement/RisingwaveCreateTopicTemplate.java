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
package io.aklivity.zilla.runtime.binding.risingwave.internal.statement;

import java.util.Map;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Stream;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Table;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.View;

public class RisingwaveCreateTopicTemplate extends RisingwaveCommandTemplate
{
    private final String sqlFormat = """
        CREATE TOPIC IF NOT EXISTS %s (%s%s);\u0000""";
    private final String primaryKeyFormat = ", PRIMARY KEY (%s)";

    private final StringBuilder fieldBuilder = new StringBuilder();
    private final StringBuilder primaryKeyBuilder = new StringBuilder();

    public String generate(
        Table table)
    {
        String topic = table.name();
        String primaryKey = !table.primaryKeys().isEmpty()
            ? String.format(primaryKeyFormat, table.primaryKeys().stream().findFirst().get())
            : "";

        fieldBuilder.setLength(0);

        table.columns().forEach(c ->
        {
            fieldBuilder.append(c.name());
            fieldBuilder.append(" ");
            fieldBuilder.append(c.type());
            if (!c.constraints().isEmpty())
            {
                fieldBuilder.append(" ");
                c.constraints().forEach(fieldBuilder::append);
            }
            fieldBuilder.append(", ");
        });

        fieldBuilder.delete(fieldBuilder.length() - 2, fieldBuilder.length());

        return String.format(sqlFormat, topic, fieldBuilder, primaryKey);
    }

    public String generate(
        Stream stream)
    {
        String topic = stream.name();

        fieldBuilder.setLength(0);

        stream.columns()
            .entrySet()
            .stream()
            .filter(e -> !ZILLA_MAPPINGS_OLD.containsKey(e.getKey()))
            .forEach(e -> fieldBuilder.append(
                String.format("%s %s, ", e.getKey(), e.getValue())));

        fieldBuilder.delete(fieldBuilder.length() - 2, fieldBuilder.length());

        return String.format(sqlFormat, topic, fieldBuilder, "");
    }

    public String generate(
        View view,
        Map<String, String> columns)
    {
        String topic = view.name();

        primaryKeyBuilder.setLength(0);
        columns.keySet().forEach(k -> primaryKeyBuilder.append(k).append(", "));
        primaryKeyBuilder.delete(primaryKeyBuilder.length() - 2, primaryKeyBuilder.length());

        String primaryKey = String.format(primaryKeyFormat, primaryKeyBuilder);

        fieldBuilder.setLength(0);

        columns.forEach((k, v) -> fieldBuilder.append(String.format("%s %s, ", k,
            RisingwavePgsqlTypeMapping.typeName(v))));
        fieldBuilder.delete(fieldBuilder.length() - 2, fieldBuilder.length());

        return String.format(sqlFormat, topic, fieldBuilder, primaryKey);
    }
}
