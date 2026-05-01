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
package io.aklivity.zilla.runtime.binding.mcp.internal.stream;

import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.ENGINE_DETACH_ON_CLOSE_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.ENGINE_SYNTHETIC_ABORT_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_INACTIVITY_TIMEOUT_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_SERVER_NAME_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_SERVER_VERSION_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_SESSION_ID_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_SSE_KEEPALIVE_INTERVAL_NAME;
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

public class McpServerIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mcp/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mcp/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/mcp/config")
        .configure(MCP_SESSION_ID_NAME, "%s::sessionId".formatted(McpServerIT.class.getName()))
        .configure(MCP_SERVER_NAME_NAME, "zilla")
        .configure(MCP_SERVER_VERSION_NAME, "1.0")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.initialize/client",
        "${app}/lifecycle.initialize/server"})
    public void shouldInitializeLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.ping/client"})
    public void shouldPingLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.shutdown/client",
        "${app}/lifecycle.shutdown/server"})
    public void shouldShutdownLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.shutdown.requests/client",
        "${app}/lifecycle.shutdown.requests/server"})
    @Configure(name = ENGINE_SYNTHETIC_ABORT_NAME, value = "false")
    @Configure(name = ENGINE_DETACH_ON_CLOSE_NAME, value = "false")
    public void shouldShutdownLifecycleRequests() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.timeout/client",
        "${app}/lifecycle.timeout/server"})
    @Configure(name = MCP_INACTIVITY_TIMEOUT_NAME, value = "PT1S")
    public void shouldTimeoutLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/reject.request.method.before.id/client",
        "${app}/reject.request.method.before.id/server"})
    public void shouldRejectRequestMethodBeforeId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/reject.request.params.before.method/client",
        "${app}/reject.request.params.before.method/server"})
    public void shouldRejectRequestParamsBeforeMethod() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/reject.request.params.array/client",
        "${app}/reject.request.params.array/server"})
    public void shouldRejectRequestParamsWithArray() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call.10k/client",
        "${app}/tools.call.10k/server"})
    public void shouldCallToolWith10kParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call.100k/client",
        "${app}/tools.call.100k/server"})
    public void shouldCallToolWith100kParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/resources.read.10k/client",
        "${app}/resources.read.10k/server"})
    public void shouldReadResourceWith10kContents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/resources.read.100k/client",
        "${app}/resources.read.100k/server"})
    public void shouldReadResourceWith100kContents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call/client",
        "${app}/tools.call/server"})
    public void shouldCallTool() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.list/client",
        "${app}/tools.list/server"})
    public void shouldListTools() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.list.aborted/client",
        "${app}/tools.list.aborted/server"})
    public void shouldAbortToolsList() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call.aborted/client",
        "${app}/tools.call.aborted/server"})
    public void shouldAbortCallTool() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/prompts.list.aborted/client",
        "${app}/prompts.list.aborted/server"})
    public void shouldAbortListPrompts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/prompts.get.aborted/client",
        "${app}/prompts.get.aborted/server"})
    public void shouldAbortGetPrompt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/resources.list.aborted/client",
        "${app}/resources.list.aborted/server"})
    public void shouldAbortListResources() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/resources.read.aborted/client",
        "${app}/resources.read.aborted/server"})
    public void shouldAbortReadResource() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.list.canceled/client",
        "${app}/tools.list.canceled/server"})
    public void shouldListToolsThenCancel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/prompts.list/client",
        "${app}/prompts.list/server"})
    public void shouldListPrompts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/resources.list/client",
        "${app}/resources.list/server"})
    public void shouldListResources() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/prompts.get/client",
        "${app}/prompts.get/server"})
    public void shouldGetPrompt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/resources.read/client",
        "${app}/resources.read/server"})
    public void shouldReadResource() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.events.open/client",
        "${app}/lifecycle.events.open/server"})
    @Configure(name = MCP_SSE_KEEPALIVE_INTERVAL_NAME, value = "PT30S")
    public void shouldOpenLifecycleEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.events.session.unknown/client"})
    public void shouldRejectLifecycleEventsSessionUnknown() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.events.session.missing/client"})
    public void shouldRejectLifecycleEventsSessionMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/reject.method.not.allowed/client"})
    public void shouldRejectMethodNotAllowed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/reject.accept.unsupported/client"})
    public void shouldRejectAcceptUnsupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.events.keepalive/client",
        "${app}/lifecycle.events.keepalive/server"})
    @Configure(name = MCP_SSE_KEEPALIVE_INTERVAL_NAME, value = "PT0.5S")
    public void shouldKeepaliveLifecycleEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.events.evict/client",
        "${app}/lifecycle.events.evict/server"})
    @Configure(name = MCP_SSE_KEEPALIVE_INTERVAL_NAME, value = "PT30S")
    public void shouldEvictLifecycleEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.shutdown.events/client",
        "${app}/lifecycle.shutdown.events/server"})
    @Configure(name = MCP_SSE_KEEPALIVE_INTERVAL_NAME, value = "PT30S")
    public void shouldLifecycleShutdownEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.timeout.events/client",
        "${app}/lifecycle.timeout.events/server"})
    @Configure(name = MCP_INACTIVITY_TIMEOUT_NAME, value = "PT1S")
    @Configure(name = MCP_SSE_KEEPALIVE_INTERVAL_NAME, value = "PT30S")
    public void shouldLifecycleTimeoutEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.notify.tools.list.changed/client",
        "${app}/lifecycle.notify.tools.list.changed/server"})
    @Configure(name = MCP_SSE_KEEPALIVE_INTERVAL_NAME, value = "PT30S")
    public void shouldNotifyToolsListChanged() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.notify.prompts.list.changed/client",
        "${app}/lifecycle.notify.prompts.list.changed/server"})
    @Configure(name = MCP_SSE_KEEPALIVE_INTERVAL_NAME, value = "PT30S")
    public void shouldNotifyPromptsListChanged() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.notify.resources.list.changed/client",
        "${app}/lifecycle.notify.resources.list.changed/server"})
    @Configure(name = MCP_SSE_KEEPALIVE_INTERVAL_NAME, value = "PT30S")
    public void shouldNotifyResourcesListChanged() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call.with.progress/client",
        "${app}/tools.call.with.progress/server"})
    public void shouldCallToolWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call.with.progress.suspend/client",
        "${app}/tools.call.with.progress.suspend/server"})
    public void shouldCallToolWithProgressSuspend() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call.with.progress.suspended/client",
        "${app}/tools.call.with.progress.suspended/server"})
    public void shouldCallToolWithProgressSuspended() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call.with.progress.resume/client",
        "${app}/tools.call.with.progress.resume/server"})
    public void shouldCallToolWithProgressResume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.suspend.events/client",
        "${app}/lifecycle.suspend.events/server"})
    @Configure(name = MCP_SSE_KEEPALIVE_INTERVAL_NAME, value = "PT30S")
    public void shouldLifecycleSuspendEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.suspended.events/client",
        "${app}/lifecycle.suspended.events/server"})
    @Configure(name = MCP_SSE_KEEPALIVE_INTERVAL_NAME, value = "PT30S")
    public void shouldLifecycleSuspendedEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call.10k.with.progress/client",
        "${app}/tools.call.10k.with.progress/server"})
    public void shouldCallToolWith10kParamsWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call.100k.with.progress/client",
        "${app}/tools.call.100k.with.progress/server"})
    public void shouldCallToolWith100kParamsWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/resources.read.10k.with.progress/client",
        "${app}/resources.read.10k.with.progress/server"})
    public void shouldReadResourceWith10kContentWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/resources.read.100k.with.progress/client",
        "${app}/resources.read.100k.with.progress/server"})
    public void shouldReadResourceWith100kContentWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/prompts.get.10k.with.progress/client",
        "${app}/prompts.get.10k.with.progress/server"})
    public void shouldGetPromptWith10kMessageWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/prompts.get.100k.with.progress/client",
        "${app}/prompts.get.100k.with.progress/server"})
    public void shouldGetPromptWith100kMessageWithProgress() throws Exception
    {
        k3po.finish();
    }

    public static String sessionId()
    {
        return "session-1";
    }
}
