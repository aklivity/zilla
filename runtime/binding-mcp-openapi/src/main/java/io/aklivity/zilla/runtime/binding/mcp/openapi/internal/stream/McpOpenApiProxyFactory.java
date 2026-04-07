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
package io.aklivity.zilla.runtime.binding.mcp.openapi.internal.stream;

import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.McpOpenApiBinding;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.McpOpenApiConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config.McpOpenApiBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config.McpOpenApiRouteConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.OpenapiBinding;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class McpOpenApiProxyFactory implements BindingHandler
{
    private static final int STREAM_STATE_INITIAL = 0;
    private static final int STREAM_STATE_OPENED = 1;
    private static final int STREAM_STATE_CLOSED = 2;

    private final Long2ObjectHashMap<McpOpenApiBindingConfig> bindings;
    private final EngineContext context;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final MutableDirectBuffer writeBuffer;
    private final BindingHandler streamFactory;
    private final int mcpOpenApiTypeId;
    private final int openapiTypeId;

    public McpOpenApiProxyFactory(
        McpOpenApiConfiguration config,
        EngineContext context)
    {
        this.context = context;
        this.bindings = new Long2ObjectHashMap<>();
        this.writeBuffer = context.writeBuffer();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.streamFactory = context.streamFactory();
        this.mcpOpenApiTypeId = context.supplyTypeId(McpOpenApiBinding.NAME);
        this.openapiTypeId = context.supplyTypeId(OpenapiBinding.NAME);
    }

    public void attach(
        BindingConfig binding)
    {
        McpOpenApiBindingConfig config = new McpOpenApiBindingConfig(binding);
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
        // Proxy binding: accept mcp streams, route to openapi streams
        // Stream routing is handled via composite config in a full implementation;
        // here we provide the minimal skeleton that wires up when the engine calls newStream.
        return null;
    }
}
