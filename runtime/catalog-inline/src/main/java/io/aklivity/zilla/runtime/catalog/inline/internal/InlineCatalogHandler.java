/*
 * Copyright 2021-2026 Aklivity Inc
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

import java.util.List;
import java.util.zip.CRC32C;

import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Object2IntHashMap;

import io.aklivity.zilla.runtime.catalog.inline.config.InlineOptionsConfig;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineSchemaConfig;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;

public class InlineCatalogHandler implements CatalogHandler
{
    private static final String VERSION_LATEST = "latest";
    private static final int NO_REFERENCES = 0;
    private static final int[] NO_SCHEMA_IDS = new int[0];

    private final Int2ObjectHashMap<String> schemas;
    private final Object2IntHashMap<String> schemaIds;
    private final Int2IntHashMap references;
    private final CRC32C crc32c;

    public InlineCatalogHandler(
        InlineOptionsConfig config)
    {
        this.schemas = new Int2ObjectHashMap<>();
        this.schemaIds = new Object2IntHashMap<>(NO_SCHEMA_ID);
        this.references = new Int2IntHashMap(NO_REFERENCES);
        this.crc32c = new CRC32C();
        if (config != null)
        {
            registerSchema(config.subjects);
        }
    }

    @Override
    public int register(
        String subject,
        String schema)
    {
        return register(subject, VERSION_LATEST, schema);
    }

    @Override
    public int[] unregister(
        String subject)
    {
        int schemaId = schemaIds.removeKey(subject + VERSION_LATEST);
        int[] removed = NO_SCHEMA_IDS;
        if (schemaId != NO_SCHEMA_ID)
        {
            release(schemaId);
            removed = new int[] { schemaId };
        }
        return removed;
    }

    @Override
    public String resolve(
        int schemaId)
    {
        return schemas.get(schemaId);
    }

    @Override
    public int resolve(
        String subject,
        String version)
    {
        return schemaIds.getValue(subject + version);
    }

    private int register(
        String subject,
        String version,
        String schema)
    {
        int schemaId = generateCRC32C(schema);
        String key = subject + version;
        int current = schemaIds.getValue(key);
        if (current != schemaId)
        {
            if (current != NO_SCHEMA_ID)
            {
                release(current);
            }
            schemaIds.put(key, schemaId);
            schemas.putIfAbsent(schemaId, schema);
            references.put(schemaId, references.get(schemaId) + 1);
        }
        return schemaId;
    }

    private void release(
        int schemaId)
    {
        int count = references.get(schemaId);
        if (count != NO_REFERENCES)
        {
            if (count <= 1)
            {
                references.remove(schemaId);
                schemas.remove(schemaId);
            }
            else
            {
                references.put(schemaId, count - 1);
            }
        }
    }

    private int generateCRC32C(
        String schema)
    {
        byte[] bytes = schema.getBytes();
        crc32c.reset();
        crc32c.update(bytes, 0, bytes.length);
        return (int) crc32c.getValue();
    }

    private void registerSchema(
        List<InlineSchemaConfig> configs)
    {
        if (configs != null)
        {
            for (InlineSchemaConfig config : configs)
            {
                register(config.subject, config.version, config.schema);
            }
        }
    }
}
