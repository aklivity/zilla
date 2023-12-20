/*
 * Copyright 2021-2023 Aklivity Inc.
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

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.runtime.engine.validator.function.ValueConsumer;

public class TestCatalogHandler implements CatalogHandler
{
    private static final int MAX_PADDING_LENGTH = 10;
    private static final byte MAGIC_BYTE = 0x0;
    private static final int PREFIX_LENGTH = 5;

    private final String schema;
    private final MutableDirectBuffer prefixRO;
    private final int id;
    private final boolean embed;

    public TestCatalogHandler(
        TestCatalogOptionsConfig config)
    {
        this.schema = config.schema;
        this.prefixRO = new UnsafeBuffer(new byte[5]);
        this.id = config.id;
        this.embed = config.embed;
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
        int valLength = 0;
        if (embed)
        {
            prefixRO.putByte(0, MAGIC_BYTE);
            prefixRO.putInt(1, schemaId, ByteOrder.BIG_ENDIAN);
            next.accept(prefixRO, 0, PREFIX_LENGTH);
            write.accept(data, index, length);
            valLength = PREFIX_LENGTH;
        }
        return valLength;
    }

    @Override
    public int maxPadding()
    {
        return MAX_PADDING_LENGTH;
    }

    @Override
    public int register(
        String subject,
        String type,
        String schema)
    {
        return id;
    }

    @Override
    public int resolve(
        String subject,
        String version)
    {
        return id;
    }

    @Override
    public String resolve(
        int schemaId)
    {
        return schema;
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
        int progress = 0;
        int valLength = -1;
        if (data.getByte(index) == MAGIC_BYTE)
        {
            progress += BitUtil.SIZE_OF_BYTE;
            schemaId = data.getInt(index + progress, ByteOrder.BIG_ENDIAN);
            progress += BitUtil.SIZE_OF_INT;
        }
        else if (catalog != null && catalog.id != NO_SCHEMA_ID)
        {
            schemaId = catalog.id;
        }
        else
        {
            schemaId = resolve(subject, catalog.version);
        }

        if (schemaId > NO_SCHEMA_ID)
        {
            valLength = read.accept(data, index + progress, length - progress, next, schemaId);
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
        if (data.getByte(index) == MAGIC_BYTE)
        {
            schemaId = data.getInt(index + BitUtil.SIZE_OF_BYTE, ByteOrder.BIG_ENDIAN);
        }
        else if (catalog.id != NO_SCHEMA_ID)
        {
            schemaId = catalog.id;
        }
        else
        {
            schemaId = resolve(subject, catalog.version);
        }
        return schemaId;
    }
}
