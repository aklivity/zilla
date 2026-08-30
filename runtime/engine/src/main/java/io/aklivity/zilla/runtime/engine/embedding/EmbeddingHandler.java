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
 * and is confined to a single I/O thread.
 * </p>
 * <p>
 * <b>Resolution always completes asynchronously.</b> {@link #embed} takes a completion
 * callback that fires <em>strictly later</em> than the call returns — never synchronously,
 * even when the embedding could be produced inline (for example, from an in-process model).
 * The callback fires on the caller's I/O thread; the implementation owns the responsibility
 * for thread alignment, deferring via {@code EngineContext.dispatch(Runnable)} to the next
 * event-loop tick of the same worker, the same mechanism {@code StoreHandler} and the async
 * {@code GuardHandler.reauthorize} are built on. For a backend doing off-thread work (for
 * example, a call to a remote embedding API) this means hopping the completion event back
 * onto the calling worker via {@code dispatch} before invoking the callback. In either case
 * the caller observes "callback runs on my thread, later", and never has to handle a
 * reentrant completion.
 * </p>
 *
 * @see EmbeddingContext
 */
public interface EmbeddingHandler
{
    /**
     * Resolves an embedding for {@code text}, asynchronously.
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
