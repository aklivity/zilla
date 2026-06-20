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

public class ProxyCacheIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mcp/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/cache.hydrate/client",
        "${app}/cache.hydrate/server" })
    public void shouldHydrate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.hydrate.error/client",
        "${app}/cache.hydrate.error/server" })
    public void shouldHydrateError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.hydrate.credentials/client",
        "${app}/cache.hydrate.credentials/server" })
    public void shouldHydrateWithCredentials() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.hydrate.credentials.toolkit/client",
        "${app}/cache.hydrate.credentials.toolkit/server" })
    public void shouldHydrateToolkitWithRouteCredentials() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.hydrate.toolkit/client",
        "${app}/cache.hydrate.toolkit/server" })
    public void shouldHydrateToolkit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.serve.initialize/client",
        "${app}/cache.serve.initialize/server" })
    public void shouldServeInitialize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.hydrate.lifecycle.reconnect/client",
        "${app}/cache.hydrate.lifecycle.reconnect/server" })
    public void shouldReconnectAfterLifecycleAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.serve.tools.list/client",
        "${app}/cache.serve.tools.list/server" })
    public void shouldServeToolsList() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.refresh.tools/client",
        "${app}/cache.refresh.tools/server" })
    public void shouldRefreshTools() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.refresh.tools.error/client",
        "${app}/cache.refresh.tools.error/server" })
    public void shouldRefreshToolsError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.refresh.tools.error.retry/client",
        "${app}/cache.refresh.tools.error.retry/server" })
    public void shouldRetryAfterToolsRefreshError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.refresh.tools.contended/client",
        "${app}/cache.refresh.tools.contended/server" })
    public void shouldRefreshToolsContended() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.serve.resources.list/client",
        "${app}/cache.serve.resources.list/server" })
    public void shouldServeResourcesList() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.refresh.resources/client",
        "${app}/cache.refresh.resources/server" })
    public void shouldRefreshResources() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.serve.prompts.list/client",
        "${app}/cache.serve.prompts.list/server" })
    public void shouldServePromptsList() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.refresh.prompts/client",
        "${app}/cache.refresh.prompts/server" })
    public void shouldRefreshPrompts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.hydrate.10k/client",
        "${app}/cache.hydrate.10k/server" })
    public void shouldHydrate10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.hydrate.100k/client",
        "${app}/cache.hydrate.100k/server" })
    public void shouldHydrate100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.serve.tools.list.10k/client",
        "${app}/cache.serve.tools.list.10k/server" })
    public void shouldServeToolsList10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.serve.tools.list.100k/client",
        "${app}/cache.serve.tools.list.100k/server" })
    public void shouldServeToolsList100k() throws Exception
    {
        k3po.finish();
    }
}
