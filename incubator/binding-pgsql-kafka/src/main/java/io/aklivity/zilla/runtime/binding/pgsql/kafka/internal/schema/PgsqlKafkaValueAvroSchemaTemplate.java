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
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Alter;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateZtable;

public class PgsqlKafkaValueAvroSchemaTemplate extends PgsqlKafkaAvroSchemaTemplate
{
    private final String namespace;

    public PgsqlKafkaValueAvroSchemaTemplate(
        String namespace)
    {
        this.namespace = namespace;
    }

    public String generate(
        CreateZtable command)
    {
        final String newNamespace = namespace.replace(DATABASE_PLACEHOLDER, command.schema());

        List<AvroField> fields = command.columns().stream()
            .map(column ->
            {
                String columnName = column.name();
                String sqlType = column.type();
                Object avroType = mapSqlTypeToAvroType(sqlType);
                List<String> constraints = column.constraints();

                boolean isNullable = !constraints.contains("NOT NULL");

                String zillaType = ZILLA_MAPPINGS.entrySet().stream()
                        .filter(e -> constraints.contains(e.getKey()))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(null);

                return isNullable
                    ? new AvroField(columnName, new Object[]{"null", avroType}, zillaType)
                    : new AvroField(columnName, avroType, zillaType);
            })
            .collect(Collectors.toList());

        AvroSchema schema = new AvroSchema("record", command.name(), newNamespace, fields);
        AvroPayload payload = new AvroPayload("AVRO", jsonb.toJson(schema));

        return jsonbFormatted.toJson(payload);
    }

    public String generate(
        String existingSchemaJson,
        Alter alter)
    {
        JsonObject schema = jsonb.fromJson(existingSchemaJson, JsonObject.class);
        JsonArray fields = schema.getJsonArray("fields");

        JsonArrayBuilder fieldsBuilder = Json.createArrayBuilder();
        if (fields != null)
        {
            for (JsonValue field : fields)
            {
                fieldsBuilder.add(field);
            }
        }

        boolean applied = applyAlterations(fieldsBuilder, alter.expressions());
        String newSchemaJson = null;

        if (applied)
        {
            JsonObjectBuilder schemaBuilder = Json.createObjectBuilder();
            for (Map.Entry<String, JsonValue> entry : schema.entrySet())
            {
                if (entry.getKey().equals("fields"))
                {
                    schemaBuilder.add("fields", fieldsBuilder.build());
                }
                else
                {
                    schemaBuilder.add(entry.getKey(), entry.getValue());
                }
            }

            AvroPayload payload = new AvroPayload("AVRO", jsonb.toJson(schemaBuilder.build()));

            newSchemaJson = jsonbFormatted.toJson(payload);
        }

        return newSchemaJson;
    }
}
