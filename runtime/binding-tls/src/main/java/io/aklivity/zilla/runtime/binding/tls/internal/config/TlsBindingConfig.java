/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.tls.internal.config;

import static io.aklivity.zilla.runtime.binding.tls.internal.identity.TlsClientX509ExtendedKeyManager.COMMON_NAME_KEY;
import static io.aklivity.zilla.runtime.binding.tls.internal.types.ProxyInfoType.ALPN;
import static io.aklivity.zilla.runtime.binding.tls.internal.types.ProxyInfoType.AUTHORITY;
import static io.aklivity.zilla.runtime.binding.tls.internal.types.ProxyInfoType.SECURE;
import static io.aklivity.zilla.runtime.binding.tls.internal.types.ProxySecureInfoType.NAME;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static javax.net.ssl.StandardConstants.SNI_HOST_NAME;

import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.binding.tls.config.TlsMutualConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsTrust;
import io.aklivity.zilla.runtime.binding.tls.internal.TlsConfiguration;
import io.aklivity.zilla.runtime.binding.tls.internal.TlsEventContext;
import io.aklivity.zilla.runtime.binding.tls.internal.identity.TlsClientX509ExtendedKeyManager;
import io.aklivity.zilla.runtime.binding.tls.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.ProxyAddressFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.ProxyInfoFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.ProxyBeginExFW;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;

public final class TlsBindingConfig
{
    private static final TlsOptionsConfig OPTIONS_DEFAULT = TlsOptionsConfig.builder().build();

    public final long id;
    public final long vaultId;
    public final String qname;
    public final TlsOptionsConfig options;
    public final KindConfig kind;
    public final List<TlsRouteConfig> routes;

    private SSLContext context;

    public TlsBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.vaultId = binding.vaultId;
        this.qname = binding.qname;
        this.kind = binding.kind;
        this.options = binding.options != null ? TlsOptionsConfig.class.cast(binding.options) : OPTIONS_DEFAULT;
        this.routes = binding.routes.stream().map(TlsRouteConfig::new).collect(toList());
    }

    public void init(
        TlsConfiguration config,
        TlsEventContext events,
        VaultHandler vault,
        SecureRandom random)
    {
        KeyManagerFactory keys = newKeys(config, vault, options.keys, options.signers);
        TrustManagerFactory trust = newTrust(config, vault, options.trust, options.trustcacerts && kind == KindConfig.CLIENT);

        try
        {
            KeyManager[] keyManagers = null;
            if (keys != null)
            {
                keyManagers = keys.getKeyManagers();

                if (keyManagers != null && kind == KindConfig.CLIENT)
                {
                    for (int i = 0; i < keyManagers.length; i++)
                    {
                        if (keyManagers[i] instanceof X509ExtendedKeyManager)
                        {
                            X509ExtendedKeyManager keyManager = (X509ExtendedKeyManager) keyManagers[i];
                            keyManagers[i] = new TlsClientX509ExtendedKeyManager(keyManager);
                        }
                    }
                }
            }

            TrustManager[] trustManagers = null;
            if (trust != null)
            {
                trustManagers = trust.getTrustManagers();
            }

            String version = options.version != null ? options.version : "TLS";
            SSLContext context = SSLContext.getInstance(version);
            context.init(keyManagers, trustManagers, random);

            this.context = context;
        }
        catch (Exception ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
    }

    public TlsRouteConfig resolve(
        long authorization,
        ProxyBeginExFW beginEx)
    {
        Array32FW<ProxyInfoFW> infos = beginEx != null ? beginEx.infos() : null;
        ProxyInfoFW authorityInfo = infos != null ? infos.matchFirst(a -> a.kind() == AUTHORITY) : null;
        String authority = authorityInfo != null ? authorityInfo.authority().asString() : null;

        ProxyInfoFW alpnInfo = infos != null ? infos.matchFirst(a -> a.kind() == ALPN) : null;
        String alpn = alpnInfo != null ? alpnInfo.alpn().asString() : null;

        int port = resolveDestinationPort(beginEx);

        return resolve(authorization, authority, alpn, port);
    }

    public TlsRouteConfig resolvePortOnly(
        long authorization,
        int port)
    {
        return routes.stream()
                .filter(r -> r.authorized(authorization) && r.matchesPortOnly(port))
                .findFirst()
                .orElse(null);
    }

    public static int resolveDestinationPort(
        ProxyBeginExFW beginEx)
    {
        int port = 0;

        if (beginEx != null)
        {
            ProxyAddressFW address = beginEx.address();

            switch (address.kind())
            {
            case INET:
                port = address.inet().destinationPort();
                break;
            case INET4:
                port = address.inet4().destinationPort();
                break;
            case INET6:
                port = address.inet6().destinationPort();
                break;
            default:
                break;
            }
        }

        return port;
    }

    public TlsRouteConfig resolve(
        long authorization,
        String hostname,
        String alpn,
        int port)
    {
        return routes.stream()
                .filter(r -> r.authorized(authorization) && r.matches(hostname, alpn, port))
                .findFirst()
                .orElse(null);
    }

    public SSLEngine newClientEngine(
        ProxyBeginExFW beginEx)
    {
        SSLEngine engine = null;

        if (context != null)
        {
            engine = context.createSSLEngine();
            engine.setUseClientMode(true);

            List<String> sni = options.sni;
            if (beginEx != null)
            {
                ProxyInfoFW info = beginEx.infos().matchFirst(a -> a.kind() == AUTHORITY);

                // TODO: support multiple authority info
                if (info != null)
                {
                    sni = singletonList(info.authority().asString());
                }
            }

            List<String> alpn = options.alpn;
            if (alpn == null && beginEx != null)
            {
                ProxyInfoFW info = beginEx.infos().matchFirst(a -> a.kind() == ALPN);

                // TODO: support multiple alpn info
                if (info != null)
                {
                    alpn = singletonList(info.alpn().asString());
                }
            }

            final SSLParameters parameters = engine.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");

            if (sni != null)
            {
                List<SNIServerName> serverNames = sni.stream()
                        .map(SNIHostName::new)
                        .collect(toList());
                parameters.setServerNames(serverNames);
            }

            if (alpn != null)
            {
                List<String> alpnNonNull = alpn.stream()
                    .filter(s -> s != null)
                    .collect(toList());
                parameters.setApplicationProtocols(alpnNonNull.toArray(new String[alpnNonNull.size()]));
            }

            engine.setSSLParameters(parameters);

            if (beginEx != null)
            {
                ProxyInfoFW info = beginEx.infos().matchFirst(a -> a.kind() == SECURE && a.secure().kind() == NAME);
                if (info != null)
                {
                    String commonName = info.secure().name().asString();
                    if (commonName != null)
                    {
                        SSLSession session = engine.getSession();
                        session.putValue(COMMON_NAME_KEY, commonName);
                    }
                }
            }
        }

        return engine;
    }

    public SSLEngine newServerEngine(
        long authorization,
        int port)
    {
        SSLEngine engine = null;

        if (context != null)
        {
            engine = context.createSSLEngine();
            engine.setUseClientMode(false);

            TlsMutualConfig mutual = Optional.ofNullable(options != null ? options.mutual : null).orElse(TlsMutualConfig.NONE);

            switch (mutual)
            {
            case NONE:
                engine.setWantClientAuth(false);
                break;
            case REQUESTED:
                engine.setWantClientAuth(true);
                break;
            case REQUIRED:
                engine.setNeedClientAuth(true);
                break;
            }

            engine.setHandshakeApplicationProtocolSelector((ngin, alpns) -> selectAlpn(ngin, alpns, authorization, port));
        }

        return engine;
    }

    private String selectAlpn(
        SSLEngine engine,
        List<String> protocols,
        long authorization,
        int port)
    {
        List<SNIServerName> serverNames = null;

        SSLSession session = engine.getHandshakeSession();
        if (session instanceof ExtendedSSLSession)
        {
            ExtendedSSLSession sessionEx = (ExtendedSSLSession) session;
            serverNames = sessionEx.getRequestedServerNames();
        }

        List<String> sni = options != null ? options.sni : null;
        List<String> alpn = options != null ? options.alpn : null;

        String selected = null;

        for (String protocol : protocols)
        {
            if (alpn != null && alpn.contains(protocol))
            {
                selected = protocol;
                break;
            }
        }

        if (serverNames != null)
        {
            for (SNIServerName serverName : serverNames)
            {
                if (serverName.getType() == SNI_HOST_NAME)
                {
                    SNIHostName hostName = (SNIHostName) serverName;
                    String authority = hostName.getAsciiName();

                    if (sni != null && !sni.contains(authority))
                    {
                        continue;
                    }

                    for (TlsRouteConfig route : routes)
                    {
                        for (String protocol : protocols)
                        {
                            if (alpn != null && !alpn.contains(protocol))
                            {
                                continue;
                            }

                            if (route.authorized(authorization) &&
                                route.matches(authority, protocol, port))
                            {
                                selected = protocol;
                                break;
                            }
                        }
                    }
                }
            }
        }
        else
        {
            for (TlsRouteConfig route : routes)
            {
                for (String protocol : protocols)
                {
                    if (alpn != null && !alpn.contains(protocol))
                    {
                        continue;
                    }

                    if (route.authorized(authorization) &&
                        route.matches(null, protocol, port))
                    {
                        selected = protocol;
                        break;
                    }
                }
            }
        }

        if (selected == null && !routes.isEmpty())
        {
            selected = "";
        }

        return selected;
    }

    private KeyManagerFactory newKeys(
        TlsConfiguration config,
        VaultHandler vault,
        List<String> keyNames,
        List<String> signerNames)
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
                if (config.ignoreEmptyVaultRefs())
                {
                    keyNames = ignoreEmptyNames(keyNames);
                }

                keys = vault.initKeys(keyNames);
            }
            else if (signerNames != null)
            {
                if (config.ignoreEmptyVaultRefs())
                {
                    signerNames = ignoreEmptyNames(signerNames);
                }

                keys = vault.initSigners(signerNames);
            }
        }
        catch (Exception ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return keys;
    }

    private TrustManagerFactory newTrust(
        TlsConfiguration config,
        VaultHandler vault,
        List<String> trustNames,
        boolean trustcacerts)
    {
        TrustManagerFactory trust = null;

        try
        {
            if (config.ignoreEmptyVaultRefs())
            {
                trustNames = ignoreEmptyNames(trustNames);
            }

            KeyStore cacerts = trustcacerts ? TlsTrust.cacerts(config) : null;

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

    private List<String> ignoreEmptyNames(
        List<String> names)
    {
        if (names != null && !names.isEmpty())
        {
            names = names.stream()
                .filter(n -> !n.isEmpty())
                .collect(Collectors.toList());

            if (names.isEmpty())
            {
                names = null;
            }
        }

        return names;
    }
}
