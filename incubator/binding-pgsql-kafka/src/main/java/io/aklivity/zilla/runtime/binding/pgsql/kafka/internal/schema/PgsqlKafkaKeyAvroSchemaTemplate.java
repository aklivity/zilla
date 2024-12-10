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
package io.aklivity.zilla.runtime.binding.pgsql.kafka.internal.schema;

import java.util.List;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateTable;

public class PgsqlKafkaKeyAvroSchemaTemplate extends PgsqlKafkaAvroSchemaTemplate
{
    private final String namespace;

    public PgsqlKafkaKeyAvroSchemaTemplate(
        String namespace)
    {
        this.namespace = namespace;
    }

    public String generate(
        String database,
        CreateTable createTable)
    {
        final String newNamespace = namespace.replace(DATABASE_PLACEHOLDER, database);

        List<AvroField> fields = createTable.columns().stream()
            .map(column -> new AvroField(column.name(), mapSqlTypeToAvroType(column.type()), null))
            .collect(Collectors.toList());

        AvroSchema schema = new AvroSchema("record", createTable.name(), newNamespace, fields);
        AvroPayload payload = new AvroPayload("AVRO", jsonb.toJson(schema));

        return jsonbFormatted.toJson(payload);
    }
}
