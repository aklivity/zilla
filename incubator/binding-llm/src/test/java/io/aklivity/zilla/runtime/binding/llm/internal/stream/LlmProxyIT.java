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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

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

public class LlmProxyIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/llm/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/llm/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/llm/config")
        .external("app0")
        .external("app1")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${net}/proxy.route.unmatched/client"})
    public void shouldRejectRequestWithUnmatchedModel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/openai.proxy/client",
        "${app}/openai.proxy/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRouteOpenaiToAppZero() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/openai.10k/client",
        "${app}/openai.10k/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRouteOpenai10kToAppZero() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/anthropic.10k/client",
        "${app}/anthropic.10k/server"})
    @ScriptProperty({ "clientAddress \"zilla://streams/net0\"", "serverAddress \"zilla://streams/app1\"" })
    public void shouldRouteAnthropic10kToAppOne() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/openai.100k/client",
        "${app}/openai.100k/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRouteOpenai100kToAppZero() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/anthropic.100k/client",
        "${app}/anthropic.100k/server"})
    @ScriptProperty({ "clientAddress \"zilla://streams/net0\"", "serverAddress \"zilla://streams/app1\"" })
    public void shouldRouteAnthropic100kToAppOne() throws Exception
    {
        k3po.finish();
    }
}
