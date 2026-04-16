/*
 * Copyright 2021-2024 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.mcp.internal.stream;

import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_SERVER_NAME_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_SERVER_VERSION_NAME;
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
