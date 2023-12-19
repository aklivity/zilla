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
package io.aklivity.zilla.runtime.engine.test.internal.validator;

import java.nio.ByteOrder;
import java.util.function.LongFunction;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.test.internal.validator.config.TestValidatorConfig;
import io.aklivity.zilla.runtime.engine.validator.FragmentValidator;
import io.aklivity.zilla.runtime.engine.validator.ValueValidator;
import io.aklivity.zilla.runtime.engine.validator.function.FragmentConsumer;
import io.aklivity.zilla.runtime.engine.validator.function.ValueConsumer;

public class TestValidator implements ValueValidator, FragmentValidator
{
    private static final byte MAGIC_BYTE = 0x0;
    private final int length;
    private final int schemaId;
    private final boolean read;
    private final CatalogHandler handler;

    public TestValidator(
        TestValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        this.length = config.length;
        this.read = config.read;
        CatalogedConfig cataloged = config.cataloged != null && !config.cataloged.isEmpty()
            ? config.cataloged.get(0)
            : null;
        schemaId = cataloged != null ? cataloged.schemas.get(0).id : 0;
        this.handler = cataloged != null ? supplyCatalog.apply(cataloged.id) : null;
    }

    @Override
    public int maxPadding(
        DirectBuffer data,
        int index,
        int length)
    {
        return handler != null ? handler.maxPadding() : 0;
    }

    @Override
    public int validate(
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        return validateComplete(data, index, length, next);
    }

    @Override
    public int validate(
        int flags,
        DirectBuffer data,
        int index,
        int length,
        FragmentConsumer next)
    {
        return (flags & FLAGS_FIN) != 0x00
                ? validateComplete(data, index, length, (b, i, l) -> next.accept(FLAGS_COMPLETE, b, i, l))
                : 0;
    }

    private int validateComplete(
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        boolean valid = length == this.length;
        if (valid)
        {
            if (read)
            {
                int progress = 0;
                if (data.getByte(index) == MAGIC_BYTE)
                {
                    progress += BitUtil.SIZE_OF_BYTE;
                    data.getInt(index + progress, ByteOrder.BIG_ENDIAN);
                    progress += BitUtil.SIZE_OF_INT;
                }
                next.accept(data, index + progress, length - progress);
            }
            else
            {
                if (handler != null)
                {
                    handler.enrich(schemaId, next);
                }
                next.accept(data, index, length);
            }
        }
        return valid ? length : -1;
    }
}

