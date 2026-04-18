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
package io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.stream;

import java.util.Map;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.McpSchemaRegistryBinding;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.McpSchemaRegistryConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.config.McpSchemaRegistryBindingConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.OpenapiBinding;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class McpSchemaRegistryProxyFactory implements BindingHandler
{
    static final Map<String, String> TOOL_OPERATION_IDS = Map.of(
        "list_subjects", "list",
        "describe_subject", "getSchemaVersions",
        "get_schema", "getSchemaByVersion",
        "register_schema", "register",
        "delete_subject", "deleteSubject",
        "delete_schema_version", "deleteSchemaVersion",
        "check_compatibility", "testCompatibilityBySubjectName_1",
        "get_compatibility", "getSubjectLevelConfig",
        "set_compatibility", "updateSubjectLevelConfig",
        "list_contexts", "getContexts"
    );

    private final Long2ObjectHashMap<McpSchemaRegistryBindingConfig> bindings;
    private final MutableDirectBuffer writeBuffer;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final BindingHandler streamFactory;
    private final int mcpSchemaRegistryTypeId;
    private final int openapiTypeId;

    public McpSchemaRegistryProxyFactory(
        McpSchemaRegistryConfiguration config,
        EngineContext context)
    {
        this.bindings = new Long2ObjectHashMap<>();
        this.writeBuffer = context.writeBuffer();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.streamFactory = context.streamFactory();
        this.mcpSchemaRegistryTypeId = context.supplyTypeId(McpSchemaRegistryBinding.NAME);
        this.openapiTypeId = context.supplyTypeId(OpenapiBinding.NAME);
    }

    public void attach(
        BindingConfig binding)
    {
        McpSchemaRegistryBindingConfig config = new McpSchemaRegistryBindingConfig(binding);
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
        return null;
    }
}
