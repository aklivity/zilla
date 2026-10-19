/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.mcp.search;

import java.util.Collection;
import java.util.List;

/**
 * A ranking backend behind {@code options.cache.tools.search}.
 * <p>
 * Instances are single-threaded, owned by the worker that rebuilds and queries the warm
 * tool-search index; {@link #index(Collection, CompletionCallback)} replaces the prior
 * contents entirely.
 * </p>
 * <p>
 * <b>Both operations complete asynchronously, always.</b> {@code completed} fires
 * <em>strictly later</em> than the call returns — never on the caller's stack, even when an
 * implementation can decide inline (e.g. a purely in-memory ranking backend). Implementations
 * defer delivery by at least one tick, typically via {@code EngineContext.dispatch}, so a
 * caller composing multiple backends (see {@code McpToolSearchComposite}) never has to handle
 * a reentrant completion.
 * </p>
 */
public interface McpToolSearchIndex
{
    /**
     * Rebuilds the index from the current warm catalog.
     *
     * @param documents  the full set of searchable tool documents
     * @param completed  invoked once the index has been rebuilt, or has failed to rebuild
     */
    void index(
        Collection<McpToolSearchDocument> documents,
        CompletionCallback<Void> completed);

    /**
     * Ranks every indexed document against the given query text.
     * <p>
     * On success, {@code completed} receives every document with a non-zero match, sorted by
     * descending relevance, with no limit applied — callers apply {@code limit} after any
     * additional filtering (e.g. per-session authorization).
     * </p>
     *
     * @param text       the query text
     * @param completed  invoked with matches sorted by descending relevance (empty if nothing
     *                   matches), or with the failure if ranking could not complete
     */
    void query(
        String text,
        CompletionCallback<List<McpToolSearchMatch>> completed);

    /**
     * Completion handler for the asynchronous operations on this interface, modelled after
     * {@link java.nio.channels.CompletionHandler}.
     *
     * @param <V> the result type
     */
    interface CompletionCallback<V>
    {
        /**
         * Invoked when the operation completes successfully.
         *
         * @param result  the operation result
         */
        void completed(
            V result);

        /**
         * Invoked when the operation fails.
         *
         * @param ex  the failure cause
         */
        void failed(
            Throwable ex);
    }
}
