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

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;
import io.aklivity.zilla.runtime.engine.metrics.Collector;
import io.aklivity.zilla.runtime.engine.metrics.reader.MetricsReader;
import io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpEndpointConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpExporterConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.config.OtlpSignalsConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.serializer.OtlpMetricsSerializer;

public class OltpExporterHandler implements ExporterHandler
{
    private static final long RETRY_INTERVAL = Duration.ofSeconds(5).toMillis();
    private static final long WARNING_INTERVAL = Duration.ofMinutes(5).toMillis();
    private static final String DEFAULT_METRICS_URI = "/v1/metrics";

    private final EngineContext context;
    private final Set<OtlpSignalsConfig.Signals> signals;
    private final URI metricsUrl;
    private final long interval;
    private final Collector collector;
    private final LongFunction<KindConfig> resolveKind;
    private final List<AttributeConfig> attributes;
    private final HttpClient httpClient;

    private OtlpMetricsSerializer serializer;
    private long lastAttempt;
    private long lastSuccess;
    private boolean warning;

    public OltpExporterHandler(
        EngineConfiguration config,
        EngineContext context,
        OtlpExporterConfig exporter,
        Collector collector,
        LongFunction<KindConfig> resolveKind,
        List<AttributeConfig> attributes)
    {
        this.context = context;
        this.metricsUrl = createMetricsUrl(exporter.options().endpoint);
        this.signals = exporter.options().signals.signals;
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
        MetricsReader metrics = new MetricsReader(collector, context::supplyLocalName);
        serializer = new OtlpMetricsSerializer(metrics.records(), attributes, context::resolveMetric, resolveKind);
        lastAttempt = 0;
        lastSuccess = System.currentTimeMillis();
        warning = false;
    }

    @Override
    public int export()
    {
        long now = System.currentTimeMillis();
        if (now - lastSuccess < interval)
        {
            return 0;
        }
        if (now - lastAttempt < RETRY_INTERVAL)
        {
            return 0;
        }
        if (now - lastSuccess >= WARNING_INTERVAL && !warning)
        {
            System.out.format("Warning: Could not successfully publish data to OpenTelemetry Collector for %d seconds.%n",
                Duration.ofMillis(now - lastSuccess).toSeconds());
            warning = true;
        }
        post();
        return 1;
    }

    // required for testing
    URI metricsUrl()
    {
        return metricsUrl;
    }

    private URI createMetricsUrl(
        OtlpEndpointConfig endpoint)
    {
        URI result;
        URI location = URI.create(endpoint.location);
        if (endpoint.overrides != null && endpoint.overrides.metrics != null)
        {
            URI metricsOverride = URI.create(endpoint.overrides.metrics);
            if (metricsOverride.isAbsolute())
            {
                result = metricsOverride;
            }
            else
            {
                result = location.resolve(metricsOverride);
            }
        }
        else
        {
            result = location.resolve(DEFAULT_METRICS_URI);
        }
        return result;
    }

    private void post()
    {
        String json = serializer.serializeAll();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(metricsUrl)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        lastAttempt = System.currentTimeMillis();
        CompletableFuture<HttpResponse<String>> response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        response.thenAccept(this::handleResponse);
    }

    private void handleResponse(
        HttpResponse<String> response)
    {
        if (response.statusCode() == HttpURLConnection.HTTP_OK)
        {
            lastSuccess = System.currentTimeMillis();
            warning = false;
        }
    }

    @Override
    public void stop()
    {
    }
}
