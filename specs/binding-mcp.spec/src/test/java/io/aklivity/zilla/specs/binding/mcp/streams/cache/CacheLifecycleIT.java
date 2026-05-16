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
package io.aklivity.zilla.specs.binding.mcp.streams.cache;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class CacheLifecycleIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mcp/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/cache.warmup.session.initialize/client",
        "${app}/cache.warmup.session.initialize/server" })
    public void shouldOpenWarmupSessionAndInitialize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.warmup.session.persists/client",
        "${app}/cache.warmup.session.persists/server" })
    public void shouldKeepWarmupSessionOpenAfterEnumeration() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.warmup.session.uses.test.guard.credentials/client",
        "${app}/cache.warmup.session.uses.test.guard.credentials/server" })
    public void shouldIssueWarmupWithTestGuardCredentials() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.warmup.session.downstream.error/client",
        "${app}/cache.warmup.session.downstream.error/server" })
    public void shouldSurviveDownstreamErrorDuringWarmup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.agent.initialize.from.cache/client",
        "${app}/cache.agent.initialize.from.cache/server" })
    public void shouldServeAgentInitializeFromCache() throws Exception
    {
        k3po.finish();
    }
}
