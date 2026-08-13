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
package io.aklivity.zilla.runtime.model.avro.internal;

import java.util.List;

import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.model.avro.AvroModelConfig;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelContext;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.model.avro.ext.AvroModelExt;
import io.aklivity.zilla.runtime.model.avro.ext.AvroModelExtContext;

public class AvroModelContext implements ModelContext
{
    private final EngineContext context;
    private final AvroModelConfiguration config;
    private final List<AvroModelExtContext> exts;

    public AvroModelContext(
        Configuration config,
        EngineContext context,
        List<AvroModelExt> exts)
    {
        this.config = new AvroModelConfiguration(config);
        this.context = context;
        this.exts = exts.stream().map(ext -> ext.supply(context)).toList();
    }

    @Override
    public ModelHandler supplyHandler(
        ModelConfig options)
    {
        return new AvroModelHandlerImpl(config, AvroModelConfig.class.cast(options), context, exts);
    }
}
