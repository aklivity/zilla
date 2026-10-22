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
        "${app}/openai.proxy.attributes/client",
        "${app}/openai.proxy.attributes/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayBeginAttributes() throws Exception
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
    @Configuration("proxy.guardrail.direction.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.accepted/client",
        "${app}/openai.proxy.guardrail.accepted/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldSupplyDirectionToGuardedSections() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.control/client",
        "${app}/openai.proxy.guardrail.control/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayControlSectionsUnguarded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/anthropic.proxy.guardrail.control/client",
        "${app}/anthropic.proxy.guardrail.control/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayAnthropicControlSectionsUnguarded() throws Exception
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
        "${app}/openai.proxy.guardrail.media.accepted/client",
        "${app}/openai.proxy.guardrail.media.accepted/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedMedia() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.media.url.accepted/client",
        "${app}/openai.proxy.guardrail.media.url.accepted/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedMediaUrl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.tool.definition.accepted/client",
        "${app}/openai.proxy.guardrail.tool.definition.accepted/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.media.image.rejected/client",
        "${app}/openai.proxy.guardrail.media.image.rejected.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRejectGuardedImage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.media.audio.rejected/client",
        "${app}/openai.proxy.guardrail.media.audio.rejected.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRejectGuardedAudio() throws Exception
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
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.response.accepted.tool.call/client",
        "${app}/openai.proxy.guardrail.response.accepted.tool.call/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.response.accepted.tool.call.member/client",
        "${app}/openai.proxy.guardrail.response.accepted.tool.call.member/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedToolCallWithMemberOutsideArguments() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.response.rejected.tool.call/client",
        "${app}/openai.proxy.guardrail.response.rejected.tool.call.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRejectGuardedResponseToolCall() throws Exception
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
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.ranges.unguarded/client",
        "${app}/openai.proxy.guardrail.ranges.unguarded.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayRangesOfUnguardedSection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.ranges.identity/client",
        "${app}/openai.proxy.guardrail.ranges.identity.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayRangesThroughIdentityGuard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.ranges.mask.unchanged/client",
        "${app}/openai.proxy.guardrail.ranges.mask.unchanged.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayRangesThroughUnchangingMaskGuard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.ranges.mask.changed/client",
        "${app}/openai.proxy.guardrail.ranges.mask.changed.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldKeepRangesBeforeFirstEditOfMaskGuard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.ranges.tool.call/client",
        "${app}/openai.proxy.guardrail.ranges.tool.call.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayRangesThroughIdentityGuardedToolCall() throws Exception
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
    @Configuration("proxy.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.transform.members/client",
        "${app}/openai.proxy.guardrail.transform.members.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedSectionsKeepingMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.transform.members.leading/client",
        "${app}/openai.proxy.guardrail.transform.members.leading.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedSectionsKeepingLeadingMembersInOrder() throws Exception
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
        "${app}/openai.proxy.fallback.attributes/client",
        "${app}/openai.proxy.fallback.refused/server",
        "${app}/openai.proxy.fallback.attributes.accepted/server"})
    public void shouldRelayBeginAttributesToFallbackModel() throws Exception
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

    @Test
    @Configuration("proxy.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.transform.consecutive/client",
        "${app}/openai.proxy.guardrail.transform.consecutive.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedConsecutiveSections() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.transform.tool.call/client",
        "${app}/openai.proxy.guardrail.transform.tool.call.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedSectionBeforeToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.response.accepted/client",
        "${app}/openai.proxy.guardrail.response.accepted.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedResponseSection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.response.accepted/client",
        "${app}/openai.proxy.guardrail.response.accepted.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedResponseSectionStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.classify.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.response.classified.streaming.exit/client",
        "${app}/openai.proxy.guardrail.response.classified.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayClassifiedResponseSectionBeforeFin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.classify.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.response.classified.paused.exit/client",
        "${app}/openai.proxy.guardrail.response.classified.paused.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayClassifiedResponseSectionWhileStreamPaused() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.classify.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.response.classified.tool.call.paused.exit/client",
        "${app}/openai.proxy.guardrail.response.classified.tool.call.paused.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayClassifiedResponseToolCallWhileStreamPaused() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.response.rejected/client",
        "${app}/openai.proxy.guardrail.response.rejected.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRejectGuardedResponseSectionStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.response.transformed/client",
        "${app}/openai.proxy.guardrail.response.transformed.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedResponseSection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.response.transformed/client",
        "${app}/openai.proxy.guardrail.response.transformed.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedResponseSectionStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.response.accepted.reasoning/client",
        "${app}/openai.proxy.guardrail.response.accepted.reasoning.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedResponseReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.response.accepted.reasoning/client",
        "${app}/openai.proxy.guardrail.response.accepted.reasoning.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedResponseReasoningStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.response.rejected.reasoning/client",
        "${app}/openai.proxy.guardrail.response.rejected.reasoning.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRejectGuardedResponseReasoningStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.response.transformed.reasoning/client",
        "${app}/openai.proxy.guardrail.response.transformed.reasoning.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedResponseReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.response.transformed.reasoning/client",
        "${app}/openai.proxy.guardrail.response.transformed.reasoning.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedResponseReasoningStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.response.accepted.tool.call/client",
        "${app}/openai.proxy.guardrail.response.accepted.tool.call.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedResponseToolCallStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.response.rejected.tool.call/client",
        "${app}/openai.proxy.guardrail.response.rejected.tool.call.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRejectGuardedResponseToolCallStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.response.transformed.tool.call/client",
        "${app}/openai.proxy.guardrail.response.transformed.tool.call.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedResponseToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.response.transformed.tool.call.member/client",
        "${app}/openai.proxy.guardrail.response.transformed.tool.call.member.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedToolCallArgumentsOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.proxy.guardrail.response.transformed.tool.call/client",
        "${app}/openai.proxy.guardrail.response.transformed.tool.call.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedResponseToolCallStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/openai.responses.proxy/client",
        "${app}/openai.responses.proxy/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRouteOpenaiResponsesToAppZeroByVendor() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/gemini.proxy/client",
        "${app}/gemini.proxy/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRouteGeminiToAppZero() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.yaml")
    @ScriptProperty({"clientAddress \"zilla://streams/net0\""})
    @Specification({
        "${app}/openai.responses.proxy/client",
        "${app}/openai.responses.proxy/server"})
    public void shouldRouteOpenaiResponsesToAppZeroByDialect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.yaml")
    @Specification({
        "${net}/proxy.route.unmatched.dialect/client"})
    public void shouldRejectOpenaiOnOpenaiResponsesRoute() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.proxy.fallback/client",
        "${app}/openai.proxy.fallback.refused/server",
        "${app}/openai.responses.proxy.fallback.accepted/server"})
    public void shouldFallbackFromOpenaiResponsesToOpenaiRoute() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.accepted/client",
        "${app}/openai.responses.proxy.guardrail.accepted/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedSectionForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/gemini.proxy.guardrail.accepted/client",
        "${app}/gemini.proxy.guardrail.accepted/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGeminiGuardedSection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.control/client",
        "${app}/openai.responses.proxy.guardrail.control/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayControlSectionsUnguardedForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guardrail.yaml")
    @Specification({
        "${app}/gemini.proxy.guardrail.rejected/client",
        "${app}/gemini.proxy.guardrail.rejected.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRejectGeminiGuardedSection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.rejected/client",
        "${app}/openai.responses.proxy.guardrail.rejected.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRejectGuardedSectionForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.rejected/client",
        "${app}/openai.responses.proxy.guardrail.response.rejected.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRejectGuardedResponseSectionForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.tool.call/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.tool.call/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedToolCallForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.rejected.tool.call/client",
        "${app}/openai.responses.proxy.guardrail.response.rejected.tool.call.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRejectGuardedResponseToolCallForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.transform.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.transform/client",
        "${app}/openai.responses.proxy.guardrail.transform.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedSectionsForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.transform.streaming/client",
        "${app}/openai.responses.proxy.guardrail.transform.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedStreamingSectionsForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.chain.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.chain.accepted/client",
        "${app}/openai.responses.proxy.guardrail.chain.accepted.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayChainedSectionForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.chain.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.chain.rejected/client",
        "${app}/openai.responses.proxy.guardrail.chain.rejected.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRejectChainedSectionForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.fallback.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.fallback/client",
        "${app}/openai.proxy.fallback.unavailable/server",
        "${app}/openai.responses.proxy.guardrail.fallback.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldChainGuardrailStagesAcrossFallbackForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.transform.consecutive/client",
        "${app}/openai.responses.proxy.guardrail.transform.consecutive.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedConsecutiveSectionsForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.transform.tool.call/client",
        "${app}/openai.responses.proxy.guardrail.transform.tool.call.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedSectionBeforeToolCallForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedResponseSectionForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedResponseSectionStreamingForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.rejected/client",
        "${app}/openai.responses.proxy.guardrail.response.rejected.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRejectGuardedResponseSectionStreamingForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.transformed/client",
        "${app}/openai.responses.proxy.guardrail.response.transformed.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedResponseSectionForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.transformed/client",
        "${app}/openai.responses.proxy.guardrail.response.transformed.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedResponseSectionStreamingForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedResponseReasoningForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedResponseReasoningStreamingForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.tool.call/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.tool.call.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedResponseToolCallStreamingForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.rejected.tool.call/client",
        "${app}/openai.responses.proxy.guardrail.response.rejected.tool.call.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRejectGuardedResponseToolCallStreamingForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.transformed.tool.call/client",
        "${app}/openai.responses.proxy.guardrail.response.transformed.tool.call.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedResponseToolCallForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.transformed.tool.call/client",
        "${app}/openai.responses.proxy.guardrail.response.transformed.tool.call.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedResponseToolCallStreamingForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.refusal/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.refusal.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedResponseRefusalForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.refusal/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.refusal.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedResponseRefusalStreamingForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.rejected.refusal/client",
        "${app}/openai.responses.proxy.guardrail.response.rejected.refusal.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRejectGuardedResponseRefusalForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.rejected.refusal/client",
        "${app}/openai.responses.proxy.guardrail.response.rejected.refusal.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRejectGuardedResponseRefusalStreamingForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.transformed.refusal/client",
        "${app}/openai.responses.proxy.guardrail.response.transformed.refusal.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedResponseRefusalForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.transformed.refusal/client",
        "${app}/openai.responses.proxy.guardrail.response.transformed.refusal.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedResponseRefusalStreamingForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.transformed.reasoning.parts/client",
        "${app}/openai.responses.proxy.guardrail.response.transformed.reasoning.parts.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedResponseReasoningPartsForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.transformed.reasoning.parts/client",
        "${app}/openai.responses.proxy.guardrail.response.transformed.reasoning.parts.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedResponseReasoningPartsStreamingForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.rejected.reasoning.parts/client",
        "${app}/openai.responses.proxy.guardrail.response.rejected.reasoning.parts.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRejectGuardedResponseReasoningMiddlePartForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.rejected.reasoning.parts/client",
        "${app}/openai.responses.proxy.guardrail.response.rejected.reasoning.parts.streaming.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRejectGuardedResponseReasoningMiddlePartStreamingForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.large/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.large.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedResponseReasoningLargeForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.large.small.window/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.large.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedResponseReasoningLargeWithSmallWindowForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.mask.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.transformed.reasoning.large/client",
        "${app}/openai.responses.proxy.guardrail.response.transformed.reasoning.large.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayTransformedResponseReasoningLargeForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.members.first/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.members.first.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedResponseReasoningMembersFirstForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.members.first.large/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.members.first.large.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedResponseReasoningMembersFirstLargeForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.no.summary/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.no.summary.exit/server"})
    @ScriptProperty("clientAddress \"zilla://streams/net0\"")
    public void shouldRelayGuardedResponseReasoningNoSummaryForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${net}/openai.responses.proxy.guardrail.rejected/client",
        "${app}/openai.responses.proxy.guardrail.rejected.exit/server"})
    public void shouldRejectGuardedSectionFromNetworkForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.proxy.openai.responses.guardrail.yaml")
    @Specification({
        "${net}/openai.responses.proxy.guardrail.transformed/client",
        "${app}/openai.responses.proxy.guardrail.transformed.exit/server"})
    public void shouldRelayTransformedSectionsFromNetworkForOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.proxy.guardrail.yaml")
    @Specification({
        "${net}/gemini.proxy.guardrail.rejected/client",
        "${app}/gemini.proxy.guardrail.rejected.request/server"})
    public void shouldRejectGeminiGuardedSectionFromNetwork() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.api.yaml")
    @ScriptProperty({"clientAddress \"zilla://streams/net0\""})
    @Specification({
        "${app}/openai.proxy/client",
        "${app}/openai.proxy/server"})
    public void shouldRouteOpenaiChatCompletionsToAppZeroByApi() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.openai.api.yaml")
    @ScriptProperty({"clientAddress \"zilla://streams/net0\"", "serverAddress \"zilla://streams/app1\""})
    @Specification({
        "${app}/openai.responses.proxy/client",
        "${app}/openai.responses.proxy/server"})
    public void shouldRouteOpenaiResponsesToAppOneByApi() throws Exception
    {
        k3po.finish();
    }
}
