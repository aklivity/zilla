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
package io.aklivity.zilla.runtime.engine.model;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;

/**
 * Validates a payload against a data model schema on the I/O hot path.
 * <p>
 * A {@code ValidatorHandler} is supplied by {@link ModelContext#supplyValidatorHandler} and
 * confined to a single I/O thread. Unlike a {@link ConverterHandler}, a validator does not
 * transform the payload — it either passes it through to {@code next} unchanged, or rejects it.
 * </p>
 * <p>
 * Fragmented payloads are supported via the {@code flags} parameter using the
 * {@link #FLAGS_INIT} and {@link #FLAGS_FIN} bits. A complete single-fragment payload sets
 * both bits ({@link #FLAGS_COMPLETE}).
 * </p>
 *
 * @see ModelContext
 * @see ValueConsumer
 */
public interface ValidatorHandler
{
    /**
     * Flags value indicating a complete, unfragmented payload (both INIT and FIN bits set).
     */
    int FLAGS_COMPLETE = 0x03;

    /**
     * Flag bit indicating this is the first (or only) fragment of a payload.
     */
    int FLAGS_INIT = 0x02;

    /**
     * Flag bit indicating this is the last (or only) fragment of a payload.
     */
    int FLAGS_FIN = 0x01;

    /**
     * Validates the payload at {@code data[index..index+length)} against the model schema.
     * <p>
     * If validation passes, the payload is forwarded to {@code next} unchanged and {@code true}
     * is returned. If validation fails, {@code next} is not called and {@code false} is returned,
     * signalling that the stream should be reset.
     * </p>
     *
     * @param traceId    the trace identifier for diagnostics
     * @param bindingId  the binding identifier
     * @param flags      fragment flags ({@link #FLAGS_INIT}, {@link #FLAGS_FIN}, or both)
     * @param data       the source buffer
     * @param index      the offset of the payload in the buffer
     * @param length     the length of the payload
     * @param next       the consumer to receive the payload if valid
     * @return {@code true} if the payload is valid and has been forwarded to {@code next};
     *         {@code false} if validation failed
     */
    boolean validate(
        long traceId,
        long bindingId,
        int flags,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next);

    /**
     * Convenience overload that validates a complete, unfragmented payload
     * (equivalent to calling {@link #validate} with {@link #FLAGS_COMPLETE}).
     *
     * @param traceId    the trace identifier
     * @param bindingId  the binding identifier
     * @param data       the source buffer
     * @param index      the offset of the payload
     * @param length     the length of the payload
     * @param next       the consumer to receive the payload if valid
     * @return {@code true} if valid, {@code false} otherwise
     */
    default boolean validate(
        long traceId,
        long bindingId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        return validate(traceId, bindingId, FLAGS_COMPLETE, data, index, length, next);
    }
}
