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

public class McpProxyIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mcp/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/mcp/config")
        .external("app1")
        .external("app2")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/lifecycle.initialize/client" })
    public void shouldInitializeLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/lifecycle.shutdown/client" })
    public void shouldShutdownLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/lifecycle.shutdown.requests/client",
        "${app}/lifecycle.shutdown.requests/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldShutdownLifecycleRequests() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/tools.call/client",
        "${app}/tools.call/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldCallTool() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/tools.call.aborted/client",
        "${app}/tools.call.aborted/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldAbortCallTool() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.yaml")
    @Specification({
        "${app}/tools.call.toolkit/client",
        "${app}/tools.call.toolkit/server" })
    public void shouldCallToolWithToolkit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/tools.list/client",
        "${app}/tools.list/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldListTools() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.yaml")
    @Specification({
        "${app}/tools.list.toolkit/client",
        "${app}/tools.list.toolkit/server" })
    public void shouldListToolsWithToolkit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.multi.yaml")
    @Specification({
        "${app}/tools.list.toolkit.multi/client",
        "${app}/tools.list.toolkit.multi/server" })
    public void shouldListToolsWithToolkitMulti() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/prompts.get/client",
        "${app}/prompts.get/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldGetPrompt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.yaml")
    @Specification({
        "${app}/prompts.get.toolkit/client",
        "${app}/prompts.get.toolkit/server" })
    public void shouldGetPromptWithToolkit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/prompts.list/client",
        "${app}/prompts.list/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldListPrompts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.yaml")
    @Specification({
        "${app}/prompts.list.toolkit/client",
        "${app}/prompts.list.toolkit/server" })
    public void shouldListPromptsWithToolkit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.multi.yaml")
    @Specification({
        "${app}/prompts.list.toolkit.multi/client",
        "${app}/prompts.list.toolkit.multi/server" })
    public void shouldListPromptsWithToolkitMulti() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/resources.read/client",
        "${app}/resources.read/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldReadResource() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.yaml")
    @Specification({
        "${app}/resources.read.toolkit/client",
        "${app}/resources.read.toolkit/server" })
    public void shouldReadResourceWithToolkit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/resources.list/client",
        "${app}/resources.list/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldListResources() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.yaml")
    @Specification({
        "${app}/resources.list.toolkit/client",
        "${app}/resources.list.toolkit/server" })
    public void shouldListResourcesWithToolkit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.multi.yaml")
    @Specification({
        "${app}/resources.list.toolkit.multi/client",
        "${app}/resources.list.toolkit.multi/server" })
    public void shouldListResourcesWithToolkitMulti() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/tools.list.aborted/client",
        "${app}/tools.list.aborted/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldAbortToolsList() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/tools.list.canceled/client",
        "${app}/tools.list.canceled/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldListToolsThenCancel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/prompts.list.aborted/client",
        "${app}/prompts.list.aborted/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldAbortListPrompts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/prompts.get.aborted/client",
        "${app}/prompts.get.aborted/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldAbortGetPrompt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/resources.list.aborted/client",
        "${app}/resources.list.aborted/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldAbortListResources() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/resources.read.aborted/client",
        "${app}/resources.read.aborted/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldAbortReadResource() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/tools.call.10k/client",
        "${app}/tools.call.10k/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldCallToolWith10kParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/tools.call.100k/client",
        "${app}/tools.call.100k/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldCallToolWith100kParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/resources.read.10k/client",
        "${app}/resources.read.10k/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldReadResourceWith10kContents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/resources.read.100k/client",
        "${app}/resources.read.100k/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldReadResourceWith100kContents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/tools.call.with.progress/client",
        "${app}/tools.call.with.progress/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldCallToolWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.yaml")
    @Specification({
        "${app}/tools.call.toolkit.with.progress/client",
        "${app}/tools.call.toolkit.with.progress/server" })
    public void shouldCallToolWithToolkitWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/tools.call.10k.with.progress/client",
        "${app}/tools.call.10k.with.progress/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldCallToolWith10kParamsWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/tools.call.100k.with.progress/client",
        "${app}/tools.call.100k.with.progress/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldCallToolWith100kParamsWithProgress() throws Exception
    {
        k3po.finish();
    }
}
