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


public class McpProxyCachePromptsListIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mcp/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/mcp/config")
        .external("app1")
        .configure(MCP_SESSION_ID_NAME, "%s::hydrateSessionId".formatted(McpProxyCachePromptsListIT.class.getName()))
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.cache.yaml")
    @Specification({
        "${app}/cache.hydrate.prompts/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldHydratePrompts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.cache.yaml")
    @Specification({
        "${app}/cache.serve.prompts.list/client",
        "${app}/cache.hydrate.prompts/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldServePromptsList() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: enable when periodic refresh lands")
    @Test
    @Configuration("proxy.cache.refresh.yaml")
    @Specification({
        "${app}/cache.refresh.prompts/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRefreshPrompts() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO: enable when hydrating-wait lands")
    @Test
    @Configuration("proxy.cache.yaml")
    @Specification({
        "${app}/cache.serve.prompts.list.hydrating/client",
        "${app}/cache.serve.prompts.list.hydrating/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldServePromptsListHydrating() throws Exception
    {
        k3po.finish();
    }

    public static String hydrateSessionId()
    {
        return "hydrate-1";
    }
}
