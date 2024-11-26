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
import java.util.Optional;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Table;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.View;

public class RisingwaveCreateSinkTemplate extends RisingwaveCommandTemplate
{
    private final String sqlFormat = """
        CREATE SINK %s.%s_view_sink INTO %s FROM %s_view;\u0000""";
    private final String sqlKafkaFormat = """
        CREATE SINK %s.%s_sink
        FROM %s
        WITH (
           connector='kafka',
           properties.bootstrap.server='%s',
           topic='%s.%s'%s
        ) FORMAT UPSERT ENCODE AVRO (
           schema.registry='%s'
        ) KEY ENCODE TEXT;\u0000""";

    private final String primaryKeyFormat = ",\n   primary_key='%s'";

    private final String schema;
    private final String bootstrapServer;
    private final String schemaRegistry;

    public RisingwaveCreateSinkTemplate(
        String schema,
        String bootstrapServer,
        String schemaRegistry)
    {
        this.schema = schema;
        this.bootstrapServer = bootstrapServer;
        this.schemaRegistry = schemaRegistry;
    }

    public String generate(
        String schema,
        Map<String, String> columns,
        View view)
    {
        String viewName = view.name();

        Optional<Map.Entry<String, String>> primaryKeyMatch = columns.entrySet().stream()
            .filter(e -> "id".equalsIgnoreCase(e.getKey()))
            .findFirst();

        if (primaryKeyMatch.isEmpty())
        {
            primaryKeyMatch = columns.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().contains("id"))
                .findFirst();
        }

        String textPrimaryKey = primaryKeyMatch.map(Map.Entry::getKey).orElse(null);
        String primaryKey = textPrimaryKey != null ? primaryKeyFormat.formatted(textPrimaryKey) : "";

        return String.format(sqlKafkaFormat, this.schema, viewName, viewName, bootstrapServer, schema, viewName, primaryKey, schemaRegistry);
    }

    public String generate(
        String schema,
        Table tableInfo)
    {
        String table = tableInfo.name();

        return String.format(sqlKafkaFormat, this.schema, table, table, bootstrapServer, schema, table,
            primaryKeyFormat.formatted(tableInfo.primaryKeys().stream().findFirst().get()), schemaRegistry);
    }

    public String generate(
        Table tableInfo)
    {
        String table = tableInfo.name();

        return String.format(sqlFormat, table, table, table);
    }
}
