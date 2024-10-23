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

import java.util.Map;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.TableInfo;

public class PgsqlKafkaKeyAvroSchemaTemplate extends PgsqlKafkaAvroSchemaTemplate
{
    private static final String DATABASE_PLACEHOLDER = "{database}";

    private final StringBuilder schemaBuilder = new StringBuilder();
    private final String namespace;

    public PgsqlKafkaKeyAvroSchemaTemplate(
        String namespace)
    {
        this.namespace = namespace;
    }

    public String generateSchema(
        String database,
        TableInfo createTable)
    {
        schemaBuilder.setLength(0);

        final String newNamespace = namespace.replace(DATABASE_PLACEHOLDER, database);
        final String recordName = String.format("%s_key", createTable.name());

        schemaBuilder.append("{\n");
        schemaBuilder.append("\"schemaType\": \"AVRO\",\n");
        schemaBuilder.append("\"schema\": \""); // Begin the schema field

        // Building the actual Avro schema
        schemaBuilder.append("{\\\"type\\\": \\\"record\\\",");
        schemaBuilder.append(" \\\"name\\\": \\\"").append(recordName).append("\\\",");
        schemaBuilder.append(" \\\"namespace\\\": \\\"").append(newNamespace).append("\\\",");
        schemaBuilder.append(" \\\"fields\\\": [");

        for (Map.Entry<String, String> column : createTable.columns().entrySet())
        {
            String fieldName = column.getKey();
            String pgsqlType = column.getValue();

            String avroType = convertPgsqlTypeToAvro(pgsqlType);

            schemaBuilder.append(" {\\\"name\\\": \\\"").append(fieldName).append("\\\",");
            schemaBuilder.append(" \\\"type\\\": [").append(avroType).append(", \\\"null\\\"] },");
        }

        // Remove the last comma and close the fields array
        schemaBuilder.setLength(schemaBuilder.length() - 1);
        schemaBuilder.append("]");

        // Closing the Avro schema
        schemaBuilder.append("}\"\n}");

        return schemaBuilder.toString();
    }
}
