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

public class A2aProxyDiscoveryIT
{
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
    @Configuration("proxy.discovery.cache.yaml")
    @Specification({
        "${app}/discovery.find.name/client",
        "${cache}/cache.hydrate.discovery/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldFindAgentByName() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.discovery.cache.yaml")
    @Specification({
        "${app}/discovery.find.skill/client",
        "${cache}/cache.hydrate.discovery/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldFindAgentBySkill() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.discovery.cache.yaml")
    @Specification({
        "${app}/discovery.no.match/client",
        "${cache}/cache.hydrate.discovery/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldFindNoAgentWhenNoMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.discovery.cache.yaml")
    @Specification({
        "${app}/discovery.limit/client",
        "${cache}/cache.hydrate.discovery/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldLimitAgentsByQuery() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.discovery.limit.yaml")
    @Specification({
        "${app}/discovery.limit.config/client",
        "${cache}/cache.hydrate.discovery/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldLimitAgentsByConfig() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.discovery.cache.yaml")
    @Specification({
        "${app}/discovery.query.data/client",
        "${cache}/cache.hydrate.discovery/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldFindAgentByDataPart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.discovery.cache.yaml")
    @Specification({
        "${app}/discovery.rank/client",
        "${cache}/cache.hydrate.discovery/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRankAgentsByRelevance() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.discovery.cache.yaml")
    @Specification({
        "${app}/discovery.card/client",
        "${cache}/cache.hydrate.discovery/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldServeDiscoveryCard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.discovery.guarded.yaml")
    @Specification({
        "${app}/discovery.guarded/client",
        "${cache}/cache.hydrate.guarded/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldFindAgentWithPermittedSkills() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.discovery.guarded.yaml")
    @Specification({
        "${app}/discovery.guarded.filtered/client",
        "${cache}/cache.hydrate.guarded/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldFindAgentWithoutRestrictedSkills() throws Exception
    {
        k3po.finish();
    }
}
