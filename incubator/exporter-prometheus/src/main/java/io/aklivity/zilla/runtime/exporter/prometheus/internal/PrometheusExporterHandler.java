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
        //System.out.println(generateOutput());
        //System.out.println("---");
        startServer();
        return 0;
    }

    @Override
    public void stop()
    {
        // TODO: Ati
        System.out.println("PrometheusExporterHandler.stop");
        layouts.keySet().stream().flatMap(kind -> layouts.get(kind).stream()).forEach(MetricsLayout::close);
    }

    private void startServer()
    {
        int port = 9090;
        try
        {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/metrics", new MyHttpHandler());
            server.setExecutor(null);
            server.start();
            // TODO: Ati - stop server etc
            while (true)
            {
                // block forever
            }
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
        // TODO: Ati
        System.out.println("http server listening on port " + port);
    }

    // TODO: Ati
    class MyHttpHandler implements HttpHandler
    {
        public void handle(
            HttpExchange t) throws IOException
        {
            if ("GET".equals(t.getRequestMethod()))
            {
                String response = generateOutput();
                t.sendResponseHeaders(200, response.length());
                t.getResponseBody().write(response.getBytes());
            }
            else
            {
                t.sendResponseHeaders(405, -1);
            }
            t.close();
        }
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
}
