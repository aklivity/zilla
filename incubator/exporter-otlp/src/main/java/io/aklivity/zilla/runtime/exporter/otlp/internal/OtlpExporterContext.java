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
package io.aklivity.zilla.runtime.exporter.otlp.internal;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.engine.config.ExporterConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.exporter.ExporterContext;
import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;
import io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpExporterConfig;

public class OtlpExporterContext implements ExporterContext
{
    private final EngineConfiguration config;
    private final EngineContext context;

    public OtlpExporterContext(
        EngineConfiguration config,
        EngineContext context)
    {
        this.config = config;
        this.context = context;
    }

    @Override
    public ExporterHandler attach(
        ExporterConfig exporter,
        List<AttributeConfig> attributes,
        Function<String, KindConfig> findBindingKind)
    {
        OtlpExporterConfig otlpExporter = new OtlpExporterConfig(exporter);
        return new OltpExporterHandler(config, context, otlpExporter, findBindingKind, attributes);
    }

    @Override
    public void detach(
        long exporterId)
    {
    }
}
