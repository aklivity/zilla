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
package io.aklivity.zilla.runtime.binding.mcp.internal.stream;

import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_CLIENT_NAME_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_CLIENT_VERSION_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_ELICITATION_ID_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_ELICIT_CORRELATION_ID_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_HYDRATE_FILTER_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_SESSION_ID_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

/**
 * Covers an acquiring guard reached through a full mcp(proxy) to mcp(client)
 * pipeline, rather than at an mcp(client) on its own. McpClientIT already
 * exercises a deferring guard directly at the client; the pipeline adds the
 * proxy's own request handling in front of it, which is where a deferred
 * credential has repeatedly failed to survive while the direct client ITs
 * stayed green.
 */
public class McpProxyClientIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mcp/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mcp/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/mcp/config")
        .configure(MCP_CLIENT_NAME_NAME, "test")
        .configure(MCP_CLIENT_VERSION_NAME, "1.0")
        .configure(MCP_SESSION_ID_NAME, "%s::sessionId".formatted(McpProxyClientIT.class.getName()))
        .configure(MCP_ELICITATION_ID_NAME, "%s::elicitationId".formatted(McpProxyClientIT.class.getName()))
        .configure(MCP_ELICIT_CORRELATION_ID_NAME, "%s::elicitCorrelationId".formatted(McpProxyClientIT.class.getName()))
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.client.guarded.deferred.yaml")
    @Specification({
        "${app}/tools.call/client",
        "${net}/tools.call.acquired/server"})
    public void shouldCallToolThroughProxyWithDeferredlyAcquiredCredential() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.client.guarded.deferred.eager.yaml")
    @Specification({
        "${app}/tools.call/client",
        "${net}/tools.call.acquired/server"})
    public void shouldCallColdToolThroughProxyWithDeferredlyAcquiredCredential() throws Exception
    {
        k3po.finish();
    }

    /**
     * zilla__execute_tool reconstructs the target tool's request and drives a delegate
     * with synthetic frames, so BEGIN, DATA and END can all be delivered within one
     * reactor turn -- ahead of an acquiring guard's completion, which queues behind
     * them. #2323 fixed that ordering by deferring the synthetic END to the caller's
     * own; this pins it, since the example regressed on exactly this path.
     */
    @Test
    @Configuration("proxy.client.guarded.deferred.search.yaml")
    @Specification({
        "${app}/cache.serve.execute.tool/client",
        "${net}/tools.call.acquired/server"})
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "tools")
    @Configure(name = MCP_SESSION_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mcp.internal.stream.McpProxyClientIT::agentSessionId")
    public void shouldExecuteToolThroughProxyWithDeferredlyAcquiredCredential() throws Exception
    {
        k3po.finish();
    }

    public static String sessionId()
    {
        return "session-1";
    }

    public static String agentSessionId()
    {
        return "agent-1";
    }

    public static String elicitationId()
    {
        return "elicit-1";
    }

    public static String elicitCorrelationId()
    {
        return "3";
    }
}
