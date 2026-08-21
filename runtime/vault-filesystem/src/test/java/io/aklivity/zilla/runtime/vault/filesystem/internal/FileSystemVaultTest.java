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
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.vault.SecretKeyManager;
import io.aklivity.zilla.runtime.engine.vault.SecretKeyManagerFactory;

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

        Captured unwrapped = unwrap(vault, wrapped.bytes());

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

        // a vault instance that only knows version 2 still unwraps it, proving the active
        // version (2) — not version 1 — was the one embedded by the wrap above
        FileSystemOptionsConfig onlyVersionTwo = FileSystemOptionsConfig.builder()
            .secrets()
                .store("stores/secrets/secrets")
                .password("generated")
                .entry("session-key")
                    .active("2")
                    .version("2", "v2")
                    .build()
                .build()
            .build();

        FileSystemVaultHandler vaultOnlyVersionTwo =
            new FileSystemVaultHandler(onlyVersionTwo, FileSystemVaultTest::resourcePath);

        Captured unwrapped = unwrap(vaultOnlyVersionTwo, wrapped.bytes());
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

        Captured unwrapped = unwrap(vaultAfterRotation, wrappedBeforeRotation.bytes());

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
        corruptEmbeddedVersion(corrupted, "session-key", 9);

        Captured unwrapped = unwrap(vault, corrupted);

        assertThat(unwrapped.buffer, nullValue());
        assertThat(unwrapped.length, equalTo(0));
    }

    @Test
    public void shouldFailUnwrapForForeignKeyName() throws Exception
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

        Captured wrapped = wrap(vault, "app-key", "top secret payload".getBytes(StandardCharsets.UTF_8));
        byte[] foreign = wrapped.bytes();
        // rewrite the embedded name to one this vault does not manage, without changing its length
        System.arraycopy("unknown-key".getBytes(StandardCharsets.UTF_8), 0, foreign, Integer.BYTES, "app-key".length());

        Captured unwrapped = unwrap(vault, foreign);

        assertThat(unwrapped.buffer, nullValue());
        assertThat(unwrapped.length, equalTo(0));
    }

    @Test
    public void shouldFailUnwrapForRandomBytes() throws Exception
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

        Captured unwrapped = unwrap(vault, "not a wrapped secret at all, just garbage".getBytes(StandardCharsets.UTF_8));

        assertThat(unwrapped.buffer, nullValue());
        assertThat(unwrapped.length, equalTo(0));
    }

    @Test
    public void shouldUnwrapEachOfMultipleNamedKeysWithoutExternalName() throws Exception
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

        byte[] plaintextA = "payload for key-128".getBytes(StandardCharsets.UTF_8);
        byte[] plaintextB = "payload for key-256".getBytes(StandardCharsets.UTF_8);

        Captured wrappedA = wrap(vault, "key-128", plaintextA);
        Captured wrappedB = wrap(vault, "key-256", plaintextB);

        assertArrayEquals(plaintextA, unwrap(vault, wrappedA.bytes()).bytes());
        assertArrayEquals(plaintextB, unwrap(vault, wrappedB.bytes()).bytes());
    }

    @Test
    public void shouldUnwrapWrappedBytesWithNoOtherContextThanTheBytesThemselves() throws Exception
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

        FileSystemVaultHandler wrappingVault = new FileSystemVaultHandler(options, FileSystemVaultTest::resourcePath);
        byte[] plaintext = "top secret payload".getBytes(StandardCharsets.UTF_8);
        Captured wrapped = wrap(wrappingVault, "app-key", plaintext);

        // a distinct vault instance, backed by the same secrets store, unwraps the bytes with
        // no other context — the wrapped artifact alone is sufficient
        FileSystemVaultHandler unwrappingVault = new FileSystemVaultHandler(options, FileSystemVaultTest::resourcePath);
        Captured unwrapped = unwrap(unwrappingVault, wrapped.bytes());

        assertArrayEquals(plaintext, unwrapped.bytes());
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

        Captured unwrapped = unwrap(promotedVault, wrapped.bytes());

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

        assertArrayEquals(plaintext, unwrap(vault, wrapped128.bytes()).bytes());
        assertArrayEquals(plaintext, unwrap(vault, wrapped256.bytes()).bytes());
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
        assertArrayEquals(plaintext, unwrap(vault, wrapped.bytes()).bytes());
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

    @Test
    public void shouldRoundTripWrapAndUnwrapViaSecretKeyManager() throws Exception
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

        SecretKeyManagerFactory factory = vault.initSecretKeys(List.of("app-key"));
        assertThat(factory, not(nullValue()));

        SecretKeyManager manager = factory.getSecretKeyManager();
        byte[] plaintext = "top secret payload".getBytes(StandardCharsets.UTF_8);
        DirectBufferEx source = new UnsafeBufferEx(plaintext);
        MutableDirectBufferEx wrapped = new UnsafeBufferEx(new byte[128]);

        int wrappedLength = manager.wrap("app-key", source, 0, source.capacity(), wrapped, 0);

        assertThat(wrappedLength, not(equalTo(-1)));
        assertThat(wrappedLength, not(equalTo(plaintext.length)));

        MutableDirectBufferEx unwrapped = new UnsafeBufferEx(new byte[128]);
        int unwrappedLength = manager.unwrap(wrapped, 0, wrappedLength, unwrapped, 0);

        assertThat(unwrappedLength, equalTo(plaintext.length));
        byte[] roundTripped = new byte[unwrappedLength];
        unwrapped.getBytes(0, roundTripped);
        assertArrayEquals(plaintext, roundTripped);
    }

    @Test
    public void shouldFailSecretKeyManagerOperationsForUnpermittedKey() throws Exception
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .secrets()
                .store("stores/secrets/secrets")
                .password("generated")
                .entry("app-key")
                    .alias("alias128")
                    .build()
                .entry("session-key")
                    .active("1")
                    .version("1", "v1")
                    .build()
                .build()
            .build();

        FileSystemVaultHandler vault = new FileSystemVaultHandler(options, FileSystemVaultTest::resourcePath);

        SecretKeyManagerFactory factory = vault.initSecretKeys(List.of("app-key"));
        assertThat(factory, not(nullValue()));

        SecretKeyManager manager = factory.getSecretKeyManager();
        DirectBufferEx source = new UnsafeBufferEx("payload".getBytes(StandardCharsets.UTF_8));
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[128]);

        assertThat(manager.wrap("session-key", source, 0, source.capacity(), dst, 0), equalTo(-1));

        // bytes wrapped under "session-key" (not permitted for this manager) embed that
        // identity themselves; the manager must reject them on unwrap even with no key
        // argument to check against its permitted set
        Captured wrappedUnderSessionKey = wrap(vault, "session-key", "payload".getBytes(StandardCharsets.UTF_8));
        DirectBufferEx foreignWrapped = new UnsafeBufferEx(wrappedUnderSessionKey.bytes());

        assertThat(manager.unwrap(foreignWrapped, 0, foreignWrapped.capacity(), dst, 0), equalTo(-1));
    }

    @Test
    public void shouldNotInitSecretKeysForUnknownAlias() throws Exception
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

        SecretKeyManagerFactory factory = vault.initSecretKeys(List.of("unknown-key"));

        assertThat(factory, nullValue());
    }

    @Test
    public void shouldNotInitSecretKeysWhenSecretsNotConfigured() throws Exception
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .build();

        FileSystemVaultHandler vault = new FileSystemVaultHandler(options, FileSystemVaultTest::resourcePath);

        SecretKeyManagerFactory factory = vault.initSecretKeys(List.of("app-key"));

        assertThat(factory, nullValue());
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
        byte[] wrapped)
    {
        Captured captured = new Captured();
        DirectBufferEx bytes = new UnsafeBufferEx(wrapped);
        vault.unwrap(1L, bytes, 0, bytes.capacity(), captured::accept);
        return captured;
    }

    private static void corruptEmbeddedVersion(
        byte[] wrapped,
        String key,
        int invalidVersion)
    {
        int versionOffset = Integer.BYTES + key.getBytes(StandardCharsets.UTF_8).length;
        wrapped[versionOffset] = 0;
        wrapped[versionOffset + 1] = 0;
        wrapped[versionOffset + 2] = 0;
        wrapped[versionOffset + 3] = (byte) invalidVersion;
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
