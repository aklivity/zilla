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
package io.aklivity.zilla.runtime.binding.mcp.asyncapi.internal.stream;

import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.AsyncapiBinding;
import io.aklivity.zilla.runtime.binding.mcp.asyncapi.internal.McpAsyncApiBinding;
import io.aklivity.zilla.runtime.binding.mcp.asyncapi.internal.McpAsyncApiConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.asyncapi.internal.config.McpAsyncApiBindingConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class McpAsyncApiProxyFactory implements BindingHandler
{
    private final Long2ObjectHashMap<McpAsyncApiBindingConfig> bindings;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final MutableDirectBuffer writeBuffer;
    private final BindingHandler streamFactory;
    private final int mcpAsyncApiTypeId;
    private final int asyncapiTypeId;

    public McpAsyncApiProxyFactory(
        McpAsyncApiConfiguration config,
        EngineContext context)
    {
        this.bindings = new Long2ObjectHashMap<>();
        this.writeBuffer = context.writeBuffer();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.streamFactory = context.streamFactory();
        this.mcpAsyncApiTypeId = context.supplyTypeId(McpAsyncApiBinding.NAME);
        this.asyncapiTypeId = context.supplyTypeId(AsyncapiBinding.NAME);
    }

    public void attach(
        BindingConfig binding)
    {
        McpAsyncApiBindingConfig config = new McpAsyncApiBindingConfig(binding);
        bindings.put(binding.id, config);
    }

    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer receiver)
    {
        // Proxy binding: accept mcp streams, route to asyncapi streams.
        // Derives tool names, call semantics, and schemas from AsyncAPI specs
        // held in catalogs. Operation-to-tool semantics:
        //   send operations -> fire-and-forget produce tools
        //   send + reply    -> request/reply tool
        //   receive         -> fetch tools
        // A full implementation would decode the mcp BEGIN extension to extract
        // the operation name, look up the route via McpAsyncApiBindingConfig#resolve,
        // then forward as an asyncapi BEGIN with the resolved api-id and operation-id.
        return null;
    }
}
