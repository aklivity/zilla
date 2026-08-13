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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.security.KeyStore;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;

public class VaultHandlerTest
{
    @Test
    public void shouldFailWrapWithNoOverride()
    {
        VaultHandler handler = new EmptyVaultHandler();
        DirectBufferEx dek = new UnsafeBufferEx(new byte[] { 0x01, 0x02, 0x03, 0x04 });
        CapturedResult captured = new CapturedResult();

        handler.wrap(1L, "kek", dek, 0, dek.capacity(), captured::accept);

        assertNull(captured.buffer);
        assertEquals(0, captured.index);
        assertEquals(0, captured.length);
    }

    @Test
    public void shouldFailUnwrapWithNoOverride()
    {
        VaultHandler handler = new EmptyVaultHandler();
        DirectBufferEx edek = new UnsafeBufferEx(new byte[] { 0x05, 0x06, 0x07, 0x08 });
        CapturedResult captured = new CapturedResult();

        handler.unwrap(1L, "kek", edek, 0, edek.capacity(), captured::accept);

        assertNull(captured.buffer);
        assertEquals(0, captured.index);
        assertEquals(0, captured.length);
    }

    @Test
    public void shouldWrapNamedKey()
    {
        VaultHandler handler = new NamedKeyVaultHandler("kek");
        DirectBufferEx dek = new UnsafeBufferEx(new byte[] { 0x01, 0x02, 0x03, 0x04 });
        CapturedResult captured = new CapturedResult();

        handler.wrap(1L, "kek", dek, 0, dek.capacity(), captured::accept);

        assertSame(dek, captured.buffer);
        assertEquals(0, captured.index);
        assertEquals(dek.capacity(), captured.length);
    }

    @Test
    public void shouldFailWrapWithUnnamedKey()
    {
        VaultHandler handler = new NamedKeyVaultHandler("kek");
        DirectBufferEx dek = new UnsafeBufferEx(new byte[] { 0x01, 0x02, 0x03, 0x04 });
        CapturedResult captured = new CapturedResult();

        handler.wrap(1L, "unknown", dek, 0, dek.capacity(), captured::accept);

        assertNull(captured.buffer);
        assertEquals(0, captured.index);
        assertEquals(0, captured.length);
    }

    @Test
    public void shouldUnwrapNamedKey()
    {
        VaultHandler handler = new NamedKeyVaultHandler("kek");
        DirectBufferEx edek = new UnsafeBufferEx(new byte[] { 0x05, 0x06, 0x07, 0x08 });
        CapturedResult captured = new CapturedResult();

        handler.unwrap(1L, "kek", edek, 0, edek.capacity(), captured::accept);

        assertSame(edek, captured.buffer);
        assertEquals(0, captured.index);
        assertEquals(edek.capacity(), captured.length);
    }

    @Test
    public void shouldFailUnwrapWithUnnamedKey()
    {
        VaultHandler handler = new NamedKeyVaultHandler("kek");
        DirectBufferEx edek = new UnsafeBufferEx(new byte[] { 0x05, 0x06, 0x07, 0x08 });
        CapturedResult captured = new CapturedResult();

        handler.unwrap(1L, "unknown", edek, 0, edek.capacity(), captured::accept);

        assertNull(captured.buffer);
        assertEquals(0, captured.index);
        assertEquals(0, captured.length);
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

    private static class EmptyVaultHandler implements VaultHandler
    {
        @Override
        public KeyManagerFactory initKeys(
            List<String> keyRefs)
        {
            return null;
        }

        @Override
        public KeyManagerFactory initKeys()
        {
            return null;
        }

        @Override
        public KeyManagerFactory initSigners(
            List<String> signerRefs)
        {
            return null;
        }

        @Override
        public KeyManagerFactory initSigners()
        {
            return null;
        }

        @Override
        public TrustManagerFactory initTrust(
            List<String> certRefs,
            KeyStore cacerts)
        {
            return null;
        }

        @Override
        public TrustManagerFactory initTrust(
            KeyStore cacerts)
        {
            return null;
        }
    }

    private static final class NamedKeyVaultHandler extends EmptyVaultHandler
    {
        private final String name;

        private NamedKeyVaultHandler(
            String name)
        {
            this.name = name;
        }

        @Override
        public void wrap(
            long traceId,
            String name,
            DirectBufferEx dek,
            int index,
            int length,
            BytesConsumer next)
        {
            if (this.name.equals(name))
            {
                next.accept(dek, index, length);
            }
            else
            {
                next.accept(null, 0, 0);
            }
        }

        @Override
        public void unwrap(
            long traceId,
            String name,
            DirectBufferEx edek,
            int index,
            int length,
            BytesConsumer next)
        {
            if (this.name.equals(name))
            {
                next.accept(edek, index, length);
            }
            else
            {
                next.accept(null, 0, 0);
            }
        }
    }
}
