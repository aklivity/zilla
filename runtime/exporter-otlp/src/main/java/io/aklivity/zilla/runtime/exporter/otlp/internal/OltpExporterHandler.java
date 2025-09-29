/*
 * Copyright 2021-2024 Aklivity Inc
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

import static io.aklivity.zilla.runtime.exporter.otlp.config.OtlpOptionsConfig.OtlpSignalsConfig.LOGS;
import static io.aklivity.zilla.runtime.exporter.otlp.config.OtlpOptionsConfig.OtlpSignalsConfig.METRICS;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.LongFunction;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.engine.config.ExporterConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;
import io.aklivity.zilla.runtime.engine.metrics.Collector;
import io.aklivity.zilla.runtime.engine.metrics.reader.MetricsReader;
import io.aklivity.zilla.runtime.engine.security.Trusted;
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;
import io.aklivity.zilla.runtime.exporter.otlp.config.OtlpEndpointConfig;
import io.aklivity.zilla.runtime.exporter.otlp.config.OtlpOptionsConfig;
import io.aklivity.zilla.runtime.exporter.otlp.config.OtlpOverridesConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.serializer.EventReader;
import io.aklivity.zilla.runtime.exporter.otlp.internal.serializer.OtlpLogsSerializer;
import io.aklivity.zilla.runtime.exporter.otlp.internal.serializer.OtlpMetricsSerializer;

public class OltpExporterHandler implements ExporterHandler
{
    private static final String HTTP = "http";
    private static final String HTTPS = "https";

    private final long retryInterval;
    private final Duration timeoutInterval;
    private final long warningInterval;
    private final EngineContext context;
    private final Set<OtlpOptionsConfig.OtlpSignalsConfig> signals;
    private final String protocol;
    private final URI metricsEndpoint;
    private final URI logsEndpoint;
    private final long interval;
    private final Collector collector;
    private final LongFunction<KindConfig> resolveKind;
    private final List<AttributeConfig> attributes;
    private final HttpClient client;
    private final String authorization;
    private final Consumer<HttpResponse<String>> responseHandler;

    private OtlpMetricsSerializer metricsSerializer;
    private OtlpLogsSerializer logsSerializer;
    private long nextAttempt;
    private long lastSuccess;
    private boolean warningLogged;
    private CompletableFuture<HttpResponse<String>> metricsResponse;
    private CompletableFuture<HttpResponse<String>> logsResponse;

    public OltpExporterHandler(
        OltpConfiguration config,
        EngineContext context,
        ExporterConfig exporter,
        Collector collector,
        LongFunction<KindConfig> resolveKind,
        List<AttributeConfig> attributes)
    {
        this.retryInterval = config.retryInterval().toMillis();
        this.timeoutInterval = config.timeoutInterval();
        this.warningInterval = config.warningInterval().toMillis();
        this.context = context;
        OtlpOptionsConfig options = (OtlpOptionsConfig) exporter.options;
        OtlpEndpointConfig endpoint = options.endpoint;
        URI location = endpoint.location;
        OtlpOverridesConfig overrides = endpoint.overrides;
        this.metricsEndpoint = location.resolve(overrides.metrics);
        this.logsEndpoint = location.resolve(overrides.logs);
        this.signals = options.signals;
        this.protocol = endpoint.protocol;
        this.interval = options.interval.toMillis();
        this.collector = collector;
        this.resolveKind = resolveKind;
        this.attributes = attributes;
        this.responseHandler = this::handleResponse;
        LongFunction<VaultHandler> supplyVault = context::supplyVault;
        VaultHandler vault = supplyVault.apply(exporter.vaultId);

        HttpClient client;
        if (HTTPS.equalsIgnoreCase(location.getScheme()))
        {
            try
            {
                KeyManagerFactory keys = newKeys(vault, options.keys);
                TrustManagerFactory trust = newTrust(config, vault, options.trust, options.trustcacerts);

                KeyManager[] keyManagers = null;
                if (keys != null)
                {
                    keyManagers = keys.getKeyManagers();
                }

                TrustManager[] trustManagers = null;
                if (trust != null)
                {
                    trustManagers = trust.getTrustManagers();
                }

                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(keyManagers, trustManagers, new SecureRandom());

                client = HttpClient.newBuilder().sslContext(sslContext).build();
            }
            catch (Exception ex)
            {
                client = HttpClient.newHttpClient();
                LangUtil.rethrowUnchecked(ex);
            }
        }
        else
        {
            client = HttpClient.newHttpClient();
        }
        this.client = client;
        this.authorization = options.authorization;
    }

    @Override
    public void start()
    {
        assert HTTP.equals(protocol);

        MetricsReader metrics = new MetricsReader(collector, context::supplyLocalName);
        metricsSerializer = new OtlpMetricsSerializer(metrics.records(), attributes, context::resolveMetric, resolveKind);
        EventReader eventReader = new EventReader(context);
        logsSerializer = new OtlpLogsSerializer(attributes, eventReader);
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
            exportMetrics(now);
            exportLogs(now);
            if (!warningLogged && now - lastSuccess > warningInterval)
            {
                System.out.format(
                    "Warning: The otlp exporter could not successfully push data to the specified endpoint for %d seconds.%n",
                    Duration.ofMillis(warningInterval).toSeconds());
                warningLogged = true;
            }
            workDone = 1;
        }
        return workDone;
    }

    private void exportMetrics(
        long now)
    {
        if (signals.contains(METRICS) && (metricsResponse == null || metricsResponse.isDone()))
        {
            String metricsJson = metricsSerializer.serializeAll();
            HttpRequest.Builder metricsRequest = HttpRequest.newBuilder()
                .uri(metricsEndpoint)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(metricsJson))
                .timeout(timeoutInterval);

            if (authorization != null)
            {
                metricsRequest.header("authorization", authorization);
            }

            metricsResponse = client.sendAsync(metricsRequest.build(), HttpResponse.BodyHandlers.ofString());
            metricsResponse.thenAccept(responseHandler);
            nextAttempt = now + retryInterval;
        }
    }

    private void exportLogs(
        long now)
    {
        if (signals.contains(LOGS) && (logsResponse == null || logsResponse.isDone()))
        {
            String logsJson = logsSerializer.serializeAll();
            HttpRequest.Builder logsRequest = HttpRequest.newBuilder()
                .uri(logsEndpoint)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(logsJson))
                .timeout(timeoutInterval);

            if (authorization != null)
            {
                logsRequest.header("authorization", authorization);
            }

            logsResponse = client.sendAsync(logsRequest.build(), HttpResponse.BodyHandlers.ofString());
            logsResponse.thenAccept(responseHandler);
            nextAttempt = now + retryInterval;
        }
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

    private TrustManagerFactory newTrust(
        Configuration config,
        VaultHandler vault,
        List<String> trustNames,
        boolean trustcacerts)
    {
        TrustManagerFactory trust = null;

        try
        {
            KeyStore cacerts = trustcacerts ? Trusted.cacerts(config) : null;

            if (vault != null)
            {
                trust = vault.initTrust(trustNames, cacerts);
            }
            else if (cacerts != null)
            {
                TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                factory.init(cacerts);
                trust = factory;
            }
        }
        catch (Exception ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return trust;
    }

    private KeyManagerFactory newKeys(
        VaultHandler vault,
        List<String> keyNames)
    {
        KeyManagerFactory keys = null;

        keys:
        try
        {
            if (vault == null)
            {
                break keys;
            }

            if (keyNames != null)
            {
                keys = vault.initKeys(keyNames);
            }
        }
        catch (Exception ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return keys;
    }
}
