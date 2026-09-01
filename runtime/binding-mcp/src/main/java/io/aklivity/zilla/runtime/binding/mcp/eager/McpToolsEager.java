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
package io.aklivity.zilla.runtime.binding.mcp.eager;

import java.util.Collection;
import java.util.List;

/**
 * A policy behind {@code options.cache.tools.eager}, deciding which tools in a
 * {@code tools/list} response are marked eager (loaded immediately) versus cold (deferred).
 * <p>
 * Instances are single-threaded, owned by the worker that rebuilds the warm tool catalog and
 * resolves each {@code tools/list} response; {@link #index(Collection, CompletionCallback)}
 * replaces the prior indexed state entirely.
 * </p>
 * <p>
 * <b>{@link #index(Collection, CompletionCallback)} completes asynchronously, always.</b>
 * {@code completed} fires <em>strictly later</em> than the call returns -- never on the
 * caller's stack, even when an implementation can decide inline (e.g. a byte-length
 * heuristic). Implementations defer delivery by at least one tick, typically via
 * {@code EngineContext.dispatch}, so a caller composing multiple policies (see
 * {@code McpToolsEagerComposite}) never has to handle a reentrant completion.
 * {@link #select(long, List)} is synchronous -- it only ever reads state {@link #index}
 * already computed.
 * </p>
 */
public interface McpToolsEager
{
    /**
     * Rebuilds whatever indexed state this policy needs from the full warm catalog.
     *
     * @param documents  one document per cached tool
     * @param completed  invoked once indexing has completed, or has failed
     */
    void index(
        Collection<McpToolEagerDocument> documents,
        CompletionCallback<Void> completed);

    /**
     * Reorders and/or narrows the candidate list for one {@code tools/list} response,
     * decided synchronously from already-indexed state.
     * <p>
     * The returned list's membership <em>and</em> order both matter -- order is how a
     * ranking policy communicates its ranking to whatever policy runs next when composed
     * (e.g. a policy that fills by that order until a budget is spent). Composing multiple
     * policies threads this list through each in turn; only the final result collapses to a
     * plain admitted/not-admitted set.
     * </p>
     *
     * @param authorization  the requesting session's authorization
     * @param names          the candidate tool names, already scope-filtered, in catalog order
     * @return the admitted tool names, in the order this policy ranks them
     */
    List<CharSequence> select(
        long authorization,
        List<CharSequence> names);

    /**
     * The write side of this policy's usage tracking, invoked once per {@code tools/call}
     * dispatch. Every policy that does not track usage returns {@link McpToolsEagerRecorder#NONE}.
     *
     * @return this policy's recorder, or {@link McpToolsEagerRecorder#NONE}
     */
    McpToolsEagerRecorder recorder();

    /**
     * Completion handler for {@link #index(Collection, CompletionCallback)}, modelled after
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
