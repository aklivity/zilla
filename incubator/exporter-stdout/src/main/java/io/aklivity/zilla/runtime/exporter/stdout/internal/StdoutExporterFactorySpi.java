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
package io.aklivity.zilla.runtime.exporter.stdout.internal;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.common.feature.Incubating;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;
import io.aklivity.zilla.runtime.engine.exporter.Exporter;
import io.aklivity.zilla.runtime.engine.exporter.ExporterFactorySpi;

@Incubating
public class StdoutExporterFactorySpi implements ExporterFactorySpi
{
    @Override
    public String type()
    {
        return StdoutExporter.NAME;
    }

    @Override
    public Exporter create(
        Configuration config)
    {
        Map<String, EventFormatterSpi> formatters = ServiceLoader.load(EventFormatterSpi.class)
            .stream()
            .collect(Collectors.toMap(
                provider -> provider.get().type(),
                provider -> provider.get()
            ));
        return new StdoutExporter(new StdoutConfiguration(config), formatters);
    }
}
