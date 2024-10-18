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

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.StreamInfo;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.TableInfo;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.ViewInfo;

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
        TableInfo tableInfo)
    {
        String topic = tableInfo.name();
        String primaryKey = !tableInfo.primaryKeys().isEmpty()
            ? String.format(primaryKeyFormat, tableInfo.primaryKeys().stream().findFirst().get())
            : "";

        fieldBuilder.setLength(0);

        tableInfo.columns()
            .forEach((k, v) -> fieldBuilder.append(
                String.format(fieldFormat, k, v)));

        fieldBuilder.delete(fieldBuilder.length() - 2, fieldBuilder.length());

        return String.format(sqlFormat, topic, fieldBuilder, primaryKey);
    }

    public String generate(
        StreamInfo streamInfo)
    {
        String topic = streamInfo.name();

        fieldBuilder.setLength(0);

        streamInfo.columns()
            .entrySet()
            .stream()
            .filter(e -> !ZILLA_MAPPINGS.containsKey(e.getKey()))
            .forEach(e -> fieldBuilder.append(
                String.format(fieldFormat, e.getKey(), e.getValue())));

        fieldBuilder.delete(fieldBuilder.length() - 2, fieldBuilder.length());

        return String.format(sqlFormat, topic, fieldBuilder, "");
    }

    public String generate(
        ViewInfo viewInfo,
        Map<String, String> columns)
    {
        String topic = viewInfo.name();

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
