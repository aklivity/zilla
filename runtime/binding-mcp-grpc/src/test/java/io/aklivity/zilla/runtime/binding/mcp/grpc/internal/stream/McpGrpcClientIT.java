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
package io.aklivity.zilla.runtime.binding.mcp.grpc.internal.stream;

import static io.aklivity.zilla.runtime.binding.mcp.grpc.internal.McpGrpcConfigurationTest.MCP_GRPC_CLIENT_EXIT_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.grpc.internal.McpGrpcConfigurationTest.MCP_GRPC_SESSION_ID_NAME;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
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

public class McpGrpcClientIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("mcp", "io/aklivity/zilla/specs/binding/mcp/grpc/streams/mcp")
        .addScriptRoot("http", "io/aklivity/zilla/specs/binding/mcp/grpc/streams/http");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 131072)
        .configure(MCP_GRPC_SESSION_ID_NAME, "%s::sessionId".formatted(McpGrpcClientIT.class.getName()))
        .configure(MCP_GRPC_CLIENT_EXIT_NAME, "http0")
        .configurationRoot("io/aklivity/zilla/specs/binding/mcp/grpc/config")
        .external("http0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    public static String sessionId(
        long affinity)
    {
        assert affinity == 0L;
        return "5ca1ab1e-c0de-4a11-5e55-000100000000";
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${mcp}/tools.list/client"})
    public void shouldListTools() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.tools.yaml")
    @Specification({
        "${mcp}/tools.list.with.tools/client"})
    public void shouldListToolsWithToolOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.route.method.yaml")
    @Specification({
        "${mcp}/tools.list.with.method.routes/client"})
    public void shouldListToolsWithMethodRoutes() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.route.option.yaml")
    @Specification({
        "${mcp}/tools.list.with.option.route/client"})
    public void shouldListToolsWithOptionRoute() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.summary.yaml")
    @Specification({
        "${mcp}/tools.list.with.summary/client"})
    public void shouldListToolsWithSummary() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${mcp}/tools.call/client",
        "${http}/tools.call/server"})
    public void shouldCallTool() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.route.method.yaml")
    @Specification({
        "${mcp}/tools.call.pinned/client",
        "${http}/tools.call/server"})
    public void shouldCallToolPinnedByRoute() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${mcp}/tools.call.error/client",
        "${http}/tools.call.error/server"})
    public void shouldCallToolWithError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.summary.yaml")
    @Specification({
        "${mcp}/tools.call.with.summary/client",
        "${http}/tools.call/server"})
    public void shouldCallToolWithSummary() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${mcp}/tools.call.10k/client",
        "${http}/tools.call.10k/server"})
    public void shouldCallTool10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${mcp}/tools.call.100k/client",
        "${http}/tools.call.100k/server"})
    public void shouldCallTool100k() throws Exception
    {
        k3po.finish();
    }
}
