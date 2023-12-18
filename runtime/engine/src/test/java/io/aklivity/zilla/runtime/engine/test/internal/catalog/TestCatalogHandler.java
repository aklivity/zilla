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

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.runtime.engine.validator.function.ValueConsumer;

public class TestCatalogHandler implements CatalogHandler
{
    private static final int MAX_PADDING_LEN = 10;
    private static final byte MAGIC_BYTE = 0x0;
    private static final int ENRICHED_LENGTH = 5;

    private final String schema;
    private final MutableDirectBuffer prefixRO;
    private final int id;
    private final boolean embed;
    private final boolean exclude;

    public TestCatalogHandler(
        TestCatalogOptionsConfig config)
    {
        this.schema = config.schema;
        this.prefixRO = new UnsafeBuffer(new byte[5]);
        this.id = config.id;
        this.embed = config.embed;
        this.exclude = config.exclude;
    }

    @Override
    public int enrich(
        int schemaId,
        ValueConsumer next)
    {
        int length = 0;
        if (embed)
        {
            prefixRO.putByte(0, MAGIC_BYTE);
            prefixRO.putInt(1, schemaId, ByteOrder.BIG_ENDIAN);
            next.accept(prefixRO, 0, 5);
            length = ENRICHED_LENGTH;
        }
        else if (exclude)
        {
            length = ENRICHED_LENGTH;
        }
        return length;
    }

    @Override
    public int maxPadding()
    {
        return MAX_PADDING_LEN;
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
}
