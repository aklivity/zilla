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
package io.aklivity.zilla.runtime.vault.filesystem.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

import org.junit.Test;

import io.aklivity.zilla.runtime.vault.filesystem.config.FileSystemOptionsConfig;

public class FileSystemVaultTest
{
    @Test
    public void shouldResolveServer() throws Exception
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .keys()
                .store("stores/server/keys")
                .type("pkcs12")
                .password("generated")
                .build()
            .trust()
                .store("stores/server/trust")
                .type("pkcs12")
                .password("generated")
                .build()
            .build();

        FileSystemVaultHandler vault = new FileSystemVaultHandler(options, FileSystemVaultTest::resourcePath);

        KeyManagerFactory keys = vault.initKeys(List.of("localhost"));
        TrustManagerFactory trust = vault.initTrust(List.of("clientca"), null);

        assertThat(keys, not(nullValue()));
        assertThat(trust, not(nullValue()));
    }

    @Test
    public void shouldResolveClient() throws Exception
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .keys()
                .store("stores/client/keys")
                .type("pkcs12")
                .password("generated")
                .build()
            .signers()
                .store("stores/server/trust")
                .type("pkcs12")
                .password("generated")
                .build()
            .build();

        FileSystemVaultHandler vault = new FileSystemVaultHandler(options, FileSystemVaultTest::resourcePath, null);

        KeyManagerFactory keys = vault.initSigners(List.of("clientca"));

        assertThat(keys, not(nullValue()));
    }

    @Test
    public void shouldResolveAllKeysViaWildcard() throws Exception
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .keys()
                .store("stores/wildcard/keys")
                .type("pkcs12")
                .password("generated")
                .build()
            .build();

        FileSystemVaultHandler vault = new FileSystemVaultHandler(options, FileSystemVaultTest::resourcePath);

        KeyManagerFactory keys = vault.initKeys();

        assertThat(keys, not(nullValue()));
        assertThat(privateKey(keys, "alias1"), not(nullValue()));
        assertThat(privateKey(keys, "alias2"), not(nullValue()));
    }

    @Test
    public void shouldResolveNoKeysWhenAliasesEmpty() throws Exception
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .keys()
                .store("stores/wildcard/keys")
                .type("pkcs12")
                .password("generated")
                .build()
            .build();

        FileSystemVaultHandler vault = new FileSystemVaultHandler(options, FileSystemVaultTest::resourcePath);

        KeyManagerFactory keys = vault.initKeys(List.of());

        assertThat(keys, not(nullValue()));
        assertThat(privateKey(keys, "alias1"), nullValue());
        assertThat(privateKey(keys, "alias2"), nullValue());
    }

    @Test
    public void shouldResolveConfiguredEntriesViaWildcard() throws Exception
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .keys()
                .store("stores/wildcard/keys")
                .type("pkcs12")
                .password("generated")
                .entries(List.of("alias1"))
                .build()
            .build();

        FileSystemVaultHandler vault = new FileSystemVaultHandler(options, FileSystemVaultTest::resourcePath);

        KeyManagerFactory keys = vault.initKeys();

        assertThat(keys, not(nullValue()));
        assertThat(privateKey(keys, "alias1"), not(nullValue()));
        assertThat(privateKey(keys, "alias2"), nullValue());
    }

    @Test
    public void shouldResolveAllTrustViaWildcard() throws Exception
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .trust()
                .store("stores/wildcard/trust")
                .type("pkcs12")
                .password("generated")
                .build()
            .build();

        FileSystemVaultHandler vault = new FileSystemVaultHandler(options, FileSystemVaultTest::resourcePath);

        TrustManagerFactory trust = vault.initTrust(null);

        assertThat(trust, not(nullValue()));
        assertThat(acceptedIssuers(trust), hasSize(2));
    }

    @Test
    public void shouldResolveConfiguredEntriesForTrustViaWildcard() throws Exception
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .trust()
                .store("stores/wildcard/trust")
                .type("pkcs12")
                .password("generated")
                .entries(List.of("alias1"))
                .build()
            .build();

        FileSystemVaultHandler vault = new FileSystemVaultHandler(options, FileSystemVaultTest::resourcePath);

        TrustManagerFactory trust = vault.initTrust(null);

        assertThat(trust, not(nullValue()));
        assertThat(acceptedIssuers(trust), hasSize(1));
    }

    public static Path resourcePath(
        String resource)
    {
        URL url = FileSystemVaultTest.class.getResource(resource);
        assert url != null;
        return Path.of(URI.create(url.toString()));
    }

    private static PrivateKey privateKey(
        KeyManagerFactory factory,
        String alias)
    {
        PrivateKey key = null;

        for (KeyManager manager : factory.getKeyManagers())
        {
            if (manager instanceof X509ExtendedKeyManager keyManager)
            {
                key = keyManager.getPrivateKey(alias);
                if (key != null)
                {
                    break;
                }
            }
        }

        return key;
    }

    private static List<X509Certificate> acceptedIssuers(
        TrustManagerFactory factory)
    {
        List<X509Certificate> issuers = new ArrayList<>();

        for (TrustManager manager : factory.getTrustManagers())
        {
            if (manager instanceof X509TrustManager trustManager)
            {
                issuers.addAll(List.of(trustManager.getAcceptedIssuers()));
            }
        }

        return issuers;
    }
}
