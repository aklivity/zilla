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
package io.aklivity.zilla.runtime.binding.mcp.grpc.internal.stream;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.grpc.internal.McpGrpcConfiguration;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public final class McpGrpcClientFactory implements BindingHandler
{
    public McpGrpcClientFactory(
        McpGrpcConfiguration config,
        EngineContext context)
    {
    }

    public void attach(
        BindingConfig binding)
    {
    }

    public void detach(
        long bindingId)
    {
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        return null;
    }
}
