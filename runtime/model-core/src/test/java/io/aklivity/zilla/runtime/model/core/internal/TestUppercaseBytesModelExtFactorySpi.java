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

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.model.core.ext.BytesModelExt;
import io.aklivity.zilla.runtime.model.core.ext.BytesModelExtContext;
import io.aklivity.zilla.runtime.model.core.ext.BytesModelExtFactorySpi;
import io.aklivity.zilla.runtime.model.core.ext.BytesModelExtHandler;
import io.aklivity.zilla.runtime.model.core.ext.BytesTransformable;

/**
 * A generic, business-agnostic test-only extension registered solely under {@code src/test} so it never
 * ships in the production jar. It exercises the same ModelExt composition mechanism a real installed
 * extension relies on, through a live engine, without model-core needing to know anything about what a
 * real extension might do with it.
 * <p>
 * It overrides {@code decode} only, leaving the encode direction exactly as it would be with no extension
 * installed. On decode it uppercases a value (proving apply, fragment streaming, and OVERFLOW/drain all
 * work end-to-end), withholds a single {@code 0x00} byte, and rejects a single {@code 0xFF} byte with a
 * diagnostic -- the two terminal outcomes being distinguishable by the event each does or does not raise.
 * </p>
 */
public final class TestUppercaseBytesModelExtFactorySpi implements BytesModelExtFactorySpi
{
    // held as int, not byte: a byte 0xFF widens to -1, which is the transform's "no marker" sentinel
    static final int WITHHOLD = 0x00;
    static final int REJECT = 0xFF;
    static final String DIAGNOSTIC = "test-uppercase rejected";

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
                return options -> new Handler();
            }
        };
    }

    private static final class Handler implements BytesModelExtHandler
    {
        @Override
        public <T extends BytesTransformable<T>> T decode(
            T stream)
        {
            return stream.transform(new UppercaseBytesTransform(WITHHOLD, REJECT, DIAGNOSTIC));
        }
    }
}
