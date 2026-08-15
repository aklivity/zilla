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
package io.aklivity.zilla.runtime.model.core.internal;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.model.core.ext.BytesModelExt;
import io.aklivity.zilla.runtime.model.core.ext.BytesModelExtContext;
import io.aklivity.zilla.runtime.model.core.ext.BytesModelExtFactorySpi;
import io.aklivity.zilla.runtime.model.core.ext.BytesTransform;

/**
 * A generic, business-agnostic test-only extension registered solely under {@code src/test} so it never
 * ships in the production jar. It uppercases a value (proving apply, fragment accumulation, and
 * OVERFLOW/drain all work through a live engine), and signals {@link BytesTransform#OMIT} for a single
 * {@code 0x00} byte (proving the reject-on-omit path works through a live engine too) -- exercising the
 * same ModelExt composition mechanism a real installed extension (e.g. zilla-plus's disclosure) relies on,
 * without model-core needing to know anything about what a real extension might do with it.
 */
public final class TestUppercaseBytesModelExtFactorySpi implements BytesModelExtFactorySpi
{
    @Override
    public String type()
    {
        return "test";
    }

    @Override
    public BytesModelExt create(
        Configuration config)
    {
        return new BytesModelExt()
        {
            @Override
            public String name()
            {
                return "test-uppercase";
            }

            @Override
            public BytesModelExtContext supply(
                EngineContext context)
            {
                return options -> stream -> stream.transform(TestUppercaseBytesModelExtFactorySpi::uppercase);
            }
        };
    }

    private static int uppercase(
        DirectBufferEx value,
        int index,
        int length,
        MutableDirectBufferEx dst,
        int dstIndex)
    {
        int produced;
        if (length == 1 && value.getByte(index) == 0)
        {
            produced = BytesTransform.OMIT;
        }
        else
        {
            for (int i = 0; i < length; i++)
            {
                byte value0 = value.getByte(index + i);
                dst.putByte(dstIndex + i, (byte) Character.toUpperCase((char) value0));
            }
            produced = length;
        }
        return produced;
    }
}
