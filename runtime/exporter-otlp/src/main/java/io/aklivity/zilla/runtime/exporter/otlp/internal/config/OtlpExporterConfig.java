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

    public final OtlpOptionsConfig options;
    public final URI metrics;
    public final URI logs;

    private final OltpConfiguration config;
    private final VaultHandler vault;

    public OtlpExporterConfig(
        OltpConfiguration config,
        EngineContext context,
        ExporterConfig exporter)
    {
        this.config = config;
        this.options = (OtlpOptionsConfig)exporter.options;
        this.vault = context.supplyVault(exporter.vaultId);
        OtlpEndpointConfig endpoint = options.endpoint;
        URI location = endpoint.location;
        OtlpOverridesConfig overrides = endpoint.overrides;
        this.metrics = resolveOverride(location, overrides.metrics);
        this.logs = resolveOverride(location, overrides.logs);
    }

        private URI resolveOverride(
        URI location,
        String override)
    {
        URI overrideURI = URI.create(override);
        
        if (overrideURI.isAbsolute() || overrideURI.getAuthority() != null)
        {
            return overrideURI;
        }
        
        String basePath = location.getPath();
        if (basePath == null || basePath.isEmpty())
        {
            basePath = "/";
        }
        
        if (!basePath.endsWith("/"))
        {
            basePath += "/";
        }
        
        String overridePath = override;
        if (overridePath.startsWith("/"))
        {
            overridePath = overridePath.substring(1);
        }
        
        try
        {
            URI normalizedBase = new URI(
                location.getScheme(),
                location.getUserInfo(),
                location.getHost(),
                location.getPort(),
                basePath,
                location.getQuery(),
                location.getFragment()
            );
            
            return normalizedBase.resolve(overridePath);
        }
        catch (Exception ex)
        {
            LangUtil.rethrowUnchecked(ex);
            return location;
        }
    }

    public HttpClient supplyHttpClient(
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
