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

public abstract class PgsqlKafkaAvroSchemaTemplate
{
    protected String convertPgsqlTypeToAvro(
        String pgsqlType)
    {
        return switch (pgsqlType.toLowerCase())
        {
        case "varchar", "text", "char", "bpchar" -> "\\\"string\\\"";
        case "int", "integer", "serial" -> "\\\"int\\\"";
        case "numeric" -> "\\\"double\\\"";
        case "bigint", "bigserial" -> "\\\"long\\\"";
        case "boolean", "bool" -> "\\\"boolean\\\"";
        case "real", "float4" -> "\\\"float\\\"";
        case "double", "double precision", "float8" -> "\\\"double\\\"";
        case "timestamp", "timestampz", "date", "time" ->
            "{ \\\"type\\\": \\\"long\\\", \\\"logicalTyp\\\": \\\"timestamp-millis\\\" }";
        default -> null;
        };
    }
}
