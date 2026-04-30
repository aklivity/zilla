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
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_CLIENT_NAME_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_CLIENT_VERSION_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_INACTIVITY_TIMEOUT_NAME;
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

public class McpClientIT
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
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/lifecycle.initialize/client",
        "${net}/lifecycle.initialize/server"})
    public void shouldInitializeLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/lifecycle.shutdown/client",
        "${net}/lifecycle.shutdown/server"})
    public void shouldShutdownLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/lifecycle.shutdown.requests/client",
        "${net}/lifecycle.shutdown.requests/server"})
    @Configure(name = ENGINE_SYNTHETIC_ABORT_NAME, value = "false")
    @Configure(name = ENGINE_DETACH_ON_CLOSE_NAME, value = "false")
    public void shouldShutdownLifecycleRequests() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/lifecycle.ping/client",
        "${net}/lifecycle.ping/server"})
    @Configure(name = MCP_INACTIVITY_TIMEOUT_NAME, value = "PT0.2S")
    public void shouldPingLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/lifecycle.timeout.rejected/client",
        "${net}/lifecycle.timeout.rejected/server"})
    @Configure(name = MCP_INACTIVITY_TIMEOUT_NAME, value = "PT0.2S")
    public void shouldTimeoutLifecycleRejected() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/tools.call/client",
        "${net}/tools.call/server"})
    public void shouldCallTool() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/tools.call.10k/client",
        "${net}/tools.call.10k/server"})
    public void shouldCallToolWith10kParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/tools.call.100k/client",
        "${net}/tools.call.100k/server"})
    public void shouldCallToolWith100kParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/tools.list/client",
        "${net}/tools.list/server"})
    public void shouldListTools() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/tools.list.aborted/client",
        "${net}/tools.list.aborted/server"})
    public void shouldAbortToolsList() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/tools.call.aborted/client",
        "${net}/tools.call.aborted/server"})
    public void shouldAbortCallTool() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/prompts.list.aborted/client",
        "${net}/prompts.list.aborted/server"})
    public void shouldAbortListPrompts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/prompts.get.aborted/client",
        "${net}/prompts.get.aborted/server"})
    public void shouldAbortGetPrompt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/resources.list.aborted/client",
        "${net}/resources.list.aborted/server"})
    public void shouldAbortListResources() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/resources.read.aborted/client",
        "${net}/resources.read.aborted/server"})
    public void shouldAbortReadResource() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/lifecycle.initialize.aborted/client",
        "${net}/lifecycle.initialize.aborted/server"})
    public void shouldAbortInitializeLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/tools.list.canceled/client",
        "${net}/tools.list.canceled/server"})
    @Configure(name = ENGINE_SYNTHETIC_ABORT_NAME, value = "false")
    @Configure(name = ENGINE_DETACH_ON_CLOSE_NAME, value = "false")
    public void shouldListToolsThenCancel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/prompts.list/client",
        "${net}/prompts.list/server"})
    public void shouldListPrompts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/resources.list/client",
        "${net}/resources.list/server"})
    public void shouldListResources() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/prompts.get/client",
        "${net}/prompts.get/server"})
    public void shouldGetPrompt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/resources.read/client",
        "${net}/resources.read/server"})
    public void shouldReadResource() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/resources.read.10k/client",
        "${net}/resources.read.10k/server"})
    public void shouldReadResourceWith10kContents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/resources.read.100k/client",
        "${net}/resources.read.100k/server"})
    public void shouldReadResourceWith100kContents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/lifecycle.events.open/client",
        "${net}/lifecycle.events.open/server"})
    public void shouldOpenLifecycleEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/lifecycle.notify.tools.list.changed/client",
        "${net}/lifecycle.notify.tools.list.changed/server"})
    public void shouldNotifyToolsListChanged() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/lifecycle.notify.prompts.list.changed/client",
        "${net}/lifecycle.notify.prompts.list.changed/server"})
    public void shouldNotifyPromptsListChanged() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/lifecycle.notify.resources.list.changed/client",
        "${net}/lifecycle.notify.resources.list.changed/server"})
    public void shouldNotifyResourcesListChanged() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/lifecycle.suspend.events/client",
        "${net}/lifecycle.suspend.events/server"})
    public void shouldLifecycleSuspendEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/lifecycle.suspended.events/client",
        "${net}/lifecycle.suspended.events/server"})
    public void shouldLifecycleSuspendedEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/tools.call.with.progress/client",
        "${net}/tools.call.with.progress/server"})
    public void shouldCallToolWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/tools.call.with.progress.suspend/client",
        "${net}/tools.call.with.progress.suspend/server"})
    public void shouldCallToolWithProgressSuspend() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/tools.call.with.progress.suspended/client",
        "${net}/tools.call.with.progress.suspended/server"})
    public void shouldCallToolWithProgressSuspended() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/tools.call.10k.with.progress/client",
        "${net}/tools.call.10k.with.progress/server"})
    public void shouldCallToolWith10kParamsWithProgress() throws Exception
    {
        k3po.finish();
    }
}
