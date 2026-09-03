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

import java.util.List;

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
 * <p>
 * <b>Always batched.</b> {@link #embed} takes a list of texts, not a single text, so a
 * caller resolving many embeddings at once (for example, indexing a corpus of documents)
 * can hand them to a single call rather than issuing one call per text. A provider whose
 * remote API accepts multiple texts per request can then satisfy the whole batch in as few
 * underlying requests as its API allows, rather than one request per text — the difference
 * between one request and dozens against a rate-limited API. A caller with exactly one text
 * to embed (for example, a single search query) passes a single-element list.
 * </p>
 *
 * @see EmbeddingContext
 */
public interface EmbeddingHandler
{
    /**
     * Resolves an embedding for each of {@code texts}, in order, asynchronously.
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
     * @param texts       the texts to embed, in order
     * @param completion  callback invoked with one embedding vector per text, in the same
     *                    order, on success (an individual vector is {@code null} if no
     *                    embedding could be produced for that text), or
     *                    {@link CompletionCallback#failed} if the attempt failed
     */
    void embed(
        long traceId,
        long bindingId,
        long contextId,
        List<String> texts,
        CompletionCallback completion);

    /**
     * Completion handler for the async
     * {@link #embed(long, long, long, List, CompletionCallback)} operation, modelled after
     * {@code GuardHandler.CompletionCallback}. The {@code contextId} supplied by the caller
     * is echoed back to both methods so a single shared callback instance can dispatch
     * results to the originating stream without per-call lambda capture.
     */
    interface CompletionCallback
    {
        /**
         * Invoked when the operation completes successfully.
         *
         * @param contextId  the {@code contextId} supplied to the originating call
         * @param results    one embedding vector per requested text, in the same order;
         *                    an individual vector is {@code null} if no embedding could be
         *                    produced for that text
         */
        void completed(
            long contextId,
            float[][] results);

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
