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
 * Substitutes located labels in a value with surrogates.
 * <p>
 * The input is complete and the output is complete. Surrogates are allocated within the
 * scope identified by {@code scopeId}, keyed on the original value, so a value appearing
 * several times maps to one surrogate within that scope. A surrogate resolves only inside
 * the scope that minted it. Spans are located by the implementation, which owns any
 * conversion between the units its analysis reports and byte offsets.
 * </p>
 * <p>
 * When nothing is found the original bytes are passed straight to the consumer. An
 * {@code Anonymizer} is not reentrant: its input is complete by construction.
 * </p>
 *
 * @see ClassifierHandler#initAnonymizer
 * @see Deanonymizer
 */
public interface Anonymizer
{
    /**
     * Rewrites {@code data} asynchronously, writing the result to {@code next}.
     *
     * @param traceId     the trace identifier for diagnostics
     * @param bindingId   the binding identifier requesting the rewrite
     * @param scopeId     identifies the scope surrogates are minted in and restored from
     * @param contextId   a context identifier, echoed back to {@code completion}
     * @param data        the buffer holding the complete value
     * @param index       the offset of the value
     * @param length      the length of the value
     * @param next        receives the rewritten value
     * @param completion  invoked when the rewrite completes, or fails
     */
    void anonymize(
        long traceId,
        long bindingId,
        long scopeId,
        long contextId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumerEx next,
        CompletionCallback completion);

    /**
     * Completion handler for the async anonymize operation.
     */
    interface CompletionCallback
    {
        /**
         * Invoked when the rewrite completes.
         *
         * @param contextId  the {@code contextId} supplied to the originating call
         * @param consumed   the number of input bytes consumed
         */
        void completed(
            long contextId,
            int consumed);

        /**
         * Invoked when the rewrite fails.
         *
         * @param contextId  the {@code contextId} supplied to the originating call
         * @param ex         the failure cause
         */
        void failed(
            long contextId,
            Throwable ex);
    }
}
