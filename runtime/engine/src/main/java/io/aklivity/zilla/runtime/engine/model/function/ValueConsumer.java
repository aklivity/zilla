/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.model.function;

import org.agrona.DirectBuffer;

/**
 * Receives a slice of a {@link DirectBuffer} as a zero-copy callback.
 * <p>
 * Used throughout the model and catalog pipeline as the downstream sink for converted,
 * decoded, encoded, or validated payload bytes. Implementations must not retain a reference
 * to the buffer beyond the duration of the {@link #accept} call.
 * </p>
 * <p>
 * The sentinel {@link #NOP} implementation discards all data and is safe to use as a
 * no-op placeholder.
 * </p>
 *
 * @see ConverterHandler
 * @see ValidatorHandler
 */
@FunctionalInterface
public interface ValueConsumer
{
    /** No-op consumer that discards all data. */
    ValueConsumer NOP = (buffer, index, length) -> {};

    /**
     * Receives the bytes at {@code buffer[index..index+length)}.
     * <p>
     * The buffer slice is only valid for the duration of this call. Implementations must
     * copy any bytes they need to retain beyond this invocation.
     * </p>
     *
     * @param buffer  the source buffer
     * @param index   the offset of the data in the buffer
     * @param length  the length of the data
     */
    void accept(
        DirectBuffer buffer,
        int index,
        int length);
}
