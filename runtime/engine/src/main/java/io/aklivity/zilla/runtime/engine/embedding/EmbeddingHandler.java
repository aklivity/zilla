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
package io.aklivity.zilla.runtime.engine.embedding;

/**
 * Produces embedding vectors for a specific embedding configuration.
 * <p>
 * An {@code EmbeddingHandler} is obtained from {@link EmbeddingContext#attach(EmbeddingConfig)}
 * and is confined to a single I/O thread. It exposes a synchronous fast path and an
 * asynchronous variant so that an implementation backed by non-blocking I/O (for example, a
 * call to a remote embedding API) never blocks the calling thread.
 * </p>
 *
 * @see EmbeddingContext
 */
public interface EmbeddingHandler
{
    /**
     * No-op handler: the sync fast path always returns {@code null}; the async variant
     * always invokes {@link CompletionCallback#completed} with {@code null}. Useful as a
     * non-null placeholder so callers can avoid null checks when no embedding is
     * configured. Unlike a configured implementation, this placeholder holds no worker
     * thread reference to hop back onto, so its async variant completes immediately
     * rather than honoring the strictly-later contract described on
     * {@link #embed(long, long, long, String, CompletionCallback)} — harmless here since
     * there is no decision or I/O to race with, but not a pattern for a real implementation
     * to copy.
     */
    EmbeddingHandler NONE = new EmbeddingHandler()
    {
        @Override
        public float[] embed(
            long traceId,
            long bindingId,
            String text)
        {
            return null;
        }

        @Override
        public void embed(
            long traceId,
            long bindingId,
            long contextId,
            String text,
            CompletionCallback completion)
        {
            completion.completed(contextId, null);
        }
    };

    /**
     * Attempts to resolve an embedding for {@code text} from local state only.
     * <p>
     * Decides locally and returns on the caller's stack. Never performs I/O and never
     * blocks; an implementation that cannot answer without I/O returns {@code null} here,
     * and the caller uses the async {@link #embed(long, long, long, String, CompletionCallback)}
     * overload instead when it is prepared to wait for a result.
     * </p>
     *
     * @param traceId    the trace identifier for diagnostics
     * @param bindingId  the binding identifier requesting the embedding
     * @param text       the text to embed
     * @return the embedding vector, or {@code null} if no local result is available
     */
    default float[] embed(
        long traceId,
        long bindingId,
        String text)
    {
        return null;
    }

    /**
     * Async variant of {@link #embed(long, long, String)} for callers prepared to wait for
     * a result, including one that requires non-blocking I/O (for example, a call to a
     * remote embedding API).
     * <p>
     * Completes asynchronously, always: {@code completion} fires strictly later than this
     * call returns — never on the caller's stack, even when the embedding could be decided
     * locally and inline. The callback fires on the engine worker thread that invoked this
     * method; an implementation doing off-thread work is responsible for hopping back onto
     * that thread first (e.g. via {@code EngineContext.signaler()}), so the caller never has
     * to handle a reentrant completion.
     * </p>
     * <p>
     * The {@code contextId} supplied at the call site is echoed back through the callback
     * so a single shared {@link CompletionCallback} instance can route results to the
     * correct stream — typically by issuing a {@code Signaler} signal — without per-call
     * lambda capture.
     * </p>
     *
     * @param traceId     the trace identifier for diagnostics
     * @param bindingId   the binding identifier requesting the embedding
     * @param contextId   a context identifier (e.g., stream id), echoed back to {@code completion}
     * @param text        the text to embed
     * @param completion  callback invoked with the embedding vector on success ({@code null}
     *                    result if no embedding could be produced), or
     *                    {@link CompletionCallback#failed} if the attempt failed
     */
    void embed(
        long traceId,
        long bindingId,
        long contextId,
        String text,
        CompletionCallback completion);

    /**
     * Completion handler for the async {@link #embed(long, long, long, String, CompletionCallback)}
     * operation, modelled after {@code GuardHandler.CompletionCallback}. The {@code contextId}
     * supplied by the caller is echoed back to both methods so a single shared callback instance
     * can dispatch results to the originating stream without per-call lambda capture.
     */
    interface CompletionCallback
    {
        /**
         * Invoked when the operation completes successfully.
         *
         * @param contextId  the {@code contextId} supplied to the originating call
         * @param result     the embedding vector, or {@code null} if no embedding could be produced
         */
        void completed(
            long contextId,
            float[] result);

        /**
         * Invoked when the operation fails.
         *
         * @param contextId  the {@code contextId} supplied to the originating call
         * @param ex         the failure cause
         */
        void failed(
            long contextId,
            Throwable ex);
    }
}
