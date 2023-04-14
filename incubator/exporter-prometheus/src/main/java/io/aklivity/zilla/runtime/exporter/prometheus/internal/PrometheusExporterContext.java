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

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.ExporterConfig;
import io.aklivity.zilla.runtime.engine.exporter.ExporterContext;
import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;

public class PrometheusExporterContext implements ExporterContext
{
    public PrometheusExporterContext(
        EngineContext context)
    {
        // TODO: Ati
        System.out.println("PrometheusExporterContext.constructor");
    }

    @Override
    public ExporterHandler attach(
        ExporterConfig config)
    {
        // TODO: Ati
        return new PrometheusExporterHandler();
    }

    @Override
    public void detach(
        long exporterId)
    {
        // TODO: Ati
    }
}
