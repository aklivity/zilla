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

import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_HYDRATE_ATTEMPTS_MAX_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_HYDRATE_FILTER_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_SESSION_ID_NAME;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_BUFFER_SLOT_CAPACITY_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
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
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class McpProxyCacheIT
{
    private static final String ENGINE_WORKERS_NAME = "zilla.engine.workers";

    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mcp/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/mcp/config")
        .external("app1")
        .external("app2")
        .configure(MCP_SESSION_ID_NAME, "%s::sessionId".formatted(McpProxyCacheIT.class.getName()))
        .aroundStart(k3po::deferStartable)
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.cache.yaml")
    @Specification({
        "${app}/cache.hydrate/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldHydrate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.yaml")
    @Specification({
        "${app}/cache.hydrate.error/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldHydrateError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.credentials.yaml")
    @Specification({
        "${app}/cache.hydrate.credentials/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldHydrateWithCredentials() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.toolkit.yaml")
    @Specification({
        "${app}/cache.hydrate.toolkit/server" })
    public void shouldHydrateToolkit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.credentials.toolkit.yaml")
    @Specification({
        "${app}/cache.hydrate.credentials.toolkit/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "tools")
    public void shouldHydrateToolkitWithRouteCredentials() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.toolkit.multi.yaml")
    @Specification({
        "${app}/cache.hydrate.toolkit.multi.skip.unauthorized/server",
        "${app}/cache.hydrate.toolkit.multi.skip.unauthorized/client" })
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "tools")
    public void shouldHydrateToolkitMultiSkippingUnauthorizedRoute() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.toolkit.multi.refresh.yaml")
    @Specification({
        "${app}/cache.refresh.toolkit.keep.stale.on.failure/server",
        "${app}/cache.refresh.toolkit.keep.stale.on.failure/client" })
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "tools")
    public void shouldRefreshToolkitKeepingStaleOnFailure() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.seeded.toolkit.multi.yaml")
    @Specification({
        "${app}/lifecycle.elicit.cached/client",
        "${app}/lifecycle.elicit.cached/server" })
    public void shouldCompleteLifecycleElicitWhenCached() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.seeded.yaml")
    @Specification({
        "${app}/cache.serve.initialize/client" })
    public void shouldServeInitialize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.seeded.yaml")
    @Specification({
        "${app}/cache.tools.call.reject.bearer/client",
        "${app}/cache.tools.call.reject.bearer/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRejectToolsCallWithBearerChallenge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.validation.yaml")
    @Specification({
        "${app}/cache.tools.call.valid.input/client",
        "${app}/cache.tools.call.valid.input/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldProxyToolsCallWithValidInput() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.validation.yaml")
    @Specification({
        "${app}/cache.tools.call.invalid.input/client",
        "${app}/cache.tools.call.invalid.input/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRejectToolsCallWithInvalidInput() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.validation.yaml")
    @Specification({
        "${app}/cache.tools.call.no.schema/client",
        "${app}/cache.tools.call.no.schema/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldForwardToolsCallWithoutSchema() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.yaml")
    @Specification({
        "${app}/cache.hydrate.lifecycle.reconnect/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldReconnectAfterLifecycleAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.seeded.yaml")
    @Specification({
        "${app}/cache.serve.tools.list/client" })
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "tools")
    public void shouldServeToolsList() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.tools.search.serve.yaml")
    @Specification({
        "${app}/cache.serve.tools.search/client" })
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "tools")
    public void shouldServeToolsSearch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.toolkit.filter.yaml")
    @Specification({
        "${app}/cache.serve.tools.list.toolkit.filtered/server",
        "${app}/cache.serve.tools.list.toolkit.filtered/client" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "tools")
    public void shouldServeToolsListFilteredByAllowSet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.toolkit.guarded.yaml")
    @Specification({
        "${app}/cache.serve.tools.list.security.schemes.filtered/server",
        "${app}/cache.serve.tools.list.security.schemes.filtered/client" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "tools")
    public void shouldServeToolsListFilteredBySecuritySchemes() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.yaml")
    @Specification({
        "${app}/cache.serve.tools.list.degraded/server",
        "${app}/cache.serve.tools.list.degraded/client" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "tools")
    @Configure(name = MCP_HYDRATE_ATTEMPTS_MAX_NAME, value = "1")
    public void shouldServeToolsListWhenDegraded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.refresh.yaml")
    @Specification({
        "${app}/cache.refresh.tools/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "tools")
    public void shouldRefreshTools() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.refresh.yaml")
    @Specification({
        "${app}/cache.refresh.tools.notify/client",
        "${app}/cache.refresh.tools/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "tools")
    public void shouldNotifyToolsListChangedAfterRefresh() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.yaml")
    @Specification({
        "${app}/cache.notify.tools.list.changed/client",
        "${app}/cache.notify.tools.list.changed/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "tools")
    public void shouldRefreshToolsOnListChangedNotification() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.refresh.yaml")
    @Specification({
        "${app}/cache.notify.tools.list.changed.during.refresh/client",
        "${app}/cache.notify.tools.list.changed.during.refresh/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "tools")
    public void shouldNotifyToolsListChangedDuringRefresh() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.yaml")
    @Specification({
        "${app}/cache.notify.tools.list.changed.after.tools.call/client",
        "${app}/cache.notify.tools.list.changed.after.tools.call/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "tools")
    public void shouldEmitOneListChangedAfterAgentInvokesToolsCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.refresh.yaml")
    @Specification({
        "${app}/cache.refresh.tools.error/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "tools")
    public void shouldRefreshToolsError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.refresh.yaml")
    @Specification({
        "${app}/cache.refresh.tools.error.retry/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "tools")
    public void shouldRetryAfterToolsRefreshError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.yaml")
    @Specification({
        "${app}/cache.serve.tools.list.during.hydrate/server",
        "${app}/cache.serve.tools.list.during.hydrate/client" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "tools")
    public void shouldServeToolsListDuringHydrate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.refresh.yaml")
    @Specification({
        "${app}/cache.refresh.tools.contended/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = ENGINE_WORKERS_NAME, value = "2")
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "tools")
    @Configure(name = MCP_SESSION_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mcp.internal.stream.McpProxyCacheIT::contendedSessionId")
    public void shouldRefreshToolsContended() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.seeded.yaml")
    @Specification({
        "${app}/cache.serve.resources.list/client" })
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "resources")
    public void shouldServeResourcesList() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.refresh.yaml")
    @Specification({
        "${app}/cache.refresh.resources/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "resources")
    public void shouldRefreshResources() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.seeded.yaml")
    @Specification({
        "${app}/cache.serve.prompts.list/client" })
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "prompts")
    public void shouldServePromptsList() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.refresh.yaml")
    @Specification({
        "${app}/cache.refresh.prompts/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "prompts")
    public void shouldRefreshPrompts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.yaml")
    @Specification({
        "${app}/cache.hydrate.10k/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = ENGINE_BUFFER_SLOT_CAPACITY_NAME, value = "8192")
    public void shouldHydrate10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.yaml")
    @Specification({
        "${app}/cache.hydrate.100k/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = ENGINE_BUFFER_SLOT_CAPACITY_NAME, value = "8192")
    public void shouldHydrate100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.seeded.tools.10k.yaml")
    @Specification({
        "${app}/cache.serve.tools.list.10k/client" })
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "tools")
    @Configure(name = ENGINE_BUFFER_SLOT_CAPACITY_NAME, value = "8192")
    public void shouldServeToolsList10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.seeded.tools.100k.yaml")
    @Specification({
        "${app}/cache.serve.tools.list.100k/client" })
    @Configure(name = MCP_HYDRATE_FILTER_NAME, value = "tools")
    @Configure(name = ENGINE_BUFFER_SLOT_CAPACITY_NAME, value = "8192")
    public void shouldServeToolsList100k() throws Exception
    {
        k3po.finish();
    }

    private static final List<String> SESSION_IDS = List.of("hydrate-1", "agent-1");
    private static Iterator<String> sessionIds = SESSION_IDS.iterator();

    public static String sessionId()
    {
        return sessionIds.next();
    }

    @After
    public void resetSessionIds()
    {
        sessionIds = SESSION_IDS.iterator();
    }

    private static final String[] CONTENDED_SESSION_IDS = { "hydrate-A", "hydrate-B" };
    private static final AtomicInteger CONTENDED_SESSION_INDEX = new AtomicInteger();

    public static String contendedSessionId()
    {
        return CONTENDED_SESSION_IDS[CONTENDED_SESSION_INDEX.getAndIncrement() % CONTENDED_SESSION_IDS.length];
    }
}
