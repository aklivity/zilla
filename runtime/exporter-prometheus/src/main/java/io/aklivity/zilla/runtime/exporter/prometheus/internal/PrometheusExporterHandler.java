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
package io.aklivity.zilla.runtime.exporter.prometheus.internal;

import static java.net.HttpURLConnection.HTTP_BAD_METHOD;
import static java.net.HttpURLConnection.HTTP_OK;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;

import org.agrona.LangUtil;
import org.agrona.collections.Int2ObjectHashMap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;
import io.aklivity.zilla.runtime.engine.metrics.processor.MetricsProcessor;
import io.aklivity.zilla.runtime.engine.metrics.processor.MetricsProcessorFactory;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.config.PrometheusEndpointConfig;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.config.PrometheusExporterConfig;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.descriptor.PrometheusMetricDescriptor;
import io.aklivity.zilla.runtime.exporter.prometheus.internal.printer.MetricsPrinter;

public class PrometheusExporterHandler implements ExporterHandler
{
    private final PrometheusMetricDescriptor descriptor;
    private final PrometheusEndpointConfig[] endpoints;
    private final Map<Integer, HttpServer> servers;
    private final Path engineDirectory;

    private MetricsProcessor metricsProcessor;
    private MetricsPrinter printer;

    public PrometheusExporterHandler(
        EngineConfiguration config,
        EngineContext context,
        PrometheusExporterConfig exporter)
    {
        this.engineDirectory = config.directory();
        this.descriptor = new PrometheusMetricDescriptor(context::resolveMetric);
        this.endpoints = exporter.options().endpoints; // options is required, at least one endpoint is required
        this.servers = new Int2ObjectHashMap<>();
    }

    @Override
    public void start()
    {
        MetricsProcessorFactory factory = new MetricsProcessorFactory(engineDirectory, null, null);
        metricsProcessor = factory.create();
        printer = new MetricsPrinter(metricsProcessor, descriptor::kind, descriptor::name, descriptor::description);

        for (PrometheusEndpointConfig endpoint : endpoints)
        {
            HttpServer server = null;
            try
            {
                server = HttpServer.create(new InetSocketAddress(endpoint.port), 0);
            }
            catch (IOException ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }
            server.createContext(endpoint.path, new MetricsHttpHandler());
            server.start();
            servers.put(endpoint.port, server);
        }

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
        for (int port : servers.keySet())
        {
            HttpServer server = servers.remove(port);
            server.stop(0);
        }
        if (metricsProcessor != null)
        {
            metricsProcessor.close();
        }
    }

    private String generateOutput()
    {
        String output = "";
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);
        printer.print(out);
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
