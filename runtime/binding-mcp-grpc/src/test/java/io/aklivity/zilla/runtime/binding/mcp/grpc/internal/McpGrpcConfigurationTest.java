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
package io.aklivity.zilla.runtime.binding.mcp.grpc.internal;

import static io.aklivity.zilla.runtime.binding.mcp.grpc.internal.McpGrpcConfiguration.MCP_GRPC_CLIENT_EXIT;
import static io.aklivity.zilla.runtime.binding.mcp.grpc.internal.McpGrpcConfiguration.MCP_GRPC_SESSION_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

public class McpGrpcConfigurationTest
{
    @Test
    public void shouldVerifyConstants()
    {
        assertThat(MCP_GRPC_CLIENT_EXIT.name(), equalTo("zilla.binding.mcp.grpc.client.exit"));
        assertThat(MCP_GRPC_SESSION_ID.name(), equalTo("zilla.binding.mcp.grpc.session.id"));
    }

    @Test
    public void shouldDefaultClientExitToSystemHttpClient()
    {
        assertThat(new McpGrpcConfiguration().clientExit(), equalTo("sys:http_client"));
    }

    @Test
    public void shouldSupplySessionId()
    {
        assertThat(new McpGrpcConfiguration().sessionIdSupplier().apply(0L), not(nullValue()));
    }
}
