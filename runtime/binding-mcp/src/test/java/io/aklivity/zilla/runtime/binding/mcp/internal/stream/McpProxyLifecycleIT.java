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

import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_SESSION_ID_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.ScriptProperty;
import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class McpProxyLifecycleIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mcp/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/mcp/config")
        .configure(MCP_SESSION_ID_NAME, "%s::sessionId".formatted(McpProxyLifecycleIT.class.getName()))
        .external("app1")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/lifecycle.server.write.abort/client",
        "${app}/lifecycle.server.write.abort/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldLifecycleServerWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/lifecycle.server.write.close/client",
        "${app}/lifecycle.server.write.close/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldLifecycleServerWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/lifecycle.server.read.abort/client",
        "${app}/lifecycle.server.read.abort/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldLifecycleServerReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/lifecycle.client.write.abort/client",
        "${app}/lifecycle.client.write.abort/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldLifecycleClientWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/lifecycle.client.write.close/client",
        "${app}/lifecycle.client.write.close/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldLifecycleClientWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/lifecycle.client.read.abort/client",
        "${app}/lifecycle.client.read.abort/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldLifecycleClientReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/lifecycle.initialize.skip.bearer/client",
        "${app}/lifecycle.initialize.skip.bearer/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldInitializeLifecycleSkippingBearerRejectedRoute() throws Exception
    {
        k3po.finish();
    }

    public static String sessionId()
    {
        return "agent-1";
    }
}
