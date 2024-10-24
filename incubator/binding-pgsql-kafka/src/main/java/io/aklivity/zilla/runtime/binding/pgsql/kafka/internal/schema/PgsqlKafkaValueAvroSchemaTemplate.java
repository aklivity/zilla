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
package io.aklivity.zilla.runtime.binding.pgsql.kafka.internal.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Alter;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Table;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.TableColumn;

public class PgsqlKafkaValueAvroSchemaTemplate extends PgsqlKafkaAvroSchemaTemplate
{
    private final ObjectMapper mapper = new ObjectMapper();
    private final String namespace;

    public PgsqlKafkaValueAvroSchemaTemplate(
        String namespace)
    {
        this.namespace = namespace;
    }

    public String generate(
        String database,
        Table table)
    {
        final String newNamespace = namespace.replace(DATABASE_PLACEHOLDER, database);

        ObjectNode schemaNode = mapper.createObjectNode();
        schemaNode.put("type", "record");
        schemaNode.put("name", table.name());
        schemaNode.put("namespace", newNamespace);

        ArrayNode fieldsArray = mapper.createArrayNode();

        for (TableColumn column : table.columns())
        {
            String columnName = column.name();
            String sqlType = column.type();
            Object avroType = mapSqlTypeToAvroType(sqlType);

            boolean isNullable = !column.constraints().contains("NOT NULL");

            ObjectNode fieldNode = mapper.createObjectNode();
            fieldNode.put("name", columnName);

            if (isNullable)
            {
                ArrayNode unionType = mapper.createArrayNode();
                unionType.add("null");
                unionType.addPOJO(avroType);
                fieldNode.set("type", unionType);
            }
            else
            {
                fieldNode.set("type", mapper.valueToTree(avroType));
            }

            fieldsArray.add(fieldNode);
        }

        schemaNode.set("fields", fieldsArray);

        return schemaNode.asText();
    }

    public String generate(
        String existingSchemaJson,
        Alter alter)
    {
        String newSchema = existingSchemaJson;
        try
        {
            ObjectNode schemaNode = (ObjectNode) mapper.readTree(existingSchemaJson);
            applyAlterations(schemaNode, alter.alterExpressions());

            newSchema = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(schemaNode);
        }
        catch (JsonProcessingException ignore)
        {
        }

        return newSchema;
    }
}
