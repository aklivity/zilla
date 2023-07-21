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

import static io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpOptionsConfig.OtlpSignalsConfig.METRICS;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.LongFunction;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;
import io.aklivity.zilla.runtime.engine.metrics.Collector;
import io.aklivity.zilla.runtime.engine.metrics.reader.MetricsReader;
import io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpExporterConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpOptionsConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.serializer.OtlpMetricsSerializer;

public class OltpExporterHandler implements ExporterHandler
{
    private static final String HTTP = "http";

    private final long retryInterval;
    private final long warningInterval;
    private final EngineContext context;
    private final Set<OtlpOptionsConfig.OtlpSignalsConfig> signals;
    private final String protocol;
    private final URI metricsEndpoint;
    private final long interval;
    private final Collector collector;
    private final LongFunction<KindConfig> resolveKind;
    private final List<AttributeConfig> attributes;
    private final HttpClient httpClient;
    private final Consumer<HttpResponse<String>> responseHandler;

    private OtlpMetricsSerializer serializer;
    private long nextAttempt;
    private long lastSuccess;
    private boolean warningLogged;

    public OltpExporterHandler(
        OltpConfiguration config,
        EngineContext context,
        OtlpExporterConfig exporter,
        Collector collector,
        LongFunction<KindConfig> resolveKind,
        List<AttributeConfig> attributes)
    {
        this.retryInterval = config.retryInterval();
        this.warningInterval = config.warningInterval();
        this.context = context;
        this.metricsEndpoint = exporter.resolveMetrics();
        this.signals = exporter.resolveSignals();
        this.protocol = exporter.resolveProtocol();
        this.interval = exporter.resolveInterval();
        this.collector = collector;
        this.resolveKind = resolveKind;
        this.attributes = attributes;
        this.httpClient = HttpClient.newBuilder().build();
        this.responseHandler = this::handleResponse;
    }

    @Override
    public void start()
    {
        assert signals.contains(METRICS);
        assert HTTP.equals(protocol);

        MetricsReader metrics = new MetricsReader(collector, context::supplyLocalName);
        serializer = new OtlpMetricsSerializer(metrics.records(), attributes, context::resolveMetric, resolveKind);
        lastSuccess = System.currentTimeMillis();
        nextAttempt = lastSuccess + interval;
    }

    @Override
    public int export()
    {
        int workDone = 0;
        long now = System.currentTimeMillis();
        if (now >= nextAttempt)
        {
            String json = serializer.serializeAll();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(metricsEndpoint)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            CompletableFuture<HttpResponse<String>> response =
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            response.thenAccept(responseHandler);
            nextAttempt = now + retryInterval;
            if (!warningLogged && now - lastSuccess > warningInterval)
            {
                System.out.format(
                    "Warning: Could not successfully publish data to OpenTelemetry Collector for %d seconds.%n",
                    Duration.ofMillis(warningInterval).toSeconds());
                warningLogged = true;
            }
            workDone = 1;
        }
        return workDone;
    }

    private void handleResponse(
        HttpResponse<String> response)
    {
        if (response.statusCode() == HttpURLConnection.HTTP_OK)
        {
            lastSuccess = System.currentTimeMillis();
            nextAttempt = lastSuccess + interval;
            warningLogged = false;
        }
    }

    @Override
    public void stop()
    {
    }
}
