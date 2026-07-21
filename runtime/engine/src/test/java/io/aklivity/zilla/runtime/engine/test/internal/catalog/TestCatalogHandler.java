/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.test.internal.catalog;

import java.nio.ByteOrder;
import java.util.zip.CRC32C;

import org.agrona.BitUtil;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Object2IntHashMap;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.internal.types.String8FW;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogOptionsConfig;

public class TestCatalogHandler implements CatalogHandler
{
    private static final int NO_REFERENCES = 0;
    private static final int[] NO_SCHEMA_IDS = new int[0];

    private final String schema;
    private final int id;
    private final DirectBufferEx prefix;
    private final String url;
    private final Int2ObjectHashMap<String> schemasById;
    private final Object2IntHashMap<String> schemaIdsBySubject;
    private final Int2IntHashMap references;
    private final CRC32C crc32c;

    public TestCatalogHandler(
        TestCatalogOptionsConfig options)
    {
        this.id = options != null ? options.id : NO_SCHEMA_ID;
        this.schema = options != null ? options.schema : null;
        this.prefix = options != null ? new String8FW(options.prefix).value() : null;
        this.url = options != null ? options.url : null;
        this.schemasById = new Int2ObjectHashMap<>();
        this.schemaIdsBySubject = new Object2IntHashMap<>(NO_SCHEMA_ID);
        this.references = new Int2IntHashMap(NO_REFERENCES);
        this.crc32c = new CRC32C();
    }

    @Override
    public int register(
        String subject,
        String schema)
    {
        int schemaId;
        if (this.schema != null)
        {
            schemaId = this.schema.equals(schema) ? id : NO_VERSION_ID;
        }
        else if (schema != null && subject != null)
        {
            schemaId = generateCRC32C(schema);
            int current = schemaIdsBySubject.getValue(subject);
            if (current != schemaId)
            {
                if (current != NO_SCHEMA_ID)
                {
                    release(current);
                }
                schemaIdsBySubject.put(subject, schemaId);
                schemasById.putIfAbsent(schemaId, schema);
                references.put(schemaId, references.get(schemaId) + 1);
            }
        }
        else
        {
            schemaId = NO_VERSION_ID;
        }
        return schemaId;
    }

    @Override
    public int[] unregister(
        String subject)
    {
        int[] removed = NO_SCHEMA_IDS;
        if (subject != null)
        {
            int schemaId = schemaIdsBySubject.removeKey(subject);
            if (schemaId != NO_SCHEMA_ID)
            {
                release(schemaId);
                removed = new int[] { schemaId };
            }
        }
        return removed;
    }

    @Override
    public int resolve(
        String subject,
        String version)
    {
        int resolved = id;
        if (subject != null && schemaIdsBySubject.containsKey(subject))
        {
            resolved = schemaIdsBySubject.getValue(subject);
        }
        return resolved;
    }

    @Override
    public String resolve(
        int schemaId)
    {
        String resolved = schemaId == id ? schema : null;
        if (resolved == null)
        {
            resolved = schemasById.get(schemaId);
        }
        return resolved;
    }

    @Override
    public int decode(
        long traceId,
        long bindingId,
        DirectBufferEx data,
        int index,
        int length,
        ValueConsumer next,
        Decoder decoder)
    {
        int offset = prefix != null ? prefix.capacity() : 0;
        return decoder.accept(traceId, bindingId, NO_SCHEMA_ID, data, index + offset, length - offset, next);
    }

    @Override
    public int encode(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBufferEx data,
        int index,
        int length,
        ValueConsumer next,
        Encoder encoder)
    {
        if (prefix != null)
        {
            next.accept(prefix, 0, prefix.capacity());
        }
        int valLength = encoder.accept(traceId, bindingId, schemaId, data, index, length, next);
        int prefixLen = prefix != null ? prefix.capacity() : 0;
        return valLength > 0 ? prefixLen + valLength : -1;
    }

    @Override
    public int encodePadding(
        int length)
    {
        return prefix != null ? prefix.capacity() : 0;
    }

    @Override
    public boolean validate(
        long traceId,
        long bindingId,
        DirectBufferEx data,
        int index,
        int length,
        ValueConsumer next,
        Validator validator)
    {
        int schemaId = data.getInt(index, ByteOrder.BIG_ENDIAN);
        return validator.accept(traceId, bindingId, schemaId, data,
            index + BitUtil.SIZE_OF_INT, length - BitUtil.SIZE_OF_INT, next);
    }

    @Override
    public String location()
    {
        return url;
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
                schemasById.remove(schemaId);
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
}
