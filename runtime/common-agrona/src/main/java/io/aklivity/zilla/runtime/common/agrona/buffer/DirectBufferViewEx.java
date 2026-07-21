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
package io.aklivity.zilla.runtime.common.agrona.buffer;

import java.nio.ByteBuffer;

/**
 * Internal access to a clean {@link ByteBuffer} view over the wrapped buffer,
 * used to take the intrinsified {@code ByteBuffer.put} bulk-copy fast path
 * without consulting the wrapped buffer's NIO {@code position}/{@code limit} —
 * which Agrona treats as scratch state independent of capacity (e.g. left dirty
 * by {@code CRC32.update}). The view is a cached {@link ByteBuffer#duplicate()}
 * held permanently at {@code position == 0}, {@code limit == capacity}, so an
 * absolute indexed {@code put} is always in bounds for any valid copy.
 * <p>
 * Sealed to {@link UnsafeBufferEx} and {@link UnsafeBufferEx.Native} so the JIT
 * can devirtualize the {@code instanceof} check on the copy hot path.
 */
sealed interface DirectBufferViewEx permits UnsafeBufferEx, UnsafeBufferEx.Native
{
    /**
     * A clean duplicate of the wrapped {@link ByteBuffer}, or {@code null} when
     * the buffer is not {@link ByteBuffer}-backed (e.g. wraps a {@code byte[]},
     * raw address, or {@code MemorySegment}). The returned buffer shares memory
     * with the wrapped buffer and must only be used for absolute indexed access
     * — never for relative reads/writes that mutate its cursors.
     */
    ByteBuffer byteBufferView();
}
