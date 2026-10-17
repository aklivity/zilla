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
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.accepted/client",
        "${app}/openai.proxy.guardrail.accepted/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedSection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.rejected/client",
        "${app}/openai.proxy.guardrail.rejected.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRejectGuardedSection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.response.rejected/client",
        "${app}/openai.proxy.guardrail.response.rejected.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRejectGuardedResponseSection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    @Specification({
        "${app}/openai.proxy.guardrail.response.rejected.reasoning/client",
        "${app}/openai.proxy.guardrail.response.rejected.reasoning.exit/server"})
    public void shouldRejectGuardedResponseSectionAcrossReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.proxy.guardrail.yaml")
    @Specification({
        "${net}/openai.proxy.guardrail.rejected/client",
        "${app}/openai.proxy.guardrail.rejected.request/server"})
    public void shouldRejectGuardedSectionFromNetwork() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.async.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.accepted/client",
        "${app}/openai.proxy.guardrail.accepted/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedSectionAsync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.async.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.rejected/client",
        "${app}/openai.proxy.guardrail.rejected.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRejectGuardedSectionAsync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.transform.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.transform/client",
        "${app}/openai.proxy.guardrail.transform.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedSections() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.transform.streaming/client",
        "${app}/openai.proxy.guardrail.transform.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedStreamingSections() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.overlap.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.transform.overlap/client",
        "${app}/openai.proxy.guardrail.transform.overlap.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedOverlapSections() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.response.rejected.streaming/client",
        "${app}/openai.proxy.guardrail.response.rejected.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRejectGuardedResponseStreamingSections() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.chain.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.chain.accepted/client",
        "${app}/openai.proxy.guardrail.chain.accepted.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayChainedSection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.chain.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.chain.rejected/client",
        "${app}/openai.proxy.guardrail.chain.rejected.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRejectChainedSection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.fallback.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.fallback/client",
        "${app}/openai.proxy.fallback.unavailable/server",
        "${app}/openai.proxy.guardrail.fallback.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldChainGuardrailStagesAcrossFallback() throws Exception
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
    @Configuration("proxy.guardrail.disclose.10k.yaml")
    @Specification({
        "${app}/openai.10k/client",
        "${app}/openai.10k/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedOpenai10kSections() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.disclose.100k.yaml")
    @Specification({
        "${app}/openai.100k/client",
        "${app}/openai.100k/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedOpenai100kSections() throws Exception
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

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/openai.proxy.rejected/client",
        "${app}/openai.proxy.rejected/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldForwardResetExtensionFromAppZero() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/openai.proxy.sections/client",
        "${app}/openai.proxy.sections/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRouteOpenaiSectionsToAppZeroRegardlessOfSectionType() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.fallback.yaml")
    @Specification({
        "${app}/openai.proxy.fallback/client",
        "${app}/openai.proxy.fallback.refused/server",
        "${app}/openai.proxy.fallback.accepted/server"})
    public void shouldFallbackToNextModelWhenExitRefusesStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.fallback.yaml")
    @Specification({
        "${app}/openai.proxy.fallback/client",
        "${app}/openai.proxy.fallback.refused/server",
        "${app}/openai.proxy.fallback.accepted/server"})
    @ScriptProperty("model \"gpt-s\"")
    public void shouldSkipFallbackModelWithoutRoute() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.fallback.yaml")
    @Specification({
        "${app}/openai.proxy.fallback.exhausted/client",
        "${app}/openai.proxy.fallback.refused/server",
        "${app}/openai.proxy.fallback.unavailable/server"})
    public void shouldRejectWhenEveryFallbackModelIsRefused() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.fallback.yaml")
    @Specification({
        "${app}/openai.proxy.fallback.unavailable/client",
        "${app}/openai.proxy.fallback.unavailable/server"})
    @ScriptProperty({ "clientAddress \"zilla://streams/net0\"", "serverAddress \"zilla://streams/app1\"" })
    public void shouldRejectWhenRouteHasNoFallback() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.fallback.yaml")
    @Specification({
        "${app}/openai.proxy.rejected/client",
        "${app}/openai.proxy.rejected/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldNotFallbackOnceExitHasGrantedCredit() throws Exception
    {
        k3po.finish();
    }
}
