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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.jwt.JweKeyManager;
import io.aklivity.zilla.runtime.common.jwt.JwtException;
import io.aklivity.zilla.runtime.engine.vault.SecretKeyManager;

final class VaultJweKeyManager implements JweKeyManager
{
    static final String ALGORITHM = "io.aklivity.zilla.vault.v1";

    private static final int CACHE_CAPACITY = 1024;
    private static final long CACHE_EXPIRY_MILLIS = 300_000L;
    private static final int UNWRAPPED_CAPACITY = 64;

    private final SecretKeyManager secrets;
    private final LongSupplier clock;
    private final Map<ByteBuffer, CachedKey> cache;

    VaultJweKeyManager(
        SecretKeyManager secrets,
        LongSupplier clock)
    {
        this.secrets = secrets;
        this.clock = clock;
        this.cache = new KeyCache();
    }

    @Override
    public String algorithm()
    {
        return ALGORITHM;
    }

    @Override
    public byte[] wrap(
        byte[] key) throws JwtException
    {
        throw new JwtException("Sealing is not supported");
    }

    @Override
    public byte[] unwrap(
        byte[] wrapped) throws JwtException
    {
        ByteBuffer cacheKey = ByteBuffer.wrap(wrapped);
        long now = clock.getAsLong();

        CachedKey cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt <= now)
        {
            cache.remove(cacheKey);
            cached = null;
        }

        byte[] key;
        if (cached != null)
        {
            key = cached.key.clone();
        }
        else
        {
            MutableDirectBufferEx unwrapped = new UnsafeBufferEx(new byte[UNWRAPPED_CAPACITY]);
            int length = secrets.unwrap(new UnsafeBufferEx(wrapped), 0, wrapped.length, unwrapped, 0);
            if (length < 0)
            {
                throw new JwtException("Unable to unwrap content encryption key");
            }

            key = new byte[length];
            unwrapped.getBytes(0, key);

            cache.put(cacheKey, new CachedKey(key.clone(), now + CACHE_EXPIRY_MILLIS));
        }

        return key;
    }

    private static final class CachedKey
    {
        private final byte[] key;
        private final long expiresAt;

        private CachedKey(
            byte[] key,
            long expiresAt)
        {
            this.key = key;
            this.expiresAt = expiresAt;
        }
    }

    private static final class KeyCache extends LinkedHashMap<ByteBuffer, CachedKey>
    {
        private static final long serialVersionUID = 1L;

        private KeyCache()
        {
            super(CACHE_CAPACITY, 0.75f, true);
        }

        @Override
        protected boolean removeEldestEntry(
            Map.Entry<ByteBuffer, CachedKey> eldest)
        {
            boolean evict = size() > CACHE_CAPACITY;

            if (evict)
            {
                Arrays.fill(eldest.getValue().key, (byte) 0);
            }

            return evict;
        }
    }
}
