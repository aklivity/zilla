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

import static io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpSignalsConfig.Signals.METRICS;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongFunction;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;
import io.aklivity.zilla.runtime.engine.metrics.Collector;
import io.aklivity.zilla.runtime.engine.metrics.reader.MetricsReader;
import io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpExporterConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpSignalsConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.serializer.OtlpMetricsSerializer;

public class OltpExporterHandler implements ExporterHandler
{
    private static final String HTTP = "http";

    private final OltpConfiguration config;
    private final EngineContext context;
    private final Set<OtlpSignalsConfig.Signals> signals;
    private final String protocol;
    private final URI metricsUrl;
    private final long interval;
    private final Collector collector;
    private final LongFunction<KindConfig> resolveKind;
    private final List<AttributeConfig> attributes;
    private final HttpClient httpClient;

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
        this.config = config;
        this.context = context;
        this.metricsUrl = exporter.options().endpoint.resolveMetrics();
        this.signals = exporter.options().signals.signals;
        this.protocol = exporter.options().endpoint.protocol;
        this.interval = Duration.ofSeconds(exporter.options().interval).toMillis();
        this.collector = collector;
        this.resolveKind = resolveKind;
        this.attributes = attributes;
        this.httpClient = HttpClient.newBuilder().build();
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
        int result;
        long now = System.currentTimeMillis();
        if (now < nextAttempt)
        {
            result = 0;
        }
        else
        {
            String json = serializer.serializeAll();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(metricsUrl)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            CompletableFuture<HttpResponse<String>> response =
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            response.thenAccept(this::handleResponse);
            nextAttempt = now + config.retryInterval();
            if (!warningLogged && now - lastSuccess > config.warningInterval())
            {
                System.out.format(
                    "Warning: Could not successfully publish data to OpenTelemetry Collector for %d seconds.%n",
                    Duration.ofMillis(config.warningInterval()).toSeconds());
                warningLogged = true;
            }
            result = 1;
        }
        return result;
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
