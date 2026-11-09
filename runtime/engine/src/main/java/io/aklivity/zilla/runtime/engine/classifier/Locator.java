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
package io.aklivity.zilla.runtime.engine.classifier;

import org.agrona.DirectBuffer;

/**
 * Locates the labelled spans of a value, and does nothing else.
 * <p>
 * Where a {@link Detector} reports only whether a label is present, a {@code Locator} reports
 * where each labelled span lies, leaving what to do with the spans to the caller.
 * </p>
 */
public interface Locator
{
    /**
     * Locates the labelled spans in a value asynchronously.
     * <p>
     * Spans are reported to {@code spans} in ascending order of offset and never overlap, with any
     * classifier thresholds already applied. Delivery of a span does not allocate. Offsets are UTF-8
     * byte offsets into {@code data}, so any unit conversion is the classifier's responsibility.
     * </p>
     * <p>
     * Completion is signalled strictly after this call returns, on the caller's I/O thread, and every
     * call is answered exactly once, either by {@link CompletionCallback#completed} or by
     * {@link CompletionCallback#failed}. Spans are delivered before completion and not after it.
     * The {@code data} buffer is not retained once this call returns.
     * </p>
     *
     * @param traceId     the trace identifier for diagnostics
     * @param bindingId   the binding identifier requesting the location
     * @param contextId   a context identifier, echoed back to {@code completion}
     * @param data        the buffer holding the complete value
     * @param index       the offset of the value
     * @param length      the length of the value
     * @param spans       receives each labelled span located
     * @param completion  invoked when the location completes, or fails
     */
    void locate(
        long traceId,
        long bindingId,
        long contextId,
        DirectBuffer data,
        int index,
        int length,
        SpanConsumer spans,
        CompletionCallback completion);

    /**
     * Receives the labelled spans located in a value.
     */
    interface SpanConsumer
    {
        /**
         * Reports a labelled span.
         *
         * @param label   the label alias of the span
         * @param offset  the UTF-8 byte offset of the span within the buffer supplied to the call
         * @param length  the length of the span in bytes
         */
        void span(
            String label,
            int offset,
            int length);
    }

    /**
     * Completion handler for the async {@link #locate} operation.
     */
    interface CompletionCallback
    {
        /**
         * Invoked when all spans have been reported.
         *
         * @param contextId  the {@code contextId} supplied to the originating call
         */
        void completed(
            long contextId);

        /**
         * Invoked when the location fails.
         *
         * @param contextId  the {@code contextId} supplied to the originating call
         * @param ex         the failure cause
         */
        void failed(
            long contextId,
            Throwable ex);
    }
}
