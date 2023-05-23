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

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;
import io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpEndpointConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpExporterConfig;

// TODO: Ati
public class OltpExporterHandler implements ExporterHandler
{
    //private final LayoutManager manager;
    //private final PrometheusMetricDescriptor metricDescriptor;
    private final EngineContext context;
    private final OtlpEndpointConfig[] endpoints;

    //private Map<Metric.Kind, List<MetricsLayout>> layouts;
    //private MetricsProcessor metrics;

    public OltpExporterHandler(
        EngineConfiguration config,
        EngineContext context,
        OtlpExporterConfig exporter)
    {
        //this.manager = new LayoutManager(config.directory());
        //this.metricDescriptor = new PrometheusMetricDescriptor(context::resolveMetric);
        this.context = context;
        this.endpoints = exporter.options().endpoints; // options is required, at least one endpoint is required
    }

    @Override
    public void start()
    {
        /*try
        {
            layouts = Map.of(
                COUNTER, manager.countersLayouts(),
                GAUGE, manager.gaugesLayouts(),
                HISTOGRAM, manager.histogramsLayouts()
            );
            metrics = new MetricsProcessor(layouts, context::supplyLocalName, metricDescriptor::kind,
                metricDescriptor::name, metricDescriptor::description, null, null);
            for (PrometheusEndpointConfig endpoint : endpoints)
            {
                HttpServer server = HttpServer.create(new InetSocketAddress(endpoint.port), 0);
                server.createContext(endpoint.path, new MetricsHttpHandler());
                server.start();
                servers.put(endpoint.port, server);
            }
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }*/
    }

    @Override
    public int export()
    {
        return 0;
    }

    @Override
    public void stop()
    {
    }
}
