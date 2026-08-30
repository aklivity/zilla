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
package io.aklivity.zilla.config.binding.mcp.internal;

import java.net.URL;
import java.util.Map;

import io.aklivity.zilla.config.binding.mcp.McpTestToolSearchIndexConfig;
import io.aklivity.zilla.config.engine.BindingExtInfo;
import io.aklivity.zilla.config.engine.ConfigExtAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class McpTestToolSearchIndexExtInfo implements BindingExtInfo
{
    @Override
    public String type()
    {
        return McpBindingInfo.TYPE;
    }

    @Override
    public URL schema()
    {
        return null;
    }

    @Override
    public ConfigExtAdapter<OptionsConfig> options()
    {
        return new ConfigExtAdapter<>(Map.of(), Map.of(),
            Map.of(McpTestToolSearchIndexConfig.NAME, new McpTestToolSearchIndexConfigAdapter()));
    }
}
