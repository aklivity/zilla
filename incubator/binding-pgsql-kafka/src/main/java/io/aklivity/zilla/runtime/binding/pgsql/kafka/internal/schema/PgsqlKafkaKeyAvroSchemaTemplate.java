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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Table;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.TableColumn;

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

            ObjectNode fieldNode = mapper.createObjectNode();
            fieldNode.put("name", columnName);

            ArrayNode unionType = mapper.createArrayNode();
            unionType.add("null");
            unionType.addPOJO(avroType);
            fieldNode.set("type", unionType);

            fieldsArray.add(fieldNode);
        }

        schemaNode.set("fields", fieldsArray);

        return schemaNode.asText();
    }
}
