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
package io.aklivity.zilla.runtime.binding.a2a.internal.stream;

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
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class A2aProxyCacheIT
{
    private static final String ENGINE_WORKERS_NAME = "zilla.engine.workers";

    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/a2a/streams/application")
        .addScriptRoot("cache", "io/aklivity/zilla/specs/binding/a2a/streams/cache");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/a2a/config")
        .external("app1")
        .external("app2")
        .aroundStart(k3po::deferStartable)
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.cache.yaml")
    @Specification({
        "${app}/agent.card.slug/client",
        "${cache}/cache.hydrate/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldServeCardFromCache() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.yaml")
    @Specification({
        "${cache}/cache.hydrate/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldHydrate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.multi.yaml")
    @Specification({
        "${cache}/cache.hydrate.multi/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldHydrateMulti() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.credentials.yaml")
    @Specification({
        "${cache}/cache.hydrate.credentials/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldHydrateWithCredentials() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.yaml")
    @Specification({
        "${cache}/cache.hydrate.error/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldHydrateError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.refresh.yaml")
    @Specification({
        "${cache}/cache.serve.card.expired/client",
        "${cache}/cache.hydrate.stale/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRefreshOnExpiry() throws Exception
    {
        k3po.start();
        Thread.sleep(1000);
        k3po.notifyBarrier("EXPIRED");
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.refresh.yaml")
    @Specification({
        "${cache}/cache.serve.card.refresh.error/client",
        "${cache}/cache.hydrate.refresh.error/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldKeepLastGoodCardOnFailedRefresh() throws Exception
    {
        k3po.start();
        Thread.sleep(1000);
        k3po.notifyBarrier("EXPIRED");
        k3po.awaitBarrier("SERVED");
        Thread.sleep(1000);
        k3po.notifyBarrier("EXPIRED_AGAIN");
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.refresh.yaml")
    @Specification({
        "${cache}/cache.serve.card.retry/client",
        "${cache}/cache.hydrate.error.retry/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRetryHydrateOnNextCycle() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("FAILED");
        Thread.sleep(1000);
        k3po.notifyBarrier("EXPIRED");
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.guarded.yaml")
    @Specification({
        "${app}/agent.card.skills/client",
        "${cache}/cache.hydrate.guarded/server"})
    @ScriptProperty({"serverAddress \"zilla://streams/app1\"", "authorization 1L"})
    public void shouldServeCardWithPermittedSkills() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.guarded.yaml")
    @Specification({
        "${app}/agent.card.skills.filtered/client",
        "${cache}/cache.hydrate.guarded/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldServeCardWithoutRestrictedSkills() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.restricted.yaml")
    @Specification({
        "${app}/agent.card.skills.restricted/client",
        "${cache}/cache.hydrate.restricted/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRejectCardWithAllSkillsRestricted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.credentials.yaml")
    @Specification({
        "${app}/agent.card.extended.twice/client",
        "${app}/agent.card.extended.twice/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldForwardExtendedCardWithoutCaching() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.yaml")
    @Specification({
        "${app}/agent.card.slug/client",
        "${cache}/cache.hydrate/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = ENGINE_WORKERS_NAME, value = "2")
    public void shouldShareCardAcrossWorkers() throws Exception
    {
        k3po.finish();
    }
}
