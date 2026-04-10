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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;

/**
 * Converts a payload from one format to another on the I/O hot path.
 * <p>
 * A {@code ConverterHandler} is supplied by {@link ModelContext} and confined to a single I/O
 * thread. Each call to {@link #convert} accepts a {@link DirectBuffer} slice and a downstream
 * {@link ValueConsumer}, enabling zero-copy transformation without intermediate object allocation.
 * </p>
 * <p>
 * A converter may also extract named field values from the payload — for example, to surface a
 * Kafka message key embedded in an Avro or Protobuf payload — via the {@link #extract},
 * {@link #extractedLength}, and {@link #extracted} methods.
 * </p>
 * <p>
 * The sentinel {@link #NONE} implementation forwards all payloads unchanged and is safe to use
 * as a no-op placeholder.
 * </p>
 *
 * @see ModelContext
 * @see ValueConsumer
 */
public interface ConverterHandler
{
    /**
     * Flag value indicating a complete, unfragmented payload (both INIT and FIN bits set).
     * Pass as the implicit flags for converters that do not distinguish fragments.
     */
    int FLAGS_COMPLETE = 0x03;

    /**
     * Return value from {@link #convert} indicating that conversion failed and the stream
     * should be reset.
     */
    int VALIDATION_FAILURE = -1;

    /**
     * No-op converter that forwards all payloads to {@code next} unchanged.
     */
    ConverterHandler NONE = (traceId, bindingId, data, index, length, next) ->
    {
        next.accept(data, index, length);
        return length;
    };

    /**
     * Functional interface for visiting an extracted field value.
     */
    @FunctionalInterface
    interface FieldVisitor
    {
        /**
         * Called with the buffer slice containing an extracted field value.
         *
         * @param buffer  the buffer containing the field value
         * @param index   the offset of the field value
         * @param length  the length of the field value
         */
        void visit(
            DirectBufferEx buffer,
            int index,
            int length);
    }

    /**
     * Registers a field extraction path so that subsequent {@link #convert} calls will
     * make the field value available via {@link #extracted}.
     * <p>
     * The path syntax is format-specific (e.g., {@code "$.fieldName"} for JSON,
     * a field name for Avro). Calling this method is optional; omitting it disables
     * extraction for that path.
     * </p>
     *
     * @param path  the field path to extract
     */
    default void extract(
        String path)
    {
    }

    /**
     * Converts the payload at {@code data[index..index+length)} and forwards the result
     * to {@code next}.
     * <p>
     * Returns the number of converted bytes forwarded to {@code next}, or
     * {@link #VALIDATION_FAILURE} if conversion fails and the stream should be reset.
     * </p>
     *
     * @param traceId    the trace identifier for diagnostics
     * @param bindingId  the binding identifier
     * @param data       the source buffer
     * @param index      the offset of the payload in the buffer
     * @param length     the length of the payload
     * @param next       the consumer to receive converted bytes
     * @return the number of bytes forwarded, or {@link #VALIDATION_FAILURE}
     */
    int convert(
        long traceId,
        long bindingId,
        DirectBufferEx data,
        int index,
        int length,
        ValueConsumer next);

    /**
     * Returns the byte length of the most recently extracted value for the given path,
     * or {@code 0} if the path has not been registered or was not found in the last payload.
     *
     * @param path  the previously registered extraction path
     * @return the extracted value length in bytes
     */
    default int extractedLength(
        String path)
    {
        return 0;
    }

    /**
     * Visits the most recently extracted value for the given path via the supplied
     * {@link FieldVisitor}.
     * <p>
     * The visitor is called only if a value was found for the path in the last converted payload.
     * </p>
     *
     * @param path     the previously registered extraction path
     * @param visitor  the visitor to invoke with the extracted value buffer slice
     */
    default void extracted(
        String path,
        FieldVisitor visitor)
    {
    }

    /**
     * Returns the number of additional bytes required in the write buffer to accommodate any
     * framing overhead the converter may add (e.g., schema id prefix bytes).
     *
     * @param data   the source buffer containing the unconverted payload
     * @param index  the offset of the payload
     * @param length the length of the payload
     * @return the padding byte count (0 for converters that do not expand the payload)
     */
    default int padding(
        DirectBufferEx data,
        int index,
        int length)
    {
        return 0;
    }
}
