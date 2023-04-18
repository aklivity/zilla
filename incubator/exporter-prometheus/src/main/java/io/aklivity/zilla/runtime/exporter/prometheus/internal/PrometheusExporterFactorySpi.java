/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.exporter.prometheus.internal;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.exporter.Exporter;
import io.aklivity.zilla.runtime.engine.exporter.ExporterFactorySpi;

public class PrometheusExporterFactorySpi implements ExporterFactorySpi
{
    @Override
    public String type()
    {
        return PrometheusExporter.NAME;
    }

    @Override
    public Exporter create(
        Configuration config)
    {
        return new PrometheusExporter(new EngineConfiguration(config));
    }
}
