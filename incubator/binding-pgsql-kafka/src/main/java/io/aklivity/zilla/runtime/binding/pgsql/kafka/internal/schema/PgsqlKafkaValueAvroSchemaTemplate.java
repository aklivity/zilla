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

import java.util.List;

import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.table.Index;

public class PgsqlKafkaValueAvroSchemaTemplate extends PgsqlKafkaAvroSchemaTemplate
{
    private final StringBuilder schemaBuilder = new StringBuilder();
    private final String namespace;

    public PgsqlKafkaValueAvroSchemaTemplate(
        String namespace)
    {
        this.namespace = namespace;
    }

    public String generateSchema(
        String database,
        CreateTable createTable)
    {
        schemaBuilder.setLength(0);

        final String recordName = String.format("%s.%s", database, createTable.getTable().getName());

        schemaBuilder.append("{\n");
        schemaBuilder.append("\"type\": \"record\",\n");
        schemaBuilder.append("\"name\": \"").append(recordName).append("\",\n");
        schemaBuilder.append("\"namespace\": \"").append(namespace).append("\",\n");
        schemaBuilder.append("\"fields\": [\n");

        for (ColumnDefinition column : createTable.getColumnDefinitions())
        {
            String fieldName = column.getColumnName();
            String pgsqlType = column.getColDataType().getDataType();

            String avroType = convertPgsqlTypeToAvro(pgsqlType);

            schemaBuilder.append("  {\n");
            schemaBuilder.append("    \"name\": \"").append(fieldName).append("\",\n");
            schemaBuilder.append("    \"type\": ").append(avroType).append("\n");
            schemaBuilder.append("  },\n");
        }

        // Remove the last comma after the last field and close the JSON array
        schemaBuilder.setLength(schemaBuilder.length() - 2); // Remove last comma
        schemaBuilder.append("\n]\n}");

        return schemaBuilder.toString();
    }

    public String primaryKey(
        CreateTable statement)
    {
        String primaryKey = null;

        final List<Index> indexes = statement.getIndexes();

        if (indexes != null && !indexes.isEmpty())
        {
            match:
            for (Index index : indexes)
            {
                if ("PRIMARY KEY".equalsIgnoreCase(index.getType()))
                {
                    final List<Index.ColumnParams> primaryKeyColumns = index.getColumns();
                    primaryKey = primaryKeyColumns.get(0).columnName;
                    break match;
                }
            }
        }

        return primaryKey;
    }
}
