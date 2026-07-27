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
package io.aklivity.zilla.config.exporter.otlp;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ExporterConfig;
import io.aklivity.zilla.config.engine.ExporterConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.config.exporter.otlp.internal.OtlpExporterInfo;

public final class OtlpExporterConfigBuilder<T> extends ExporterConfigBuilder<T, OtlpExporterConfigBuilder<T>>
{
    OtlpExporterConfigBuilder(
        Function<ExporterConfig, T> mapper)
    {
        super(mapper);
        type(OtlpExporterInfo.TYPE);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<OtlpExporterConfigBuilder<T>> thisType()
    {
        return (Class<OtlpExporterConfigBuilder<T>>) getClass();
    }

    public OtlpOptionsConfigBuilder<OtlpExporterConfigBuilder<T>> options()
    {
        return new OtlpOptionsConfigBuilder<>(this::options);
    }

    @Override
    protected ExporterConfig newExporter(
        String namespace,
        String name,
        String type,
        String vault,
        OptionsConfig options)
    {
        return new OtlpExporterConfig(namespace, name, type, vault, options);
    }
}
