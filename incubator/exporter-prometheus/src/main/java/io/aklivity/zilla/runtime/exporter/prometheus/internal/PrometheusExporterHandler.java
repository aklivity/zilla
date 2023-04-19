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
import static io.aklivity.zilla.runtime.exporter.prometheus.internal.config.PrometheusEndpointConfig.DEFAULT;
import static java.net.HttpURLConnection.HTTP_BAD_METHOD;
import static java.net.HttpURLConnection.HTTP_OK;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import org.agrona.LangUtil;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.config.ExporterConfig;
import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.config.PrometheusEndpointConfig;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.config.PrometheusOptionsConfig;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.descriptor.PrometheusMetricDescriptor;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.layout.MetricsLayout;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.processor.LayoutManager;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.processor.MetricsProcessor;

public class PrometheusExporterHandler implements ExporterHandler
{
    private final PrometheusEndpointConfig endpoint;
    private final LayoutManager manager;
    private final PrometheusMetricDescriptor metricDescriptor;

    private Map<Metric.Kind, List<MetricsLayout>> layouts;
    private MetricsProcessor metrics;
    private HttpServer server;

    public PrometheusExporterHandler(
        EngineConfiguration config,
        ExporterConfig exporter)
    {
        this.endpoint = resolveEndpoint(exporter);
        this.manager = new LayoutManager(config.directory());
        this.metricDescriptor = new PrometheusMetricDescriptor(config);
    }

    @Override
    public void start()
    {
        try
        {
            layouts = Map.of(
                COUNTER, manager.countersLayouts(),
                GAUGE, manager.gaugesLayouts(),
                HISTOGRAM, manager.histogramsLayouts()
            );
            metrics = new MetricsProcessor(layouts, manager.labels(), metricDescriptor::kind, metricDescriptor::name,
                metricDescriptor::description, null, null);
            server = HttpServer.create(new InetSocketAddress(endpoint.port), 0);
            server.createContext(endpoint.path, new MetricsHttpHandler());
            server.start();
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
    }

    @Override
    public int export()
    {
        return 0;
    }

    @Override
    public void stop()
    {
        if (server != null)
        {
            server.stop(0);
            server = null;
        }
        layouts.keySet().stream().flatMap(kind -> layouts.get(kind).stream()).forEach(MetricsLayout::close);
    }

    private PrometheusEndpointConfig resolveEndpoint(
        ExporterConfig exporter)
    {
        PrometheusOptionsConfig options = (PrometheusOptionsConfig) exporter.options;
        PrometheusEndpointConfig endpoint;

        if (options != null && options.endpoints != null && options.endpoints.length > 0)
        {
            endpoint = options.endpoints[0];
            if (endpoint.scheme == null)
            {
                endpoint.scheme = DEFAULT.scheme;
            }
            if (endpoint.port == 0)
            {
                endpoint.port = DEFAULT.port;
            }
            if (endpoint.path == null)
            {
                endpoint.path = DEFAULT.path;
            }
        }
        else
        {
            endpoint = DEFAULT;
        }
        return endpoint;
    }

    private String generateOutput()
    {
        String output = "";
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);
        metrics.print(out);
        try
        {
            output = os.toString("UTF8");
        }
        catch (UnsupportedEncodingException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
        return output;
    }

    private class MetricsHttpHandler implements HttpHandler
    {
        private static final int NO_RESPONSE_BODY = -1;

        public void handle(
            HttpExchange exchange) throws IOException
        {
            if ("GET".equals(exchange.getRequestMethod()))
            {
                String response = generateOutput();
                exchange.sendResponseHeaders(HTTP_OK, response.length());
                exchange.getResponseBody().write(response.getBytes());
            }
            else
            {
                exchange.sendResponseHeaders(HTTP_BAD_METHOD, NO_RESPONSE_BODY);
            }
            exchange.close();
        }
    }
}
