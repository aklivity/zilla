/*
 * Copyright 2021-2024 Aklivity Inc.
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

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.tls.config.TlsMutualConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.internal.TlsConfiguration;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;

public class TlsBindingConfigTest
{
    private final TlsConfiguration config = new TlsConfiguration(new Configuration());
    private final SecureRandom random = new SecureRandom();

    private static TlsBindingConfig binding(
        KindConfig kind,
        TlsOptionsConfig options)
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("tls0")
            .type("tls")
            .kind(kind)
            .options(options)
            .build();

        return new TlsBindingConfig(binding);
    }

    private static TrustManagerFactory mockTrust()
    {
        return mock(TrustManagerFactory.class);
    }

    @Test
    public void shouldDefaultMutualToRequiredWhenExplicitTrustResolvesViaVault()
    {
        VaultHandler vault = mock(VaultHandler.class);
        when(vault.initTrust(eq(asList("serverca")), isNull())).thenReturn(mockTrust());

        TlsOptionsConfig options = TlsOptionsConfig.builder()
            .trust(asList("serverca"))
            .build();
        TlsBindingConfig binding = binding(KindConfig.SERVER, options);

        binding.init(config, null, vault, random);

        SSLEngine engine = binding.newServerEngine(0L, 0);
        assertThat(engine.getNeedClientAuth(), equalTo(true));
        assertThat(engine.getWantClientAuth(), equalTo(false));
    }

    @Test
    public void shouldDefaultMutualToRequiredWhenWildcardTrustResolvesViaVault()
    {
        VaultHandler vault = mock(VaultHandler.class);
        when(vault.initTrust(isNull())).thenReturn(mockTrust());

        TlsOptionsConfig options = TlsOptionsConfig.builder().build();
        TlsBindingConfig binding = binding(KindConfig.SERVER, options);

        binding.init(config, null, vault, random);

        SSLEngine engine = binding.newServerEngine(0L, 0);
        assertThat(engine.getNeedClientAuth(), equalTo(true));
        assertThat(engine.getWantClientAuth(), equalTo(false));
    }

    @Test
    public void shouldDefaultMutualToNoneWhenNoTrustResolves()
    {
        VaultHandler vault = mock(VaultHandler.class);

        TlsOptionsConfig options = TlsOptionsConfig.builder().build();
        TlsBindingConfig binding = binding(KindConfig.SERVER, options);

        binding.init(config, null, vault, random);

        SSLEngine engine = binding.newServerEngine(0L, 0);
        assertThat(engine.getNeedClientAuth(), equalTo(false));
        assertThat(engine.getWantClientAuth(), equalTo(false));
    }

    @Test
    public void shouldHonorExplicitMutualOverDefaultWhenTrustResolves()
    {
        VaultHandler vault = mock(VaultHandler.class);
        when(vault.initTrust(eq(asList("serverca")), isNull())).thenReturn(mockTrust());

        TlsOptionsConfig options = TlsOptionsConfig.builder()
            .trust(asList("serverca"))
            .mutual(TlsMutualConfig.NONE)
            .build();
        TlsBindingConfig binding = binding(KindConfig.SERVER, options);

        binding.init(config, null, vault, random);

        SSLEngine engine = binding.newServerEngine(0L, 0);
        assertThat(engine.getNeedClientAuth(), equalTo(false));
        assertThat(engine.getWantClientAuth(), equalTo(false));
    }

    @Test
    public void shouldNotMergeCacertsWhenClientWildcardTrustResolvesViaVault()
    {
        VaultHandler vault = mock(VaultHandler.class);
        when(vault.initTrust(isNull())).thenReturn(mockTrust());

        TlsOptionsConfig options = TlsOptionsConfig.builder().build();
        TlsBindingConfig binding = binding(KindConfig.CLIENT, options);

        binding.init(config, null, vault, random);

        verify(vault).initTrust(isNull());
    }

    @Test
    public void shouldNotMergeCacertsWhenClientExplicitTrustResolvesViaVault()
    {
        VaultHandler vault = mock(VaultHandler.class);
        when(vault.initTrust(eq(asList("pinnedca")), isNull())).thenReturn(mockTrust());

        TlsOptionsConfig options = TlsOptionsConfig.builder()
            .trust(asList("pinnedca"))
            .build();
        TlsBindingConfig binding = binding(KindConfig.CLIENT, options);

        binding.init(config, null, vault, random);

        verify(vault).initTrust(eq(asList("pinnedca")), isNull());
    }

    @Test
    public void shouldFallBackToCacertsWhenClientWildcardVaultResolvesNothing()
    {
        VaultHandler vault = mock(VaultHandler.class);

        TlsOptionsConfig options = TlsOptionsConfig.builder().build();
        TlsBindingConfig binding = binding(KindConfig.CLIENT, options);

        binding.init(config, null, vault, random);

        SSLEngine engine = binding.newServerEngine(0L, 0);
        assertThat(engine.getNeedClientAuth(), equalTo(true));
    }

    @Test
    public void shouldFallBackToCacertsWhenClientExplicitTrustVaultResolvesNothing()
    {
        VaultHandler vault = mock(VaultHandler.class);

        TlsOptionsConfig options = TlsOptionsConfig.builder()
            .trust(asList("missingca"))
            .build();
        TlsBindingConfig binding = binding(KindConfig.CLIENT, options);

        binding.init(config, null, vault, random);

        SSLEngine engine = binding.newServerEngine(0L, 0);
        assertThat(engine.getNeedClientAuth(), equalTo(true));
    }

    @Test
    public void shouldMergeCacertsUpfrontWhenClientTrustcacertsExplicitlyTrue()
    {
        VaultHandler vault = mock(VaultHandler.class);
        when(vault.initTrust(notNull())).thenReturn(mockTrust());

        TlsOptionsConfig options = TlsOptionsConfig.builder()
            .trustcacerts(true)
            .build();
        TlsBindingConfig binding = binding(KindConfig.CLIENT, options);

        binding.init(config, null, vault, random);

        verify(vault).initTrust(notNull());
    }

    @Test
    public void shouldNotApplyTrustcacertsForServerKind()
    {
        VaultHandler vault = mock(VaultHandler.class);
        when(vault.initTrust(anyList(), isNull())).thenReturn(mockTrust());

        TlsOptionsConfig options = TlsOptionsConfig.builder()
            .trust(asList("serverca"))
            .trustcacerts(true)
            .build();
        TlsBindingConfig binding = binding(KindConfig.SERVER, options);

        binding.init(config, null, vault, random);

        verify(vault).initTrust(eq(asList("serverca")), isNull());
    }

    @Test
    public void shouldLeaveMutualAndTrustcacertsUnsetAfterBuildWithNoExplicitConfig()
    {
        TlsOptionsConfig options = TlsOptionsConfig.builder().build();

        assertThat(options.mutual, nullValue());
        assertThat(options.trustcacerts, nullValue());
    }

    @Test
    public void shouldPreserveExplicitOptionsAfterBuild()
    {
        TlsOptionsConfig options = TlsOptionsConfig.builder()
            .trust(asList("serverca"))
            .mutual(TlsMutualConfig.REQUESTED)
            .trustcacerts(false)
            .build();

        assertThat(options.mutual, equalTo(TlsMutualConfig.REQUESTED));
        assertThat(options.trustcacerts, not(nullValue()));
        assertThat(options.trustcacerts, equalTo(false));
    }
}
