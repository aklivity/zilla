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
package io.aklivity.zilla.runtime.binding.mcp.openapi.internal;

import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiBindingInfo;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.Binding;

public final class McpOpenapiBinding implements Binding
{
    public static final String NAME = McpOpenapiBindingInfo.TYPE;

    private final McpOpenapiConfiguration config;

    McpOpenapiBinding(
        McpOpenapiConfiguration config)
    {
        this.config = config;
    }

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public String originType(
        KindConfig kind)
    {
        return kind == KindConfig.CLIENT ? NAME : null;
    }

    @Override
    public String routedType(
        KindConfig kind)
    {
        return kind == KindConfig.CLIENT ? NAME : null;
    }

    @Override
    public McpOpenapiBindingContext supply(
        EngineContext context)
    {
        return new McpOpenapiBindingContext(config, context);
    }
}
