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
package io.aklivity.zilla.runtime.vault.filesystem.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertArrayEquals;

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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

import io.aklivity.zilla.config.vault.filesystem.FileSystemOptionsConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

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

        int aliasCount = 0;
        for (KeyManager manager : keys.getKeyManagers())
        {
            if (manager instanceof X509ExtendedKeyManager keyManager)
            {
                String[] aliases = keyManager.getServerAliases("RSA", null);
                aliasCount += aliases != null ? aliases.length : 0;
            }
        }

        assertThat(keys, not(nullValue()));
        assertThat(aliasCount, equalTo(2));
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

        int aliasCount = 0;
        for (KeyManager manager : keys.getKeyManagers())
        {
            if (manager instanceof X509ExtendedKeyManager keyManager)
            {
                String[] aliases = keyManager.getServerAliases("RSA", null);
                aliasCount += aliases != null ? aliases.length : 0;
            }
        }

        assertThat(keys, not(nullValue()));
        assertThat(aliasCount, equalTo(0));
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

        int aliasCount = 0;
        for (KeyManager manager : keys.getKeyManagers())
        {
            if (manager instanceof X509ExtendedKeyManager keyManager)
            {
                String[] aliases = keyManager.getServerAliases("RSA", null);
                aliasCount += aliases != null ? aliases.length : 0;
            }
        }

        assertThat(keys, not(nullValue()));
        assertThat(aliasCount, equalTo(1));
    }

    @Test
    public void shouldRestrictExplicitKeyAliasesToConfiguredEntries() throws Exception
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

        KeyManagerFactory keys = vault.initKeys(List.of("alias1", "alias2"));

        int aliasCount = 0;
        for (KeyManager manager : keys.getKeyManagers())
        {
            if (manager instanceof X509ExtendedKeyManager keyManager)
            {
                String[] aliases = keyManager.getServerAliases("RSA", null);
                aliasCount += aliases != null ? aliases.length : 0;
            }
        }

        assertThat(keys, not(nullValue()));
        assertThat(aliasCount, equalTo(1));
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

        List<X509Certificate> issuers = new ArrayList<>();
        for (TrustManager manager : trust.getTrustManagers())
        {
            if (manager instanceof X509TrustManager trustManager)
            {
                issuers.addAll(List.of(trustManager.getAcceptedIssuers()));
            }
        }

        assertThat(trust, not(nullValue()));
        assertThat(issuers, hasSize(2));
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

        List<X509Certificate> issuers = new ArrayList<>();
        for (TrustManager manager : trust.getTrustManagers())
        {
            if (manager instanceof X509TrustManager trustManager)
            {
                issuers.addAll(List.of(trustManager.getAcceptedIssuers()));
            }
        }

        assertThat(trust, not(nullValue()));
        assertThat(issuers, hasSize(1));
    }

    @Test
    public void shouldRestrictExplicitTrustAliasesToConfiguredEntries() throws Exception
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

        TrustManagerFactory trust = vault.initTrust(List.of("alias1", "alias2"), null);

        List<X509Certificate> issuers = new ArrayList<>();
        for (TrustManager manager : trust.getTrustManagers())
        {
            if (manager instanceof X509TrustManager trustManager)
            {
                issuers.addAll(List.of(trustManager.getAcceptedIssuers()));
            }
        }

        assertThat(trust, not(nullValue()));
        assertThat(issuers, hasSize(1));
    }

    @Test
    public void shouldWrapAndUnwrapPlainStringSecretEntry() throws Exception
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .secrets()
                .store("stores/secrets/secrets")
                .password("generated")
                .entry("app-key")
                    .alias("alias128")
                    .build()
                .build()
            .build();

        FileSystemVaultHandler vault = new FileSystemVaultHandler(options, FileSystemVaultTest::resourcePath);

        byte[] plaintext = "top secret payload".getBytes(StandardCharsets.UTF_8);
        Captured wrapped = wrap(vault, "app-key", plaintext);

        assertThat(wrapped.buffer, not(nullValue()));
        assertThat(wrapped.bytes()[3], equalTo((byte) 1));

        Captured unwrapped = unwrap(vault, "app-key", wrapped.bytes());

        assertThat(unwrapped.buffer, not(nullValue()));
        assertArrayEquals(plaintext, unwrapped.bytes());
    }

    @Test
    public void shouldUseActiveVersionForNewWraps() throws Exception
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .secrets()
                .store("stores/secrets/secrets")
                .password("generated")
                .entry("session-key")
                    .active("2")
                    .version("1", "v1")
                    .version("2", "v2")
                    .build()
                .build()
            .build();

        FileSystemVaultHandler vault = new FileSystemVaultHandler(options, FileSystemVaultTest::resourcePath);

        byte[] plaintext = "session secret".getBytes(StandardCharsets.UTF_8);
        Captured wrapped = wrap(vault, "session-key", plaintext);

        assertThat(wrapped.buffer, not(nullValue()));
        assertThat(wrapped.bytes()[3], equalTo((byte) 2));

        Captured unwrapped = unwrap(vault, "session-key", wrapped.bytes());
        assertArrayEquals(plaintext, unwrapped.bytes());
    }

    @Test
    public void shouldUnwrapOlderVersionCiphertext() throws Exception
    {
        FileSystemOptionsConfig beforeRotation = FileSystemOptionsConfig.builder()
            .secrets()
                .store("stores/secrets/secrets")
                .password("generated")
                .entry("session-key")
                    .active("1")
                    .version("1", "v1")
                    .build()
                .build()
            .build();

        FileSystemVaultHandler vaultBeforeRotation =
            new FileSystemVaultHandler(beforeRotation, FileSystemVaultTest::resourcePath);

        byte[] plaintext = "session secret v1".getBytes(StandardCharsets.UTF_8);
        Captured wrappedBeforeRotation = wrap(vaultBeforeRotation, "session-key", plaintext);

        FileSystemOptionsConfig afterRotation = FileSystemOptionsConfig.builder()
            .secrets()
                .store("stores/secrets/secrets")
                .password("generated")
                .entry("session-key")
                    .active("2")
                    .version("1", "v1")
                    .version("2", "v2")
                    .build()
                .build()
            .build();

        FileSystemVaultHandler vaultAfterRotation = new FileSystemVaultHandler(afterRotation, FileSystemVaultTest::resourcePath);

        Captured unwrapped = unwrap(vaultAfterRotation, "session-key", wrappedBeforeRotation.bytes());

        assertThat(unwrapped.buffer, not(nullValue()));
        assertArrayEquals(plaintext, unwrapped.bytes());
    }

    @Test
    public void shouldFailUnwrapForUnknownVersion() throws Exception
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .secrets()
                .store("stores/secrets/secrets")
                .password("generated")
                .entry("session-key")
                    .active("2")
                    .version("1", "v1")
                    .version("2", "v2")
                    .build()
                .build()
            .build();

        FileSystemVaultHandler vault = new FileSystemVaultHandler(options, FileSystemVaultTest::resourcePath);

        Captured wrapped = wrap(vault, "session-key", "session secret".getBytes(StandardCharsets.UTF_8));
        byte[] corrupted = wrapped.bytes();
        corrupted[3] = (byte) 9;

        Captured unwrapped = unwrap(vault, "session-key", corrupted);

        assertThat(unwrapped.buffer, nullValue());
        assertThat(unwrapped.length, equalTo(0));
    }

    @Test
    public void shouldUnwrapImplicitVersionOneAfterPromotionToVersionsMap() throws Exception
    {
        FileSystemOptionsConfig plainString = FileSystemOptionsConfig.builder()
            .secrets()
                .store("stores/secrets/secrets")
                .password("generated")
                .entry("app-key")
                    .alias("alias128")
                    .build()
                .build()
            .build();

        FileSystemVaultHandler plainVault = new FileSystemVaultHandler(plainString, FileSystemVaultTest::resourcePath);

        byte[] plaintext = "app secret".getBytes(StandardCharsets.UTF_8);
        Captured wrapped = wrap(plainVault, "app-key", plaintext);

        FileSystemOptionsConfig promoted = FileSystemOptionsConfig.builder()
            .secrets()
                .store("stores/secrets/secrets")
                .password("generated")
                .entry("app-key")
                    .active("1")
                    .version("1", "alias128")
                    .build()
                .build()
            .build();

        FileSystemVaultHandler promotedVault = new FileSystemVaultHandler(promoted, FileSystemVaultTest::resourcePath);

        Captured unwrapped = unwrap(promotedVault, "app-key", wrapped.bytes());

        assertThat(unwrapped.buffer, not(nullValue()));
        assertArrayEquals(plaintext, unwrapped.bytes());
    }

    @Test
    public void shouldInferAlgorithmFromKeyLength() throws Exception
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .secrets()
                .store("stores/secrets/secrets")
                .password("generated")
                .entry("key-128")
                    .alias("alias128")
                    .build()
                .entry("key-256")
                    .alias("alias256")
                    .build()
                .build()
            .build();

        FileSystemVaultHandler vault = new FileSystemVaultHandler(options, FileSystemVaultTest::resourcePath);

        byte[] plaintext = "secret".getBytes(StandardCharsets.UTF_8);

        Captured wrapped128 = wrap(vault, "key-128", plaintext);
        Captured wrapped256 = wrap(vault, "key-256", plaintext);

        assertThat(wrapped128.buffer, not(nullValue()));
        assertThat(wrapped256.buffer, not(nullValue()));

        assertArrayEquals(plaintext, unwrap(vault, "key-128", wrapped128.bytes()).bytes());
        assertArrayEquals(plaintext, unwrap(vault, "key-256", wrapped256.bytes()).bytes());
    }

    @Test
    public void shouldFailWrapForUnsupportedKeyLengthWithoutAlgorithmOverride() throws Exception
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .secrets()
                .store("stores/secrets/secrets")
                .password("generated")
                .entry("wide-key")
                    .alias("alias192")
                    .build()
                .build()
            .build();

        FileSystemVaultHandler vault = new FileSystemVaultHandler(options, FileSystemVaultTest::resourcePath);

        Captured wrapped = wrap(vault, "wide-key", "secret".getBytes(StandardCharsets.UTF_8));

        assertThat(wrapped.buffer, nullValue());
    }

    @Test
    public void shouldWrapWithAlgorithmOverrideForUnsupportedKeyLength() throws Exception
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .secrets()
                .store("stores/secrets/secrets")
                .password("generated")
                .entry("wide-key")
                    .active("1")
                    .version("1", "alias192")
                    .algorithm("AES256_GCM")
                    .build()
                .build()
            .build();

        FileSystemVaultHandler vault = new FileSystemVaultHandler(options, FileSystemVaultTest::resourcePath);

        byte[] plaintext = "secret".getBytes(StandardCharsets.UTF_8);
        Captured wrapped = wrap(vault, "wide-key", plaintext);

        assertThat(wrapped.buffer, not(nullValue()));
        assertArrayEquals(plaintext, unwrap(vault, "wide-key", wrapped.bytes()).bytes());
    }

    @Test
    public void shouldFailWrapForUnknownSecretName() throws Exception
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .secrets()
                .store("stores/secrets/secrets")
                .password("generated")
                .entry("app-key")
                    .alias("alias128")
                    .build()
                .build()
            .build();

        FileSystemVaultHandler vault = new FileSystemVaultHandler(options, FileSystemVaultTest::resourcePath);

        Captured wrapped = wrap(vault, "unknown-key", "secret".getBytes(StandardCharsets.UTF_8));

        assertThat(wrapped.buffer, nullValue());
    }

    @Test
    public void shouldFailWrapWhenSecretsNotConfigured() throws Exception
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .build();

        FileSystemVaultHandler vault = new FileSystemVaultHandler(options, FileSystemVaultTest::resourcePath);

        Captured wrapped = wrap(vault, "app-key", "secret".getBytes(StandardCharsets.UTF_8));

        assertThat(wrapped.buffer, nullValue());
    }

    private static Captured wrap(
        FileSystemVaultHandler vault,
        String key,
        byte[] plaintext)
    {
        Captured captured = new Captured();
        DirectBufferEx bytes = new UnsafeBufferEx(plaintext);
        vault.wrap(1L, key, bytes, 0, bytes.capacity(), captured::accept);
        return captured;
    }

    private static Captured unwrap(
        FileSystemVaultHandler vault,
        String key,
        byte[] wrapped)
    {
        Captured captured = new Captured();
        DirectBufferEx bytes = new UnsafeBufferEx(wrapped);
        vault.unwrap(1L, key, bytes, 0, bytes.capacity(), captured::accept);
        return captured;
    }

    public static Path resourcePath(
        String resource)
    {
        URL url = FileSystemVaultTest.class.getResource(resource);
        assert url != null;
        return Path.of(URI.create(url.toString()));
    }

    private static final class Captured
    {
        private DirectBufferEx buffer;
        private int index;
        private int length;
        private byte[] copy;

        private void accept(
            DirectBufferEx buffer,
            int index,
            int length)
        {
            this.buffer = buffer;
            this.index = index;
            this.length = length;

            this.copy = new byte[length];
            if (buffer != null)
            {
                buffer.getBytes(index, copy, 0, length);
            }
        }

        private byte[] bytes()
        {
            return copy;
        }
    }
}
