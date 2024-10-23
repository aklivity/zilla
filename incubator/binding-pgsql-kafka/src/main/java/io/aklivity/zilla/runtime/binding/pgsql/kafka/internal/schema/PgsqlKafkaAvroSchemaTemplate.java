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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public abstract class PgsqlKafkaAvroSchemaTemplate
{
    protected static final String DATABASE_PLACEHOLDER = "{database}";

    protected final ObjectMapper mapper = new ObjectMapper();

    protected Object mapSqlTypeToAvroType(
        String sqlType)
    {
        sqlType = sqlType.toUpperCase();

        Object result = null;

        switch (sqlType)
        {
        case "INT":
        case "INTEGER":
            result = "int";
            break;
        case "BIGINT":
            result = "long";
            break;
        case "BOOLEAN":
            result = "boolean";
            break;
        case "FLOAT":
            result = "float";
            break;
        case "DOUBLE":
            result = "double";
            break;
        case "DECIMAL":
            ObjectNode decimalNode = mapper.createObjectNode();
            decimalNode.put("type", "bytes");
            decimalNode.put("logicalType", "decimal");
            decimalNode.put("precision", 10);
            decimalNode.put("scale", 2);
            result = decimalNode;
            break;
        case "DATE":
            ObjectNode dateNode = mapper.createObjectNode();
            dateNode.put("type", "int");
            dateNode.put("logicalType", "date");
            result = dateNode;
            break;
        case "TIMESTAMP":
            ObjectNode timestampNode = mapper.createObjectNode();
            timestampNode.put("type", "long");
            timestampNode.put("logicalType", "timestamp-millis");
            result = timestampNode;
            break;
        case "VARCHAR":
        case "CHAR":
        case "TEXT":
        default:
            result = "string";
        }

        return result;
    }

    private void applyAlterations(
        ObjectNode schemaNode,
        List<AlterExpression> alterExpressions)
    {
        ArrayNode fieldsArray = (ArrayNode) schemaNode.get("fields");

        for (AlterExpression alterExpr : alterExpressions)
        {
            switch (alterExpr.getOperation())
            {
                case ADD:
                    // Handle adding new columns
                    // Create new field nodes and add them to fieldsArray
                    break;
                case DROP:
                    // Handle dropping columns
                    // Remove field nodes from fieldsArray
                    break;
                case MODIFY:
                    // Handle modifying columns
                    // Update field nodes in fieldsArray
                    break;
                // Handle other operations as needed
            }
        }
    }
}
