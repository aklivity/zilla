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
package io.aklivity.zilla.runtime.engine.test.internal.vault;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.engine.GenericVaultConfig;
import io.aklivity.zilla.config.engine.VaultConfig;
import io.aklivity.zilla.config.engine.test.internal.vault.config.TestVaultOptionsConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.vault.SecretKeyManager;
import io.aklivity.zilla.runtime.engine.vault.SecretKeyManagerFactory;

public class TestVaultHandlerTest
{
    // an arbitrary 32-byte AES-256 key, PEM-encoded exactly like a declarative TLS key/trust entry
    private static final String SECRET_KEY_PEM =
        "-----BEGIN SECRET KEY-----\n" +
        "jOl3tg1ZDuqTrzB7WOVaUOEoezst8sz/ywPPbsGE8bA=\n" +
        "-----END SECRET KEY-----";

    // a second, distinct 32-byte AES-256 key, used to prove dispatch is by embedded name
    private static final String OTHER_SECRET_KEY_PEM =
        "-----BEGIN SECRET KEY-----\n" +
        "J5XxCYN3xHEx4qzx6juAdpYPDBhT11KBg2Y/LJz6AIE=\n" +
        "-----END SECRET KEY-----";

    @Test
    public void shouldRoundTripWrapAndUnwrapViaSecretKeyManager()
    {
        TestVaultHandler handler = newHandler();
        SecretKeyManagerFactory factory = handler.initSecretKeys(List.of("kek"));
        assertNotNull(factory);

        SecretKeyManager manager = factory.getSecretKeyManager();
        byte[] plaintext = "super secret payload".getBytes(UTF_8);
        DirectBufferEx source = new UnsafeBufferEx(plaintext);
        MutableDirectBufferEx wrapped = new UnsafeBufferEx(new byte[128]);

        int wrappedLength = manager.wrap("kek", source, 0, source.capacity(), wrapped, 0);

        assertNotEquals(-1, wrappedLength);
        assertNotEquals(plaintext.length, wrappedLength);

        MutableDirectBufferEx unwrapped = new UnsafeBufferEx(new byte[128]);
        int unwrappedLength = manager.unwrap(wrapped, 0, wrappedLength, unwrapped, 0);

        assertEquals(plaintext.length, unwrappedLength);
        byte[] roundTripped = new byte[unwrappedLength];
        unwrapped.getBytes(0, roundTripped);
        assertArrayEquals(plaintext, roundTripped);
    }

    @Test
    public void shouldUnwrapEachOfMultipleNamedKeysWithoutExternalName()
    {
        TestVaultHandler handler = newHandlerWithTwoKeys();
        SecretKeyManager manager = handler.initSecretKeys(List.of("kek", "other-kek")).getSecretKeyManager();

        byte[] plaintextA = "payload for kek".getBytes(UTF_8);
        byte[] plaintextB = "payload for other-kek".getBytes(UTF_8);

        MutableDirectBufferEx wrappedA = new UnsafeBufferEx(new byte[128]);
        int wrappedALength = manager.wrap("kek", new UnsafeBufferEx(plaintextA), 0, plaintextA.length, wrappedA, 0);

        MutableDirectBufferEx wrappedB = new UnsafeBufferEx(new byte[128]);
        int wrappedBLength =
            manager.wrap("other-kek", new UnsafeBufferEx(plaintextB), 0, plaintextB.length, wrappedB, 0);

        MutableDirectBufferEx unwrappedA = new UnsafeBufferEx(new byte[128]);
        int unwrappedALength = manager.unwrap(wrappedA, 0, wrappedALength, unwrappedA, 0);

        MutableDirectBufferEx unwrappedB = new UnsafeBufferEx(new byte[128]);
        int unwrappedBLength = manager.unwrap(wrappedB, 0, wrappedBLength, unwrappedB, 0);

        byte[] roundTrippedA = new byte[unwrappedALength];
        unwrappedA.getBytes(0, roundTrippedA);
        byte[] roundTrippedB = new byte[unwrappedBLength];
        unwrappedB.getBytes(0, roundTrippedB);

        assertArrayEquals(plaintextA, roundTrippedA);
        assertArrayEquals(plaintextB, roundTrippedB);
    }

    @Test
    public void shouldUnwrapWrappedBytesWithNoOtherContextThanTheBytesThemselves()
    {
        TestVaultHandler wrappingHandler = newHandler();
        SecretKeyManager wrappingManager = wrappingHandler.initSecretKeys(List.of("kek")).getSecretKeyManager();

        byte[] plaintext = "super secret payload".getBytes(UTF_8);
        MutableDirectBufferEx wrapped = new UnsafeBufferEx(new byte[128]);
        int wrappedLength =
            wrappingManager.wrap("kek", new UnsafeBufferEx(plaintext), 0, plaintext.length, wrapped, 0);

        // a distinct handler instance, built from the same declarative config, unwraps the
        // bytes with no other context — the wrapped artifact alone is sufficient
        TestVaultHandler unwrappingHandler = newHandler();
        SecretKeyManager unwrappingManager = unwrappingHandler.initSecretKeys(List.of("kek")).getSecretKeyManager();

        MutableDirectBufferEx unwrapped = new UnsafeBufferEx(new byte[128]);
        int unwrappedLength = unwrappingManager.unwrap(wrapped, 0, wrappedLength, unwrapped, 0);

        byte[] roundTripped = new byte[unwrappedLength];
        unwrapped.getBytes(0, roundTripped);
        assertArrayEquals(plaintext, roundTripped);
    }

    @Test
    public void shouldNotInitSecretKeysForUnknownAlias()
    {
        TestVaultHandler handler = newHandler();
        SecretKeyManagerFactory factory = handler.initSecretKeys(List.of("unknown"));

        assertNull(factory);
    }

    @Test
    public void shouldFailWrapWithUnknownAlias()
    {
        TestVaultHandler handler = newHandler();
        SecretKeyManager manager = handler.initSecretKeys(List.of("kek")).getSecretKeyManager();
        DirectBufferEx source = new UnsafeBufferEx("payload".getBytes(UTF_8));
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[128]);

        assertEquals(-1, manager.wrap("unknown", source, 0, source.capacity(), dst, 0));
    }

    @Test
    public void shouldFailUnwrapWithForeignBytes()
    {
        TestVaultHandler handler = newHandler();
        SecretKeyManager manager = handler.initSecretKeys(List.of("kek")).getSecretKeyManager();
        DirectBufferEx source = new UnsafeBufferEx("payload".getBytes(UTF_8));
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[128]);

        assertEquals(-1, manager.unwrap(source, 0, source.capacity(), dst, 0));
    }

    @Test
    public void shouldFailUnwrapWithTamperedCiphertext()
    {
        TestVaultHandler handler = newHandler();
        SecretKeyManager manager = handler.initSecretKeys(List.of("kek")).getSecretKeyManager();
        DirectBufferEx source = new UnsafeBufferEx("payload".getBytes(UTF_8));
        MutableDirectBufferEx wrapped = new UnsafeBufferEx(new byte[128]);

        int wrappedLength = manager.wrap("kek", source, 0, source.capacity(), wrapped, 0);
        byte[] tampered = new byte[wrappedLength];
        wrapped.getBytes(0, tampered);
        tampered[tampered.length - 1] ^= (byte) 0xFF;

        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[128]);
        assertEquals(-1, manager.unwrap(new UnsafeBufferEx(tampered), 0, tampered.length, dst, 0));
    }

    @Test
    public void shouldFailUnwrapWithTruncatedInput()
    {
        TestVaultHandler handler = newHandler();
        SecretKeyManager manager = handler.initSecretKeys(List.of("kek")).getSecretKeyManager();
        DirectBufferEx source = new UnsafeBufferEx(new byte[] { 0x01, 0x02, 0x03 });
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[128]);

        assertEquals(-1, manager.unwrap(source, 0, source.capacity(), dst, 0));
    }

    private static TestVaultHandler newHandler()
    {
        VaultConfig vault = GenericVaultConfig.builder()
            .namespace("test")
            .name("vault0")
            .type("test")
            .options(TestVaultOptionsConfig.builder()
                .wrap("kek", SECRET_KEY_PEM)
                .build())
            .build();
        return new TestVaultHandler(vault);
    }

    private static TestVaultHandler newHandlerWithTwoKeys()
    {
        VaultConfig vault = GenericVaultConfig.builder()
            .namespace("test")
            .name("vault0")
            .type("test")
            .options(TestVaultOptionsConfig.builder()
                .wrap("kek", SECRET_KEY_PEM)
                .wrap("other-kek", OTHER_SECRET_KEY_PEM)
                .build())
            .build();
        return new TestVaultHandler(vault);
    }
}
