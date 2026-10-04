/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.guard.jwt.internal;

import static io.aklivity.zilla.config.guard.jwt.internal.keys.JwtKeyConfigs.RFC7515_RS256_CONFIG;
import static io.aklivity.zilla.runtime.guard.jwt.internal.JwtGuardHandlerTest.sign;
import static io.aklivity.zilla.specs.guard.jwt.keys.JwtKeys.RFC7515_RS256;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.json.Json;

import org.agrona.collections.MutableLong;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.GenericVaultConfig;
import io.aklivity.zilla.config.engine.VaultConfig;
import io.aklivity.zilla.config.engine.test.internal.vault.config.TestVaultOptionsConfig;
import io.aklivity.zilla.config.guard.jwt.JwtOptionsConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.jwt.Jwe;
import io.aklivity.zilla.runtime.common.jwt.JweKeyManager;
import io.aklivity.zilla.runtime.common.jwt.JwtException;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.test.internal.vault.TestVaultHandler;
import io.aklivity.zilla.runtime.engine.vault.SecretKeyManager;

public class JwtGuardEncryptedTest
{
    private static final String TENANT_1_KEY =
        "-----BEGIN SECRET KEY-----\njOl3tg1ZDuqTrzB7WOVaUOEoezst8sz/ywPPbsGE8bA=\n-----END SECRET KEY-----";
    private static final String TENANT_2_KEY =
        "-----BEGIN SECRET KEY-----\nJ5XxCYN3xHEx4qzx6juAdpYPDBhT11KBg2Y/LJz6AIE=\n-----END SECRET KEY-----";
    private static final String OTHER_KEY =
        "-----BEGIN SECRET KEY-----\nQ2hhbmdlTWVDaGFuZ2VNZUNoYW5nZU1lQ2hhbmdlTWU=\n-----END SECRET KEY-----";
    private static final String GHOST_KEY =
        "-----BEGIN SECRET KEY-----\nR2hvc3RHaG9zdEdob3N0R2hvc3RHaG9zdEdob3N0R2g=\n-----END SECRET KEY-----";

    private EngineContext context;
    private SecretKeyManager permitted;
    private SecretKeyManager sealing;
    private SecretKeyManager ghosts;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
        when(context.clock()).thenReturn(mock(Clock.class));
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));

        TestVaultHandler vault = new TestVaultHandler(vault(
            TestVaultOptionsConfig.builder()
                .wrap("tenant-1", TENANT_1_KEY)
                .wrap("tenant-2", TENANT_2_KEY)
                .wrap("other", OTHER_KEY)
                .build()));

        TestVaultHandler outside = new TestVaultHandler(vault(
            TestVaultOptionsConfig.builder()
                .wrap("ghost", GHOST_KEY)
                .build()));

        permitted = vault.initSecretKeys(List.of("tenant-1", "tenant-2")).getSecretKeyManager();
        sealing = vault.initSecretKeys(List.of("tenant-1", "tenant-2", "other")).getSecretKeyManager();
        ghosts = outside.initSecretKeys(List.of("ghost")).getSecretKeyManager();
    }

    @Test
    public void shouldAuthorizeEncryptedToken() throws Exception
    {
        JwtGuardHandler guard = newGuard(newEncryption(permitted));

        long sessionId = guard.reauthorize(0L, 0L, 101L, seal(sealing, "tenant-1", claims()));

        assertThat(sessionId, not(equalTo(0L)));
        assertThat(guard.identity(sessionId), equalTo("alice"));
        assertThat(guard.attribute(sessionId, "site"), equalTo(Json.createValue("http://example.com")));
        assertTrue(guard.verify(sessionId, asList("read")));
        assertFalse(guard.verify(sessionId, asList("admin")));
    }

    @Test
    public void shouldAuthorizeTokensSealedWithEachPermittedKey() throws Exception
    {
        JwtGuardHandler guard = newGuard(newEncryption(permitted));

        long first = guard.reauthorize(0L, 0L, 101L, seal(sealing, "tenant-1", claims()));
        long second = guard.reauthorize(0L, 0L, 102L, seal(sealing, "tenant-2", claims()));

        assertThat(first, not(equalTo(0L)));
        assertThat(second, not(equalTo(0L)));
    }

    @Test
    public void shouldAuthorizeSignedTokenWhenVaultConfigured() throws Exception
    {
        JwtGuardHandler guard = newGuard(newEncryption(permitted));

        String token = sign(claims(), "test", RFC7515_RS256, "RS256");

        assertThat(guard.reauthorize(0L, 0L, 101L, token), not(equalTo(0L)));
    }

    @Test
    public void shouldUnwrapOncePerEncryptedKey() throws Exception
    {
        AtomicInteger unwraps = new AtomicInteger();
        AtomicLong clock = new AtomicLong(1_000L);
        VaultJweKeyManager encryption = new VaultJweKeyManager(counting(permitted, unwraps), clock::get);
        byte[] wrapped = Jwe.parse(seal(sealing, "tenant-1", claims())).encryptedKey();

        byte[] first = encryption.unwrap(wrapped);
        byte[] second = encryption.unwrap(wrapped);

        assertThat(unwraps.get(), equalTo(1));
        assertThat(second, equalTo(first));
    }

    @Test
    public void shouldKeepCachedKeyIntactAfterCallerClearsIt() throws Exception
    {
        VaultJweKeyManager encryption = new VaultJweKeyManager(permitted, () -> 1_000L);
        byte[] wrapped = Jwe.parse(seal(sealing, "tenant-1", claims())).encryptedKey();

        byte[] expected = encryption.unwrap(wrapped);
        Arrays.fill(encryption.unwrap(wrapped), (byte) 0);

        assertThat(encryption.unwrap(wrapped), equalTo(expected));
    }

    @Test
    public void shouldUnwrapAgainAfterCacheExpiry() throws Exception
    {
        AtomicInteger unwraps = new AtomicInteger();
        AtomicLong clock = new AtomicLong(1_000L);
        VaultJweKeyManager encryption = new VaultJweKeyManager(counting(permitted, unwraps), clock::get);
        byte[] wrapped = Jwe.parse(seal(sealing, "tenant-1", claims())).encryptedKey();

        encryption.unwrap(wrapped);
        clock.addAndGet(300_001L);
        encryption.unwrap(wrapped);

        assertThat(unwraps.get(), equalTo(2));
    }

    @Test
    public void shouldEvictLeastRecentlyUsedKeys() throws Exception
    {
        AtomicInteger unwraps = new AtomicInteger();
        VaultJweKeyManager encryption = new VaultJweKeyManager(counting(permitted, unwraps), () -> 1_000L);
        byte[] oldest = Jwe.parse(seal(sealing, "tenant-1", claims())).encryptedKey();

        encryption.unwrap(oldest);
        for (int i = 0; i < 1100; i++)
        {
            encryption.unwrap(Jwe.parse(seal(sealing, "tenant-1", claims())).encryptedKey());
        }
        int before = unwraps.get();
        encryption.unwrap(oldest);

        assertThat(unwraps.get(), equalTo(before + 1));
    }

    @Test
    public void shouldRejectUnwrapFailureAndSealing() throws Exception
    {
        VaultJweKeyManager encryption = new VaultJweKeyManager(permitted, () -> 1_000L);
        byte[] foreign = Jwe.parse(seal(ghosts, "ghost", claims())).encryptedKey();

        assertThrows(JwtException.class, () -> encryption.unwrap(foreign));
        assertThrows(JwtException.class, () -> encryption.wrap(new byte[32]));
        assertThat(encryption.algorithm(), equalTo("io.aklivity.zilla.vault.v1"));
    }

    private JwtGuardHandler newGuard(
        JweKeyManager encryption)
    {
        JwtOptionsConfig options = JwtOptionsConfig.builder()
            .issuer("test issuer")
            .audience("testAudience")
            .key(RFC7515_RS256_CONFIG)
            .attributes(Map.of("site", "website"))
            .vault("vault0")
            .secrets(List.of("tenant-1", "tenant-2"))
            .build();

        return new JwtGuardHandler(options, context, new MutableLong(1L)::getAndIncrement, sessionId -> null, encryption);
    }

    private static JweKeyManager newEncryption(
        SecretKeyManager secrets)
    {
        return new VaultJweKeyManager(secrets, System::currentTimeMillis);
    }

    private static String claims()
    {
        return claims("test issuer", "testAudience", Instant.now().getEpochSecond() + 60L);
    }

    private static String claims(
        String issuer,
        String audience,
        long exp)
    {
        return Json.createObjectBuilder()
            .add("iss", issuer)
            .add("aud", audience)
            .add("sub", "alice")
            .add("exp", exp)
            .add("scope", "read write")
            .add("website", "http://example.com")
            .build()
            .toString();
    }

    private static String seal(
        SecretKeyManager secrets,
        String keyName,
        String claims) throws JwtException
    {
        return Jwe.seal(new VaultSealer(secrets, keyName, VaultJweKeyManager.ALGORITHM), null, claims.getBytes(UTF_8));
    }

    private static SecretKeyManager counting(
        SecretKeyManager delegate,
        AtomicInteger unwraps)
    {
        return new SecretKeyManager()
        {
            @Override
            public int wrap(
                String keyName,
                DirectBufferEx bytes,
                int index,
                int length,
                MutableDirectBufferEx dst,
                int dstIndex)
            {
                return delegate.wrap(keyName, bytes, index, length, dst, dstIndex);
            }

            @Override
            public int unwrap(
                DirectBufferEx bytes,
                int index,
                int length,
                MutableDirectBufferEx dst,
                int dstIndex)
            {
                unwraps.incrementAndGet();
                return delegate.unwrap(bytes, index, length, dst, dstIndex);
            }
        };
    }

    private static VaultConfig vault(
        TestVaultOptionsConfig options)
    {
        return GenericVaultConfig.builder()
            .namespace("test")
            .name("vault0")
            .type("test")
            .options(options)
            .build();
    }

    private static final class VaultSealer implements JweKeyManager
    {
        private final SecretKeyManager secrets;
        private final String keyName;
        private final String algorithm;

        private VaultSealer(
            SecretKeyManager secrets,
            String keyName,
            String algorithm)
        {
            this.secrets = secrets;
            this.keyName = keyName;
            this.algorithm = algorithm;
        }

        @Override
        public String algorithm()
        {
            return algorithm;
        }

        @Override
        public byte[] wrap(
            byte[] key) throws JwtException
        {
            MutableDirectBufferEx wrapped = new UnsafeBufferEx(new byte[256]);
            int length = secrets.wrap(keyName, new UnsafeBufferEx(key), 0, key.length, wrapped, 0);
            if (length < 0)
            {
                throw new JwtException("Unable to wrap key");
            }

            byte[] bytes = new byte[length];
            wrapped.getBytes(0, bytes);
            return bytes;
        }

        @Override
        public byte[] unwrap(
            byte[] wrapped) throws JwtException
        {
            throw new JwtException("Opening is not supported");
        }
    }
}
