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

import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.COUNTER;
import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.GAUGE;
import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.HISTOGRAM;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.layout.MetricsLayout;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.processor.LayoutManager;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.processor.MetricsProcessor;

public class PrometheusExporterHandler implements ExporterHandler
{
    private final LayoutManager manager;
    private Map<Metric.Kind, List<MetricsLayout>> layouts;
    private MetricsProcessor metrics;

    public PrometheusExporterHandler()
    {
        // TODO: Ati
        System.out.println("PrometheusExporterHandler.constructor");
        manager = new LayoutManager();
    }

    @Override
    public void start()
    {
        // TODO: Ati
        System.out.println("PrometheusExporterHandler.start");
        try
        {
            layouts = Map.of(
                COUNTER, manager.countersLayouts(),
                GAUGE, manager.gaugesLayouts(),
                HISTOGRAM, manager.histogramsLayouts()
            );
            metrics = new MetricsProcessor(layouts, manager.labels(), null, null);
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
    }

    @Override
    public int export()
    {
        // TODO: Ati
        System.out.println("PrometheusExporterHandler.export");
        metrics.print(System.out);
        try
        {
            Thread.sleep(5 * 1000L);
        }
        catch (InterruptedException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
        return 0;
    }

    @Override
    public void stop()
    {
        // TODO: Ati
        System.out.println("PrometheusExporterHandler.stop");
        layouts.keySet().stream().flatMap(kind -> layouts.get(kind).stream()).forEach(MetricsLayout::close);
    }
}
