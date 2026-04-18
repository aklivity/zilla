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
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Ignore;
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
        "${net}/tools.call.10k/client",
        "${app}/tools.call.10k/server"})
    @Configure(name = "zilla.engine.drain.on.close", value = "false")
    public void shouldCallToolWith10kParams() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: flush params bytes incrementally inside skipObject for message sizes > decode slot")
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call.100k/client",
        "${app}/tools.call.100k/server"})
    @Configure(name = "zilla.engine.drain.on.close", value = "false")
    public void shouldCallToolWith100kParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/resources.read.10k/client",
        "${app}/resources.read.10k/server"})
    @Configure(name = "zilla.engine.drain.on.close", value = "false")
    public void shouldReadResourceWith10kContents() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: chunk response encoding for message sizes > encode slot")
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/resources.read.100k/client",
        "${app}/resources.read.100k/server"})
    @Configure(name = "zilla.engine.drain.on.close", value = "false")
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

    public static String sessionId()
    {
        return "session-1";
    }
}
