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
 * A second test-only extension registered solely under {@code src/test}, installed alongside
 * {@link TestUppercaseBytesModelExtFactorySpi} so more than one extension composes at once.
 * <p>
 * It overrides {@code encode} only, so it proves an extension reaches the encode direction end-to-end
 * while the other proves an extension overriding only {@code decode} leaves encode untouched. Its stage
 * applies only to a value opening with {@link #MARKER}, so every other scenario's values flow through the
 * encode direction exactly as they would with no extension installed.
 * </p>
 */
public final class TestMarkedBytesModelExtFactorySpi implements BytesModelExtFactorySpi
{
    static final int MARKER = 0x01;

    @Override
    public String type()
    {
        return "test-marked";
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
                return "test-marked";
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
        public <T extends BytesTransformable<T>> T encode(
            T stream)
        {
            return stream.transform(new UppercaseBytesTransform(MARKER,
                UppercaseBytesTransform.NO_MARKER, UppercaseBytesTransform.NO_MARKER, null));
        }
    }
}
