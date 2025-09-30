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
package io.aklivity.zilla.runtime.exporter.otlp.internal.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.List;
import java.util.function.LongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.ExporterConfig;
import io.aklivity.zilla.runtime.engine.security.Trusted;
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;
import io.aklivity.zilla.runtime.exporter.otlp.config.OtlpEndpointConfig;
import io.aklivity.zilla.runtime.exporter.otlp.config.OtlpOptionsConfig;
import io.aklivity.zilla.runtime.exporter.otlp.config.OtlpOverridesConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.OltpConfiguration;

public class OtlpExporterConfig
{
    private static final String HTTPS = "https";
    private static final Pattern URI_PATTERN = Pattern.compile("^https?://\\S+$");

    private final OltpConfiguration config;
    private final OtlpOptionsConfig options;
    private final VaultHandler vault;
    private final Matcher matcher;
    private final URI location;
    private final OtlpOverridesConfig overrides;

    public OtlpExporterConfig(
        OltpConfiguration config,
        EngineContext context,
        ExporterConfig exporter)
    {
        this.config = config;
        this.options = (OtlpOptionsConfig)exporter.options;
        LongFunction<VaultHandler> supplyVault = context::supplyVault;
        vault = supplyVault.apply(exporter.vaultId);
        this.matcher = URI_PATTERN.matcher("");
        OtlpEndpointConfig endpoint = options.endpoint;
        this.location = endpoint.location;
        this.overrides = endpoint.overrides;
    }

    public URI resolveMetrics()
    {
        URI endpoint;
        if (matcher.reset(overrides.metrics.toString()).matches())
        {
            endpoint = overrides.metrics;
        }
        else
        {
            endpoint = location.resolve(overrides.metrics);
        }

        return endpoint;
    }

    public URI resolveLogs()
    {
        URI endpoint;
        if (matcher.reset(overrides.logs.toString()).matches())
        {
            endpoint = overrides.logs;
        }
        else
        {
            endpoint = location.resolve(overrides.logs);
        }

        return endpoint;
    }

    public HttpClient supplyMetricsClient()
    {
        return supplyHttpClient(resolveMetrics());
    }

    public HttpClient supplyLogsClient()
    {
        return supplyHttpClient(resolveLogs());
    }

    private HttpClient supplyHttpClient(
        URI location)
    {
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
        return client;
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
