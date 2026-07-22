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
package io.aklivity.zilla.runtime.binding.mcp.internal;

import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_ALT_SVC_ENABLED;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_ALT_SVC_MAX_AGE;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_CLIENT_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_CLIENT_VERSION;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_ELICITATION_ID;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_ELICIT_CORRELATION_ID;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_HYDRATE_FILTER;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_INACTIVITY_TIMEOUT;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_KEEPALIVE_TOLERANCE;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_LEASE_RETRY;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_LEASE_TTL;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_SERVER_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_SERVER_VERSION;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_SESSION_ID;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_SESSION_ID_ATTEMPTS;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration.MCP_SSE_KEEPALIVE_INTERVAL;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DETACH_ON_CLOSE;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_SYNTHETIC_ABORT;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class McpConfigurationTest
{
    public static final String ENGINE_DETACH_ON_CLOSE_NAME = "zilla.engine.detach.on.close";
    public static final String ENGINE_SYNTHETIC_ABORT_NAME = "zilla.engine.synthetic.abort";
    public static final String MCP_SESSION_ID_NAME = "zilla.binding.mcp.session.id";
    public static final String MCP_ELICITATION_ID_NAME = "zilla.binding.mcp.elicitation.id";
    public static final String MCP_ELICIT_CORRELATION_ID_NAME = "zilla.binding.mcp.elicit.correlation.id";
    public static final String MCP_SERVER_NAME_NAME = "zilla.binding.mcp.server.name";
    public static final String MCP_SERVER_VERSION_NAME = "zilla.binding.mcp.server.version";
    public static final String MCP_CLIENT_NAME_NAME = "zilla.binding.mcp.client.name";
    public static final String MCP_CLIENT_VERSION_NAME = "zilla.binding.mcp.client.version";
    public static final String MCP_INACTIVITY_TIMEOUT_NAME = "zilla.binding.mcp.inactivity.timeout";
    public static final String MCP_SESSION_ID_ATTEMPTS_NAME = "zilla.binding.mcp.session.id.attempts";
    public static final String MCP_KEEPALIVE_TOLERANCE_NAME = "zilla.binding.mcp.keepalive.tolerance";
    public static final String MCP_SSE_KEEPALIVE_INTERVAL_NAME = "zilla.binding.mcp.sse.keepalive.interval";
    public static final String MCP_ALT_SVC_ENABLED_NAME = "zilla.binding.mcp.alt.svc.enabled";
    public static final String MCP_ALT_SVC_MAX_AGE_NAME = "zilla.binding.mcp.alt.svc.max.age";
    public static final String MCP_HYDRATE_FILTER_NAME = "zilla.binding.mcp.hydrate.filter";
    public static final String MCP_LEASE_TTL_NAME = "zilla.binding.mcp.lease.ttl";
    public static final String MCP_LEASE_RETRY_NAME = "zilla.binding.mcp.lease.retry";

    @Test
    public void shouldVerifyConstants() throws Exception
    {
        assertEquals(ENGINE_DETACH_ON_CLOSE.name(), ENGINE_DETACH_ON_CLOSE_NAME);
        assertEquals(ENGINE_SYNTHETIC_ABORT.name(), ENGINE_SYNTHETIC_ABORT_NAME);
        assertEquals(MCP_SESSION_ID.name(), MCP_SESSION_ID_NAME);
        assertEquals(MCP_ELICITATION_ID.name(), MCP_ELICITATION_ID_NAME);
        assertEquals(MCP_ELICIT_CORRELATION_ID.name(), MCP_ELICIT_CORRELATION_ID_NAME);
        assertEquals(MCP_SERVER_NAME.name(), MCP_SERVER_NAME_NAME);
        assertEquals(MCP_SERVER_VERSION.name(), MCP_SERVER_VERSION_NAME);
        assertEquals(MCP_CLIENT_NAME.name(), MCP_CLIENT_NAME_NAME);
        assertEquals(MCP_CLIENT_VERSION.name(), MCP_CLIENT_VERSION_NAME);
        assertEquals(MCP_INACTIVITY_TIMEOUT.name(), MCP_INACTIVITY_TIMEOUT_NAME);
        assertEquals(MCP_SESSION_ID_ATTEMPTS.name(), MCP_SESSION_ID_ATTEMPTS_NAME);
        assertEquals(MCP_KEEPALIVE_TOLERANCE.name(), MCP_KEEPALIVE_TOLERANCE_NAME);
        assertEquals(MCP_SSE_KEEPALIVE_INTERVAL.name(), MCP_SSE_KEEPALIVE_INTERVAL_NAME);
        assertEquals(MCP_ALT_SVC_ENABLED.name(), MCP_ALT_SVC_ENABLED_NAME);
        assertEquals(MCP_ALT_SVC_MAX_AGE.name(), MCP_ALT_SVC_MAX_AGE_NAME);
        assertEquals(MCP_HYDRATE_FILTER.name(), MCP_HYDRATE_FILTER_NAME);
        assertEquals(MCP_LEASE_TTL.name(), MCP_LEASE_TTL_NAME);
        assertEquals(MCP_LEASE_RETRY.name(), MCP_LEASE_RETRY_NAME);
    }
}
