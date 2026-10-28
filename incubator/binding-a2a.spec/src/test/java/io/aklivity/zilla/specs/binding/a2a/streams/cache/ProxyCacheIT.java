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
package io.aklivity.zilla.specs.binding.a2a.streams.cache;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class ProxyCacheIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("cache", "io/aklivity/zilla/specs/binding/a2a/streams/cache");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${cache}/cache.hydrate/client",
        "${cache}/cache.hydrate/server"})
    public void shouldCacheHydrate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${cache}/cache.hydrate.credentials/client",
        "${cache}/cache.hydrate.credentials/server"})
    public void shouldCacheHydrateCredentials() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${cache}/cache.hydrate.error/client",
        "${cache}/cache.hydrate.error/server"})
    public void shouldCacheHydrateError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${cache}/cache.hydrate.error.retry/client",
        "${cache}/cache.hydrate.error.retry/server"})
    public void shouldCacheHydrateErrorRetry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${cache}/cache.hydrate.multi/client",
        "${cache}/cache.hydrate.multi/server"})
    public void shouldCacheHydrateMulti() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${cache}/cache.hydrate.stale/client",
        "${cache}/cache.hydrate.stale/server"})
    public void shouldCacheHydrateStale() throws Exception
    {
        k3po.finish();
    }
}
