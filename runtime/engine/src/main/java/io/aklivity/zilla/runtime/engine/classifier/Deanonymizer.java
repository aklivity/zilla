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
 * Restores surrogates minted by an {@link Anonymizer} within a scope.
 * <p>
 * Synchronous and streaming: it never calls a classifier, restoring surrogates by lookup
 * in the scope identified by {@code scopeId}. A surrogate absent from the scope is
 * written as it appears and counted in {@link Result#unresolved()}, because the written bytes
 * alone cannot distinguish it from text that merely resembles a surrogate. Bytes it does not
 * consume remain with the caller, which presents them again with the next fragment, so an
 * implementation holds no state between calls.
 * </p>
 *
 * @see ClassifierHandler#initDeanonymizer
 * @see Anonymizer
 */
public interface Deanonymizer
{
    /**
     * Restores surrogates in a fragment of a value, writing the result to {@code next}.
     *
     * @param traceId     the trace identifier for diagnostics
     * @param bindingId   the binding identifier requesting the restore
     * @param scopeId     identifies the scope surrogates were minted in
     * @param data        the buffer holding the fragment
     * @param index       the offset of the fragment
     * @param length      the length of the fragment
     * @param completion  whether these are the last bytes of the value
     * @param next        receives the restored value
     * @param result      the holder this call updates and returns
     * @return {@code result}, updated with the outcome
     */
    Result deanonymize(
        long traceId,
        long bindingId,
        long scopeId,
        DirectBuffer data,
        int index,
        int length,
        Completion completion,
        ValueConsumerEx next,
        Result result);

    /**
     * Whether a {@link #deanonymize} call carries the last bytes of the value or leaves it open
     * for further fragments. {@code COMPLETE} describes the value, not the call: it stays true
     * across however many partial calls it takes to drain the last bytes, so repeating it is safe.
     */
    enum Completion
    {
        COMPLETE,
        INCOMPLETE
    }

    /**
     * The outcome of a single {@link #deanonymize} call.
     */
    enum Status
    {
        /** the input was consumed or held back; supply more input on the next call */
        UNDERFLOW,
        /** the consumer refused output; wait for it to drain, then present the same bytes again */
        OVERFLOW,
        /** the value is finished and fully consumed */
        COMPLETE,
        /** a replacement can never fit the consumer; the value should be rejected */
        REJECTED
    }

    /**
     * The result of a single {@link #deanonymize} call.
     * <p>
     * A mutable holder owned by, and reused across every call of, the caller. The caller
     * reads its fields immediately after each call and must not retain the instance beyond
     * the next call.
     * </p>
     */
    final class Result
    {
        private Status status;
        private int consumed;
        private int produced;
        private int unresolved;

        public Status status()
        {
            return status;
        }

        public int consumed()
        {
            return consumed;
        }

        public int produced()
        {
            return produced;
        }

        /**
         * The number of surrogate-shaped tokens in the consumed bytes of this call that did
         * not resolve in the scope, each written through unchanged.
         * <p>
         * The count is per call and covers consumed bytes only. An implementation holds no
         * state between calls, so a token in the unconsumed tail is not counted here; the
         * caller presents those bytes again and the next call counts it. The caller sums the
         * counts across the calls that make up a value.
         * </p>
         * <p>
         * A trailing partial token is counted only under {@link Completion#COMPLETE}. Under
         * {@link Completion#INCOMPLETE} it is a fragment boundary, not a miss, and is counted
         * once it completes after the caller presents it again.
         * </p>
         * <p>
         * The count covers tokens that have the surrogate form but do not resolve in the
         * scope. It does not claim each was minted by an {@link Anonymizer}: a surrogate
         * from another scope, and text that happens to match the form, are both counted.
         * The form of a surrogate is implementation-specific.
         * </p>
         * <p>
         * It is a count and not a collection, so a call allocates nothing to report it.
         * </p>
         *
         * @return the number of unresolved surrogate-shaped tokens in the consumed bytes
         */
        public int unresolved()
        {
            return unresolved;
        }

        /**
         * Updates this result in place and returns it.
         *
         * @param status      the outcome
         * @param consumed    the number of input bytes consumed
         * @param produced    the number of output bytes produced
         * @param unresolved  the number of unresolved surrogate-shaped tokens in the consumed bytes
         * @return this result
         */
        public Result set(
            Status status,
            int consumed,
            int produced,
            int unresolved)
        {
            this.status = status;
            this.consumed = consumed;
            this.produced = produced;
            this.unresolved = unresolved;
            return this;
        }
    }
}
