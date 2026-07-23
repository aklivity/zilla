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
module io.aklivity.zilla.config.binding.mcp.http
{
    requires jakarta.json;
    requires jakarta.json.bind;
    requires io.aklivity.zilla.config.engine;

    exports io.aklivity.zilla.config.binding.mcp.http;

    provides io.aklivity.zilla.config.engine.BindingInfo
        with io.aklivity.zilla.config.binding.mcp.http.internal.McpHttpBindingInfo;

    provides io.aklivity.zilla.config.engine.WithConfigAdapterSpi
        with io.aklivity.zilla.config.binding.mcp.http.internal.McpHttpWithConfigAdapter;
}
