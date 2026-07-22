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

public class McpProxyIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mcp/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/mcp/config")
        .configure(MCP_SESSION_ID_NAME, "%s::sessionId".formatted(McpProxyIT.class.getName()))
        .external("app1")
        .external("app2")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/lifecycle.initialize/client",
        "${app}/lifecycle.initialize/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldInitializeLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/lifecycle.shutdown/client",
        "${app}/lifecycle.shutdown/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
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
        "${app}/tools.call.is.error/client",
        "${app}/tools.call.is.error/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldCallToolIsError() throws Exception
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
        "${app}/tools.call.toolkit.prefixed/client",
        "${app}/tools.call.toolkit/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldCallToolWithToolkit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.yaml")
    @Specification({
        "${app}/tools.call.toolkit.prefixed.fragmented/client",
        "${app}/tools.call.toolkit/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldCallToolWithToolkitFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.filter.yaml")
    @Specification({
        "${app}/tools.call.toolkit.reject.unauthorized/client",
        "${app}/tools.call.toolkit.reject.unauthorized/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRejectUnauthorizedToolCallWithToolkit() throws Exception
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
        "${app}/tools.list.toolkit.prefixed/client",
        "${app}/tools.list.toolkit/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldListToolsWithToolkit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.filter.yaml")
    @Specification({
        "${app}/tools.list.toolkit.filtered/client",
        "${app}/tools.list.toolkit.filtered/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldListToolsFilteredByAllowSet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.multi.yaml")
    @Specification({
        "${app}/tools.list.toolkit.multi.prefixed/client",
        "${app}/tools.list.toolkit.multi/server" })
    public void shouldListToolsWithToolkitMulti() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.multi.yaml")
    @Specification({
        "${app}/lifecycle.initialize.skip.bearer.toolkit.multi/client",
        "${app}/lifecycle.initialize.skip.bearer.toolkit.multi/server" })
    public void shouldInitializeLifecyclePartialSkippingBearerRejectedToolkitMulti() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.multi.yaml")
    @Specification({
        "${app}/lifecycle.notify.tools.list.changed.toolkit.multi.prefixed/client",
        "${app}/lifecycle.notify.tools.list.changed.toolkit.multi/server" })
    public void shouldNotifyToolsListChangedWithAggregateEventId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.multi.yaml")
    @Specification({
        "${app}/lifecycle.initialize.partial.toolkit.multi/client",
        "${app}/lifecycle.initialize.partial.toolkit.multi/server" })
    public void shouldInitializeLifecyclePartialToolkitMulti() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.multi.yaml")
    @Specification({
        "${app}/lifecycle.elicit.toolkit.multi/client",
        "${app}/lifecycle.elicit.toolkit.multi/server" })
    public void shouldRouteElicitCallbackToToolkit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.multi.yaml")
    @Specification({
        "${app}/tools.list.partial.toolkit.multi.prefixed/client",
        "${app}/tools.list.partial.toolkit.multi/server" })
    public void shouldListToolsWithPartialToolkitMulti() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.multi.yaml")
    @Specification({
        "${app}/lifecycle.notify.tools.list.changed.after.authorize.toolkit.multi.prefixed/client",
        "${app}/lifecycle.notify.tools.list.changed.after.authorize.toolkit.multi/server" })
    public void shouldNotifyToolsListChangedAfterAuthorizeToolkitMulti() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.multi.yaml")
    @Specification({
        "${app}/lifecycle.events.resume.aggregate.prefixed/client",
        "${app}/lifecycle.events.resume.aggregate/server" })
    public void shouldResumeLifecycleEventsAggregate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.multi.yaml")
    @Specification({
        "${app}/lifecycle.events.resume.partial.prefixed/client",
        "${app}/lifecycle.events.resume.partial.prefixed/server" })
    public void shouldResumeLifecycleEventsPartial() throws Exception
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
        "${app}/prompts.get.toolkit.prefixed/client",
        "${app}/prompts.get.toolkit/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
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
        "${app}/prompts.list.toolkit.prefixed/client",
        "${app}/prompts.list.toolkit/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldListPromptsWithToolkit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.multi.yaml")
    @Specification({
        "${app}/prompts.list.toolkit.multi.prefixed/client",
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
        "${app}/resources.read.toolkit.prefixed/client",
        "${app}/resources.read.toolkit/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
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
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/resources.templates.list/client",
        "${app}/resources.templates.list/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldListResourcesTemplates() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.yaml")
    @Specification({
        "${app}/resources.list.toolkit.prefixed/client",
        "${app}/resources.list.toolkit/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldListResourcesWithToolkit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.toolkit.multi.yaml")
    @Specification({
        "${app}/resources.list.toolkit.multi.prefixed/client",
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
        "${app}/resources.templates.list.aborted/client",
        "${app}/resources.templates.list.aborted/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldAbortListResourcesTemplates() throws Exception
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
        "${app}/tools.call.toolkit.with.progress.prefixed/client",
        "${app}/tools.call.toolkit.with.progress/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
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

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/resources.read.10k.with.progress/client",
        "${app}/resources.read.10k.with.progress/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldReadResourceWith10kContentWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/resources.read.100k.with.progress/client",
        "${app}/resources.read.100k.with.progress/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldReadResourceWith100kContentWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/prompts.get.10k.with.progress/client",
        "${app}/prompts.get.10k.with.progress/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldGetPromptWith10kMessageWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/prompts.get.100k.with.progress/client",
        "${app}/prompts.get.100k.with.progress/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldGetPromptWith100kMessageWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/tools.call.elicit.completed.proxied/client",
        "${app}/tools.call.elicit.completed.proxied/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldCallToolElicitCompletedProxied() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/lifecycle.elicit.completed/client",
        "${app}/lifecycle.elicit.completed/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldCompleteLifecycleElicit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/tools.call.elicit.declined.proxied/client",
        "${app}/tools.call.elicit.declined.proxied/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldCallToolElicitDeclinedProxied() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/tools.call.elicit.timeout.proxied/client",
        "${app}/tools.call.elicit.timeout.proxied/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldCallToolElicitTimeoutProxied() throws Exception
    {
        k3po.finish();
    }

    public static String sessionId()
    {
        return "session-1";
    }
}
