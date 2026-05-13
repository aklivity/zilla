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

import org.junit.Ignore;
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

public class McpCacheIT
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

    // ---- Group A. Warmup lifecycle (proactive bring-up) ----

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/cache.warmup.session.initialize/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldOpenWarmupSessionAndInitialize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/cache.warmup.session.tools.list/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldPopulateToolsViaWarmup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/cache.warmup.session.resources.list/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldPopulateResourcesViaWarmup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/cache.warmup.session.prompts.list/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldPopulatePromptsViaWarmup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/cache.warmup.session.persists/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldKeepWarmupSessionOpenAfterEnumeration() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.guarded.yaml")
    @Specification({
        "${app}/cache.warmup.session.uses.test.guard.credentials/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldIssueWarmupWithTestGuardCredentials() throws Exception
    {
        k3po.finish();
    }

    // ---- Group B. Agent-side served from cache (zero downstream RTT) ----

    @Ignore("TODO: implement script")
    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/cache.agent.initialize.from.cache/client",
        "${app}/cache.agent.initialize.from.cache/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldServeAgentInitializeFromCache() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: implement script")
    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/cache.agent.tools.list.from.cache/client",
        "${app}/cache.agent.tools.list.from.cache/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldServeAgentToolsListFromCache() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: implement script")
    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/cache.agent.resources.list.from.cache/client",
        "${app}/cache.agent.resources.list.from.cache/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldServeAgentResourcesListFromCache() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: implement script")
    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/cache.agent.prompts.list.from.cache/client",
        "${app}/cache.agent.prompts.list.from.cache/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldServeAgentPromptsListFromCache() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: implement script")
    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/cache.agent.list.before.warmup.completes/client",
        "${app}/cache.agent.list.before.warmup.completes/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldBlockAgentListUntilWarmupCompletes() throws Exception
    {
        k3po.finish();
    }

    // ---- Group C. Invocation pass-through ----

    @Ignore("TODO: implement script")
    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/cache.agent.tools.call.passes.through/client",
        "${app}/cache.agent.tools.call.passes.through/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldPassThroughToolsCall() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: implement script")
    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/cache.agent.resources.read.passes.through/client",
        "${app}/cache.agent.resources.read.passes.through/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldPassThroughResourcesRead() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: implement script")
    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/cache.agent.prompts.get.passes.through/client",
        "${app}/cache.agent.prompts.get.passes.through/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldPassThroughPromptsGet() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: implement script")
    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/cache.agent.tools.call.with.progress.passes.through/client",
        "${app}/cache.agent.tools.call.with.progress.passes.through/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldPassThroughToolsCallWithProgress() throws Exception
    {
        k3po.finish();
    }

    // ---- Group D. Fanout / aggregate-catalog correctness ----

    @Ignore("TODO: implement script")
    @Test
    @Configuration("cache.multi.yaml")
    @Specification({
        "${app}/cache.warmup.aggregates.two.exits/client",
        "${app}/cache.warmup.aggregates.two.exits/server" })
    public void shouldAggregateCatalogAcrossTwoExits() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: implement script")
    @Test
    @Configuration("cache.multi.yaml")
    @Specification({
        "${app}/cache.warmup.exit.error.partial.aggregate/client",
        "${app}/cache.warmup.exit.error.partial.aggregate/server" })
    public void shouldTolerateExitErrorDuringWarmup() throws Exception
    {
        k3po.finish();
    }

    // ---- Group F. Session lifecycle isolation ----

    @Ignore("TODO: implement script")
    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/cache.agent.session.ends.warmup.persists/client",
        "${app}/cache.agent.session.ends.warmup.persists/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldKeepWarmupAfterAgentEnds() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: implement script")
    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/cache.binding.shutdown.closes.both/client",
        "${app}/cache.binding.shutdown.closes.both/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldCloseWarmupAndAgentOnShutdown() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: implement script")
    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/cache.agent.reconnect.shares.cache/client",
        "${app}/cache.agent.reconnect.shares.cache/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldShareCacheAcrossAgentReconnects() throws Exception
    {
        k3po.finish();
    }

    // ---- Group G. Store ----

    @Ignore("TODO: implement script")
    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/cache.persisted.in.store/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldPersistCatalogInStore() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: implement script")
    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/cache.entries.scoped.per.method/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldScopeStoreEntriesPerMethodType() throws Exception
    {
        k3po.finish();
    }

    // ---- Group I. Periodic refresh (per method type) ----

    @Ignore("TODO: implement script")
    @Test
    @Configuration("cache.refresh.yaml")
    @Specification({
        "${app}/cache.refresh.tools.only/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRefreshToolsOnly() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: implement script")
    @Test
    @Configuration("cache.refresh.yaml")
    @Specification({
        "${app}/cache.refresh.resources.only/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRefreshResourcesOnly() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: implement script")
    @Test
    @Configuration("cache.refresh.yaml")
    @Specification({
        "${app}/cache.refresh.prompts.only/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRefreshPromptsOnly() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: implement script")
    @Test
    @Configuration("cache.refresh.yaml")
    @Specification({
        "${app}/cache.refresh.downstream.error.keeps.stale/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldKeepStaleCacheWhenRefreshFails() throws Exception
    {
        k3po.finish();
    }
}
