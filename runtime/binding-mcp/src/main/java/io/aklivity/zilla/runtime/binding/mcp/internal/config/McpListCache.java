/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mcp.internal.config;

import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public final class McpListCache
{
    private static final String STORE_KEY_TOOLS = "tools";
    private static final String STORE_KEY_RESOURCES = "resources";
    private static final String STORE_KEY_PROMPTS = "prompts";
    private static final long STORE_TTL_FOREVER = Long.MAX_VALUE;

    private final StoreHandler store;

    public McpListCache(
        StoreHandler store)
    {
        this.store = store;
    }

    public void get(
        int kind,
        BiConsumer<String, String> completion)
    {
        store.get(storeKeyForListKind(kind), completion);
    }

    public void put(
        int kind,
        String value,
        Consumer<String> completion)
    {
        store.put(storeKeyForListKind(kind), value, STORE_TTL_FOREVER, completion);
    }

    private static String storeKeyForListKind(
        int kind)
    {
        return switch (kind)
        {
        case KIND_TOOLS_LIST -> STORE_KEY_TOOLS;
        case KIND_RESOURCES_LIST -> STORE_KEY_RESOURCES;
        case KIND_PROMPTS_LIST -> STORE_KEY_PROMPTS;
        default -> throw new IllegalStateException("unexpected list kind: " + kind);
        };
    }
}
