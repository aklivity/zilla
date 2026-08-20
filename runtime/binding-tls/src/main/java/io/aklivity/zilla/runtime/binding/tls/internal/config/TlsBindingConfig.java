/*
 * Copyright 2021-2026 Aklivity Inc.
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

import static io.aklivity.zilla.runtime.binding.tls.internal.identity.TlsClientX509ExtendedKeyManager.CERTIFICATE_FIELDS_KEY;
import static io.aklivity.zilla.runtime.binding.tls.internal.types.ProxyInfoType.ALPN;
import static io.aklivity.zilla.runtime.binding.tls.internal.types.ProxyInfoType.AUTHORITY;
import static io.aklivity.zilla.runtime.binding.tls.internal.types.ProxyInfoType.SECURE;
import static io.aklivity.zilla.runtime.binding.tls.internal.types.ProxySecureInfoType.NAME;
import static io.aklivity.zilla.runtime.common.x509.X509Fields.SUBJECT_CN;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static javax.net.ssl.StandardConstants.SNI_HOST_NAME;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;

import org.agrona.LangUtil;

import io.aklivity.zilla.config.binding.tls.TlsAuthorizationConfig;
import io.aklivity.zilla.config.binding.tls.TlsCredentialsConfig;
import io.aklivity.zilla.config.binding.tls.TlsMutualConfig;
import io.aklivity.zilla.config.binding.tls.TlsOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.runtime.binding.tls.internal.TlsConfiguration;
import io.aklivity.zilla.runtime.binding.tls.internal.TlsEventContext;
import io.aklivity.zilla.runtime.binding.tls.internal.identity.TlsClientX509ExtendedKeyManager;
import io.aklivity.zilla.runtime.binding.tls.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.ProxyAddressFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.ProxyInfoFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.stream.ProxyBeginExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.security.Trusted;
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;

public final class TlsBindingConfig
{
    private static final TlsOptionsConfig OPTIONS_DEFAULT = TlsOptionsConfig.builder().build();

    private static final String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----\n";
    private static final String END_CERTIFICATE = "\n-----END CERTIFICATE-----\n";
    private static final int PEM_LINE_LENGTH = 64;
    private static final byte[] PEM_LINE_SEPARATOR = "\n".getBytes(StandardCharsets.US_ASCII);
    private static final Base64.Encoder PEM_ENCODER = Base64.getMimeEncoder(PEM_LINE_LENGTH, PEM_LINE_SEPARATOR);

    public final long id;
    public final long vaultId;
    public final String qname;
    public final TlsOptionsConfig options;
    public final KindConfig kind;
    public final List<TlsRouteConfig> routes;
    public final GuardHandler guard;

    private SSLContext context;
    private TlsEventContext events;
    private TlsClientX509ExtendedKeyManager clientKeyManager;

    private boolean clientHttpsIdentification;
    private boolean clientServerNameIndication;
    private TlsMutualConfig mutualDefault;

    public TlsBindingConfig(
        EngineContext context,
        BindingConfig binding)
    {
        this.id = binding.id;
        this.vaultId = binding.vaultId;
        this.qname = binding.qname;
        this.kind = binding.kind;
        this.options = binding.options != null ? TlsOptionsConfig.class.cast(binding.options) : OPTIONS_DEFAULT;
        this.routes = binding.routes.stream().map(r -> new TlsRouteConfig(context, qname, r)).collect(toList());
        this.guard = resolveGuard(context, binding, options.authorization);
    }

    /**
     * Renders the verified peer certificate chain as leaf-first concatenated PEM, the
     * credential format consumed by a guard named under {@code options.authorization}.
     * Returns {@code null} when the peer was not verified, so an unverified peer yields
     * no credentials rather than an empty or partial chain.
     */
    public String credentials(
        SSLSession session)
    {
        String credentials = null;

        try
        {
            final Certificate[] certs = session.getPeerCertificates();
            final StringBuilder chain = new StringBuilder();

            for (Certificate cert : certs)
            {
                chain.append(BEGIN_CERTIFICATE)
                     .append(PEM_ENCODER.encodeToString(cert.getEncoded()))
                     .append(END_CERTIFICATE);
            }

            credentials = chain.toString();
        }
        catch (SSLPeerUnverifiedException | CertificateEncodingException ex)
        {
            // peer not verified, or chain not encodable; supply no credentials
        }

        return credentials;
    }

    public void init(
        TlsConfiguration config,
        TlsEventContext events,
        VaultHandler vault,
        SecureRandom random)
    {
        this.events = events;

        boolean nothingConfigured = options.keys == null && options.trust == null && options.signers == null;

        KeyManagerFactory keys = nothingConfigured
            ? newKeysWildcard(vault)
            : newKeys(config, vault, options.keys, options.signers);

        boolean trustcacerts = kind == KindConfig.CLIENT && Boolean.TRUE.equals(options.trustcacerts);
        TrustManagerFactory trust = nothingConfigured
            ? newTrustWildcard(config, vault, trustcacerts)
            : newTrust(config, vault, options.trust, trustcacerts);

        if (trust == null && kind == KindConfig.CLIENT && !trustcacerts)
        {
            trust = newTrustCacertsOnly(config);
        }

        this.mutualDefault = trust != null ? TlsMutualConfig.REQUIRED : TlsMutualConfig.NONE;

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
                            this.clientKeyManager = new TlsClientX509ExtendedKeyManager(config, keyManager);
                            keyManagers[i] = clientKeyManager;
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
            this.clientHttpsIdentification = config.clientHttpsIdentification();
            this.clientServerNameIndication = config.clientServerNameIndication();

            for (TlsRouteConfig route : routes)
            {
                route.init(vault);
            }
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

        return resolve(authorization, authority, alpn, port, null);
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

    /**
     * Matches a route on port alone, without applying {@code routes[].guarded}. A server
     * binding accepts and negotiates before any guard can run, so authorization is not yet
     * known at that point; the guarded predicate is applied by {@link #resolve} once the
     * handshake has completed.
     */
    public TlsRouteConfig resolvePortOnlyBeforeHandshake(
        int port)
    {
        return routes.stream()
                .filter(r -> r.matchesPortOnly(port))
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
        int port,
        Certificate[] clientCerts)
    {
        return routes.stream()
                .filter(r -> r.authorized(authorization) &&
                    r.matches(hostname, alpn, port, clientCerts))
                .findFirst()
                .orElse(null);
    }

    public SSLEngine newClientEngine(
        long traceId,
        long authorization,
        TlsRouteConfig route,
        ProxyBeginExFW beginEx)
    {
        SSLEngine engine = null;

        if (context != null)
        {
            engine = context.createSSLEngine();
            engine.setUseClientMode(true);

            List<String> sni = options.sni;
            if (sni == null && beginEx != null)
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

            if (clientHttpsIdentification)
            {
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
            }

            if (clientServerNameIndication && sni != null)
            {
                List<SNIServerName> serverNames = sni.stream()
                        .map(TlsBindingConfig::trimHostnameTrailingDot)
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

            Map<String, String> certificate = resolveCertificate(traceId, authorization, route, beginEx);
            if (certificate != null)
            {
                SSLSession session = engine.getSession();
                session.putValue(CERTIFICATE_FIELDS_KEY, certificate);
            }
        }

        return engine;
    }

    /**
     * Names the certificate the client should present, from {@code routes[].with.certificate} when
     * the matched route declares one, and otherwise from the inbound {@code secure.name} info item.
     * Returns {@code null} when neither applies, leaving the default key manager to choose.
     */
    private Map<String, String> resolveCertificate(
        long traceId,
        long authorization,
        TlsRouteConfig route,
        ProxyBeginExFW beginEx)
    {
        Map<String, String> certificate = null;

        final TlsWithResolver with = route != null ? route.with : null;

        if (with != null)
        {
            certificate = with.resolve(authorization);

            if (certificate == null)
            {
                if (events != null)
                {
                    events.tlsClientCertificateNotResolved(traceId, id, with.unresolvedField(authorization));
                }
            }
            else if (events != null && clientKeyManager != null && !clientKeyManager.matchesAny(certificate))
            {
                // the selector is still applied, so no certificate is presented rather than a default one
                events.tlsClientCertificateNotMatched(traceId, id, asSelector(certificate));
            }
        }
        else if (beginEx != null)
        {
            ProxyInfoFW info = beginEx.infos().matchFirst(a -> a.kind() == SECURE && a.secure().kind() == NAME);
            if (info != null)
            {
                String commonName = info.secure().name().asString();
                if (commonName != null)
                {
                    certificate = Map.of(SUBJECT_CN, commonName);
                }
            }
        }

        return certificate;
    }

    public SSLEngine newServerEngine(
        int port)
    {
        SSLEngine engine = null;

        if (context != null)
        {
            engine = context.createSSLEngine();
            engine.setUseClientMode(false);

            TlsMutualConfig mutual = options.mutual != null ? options.mutual : mutualDefault;

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

            engine.setHandshakeApplicationProtocolSelector((ngin, alpns) -> selectAlpn(ngin, alpns, port));
        }

        return engine;
    }

    private static String asSelector(
        Map<String, String> certificate)
    {
        return certificate.entrySet().stream()
                .map(e -> String.format("%s=%s", e.getKey(), e.getValue()))
                .collect(Collectors.joining(", "));
    }

    private static GuardHandler resolveGuard(
        EngineContext context,
        BindingConfig binding,
        TlsAuthorizationConfig authorization)
    {
        GuardHandler guard = null;

        if (authorization != null)
        {
            final TlsCredentialsConfig credentials = authorization.credentials;

            if (credentials != null && credentials.certificates != null)
            {
                final long guardId = binding.resolveId.applyAsLong(authorization.name);
                guard = context.supplyGuard(guardId);
            }
        }

        return guard;
    }

    private String selectAlpn(
        SSLEngine engine,
        List<String> protocols,
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

                            if (route.matchesIgnoringCert(authority, protocol, port))
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

                    if (route.matchesIgnoringCert(null, protocol, port))
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

            KeyStore cacerts = trustcacerts ? Trusted.cacerts(config) : null;

            if (vault != null)
            {
                trust = vault.initTrust(trustNames, cacerts);
            }
            else
            {
                trust = newTrustFromCacerts(cacerts);
            }
        }
        catch (Exception ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return trust;
    }

    private KeyManagerFactory newKeysWildcard(
        VaultHandler vault)
    {
        KeyManagerFactory keys = null;

        try
        {
            if (vault != null)
            {
                keys = vault.initKeys();

                if (keys == null)
                {
                    keys = vault.initSigners();
                }
            }
        }
        catch (Exception ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return keys;
    }

    private TrustManagerFactory newTrustWildcard(
        TlsConfiguration config,
        VaultHandler vault,
        boolean trustcacerts)
    {
        TrustManagerFactory trust = null;

        try
        {
            KeyStore cacerts = trustcacerts ? Trusted.cacerts(config) : null;

            if (vault != null)
            {
                trust = vault.initTrust(cacerts);
            }
            else
            {
                trust = newTrustFromCacerts(cacerts);
            }
        }
        catch (Exception ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return trust;
    }

    private TrustManagerFactory newTrustCacertsOnly(
        TlsConfiguration config)
    {
        TrustManagerFactory trust = null;

        try
        {
            trust = newTrustFromCacerts(Trusted.cacerts(config));
        }
        catch (Exception ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return trust;
    }

    private TrustManagerFactory newTrustFromCacerts(
        KeyStore cacerts)
        throws Exception
    {
        TrustManagerFactory trust = null;

        if (cacerts != null)
        {
            trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(cacerts);
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

    private static String trimHostnameTrailingDot(
        String hostname)
    {
        return hostname.endsWith(".") ? hostname.substring(0, hostname.length() - 1) : hostname;
    }
}
