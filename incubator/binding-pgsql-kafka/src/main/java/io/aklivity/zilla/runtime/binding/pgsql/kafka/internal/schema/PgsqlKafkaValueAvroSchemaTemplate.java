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

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;

import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.table.Index;

public class PgsqlKafkaValueAvroSchemaTemplate extends PgsqlKafkaAvroSchemaTemplate
{
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
        SchemaBuilder.FieldAssembler<Schema> fieldAssembler = SchemaBuilder
            .record(String.format("%s.%s", database, createTable.getTable().getName()))
                .namespace(namespace)
                .fields();

        for (ColumnDefinition column : createTable.getColumnDefinitions())
        {
            String fieldName = column.getColumnName();
            String pgsqlType = column.getColDataType().getDataType();

            String avroType = convertPgsqlTypeToAvro(pgsqlType);

            fieldAssembler = switch (avroType.toLowerCase())
            {
            case "string" -> fieldAssembler.name(fieldName).type().stringType().noDefault();
            case "int" -> fieldAssembler.name(fieldName).type().intType().noDefault();
            case "long" -> fieldAssembler.name(fieldName).type().longType().noDefault();
            case "boolean" -> fieldAssembler.name(fieldName).type().booleanType().noDefault();
            case "float" -> fieldAssembler.name(fieldName).type().floatType().noDefault();
            case "double" -> fieldAssembler.name(fieldName).type().doubleType().noDefault();
            default -> fieldAssembler;
            };
        }

        Schema schema = fieldAssembler.endRecord();

        return schema.toString(true);
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
