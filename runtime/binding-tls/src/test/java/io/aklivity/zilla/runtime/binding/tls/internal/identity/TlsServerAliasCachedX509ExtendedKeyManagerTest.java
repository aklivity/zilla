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
package io.aklivity.zilla.runtime.binding.tls.internal.identity;

import static io.aklivity.zilla.runtime.binding.tls.internal.identity.TlsServerAliasCachedX509ExtendedKeyManager.MAXIMUM_CHOICES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509ExtendedKeyManager;

import org.junit.Before;
import org.junit.Test;

public class TlsServerAliasCachedX509ExtendedKeyManagerTest
{
    private static final String[] SIGNATURE_ALGORITHMS = { "rsa_pss_rsae_sha256" };

    private X509ExtendedKeyManager delegate;
    private TlsServerAliasCachedX509ExtendedKeyManager keyManager;

    @Before
    public void init()
    {
        delegate = mock(X509ExtendedKeyManager.class);
        keyManager = new TlsServerAliasCachedX509ExtendedKeyManager(delegate);
    }

    @Test
    public void shouldChooseServerAliasOncePerHandshakeSelection()
    {
        final SSLEngine first = engine("example.com");
        final SSLEngine second = engine("example.com");
        when(delegate.chooseEngineServerAlias(any(), any(), any())).thenReturn("localhost");

        assertThat(keyManager.chooseEngineServerAlias("RSA", null, first), equalTo("localhost"));
        assertThat(keyManager.chooseEngineServerAlias("RSA", null, second), equalTo("localhost"));

        verify(delegate, times(1)).chooseEngineServerAlias(any(), any(), any());
    }

    @Test
    public void shouldChooseServerAliasForEachRequestedServerName()
    {
        final SSLEngine example = engine("example.com");
        final SSLEngine other = engine("other.com");
        when(delegate.chooseEngineServerAlias("RSA", null, example)).thenReturn("example");
        when(delegate.chooseEngineServerAlias("RSA", null, other)).thenReturn("other");

        assertThat(keyManager.chooseEngineServerAlias("RSA", null, example), equalTo("example"));
        assertThat(keyManager.chooseEngineServerAlias("RSA", null, other), equalTo("other"));
        assertThat(keyManager.chooseEngineServerAlias("RSA", null, example), equalTo("example"));

        verify(delegate, times(2)).chooseEngineServerAlias(any(), any(), any());
    }

    @Test
    public void shouldChooseServerAliasForEachKeyType()
    {
        final SSLEngine engine = engine("example.com");
        when(delegate.chooseEngineServerAlias("RSA", null, engine)).thenReturn("rsa");
        when(delegate.chooseEngineServerAlias("EC", null, engine)).thenReturn("ec");

        assertThat(keyManager.chooseEngineServerAlias("RSA", null, engine), equalTo("rsa"));
        assertThat(keyManager.chooseEngineServerAlias("EC", null, engine), equalTo("ec"));
    }

    @Test
    public void shouldNotRememberMissingServerAlias()
    {
        final SSLEngine engine = engine("example.com");

        assertThat(keyManager.chooseEngineServerAlias("RSA", null, engine), nullValue());
        assertThat(keyManager.chooseEngineServerAlias("RSA", null, engine), nullValue());

        verify(delegate, times(2)).chooseEngineServerAlias(any(), any(), any());
    }

    @Test
    public void shouldNotRememberWithoutExtendedHandshakeSession()
    {
        final SSLEngine engine = mock(SSLEngine.class);
        when(engine.getHandshakeSession()).thenReturn(mock(SSLSession.class));
        when(delegate.chooseEngineServerAlias(any(), any(), any())).thenReturn("localhost");

        assertThat(keyManager.chooseEngineServerAlias("RSA", null, engine), equalTo("localhost"));
        assertThat(keyManager.chooseEngineServerAlias("RSA", null, engine), equalTo("localhost"));

        verify(delegate, times(2)).chooseEngineServerAlias(any(), any(), any());
    }

    @Test
    public void shouldBoundRememberedServerAliases()
    {
        when(delegate.chooseEngineServerAlias(any(), any(), any())).thenReturn("localhost");

        for (int name = 0; name < MAXIMUM_CHOICES + 1; name++)
        {
            keyManager.chooseEngineServerAlias("RSA", null, engine(String.format("%d.example.com", name)));
        }

        final SSLEngine overflow = engine(String.format("%d.example.com", MAXIMUM_CHOICES));
        keyManager.chooseEngineServerAlias("RSA", null, overflow);
        keyManager.chooseEngineServerAlias("RSA", null, overflow);

        verify(delegate, times(MAXIMUM_CHOICES + 3)).chooseEngineServerAlias(any(), any(), any());
    }

    private static SSLEngine engine(
        String hostname)
    {
        final ExtendedSSLSession session = mock(ExtendedSSLSession.class);
        when(session.getPeerSupportedSignatureAlgorithms()).thenReturn(SIGNATURE_ALGORITHMS);
        when(session.getRequestedServerNames()).thenReturn(List.<SNIServerName>of(new SNIHostName(hostname)));

        final SSLEngine engine = mock(SSLEngine.class);
        when(engine.getHandshakeSession()).thenReturn(session);

        return engine;
    }
}
