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
package io.aklivity.zilla.runtime.catalog.inline.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32C;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.catalog.inline.config.InlineOptionsConfig;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineSchemaConfig;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.engine.validator.function.ValueConsumer;

public class InlineCatalogHandler implements CatalogHandler
{
    private final Map<Integer, String> schemas;
    private final Map<String, Integer> schemaIds;
    private final CRC32C crc32c;

    public InlineCatalogHandler(
        InlineOptionsConfig config)
    {
        this.schemas = new HashMap<>();
        this.schemaIds =  new HashMap<>();
        this.crc32c = new CRC32C();
        registerSchema(config.subjects);
    }

    @Override
    public int register(
        String subject,
        String type,
        String schema)
    {
        return NO_SCHEMA_ID;
    }

    @Override
    public String resolve(
        int schemaId)
    {
        return  schemas.containsKey(schemaId) ? schemas.get(schemaId) : null;
    }

    @Override
    public int resolve(
        String subject,
        String version)
    {
        String key = subject + version;
        return schemaIds.containsKey(key) ? schemaIds.get(key) : NO_SCHEMA_ID;
    }

    @Override
    public int decode(
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next,
        SchemaConfig catalog,
        String subject,
        Read read)
    {
        int schemaId;
        int valLength = -1;
        if (catalog.id != NO_SCHEMA_ID)
        {
            schemaId = catalog.id;
        }
        else
        {
            schemaId = resolve(subject, catalog.version);
        }

        if (schemaId > NO_SCHEMA_ID)
        {
            valLength = read.accept(data, index, length, next, schemaId);
        }
        return valLength;
    }

    @Override
    public int resolve(
        DirectBuffer data,
        int index,
        int length,
        SchemaConfig catalog,
        String subject)
    {
        int schemaId;
        if (catalog.id != NO_SCHEMA_ID)
        {
            schemaId = catalog.id;
        }
        else
        {
            schemaId = resolve(subject, catalog.version);
        }
        return schemaId;
    }

    @Override
    public int encode(
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next,
        int schemaId,
        Write write)
    {
        write.accept(data, index, length);
        return 0;
    }

    private int generateCRC32C(
        String schema)
    {
        byte[] bytes = schema.getBytes();
        crc32c.reset();
        crc32c.update(bytes, 0, bytes.length);
        return (int) crc32c.getValue();
    }

    private void registerSchema(List<InlineSchemaConfig> configs)
    {
        for (InlineSchemaConfig config : configs)
        {
            String schema = config.schema;
            int schemaId = generateCRC32C(schema);
            schemas.put(schemaId, schema);
            schemaIds.put(config.subject + config.version, schemaId);
        }
    }
}
