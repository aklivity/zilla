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

import org.junit.Test;

import io.aklivity.zilla.config.engine.GenericVaultConfig;
import io.aklivity.zilla.config.engine.VaultConfig;
import io.aklivity.zilla.config.engine.test.internal.vault.config.TestVaultOptionsConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class TestVaultHandlerTest
{
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

        handler.unwrap(1L, "kek", wrappedBuffer, 0, wrappedBuffer.capacity(), unwrapped::accept);

        assertNotNull(unwrapped.buffer);
        assertArrayEquals(plaintext, copyOf(unwrapped));
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
    public void shouldFailUnwrapWithUnknownAlias()
    {
        TestVaultHandler handler = newHandler();
        DirectBufferEx source = new UnsafeBufferEx("payload".getBytes(UTF_8));
        CapturedResult captured = new CapturedResult();

        handler.unwrap(1L, "unknown", source, 0, source.capacity(), captured::accept);

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

        handler.unwrap(1L, "kek", tamperedBuffer, 0, tamperedBuffer.capacity(), unwrapped::accept);

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

        handler.unwrap(1L, "kek", source, 0, source.capacity(), captured::accept);

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
                .wrap("kek", "shared test secret")
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
