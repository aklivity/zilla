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

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.AlterExpression;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Operation;

public abstract class PgsqlKafkaAvroSchemaTemplate
{
    protected static final String DATABASE_PLACEHOLDER = "{database}";

    protected Jsonb jsonbFormatted = JsonbBuilder.create(new JsonbConfig().withFormatting(true));
    protected Jsonb jsonb = JsonbBuilder.create();

    protected JsonObject mapSqlTypeToAvroType(
        String sqlType)
    {
        sqlType = sqlType.toUpperCase();

        JsonObjectBuilder builder = Json.createObjectBuilder();
        JsonObject result = null;

        switch (sqlType)
        {
        case "INT":
        case "INTEGER":
            result = builder.add("type", "int").build();
            break;
        case "BIGINT":
            result = builder.add("type", "long").build();
            break;
        case "BOOLEAN":
            result = builder.add("type", "boolean").build();
            break;
        case "FLOAT":
            result = builder.add("type", "float").build();
            break;
        case "DOUBLE":
        case "DOUBLE PRECISION":
            result = builder.add("type", "double").build();
            break;
        case "DECIMAL":
            result = builder
                .add("type", "bytes")
                .add("logicalType", "decimal")
                .add("precision", 10)
                .add("scale", 2)
                .build();
            break;
        case "DATE":
            result = builder
                .add("type", "int")
                .add("logicalType", "date")
                .build();
            break;
        case "TIMESTAMP":
            builder.add("type", "long");
            builder.add("logicalType", "timestamp-millis");
            result = builder.build();
            break;
        case "VARCHAR":
        case "CHAR":
        case "TEXT":
        default:
            builder.add("type", "string");
            result = builder.build();
            break;
        }

        return result;
    }

    protected boolean applyAlterations(
        JsonArrayBuilder fields,
        List<AlterExpression> expressions)
    {
        boolean applied = true;

        apply:
        for (AlterExpression alterExpr : expressions)
        {
            if (alterExpr.operation() == Operation.ADD)
            {
                JsonObjectBuilder field = Json.createObjectBuilder()
                    .add("name", alterExpr.columnName());
                Object avroType = mapSqlTypeToAvroType(alterExpr.columnType());

                JsonArray unionType = Json.createArrayBuilder()
                    .add("null")
                    .add(jsonb.toJson(avroType))
                    .build();
                field.add("type", unionType);
                field.add("default", Json.createValue("null"));

                fields.add(field.build());
            }
            else
            {
                applied = false;
                break apply;
            }
        }

        return applied;
    }
}
