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
 * tool-search index; {@link #index(Collection)} replaces the prior contents entirely.
 * </p>
 */
public interface McpToolSearchIndex
{
    /**
     * Rebuilds the index from the current warm catalog.
     *
     * @param documents  the full set of searchable tool documents
     */
    void index(
        Collection<McpToolSearchDocument> documents);

    /**
     * Ranks every indexed document against the given query text.
     * <p>
     * Returns every document with a non-zero match, sorted by descending relevance, with no
     * limit applied — callers apply {@code limit} after any additional filtering (e.g.
     * per-session authorization).
     * </p>
     *
     * @param text  the query text
     * @return matches sorted by descending relevance; empty if nothing matches
     */
    List<McpToolSearchMatch> query(
        String text);
}
