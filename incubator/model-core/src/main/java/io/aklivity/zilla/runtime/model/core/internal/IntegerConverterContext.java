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
package io.aklivity.zilla.runtime.model.core.internal;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.ConverterConfig;
import io.aklivity.zilla.runtime.engine.converter.ConverterContext;
import io.aklivity.zilla.runtime.engine.converter.ConverterHandler;
import io.aklivity.zilla.runtime.model.core.config.IntegerConverterConfig;

public class IntegerConverterContext implements ConverterContext
{
    public IntegerConverterContext(
        EngineContext context)
    {
    }

    @Override
    public ConverterHandler supplyReadHandler(
        ConverterConfig config)
    {
        return supply(config);
    }

    @Override
    public ConverterHandler supplyWriteHandler(
        ConverterConfig config)
    {
        return supply(config);
    }

    private IntegerConverterHandler supply(
        ConverterConfig config)
    {
        return new IntegerConverterHandler(IntegerConverterConfig.class.cast(config));
    }
}
