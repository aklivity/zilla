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
    public void shouldRoundTripWrapAndUnwrap()
    {
        TestVaultHandler handler = newHandler();
        byte[] plaintext = "super secret payload".getBytes(UTF_8);
        DirectBufferEx source = new UnsafeBufferEx(plaintext);
        CapturedResult wrapped = new CapturedResult();

        handler.wrap(1L, "kek", source, 0, source.capacity(), wrapped::accept);

        assertNotNull(wrapped.buffer);
        assertNotEquals(plaintext.length, wrapped.length);

        byte[] wrappedBytes = copyOf(wrapped);
        DirectBufferEx wrappedBuffer = new UnsafeBufferEx(wrappedBytes);
        CapturedResult unwrapped = new CapturedResult();

        handler.unwrap(1L, wrappedBuffer, 0, wrappedBuffer.capacity(), unwrapped::accept);

        assertNotNull(unwrapped.buffer);
        assertArrayEquals(plaintext, copyOf(unwrapped));
    }

    @Test
    public void shouldUnwrapEachOfMultipleNamedKeysWithoutExternalName()
    {
        TestVaultHandler handler = newHandlerWithTwoKeys();

        byte[] plaintextA = "payload for kek".getBytes(UTF_8);
        byte[] plaintextB = "payload for other-kek".getBytes(UTF_8);

        CapturedResult wrappedA = new CapturedResult();
        handler.wrap(1L, "kek", new UnsafeBufferEx(plaintextA), 0, plaintextA.length, wrappedA::accept);

        CapturedResult wrappedB = new CapturedResult();
        handler.wrap(1L, "other-kek", new UnsafeBufferEx(plaintextB), 0, plaintextB.length, wrappedB::accept);

        CapturedResult unwrappedA = new CapturedResult();
        handler.unwrap(1L, new UnsafeBufferEx(copyOf(wrappedA)), 0, wrappedA.length, unwrappedA::accept);

        CapturedResult unwrappedB = new CapturedResult();
        handler.unwrap(1L, new UnsafeBufferEx(copyOf(wrappedB)), 0, wrappedB.length, unwrappedB::accept);

        assertArrayEquals(plaintextA, copyOf(unwrappedA));
        assertArrayEquals(plaintextB, copyOf(unwrappedB));
    }

    @Test
    public void shouldUnwrapWrappedBytesWithNoOtherContextThanTheBytesThemselves()
    {
        TestVaultHandler wrappingHandler = newHandler();
        byte[] plaintext = "super secret payload".getBytes(UTF_8);
        CapturedResult wrapped = new CapturedResult();

        wrappingHandler.wrap(1L, "kek", new UnsafeBufferEx(plaintext), 0, plaintext.length, wrapped::accept);

        // a distinct handler instance, built from the same declarative config, unwraps the
        // bytes with no other context — the wrapped artifact alone is sufficient
        TestVaultHandler unwrappingHandler = newHandler();
        CapturedResult unwrapped = new CapturedResult();

        unwrappingHandler.unwrap(1L, new UnsafeBufferEx(copyOf(wrapped)), 0, wrapped.length, unwrapped::accept);

        assertArrayEquals(plaintext, copyOf(unwrapped));
    }

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
        DirectBufferEx source = new UnsafeBufferEx("payload".getBytes(UTF_8));
        CapturedResult captured = new CapturedResult();

        handler.wrap(1L, "unknown", source, 0, source.capacity(), captured::accept);

        assertNull(captured.buffer);
        assertEquals(0, captured.index);
        assertEquals(0, captured.length);
    }

    @Test
    public void shouldFailUnwrapWithForeignBytes()
    {
        TestVaultHandler handler = newHandler();
        DirectBufferEx source = new UnsafeBufferEx("payload".getBytes(UTF_8));
        CapturedResult captured = new CapturedResult();

        handler.unwrap(1L, source, 0, source.capacity(), captured::accept);

        assertNull(captured.buffer);
        assertEquals(0, captured.index);
        assertEquals(0, captured.length);
    }

    @Test
    public void shouldFailUnwrapWithTamperedCiphertext()
    {
        TestVaultHandler handler = newHandler();
        DirectBufferEx source = new UnsafeBufferEx("payload".getBytes(UTF_8));
        CapturedResult wrapped = new CapturedResult();

        handler.wrap(1L, "kek", source, 0, source.capacity(), wrapped::accept);

        byte[] tampered = copyOf(wrapped);
        tampered[tampered.length - 1] ^= (byte) 0xFF;
        DirectBufferEx tamperedBuffer = new UnsafeBufferEx(tampered);
        CapturedResult unwrapped = new CapturedResult();

        handler.unwrap(1L, tamperedBuffer, 0, tamperedBuffer.capacity(), unwrapped::accept);

        assertNull(unwrapped.buffer);
        assertEquals(0, unwrapped.index);
        assertEquals(0, unwrapped.length);
    }

    @Test
    public void shouldFailUnwrapWithTruncatedInput()
    {
        TestVaultHandler handler = newHandler();
        DirectBufferEx source = new UnsafeBufferEx(new byte[] { 0x01, 0x02, 0x03 });
        CapturedResult captured = new CapturedResult();

        handler.unwrap(1L, source, 0, source.capacity(), captured::accept);

        assertNull(captured.buffer);
        assertEquals(0, captured.index);
        assertEquals(0, captured.length);
    }

    private static byte[] copyOf(
        CapturedResult captured)
    {
        byte[] copy = new byte[captured.length];
        captured.buffer.getBytes(captured.index, copy);
        return copy;
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

    private static final class CapturedResult
    {
        private DirectBufferEx buffer;
        private int index;
        private int length;

        private void accept(
            DirectBufferEx buffer,
            int index,
            int length)
        {
            this.buffer = buffer;
            this.index = index;
            this.length = length;
        }
    }
}
