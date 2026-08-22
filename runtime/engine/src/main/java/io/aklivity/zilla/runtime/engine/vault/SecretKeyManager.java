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
package io.aklivity.zilla.runtime.engine.vault;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

/**
 * Wraps buffers of bytes under named keys already resolved by a {@link SecretKeyManagerFactory},
 * and unwraps buffers previously wrapped this way, without either key's raw material ever
 * leaving this manager's implementation.
 * <p>
 * Every operation completes synchronously: the named keys were already retrieved when the
 * owning {@link SecretKeyManagerFactory} was initialized, the same way a TLS
 * {@link javax.net.ssl.KeyManagerFactory} resolves its certificate material once up front
 * rather than per handshake. A named key that rotates behind this manager (or was never
 * retrieved, e.g. because of a transient failure) simply fails the one operation that
 * needed it; the caller is not otherwise notified.
 * </p>
 * <p>
 * The bytes {@link #wrap} produces must be sufficient on their own for a matching
 * {@link #unwrap} call, with no key name supplied back. How an implementation satisfies
 * that is its own decision: a backend whose wrapped-key format already identifies the key
 * that produced it needs no extra framing, while one that doesn't must embed that identity
 * itself and recover it at the start of {@link #unwrap}.
 * </p>
 *
 * @see SecretKeyManagerFactory
 */
public interface SecretKeyManager
{
    /**
     * Wraps a buffer of bytes under the named key.
     *
     * @param keyName  the vault-specific name of the key to wrap under
     * @param bytes    the buffer containing the bytes to wrap
     * @param index    the index within {@code bytes} at which the bytes to wrap begin
     * @param length   the length in bytes of the data to wrap
     * @param dst      the buffer to write the wrapped bytes into
     * @param dstIndex the index within {@code dst} at which to begin writing
     * @return the number of bytes written to {@code dst}, or {@code -1} if the named key
     *         is not resolved
     */
    int wrap(
        String keyName,
        DirectBufferEx bytes,
        int index,
        int length,
        MutableDirectBufferEx dst,
        int dstIndex);

    /**
     * Unwraps a buffer of previously wrapped bytes, resolving the key to unwrap under from
     * the wrapped bytes themselves.
     *
     * @param bytes    the buffer containing the wrapped bytes to unwrap
     * @param index    the index within {@code bytes} at which the wrapped bytes begin
     * @param length   the length in bytes of the wrapped data
     * @param dst      the buffer to write the unwrapped bytes into
     * @param dstIndex the index within {@code dst} at which to begin writing
     * @return the number of bytes written to {@code dst}, or {@code -1} if no key could be
     *         resolved from the wrapped bytes, or the wrapped bytes fail to authenticate
     */
    int unwrap(
        DirectBufferEx bytes,
        int index,
        int length,
        MutableDirectBufferEx dst,
        int dstIndex);
}
