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
package io.aklivity.zilla.specs.binding.llm.streams.application;

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

public class ApplicationIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/llm/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/openai.response.invalid.content.type/client",
        "${app}/openai.response.invalid.content.type/server"})
    public void shouldAbortResponseWithInvalidContentType() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/abort/client",
        "${app}/abort/server"})
    public void shouldAbortClientRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/begin.attributes/client",
        "${app}/begin.attributes/server"})
    public void shouldReceiveBeginAttributes() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/begin.attributes.unconstrained/client",
        "${app}/begin.attributes.unconstrained/server"})
    public void shouldReceiveBeginWithAttributesUnconstrained() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.echo/client",
        "${app}/openai.echo/server"})
    public void shouldForwardOpenaiRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.echo/client",
        "${app}/anthropic.echo/server"})
    public void shouldForwardAnthropicRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.streaming.usage/client",
        "${app}/openai.streaming.usage/server"})
    public void shouldForwardOpenaiStreamingUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.streaming.usage.padded/client",
        "${app}/openai.streaming.usage.padded/server"})
    public void shouldForwardOpenaiStreamingUsagePadded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai/client",
        "${app}/openai/server"})
    public void shouldForwardOpenaiNonstreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.usage/client",
        "${app}/openai.usage/server"})
    public void shouldForwardOpenaiNonstreamingUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.usage.padded/client",
        "${app}/openai.usage.padded/server"})
    public void shouldForwardOpenaiNonstreamingUsagePadded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.reply.padded/client",
        "${app}/openai.reply.padded/server"})
    public void shouldForwardOpenaiNonstreamingWithReplyPadding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.request.padded/client",
        "${app}/openai.request.padded/server"})
    public void shouldForwardOpenaiRequestWithReplyPadding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy/client",
        "${app}/openai.proxy/server"})
    public void shouldRouteOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.invalid/client",
        "${app}/openai.response.invalid/server"})
    public void shouldAbortInvalidOpenaiResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.streaming.usage/client",
        "${app}/anthropic.streaming.usage/server"})
    public void shouldForwardAnthropicStreamingUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.streaming.usage.padded/client",
        "${app}/anthropic.streaming.usage.padded/server"})
    public void shouldForwardAnthropicStreamingUsagePadded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.streaming.abort/client",
        "${app}/openai.streaming.abort/server"})
    public void shouldAbortOpenaiStreamingWithPartialUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.streaming.abort/client",
        "${app}/anthropic.streaming.abort/server"})
    public void shouldAbortAnthropicStreamingWithPartialUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.streaming.abort.nocredit/client",
        "${app}/openai.streaming.abort.nocredit/server"})
    public void shouldAbortOpenaiStreamingWithPartialUsageWithoutCredit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.streaming.abort.nocredit/client",
        "${app}/anthropic.streaming.abort.nocredit/server"})
    public void shouldAbortAnthropicStreamingWithPartialUsageWithoutCredit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.usage/client",
        "${app}/anthropic.usage/server"})
    public void shouldForwardAnthropicNonstreamingUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.usage.padded/client",
        "${app}/anthropic.usage.padded/server"})
    public void shouldForwardAnthropicNonstreamingUsagePadded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic/client",
        "${app}/anthropic/server"})
    public void shouldForwardAnthropicNonstreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.authorized/client",
        "${app}/openai.authorized/server"})
    public void shouldForwardOpenaiRequestAuthorized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.authorized/client",
        "${app}/anthropic.authorized/server"})
    public void shouldForwardAnthropicRequestAuthorized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.10k/client",
        "${app}/openai.10k/server"})
    public void shouldForwardOpenai10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.10k/client",
        "${app}/anthropic.10k/server"})
    public void shouldForwardAnthropic10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.100k/client",
        "${app}/openai.100k/server"})
    public void shouldForwardOpenai100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.100k/client",
        "${app}/anthropic.100k/server"})
    public void shouldForwardAnthropic100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.streaming.10k/client",
        "${app}/openai.streaming.10k/server"})
    public void shouldForwardOpenaiStreaming10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.streaming.10k/client",
        "${app}/anthropic.streaming.10k/server"})
    public void shouldForwardAnthropicStreaming10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.streaming.100k/client",
        "${app}/openai.streaming.100k/server"})
    public void shouldForwardOpenaiStreaming100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.streaming.100k/client",
        "${app}/anthropic.streaming.100k/server"})
    public void shouldForwardAnthropicStreaming100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.rejected.rate.limit/client",
        "${app}/openai.rejected.rate.limit/server"})
    public void shouldRejectOpenaiRateLimited() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.rejected.rate.limit/client",
        "${app}/anthropic.rejected.rate.limit/server"})
    public void shouldRejectAnthropicRateLimited() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.accepted/client",
        "${app}/openai.proxy.guardrail.accepted/server"})
    public void shouldRelayOpenaiGuardedSection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.transform/client",
        "${app}/openai.proxy.guardrail.transform/server"})
    public void shouldGuardOpenaiTransformNet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.transform.exit/client",
        "${app}/openai.proxy.guardrail.transform.exit/server"})
    public void shouldGuardOpenaiTransformExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.ranges.unguarded/client",
        "${app}/openai.proxy.guardrail.ranges.unguarded/server"})
    public void shouldGuardOpenaiRangesUnguardedNet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.ranges.unguarded.exit/client",
        "${app}/openai.proxy.guardrail.ranges.unguarded.exit/server"})
    public void shouldGuardOpenaiRangesUnguardedExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.ranges.identity/client",
        "${app}/openai.proxy.guardrail.ranges.identity/server"})
    public void shouldGuardOpenaiRangesIdentityNet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.ranges.identity.exit/client",
        "${app}/openai.proxy.guardrail.ranges.identity.exit/server"})
    public void shouldGuardOpenaiRangesIdentityExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.ranges.mask.unchanged/client",
        "${app}/openai.proxy.guardrail.ranges.mask.unchanged/server"})
    public void shouldGuardOpenaiRangesMaskUnchangedNet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.ranges.mask.changed/client",
        "${app}/openai.proxy.guardrail.ranges.mask.changed/server"})
    public void shouldGuardOpenaiRangesMaskChangedNet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.ranges.mask.unchanged.exit/client",
        "${app}/openai.proxy.guardrail.ranges.mask.unchanged.exit/server"})
    public void shouldGuardOpenaiRangesMaskUnchangedExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.ranges.mask.changed.exit/client",
        "${app}/openai.proxy.guardrail.ranges.mask.changed.exit/server"})
    public void shouldGuardOpenaiRangesMaskChangedExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.ranges.tool.call/client",
        "${app}/openai.proxy.guardrail.ranges.tool.call/server"})
    public void shouldGuardOpenaiRangesToolCallNet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.ranges.tool.call.exit/client",
        "${app}/openai.proxy.guardrail.ranges.tool.call.exit/server"})
    public void shouldGuardOpenaiRangesToolCallExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.transform.streaming/client",
        "${app}/openai.proxy.guardrail.transform.streaming/server"})
    public void shouldGuardOpenaiTransformStreamingNet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.transform.streaming.exit/client",
        "${app}/openai.proxy.guardrail.transform.streaming.exit/server"})
    public void shouldGuardOpenaiTransformStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.transform.members/client",
        "${app}/openai.proxy.guardrail.transform.members/server"})
    public void shouldGuardOpenaiTransformMembersNet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.transform.members.exit/client",
        "${app}/openai.proxy.guardrail.transform.members.exit/server"})
    public void shouldGuardOpenaiTransformMembersExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.transform.members.leading/client",
        "${app}/openai.proxy.guardrail.transform.members.leading/server"})
    public void shouldGuardOpenaiTransformMembersLeadingNet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.transform.members.leading.exit/client",
        "${app}/openai.proxy.guardrail.transform.members.leading.exit/server"})
    public void shouldGuardOpenaiTransformMembersLeadingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.accepted.tool.call/client",
        "${app}/openai.proxy.guardrail.response.accepted.tool.call/server"})
    public void shouldGuardOpenaiResponseAcceptedToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.accepted.tool.call.member/client",
        "${app}/openai.proxy.guardrail.response.accepted.tool.call.member/server"})
    public void shouldGuardOpenaiResponseAcceptedToolCallMember() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.media.accepted/client",
        "${app}/openai.proxy.guardrail.media.accepted/server"})
    public void shouldGuardOpenaiMediaAccepted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.media.url.accepted/client",
        "${app}/openai.proxy.guardrail.media.url.accepted/server"})
    public void shouldGuardOpenaiMediaUrlAccepted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.tool.definition.accepted/client",
        "${app}/openai.proxy.guardrail.tool.definition.accepted/server"})
    public void shouldGuardOpenaiToolDefinitionAccepted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.media.image.rejected/client",
        "${app}/openai.proxy.guardrail.media.image.rejected/server"})
    public void shouldGuardOpenaiMediaImageRejected() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.media.image.rejected.exit/client",
        "${app}/openai.proxy.guardrail.media.image.rejected.exit/server"})
    public void shouldGuardOpenaiMediaImageRejectedExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.media.audio.rejected/client",
        "${app}/openai.proxy.guardrail.media.audio.rejected/server"})
    public void shouldGuardOpenaiMediaAudioRejected() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.media.audio.rejected.exit/client",
        "${app}/openai.proxy.guardrail.media.audio.rejected.exit/server"})
    public void shouldGuardOpenaiMediaAudioRejectedExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.rejected.tool.call/client",
        "${app}/openai.proxy.guardrail.response.rejected.tool.call/server"})
    public void shouldGuardOpenaiResponseRejectedToolCallNet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.rejected.tool.call.exit/client",
        "${app}/openai.proxy.guardrail.response.rejected.tool.call.exit/server"})
    public void shouldGuardOpenaiResponseRejectedToolCallExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.chain.accepted/client",
        "${app}/openai.proxy.guardrail.chain.accepted/server"})
    public void shouldGuardOpenaiChainAcceptedNet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.chain.accepted.exit/client",
        "${app}/openai.proxy.guardrail.chain.accepted.exit/server"})
    public void shouldGuardOpenaiChainAcceptedExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.chain.rejected/client",
        "${app}/openai.proxy.guardrail.chain.rejected/server"})
    public void shouldGuardOpenaiChainRejectedNet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.chain.rejected.exit/client",
        "${app}/openai.proxy.guardrail.chain.rejected.exit/server"})
    public void shouldGuardOpenaiChainRejectedExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.fallback/client",
        "${app}/openai.proxy.guardrail.fallback/server"})
    public void shouldGuardOpenaiFallbackNet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.fallback.exit/client",
        "${app}/openai.proxy.guardrail.fallback.exit/server"})
    public void shouldGuardOpenaiFallbackExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.rejected/client",
        "${app}/openai.proxy.guardrail.rejected/server"})
    public void shouldGuardOpenaiRejectedNet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.rejected.exit/client",
        "${app}/openai.proxy.guardrail.rejected.exit/server"})
    public void shouldGuardOpenaiRejectedExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.rejected/client",
        "${app}/openai.proxy.guardrail.response.rejected/server"})
    public void shouldGuardOpenaiResponseRejectedNet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.rejected.exit/client",
        "${app}/openai.proxy.guardrail.response.rejected.exit/server"})
    public void shouldGuardOpenaiResponseRejectedExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.rejected.section/client",
        "${app}/openai.rejected.section/server"})
    public void shouldRejectOpenaiSection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.rejected.section/client",
        "${app}/anthropic.rejected.section/server"})
    public void shouldRejectAnthropicSection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.rejected.bad.gateway/client",
        "${app}/openai.rejected.bad.gateway/server"})
    public void shouldRejectOpenaiBadGateway() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.rejected.reset/client",
        "${app}/openai.rejected.reset/server"})
    public void shouldRejectOpenaiReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.streaming.error/client",
        "${app}/openai.streaming.error/server"})
    public void shouldAbortOpenaiStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.streaming.error/client",
        "${app}/anthropic.streaming.error/server"})
    public void shouldAbortAnthropicStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.rejected/client",
        "${app}/openai.proxy.rejected/server"})
    public void shouldRejectOpenaiProxy() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.fallback.unavailable/client",
        "${app}/openai.proxy.fallback.unavailable/server"})
    public void shouldRejectOpenaiProxyFallbackUnavailable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/canonical.block.single/client",
        "${app}/canonical.block.single/server"})
    public void shouldCanonicalBlockSingle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/canonical.user.video/client",
        "${app}/canonical.user.video/server"})
    public void shouldCanonicalUserVideo() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/canonical.block.fragmented/client",
        "${app}/canonical.block.fragmented/server"})
    public void shouldCanonicalBlockFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/canonical.multi.block/client",
        "${app}/canonical.multi.block/server"})
    public void shouldCanonicalMultiBlock() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/canonical.transformed/client",
        "${app}/canonical.transformed/server"})
    public void shouldCanonicalTransformed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/canonical.rejected.section/client",
        "${app}/canonical.rejected.section/server"})
    public void shouldCanonicalRejectedSection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/canonical.rejected.upstream.abort/client",
        "${app}/canonical.rejected.upstream.abort/server"})
    public void shouldCanonicalRejectedUpstreamAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.system.instruction/client",
        "${app}/openai.section.system.instruction/server"})
    public void shouldOpenaiSectionSystemInstruction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.user.text/client",
        "${app}/openai.section.user.text/server"})
    public void shouldOpenaiSectionUserText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.user.text.empty/client",
        "${app}/openai.section.user.text.empty/server"})
    public void shouldOpenaiSectionUserTextEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.assistant.text.empty/client",
        "${app}/openai.section.assistant.text.empty/server"})
    public void shouldOpenaiSectionAssistantTextEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.request.response.format/client",
        "${app}/openai.request.response.format/server"})
    public void shouldOpenaiRequestResponseFormat() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.request.response.format.10k/client",
        "${app}/openai.request.response.format.10k/server"})
    public void shouldOpenaiRequestResponseFormat10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.request.response.format.100k/client",
        "${app}/openai.request.response.format.100k/server"})
    public void shouldOpenaiRequestResponseFormat100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.request.options/client",
        "${app}/openai.request.options/server"})
    public void shouldOpenaiRequestOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.request.options.early/client",
        "${app}/openai.request.options.early/server"})
    public void shouldOpenaiRequestOptionsEarly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.request.options.leading/client",
        "${app}/openai.request.options.leading/server"})
    public void shouldOpenaiRequestOptionsLeading() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.request.options.verbatim/client",
        "${app}/openai.request.options.verbatim/server"})
    public void shouldOpenaiRequestOptionsVerbatim() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.user.image/client",
        "${app}/openai.section.user.image/server"})
    public void shouldOpenaiSectionUserImage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.user.image.detail/client",
        "${app}/openai.section.user.image.detail/server"})
    public void shouldOpenaiSectionUserImageDetail() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.user.image.detail.first/client",
        "${app}/openai.section.user.image.detail.first/server"})
    public void shouldOpenaiSectionUserImageDetailFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.user.document/client",
        "${app}/openai.section.user.document/server"})
    public void shouldOpenaiSectionUserDocument() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.user.audio/client",
        "${app}/openai.section.user.audio/server"})
    public void shouldOpenaiSectionUserAudio() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.assistant.text/client",
        "${app}/openai.section.assistant.text/server"})
    public void shouldOpenaiSectionAssistantText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.assistant.text.annotations/client",
        "${app}/openai.section.assistant.text.annotations/server"})
    public void shouldOpenaiSectionAssistantTextAnnotations() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.assistant.reasoning.history/client",
        "${app}/openai.section.assistant.reasoning.history/server"})
    public void shouldDecodeOpenaiSectionAssistantReasoningHistory() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.assistant.refusal/client",
        "${app}/openai.section.assistant.refusal/server"})
    public void shouldOpenaiSectionAssistantRefusal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.tool.definition/client",
        "${app}/openai.section.tool.definition/server"})
    public void shouldOpenaiSectionToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.tool.call/client",
        "${app}/openai.section.tool.call/server"})
    public void shouldOpenaiSectionToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.tool.result/client",
        "${app}/openai.section.tool.result/server"})
    public void shouldOpenaiSectionToolResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.mixed.content/client",
        "${app}/openai.section.mixed.content/server"})
    public void shouldOpenaiSectionMixedContent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.assistant.text.and.tool.call/client",
        "${app}/openai.section.assistant.text.and.tool.call/server"})
    public void shouldOpenaiSectionAssistantTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.conversation/client",
        "${app}/openai.section.conversation/server"})
    public void shouldOpenaiSectionConversation() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.user.text.10k/client",
        "${app}/openai.section.user.text.10k/server"})
    public void shouldOpenaiSectionUserText10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.user.text.100k/client",
        "${app}/openai.section.user.text.100k/server"})
    public void shouldOpenaiSectionUserText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.user.text.100k.parts/client",
        "${app}/openai.section.user.text.100k.parts/server"})
    public void shouldOpenaiSectionUserText100kParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.tool.result.100k/client",
        "${app}/openai.section.tool.result.100k/server"})
    public void shouldOpenaiSectionToolResult100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.tool.result.100k.parts/client",
        "${app}/openai.section.tool.result.100k.parts/server"})
    public void shouldOpenaiSectionToolResult100kParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.control.request/client",
        "${app}/openai.control.request/server"})
    public void shouldOpenaiControlRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.control.request.streaming/client",
        "${app}/openai.control.request.streaming/server"})
    public void shouldOpenaiControlRequestStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.control.response/client",
        "${app}/openai.control.response/server"})
    public void shouldOpenaiControlResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.control.response.streaming/client",
        "${app}/openai.control.response.streaming/server"})
    public void shouldOpenaiControlResponseStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.control/client",
        "${app}/openai.proxy.guardrail.control/server"})
    public void shouldOpenaiProxyGuardrailControl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.proxy.guardrail.control/client",
        "${app}/anthropic.proxy.guardrail.control/server"})
    public void shouldAnthropicProxyGuardrailControl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.control.foreign.request/client",
        "${app}/anthropic.control.foreign.request/server"})
    public void shouldAnthropicControlForeignRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.control.foreign.response/client",
        "${app}/anthropic.control.foreign.response/server"})
    public void shouldAnthropicControlForeignResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.unknown.part/client",
        "${app}/openai.section.unknown.part/server"})
    public void shouldOpenaiSectionUnknownPart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.user.image.10k/client",
        "${app}/openai.section.user.image.10k/server"})
    public void shouldOpenaiSectionUserImage10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.user.text.escaped/client",
        "${app}/openai.section.user.text.escaped/server"})
    public void shouldOpenaiSectionUserTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.user.text.multibyte/client",
        "${app}/openai.section.user.text.multibyte/server"})
    public void shouldOpenaiSectionUserTextMultibyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.whitespace.between.tokens/client",
        "${app}/openai.section.whitespace.between.tokens/server"})
    public void shouldOpenaiSectionWhitespaceBetweenTokens() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.other.blocks/client",
        "${app}/openai.section.other.blocks/server"})
    public void shouldOpenaiSectionOtherBlocks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.rejected.role.missing/client",
        "${app}/openai.section.rejected.role.missing/server"})
    public void shouldOpenaiSectionRejectedRoleMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.rejected.messages.not.array/client",
        "${app}/openai.section.rejected.messages.not.array/server"})
    public void shouldOpenaiSectionRejectedMessagesNotArray() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.rejected.json.malformed/client",
        "${app}/openai.section.rejected.json.malformed/server"})
    public void shouldOpenaiSectionRejectedJsonMalformed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.rejected.trailing.content/client",
        "${app}/openai.section.rejected.trailing.content/server"})
    public void shouldOpenaiSectionRejectedTrailingContent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.rejected.role.last/client",
        "${app}/openai.section.rejected.role.last/server"})
    public void shouldOpenaiSectionRejectedRoleLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.rejected.tool.call.id.last/client",
        "${app}/openai.section.rejected.tool.call.id.last/server"})
    public void shouldOpenaiSectionRejectedToolCallIdLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.rejected.part.type.last/client",
        "${app}/openai.section.rejected.part.type.last/server"})
    public void shouldOpenaiSectionRejectedPartTypeLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.assistant.text.members/client",
        "${app}/openai.section.assistant.text.members/server"})
    public void shouldOpenaiResponseDecodeOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.assistant.text.streaming/client",
        "${app}/openai.section.assistant.text.streaming/server"})
    public void shouldDecodeOpenaiResponseStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.assistant.tool.call.streaming/client",
        "${app}/openai.section.assistant.tool.call.streaming/server"})
    public void shouldDecodeOpenaiResponseStreamingToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.assistant.text.usage.streaming/client",
        "${app}/openai.section.assistant.text.usage.streaming/server"})
    public void shouldDecodeOpenaiResponseStreamingUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.rejected.streaming/client",
        "${app}/openai.rejected.streaming/server"})
    public void shouldDecodeOpenaiResponseStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.assistant.text.100k/client",
        "${app}/openai.section.assistant.text.100k/server"})
    public void shouldDecodeOpenaiResponseText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.assistant.text.escaped/client",
        "${app}/openai.section.assistant.text.escaped/server"})
    public void shouldDecodeOpenaiResponseTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.assistant.reasoning.escaped/client",
        "${app}/openai.section.assistant.reasoning.escaped/server"})
    public void shouldDecodeOpenaiResponseReasoningEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.response.streaming.multibyte/client",
        "${app}/openai.transform.response.streaming.multibyte/server"})
    public void shouldEncodeOpenaiResponseStreamingMultibyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.reasoning/client",
        "${app}/anthropic.section.assistant.reasoning/server"})
    public void shouldAnthropicSectionAssistantReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.reasoning.signature.first/client",
        "${app}/anthropic.section.assistant.reasoning.signature.first/server"})
    public void shouldAnthropicSectionAssistantReasoningSignatureFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.reasoning.10k/client",
        "${app}/anthropic.section.assistant.reasoning.10k/server"})
    public void shouldAnthropicSectionAssistantReasoning10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.reasoning.redacted/client",
        "${app}/anthropic.section.assistant.reasoning.redacted/server"})
    public void shouldAnthropicSectionAssistantReasoningRedacted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.text/client",
        "${app}/anthropic.section.assistant.text/server"})
    public void shouldAnthropicSectionAssistantText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.tool.result.cache.control.part.first/client",
        "${app}/anthropic.section.tool.result.cache.control.part.first/server"})
    public void shouldAnthropicSectionToolResultCacheControlPartFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.tool.result.cache.control.first/client",
        "${app}/anthropic.section.tool.result.cache.control.first/server"})
    public void shouldAnthropicSectionToolResultCacheControlFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.tool.result.cache.control.part/client",
        "${app}/anthropic.section.tool.result.cache.control.part/server"})
    public void shouldAnthropicSectionToolResultCacheControlPart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.tool.result.cache.control/client",
        "${app}/anthropic.section.tool.result.cache.control/server"})
    public void shouldAnthropicSectionToolResultCacheControl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.tool.definition.cache.control/client",
        "${app}/anthropic.section.tool.definition.cache.control/server"})
    public void shouldAnthropicSectionToolDefinitionCacheControl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.server.tool.definition/client",
        "${app}/anthropic.section.server.tool.definition/server"})
    public void shouldAnthropicSectionServerToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.other.blocks/client",
        "${app}/anthropic.section.other.blocks/server"})
    public void shouldAnthropicSectionOtherBlocks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.system.instruction.cache.control/client",
        "${app}/anthropic.section.system.instruction.cache.control/server"})
    public void shouldAnthropicSectionSystemInstructionCacheControl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.text.cache.control.first/client",
        "${app}/anthropic.section.user.text.cache.control.first/server"})
    public void shouldAnthropicSectionUserTextCacheControlFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.text.cache.control/client",
        "${app}/anthropic.section.user.text.cache.control/server"})
    public void shouldAnthropicSectionUserTextCacheControl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.text.and.tool.call/client",
        "${app}/anthropic.section.assistant.text.and.tool.call/server"})
    public void shouldAnthropicSectionAssistantTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.text.citations/client",
        "${app}/anthropic.section.assistant.text.citations/server"})
    public void shouldAnthropicSectionAssistantTextCitations() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.text.citations.url/client",
        "${app}/anthropic.section.assistant.text.citations.url/server"})
    public void shouldAnthropicSectionAssistantTextCitationsUrl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.tool.call/client",
        "${app}/anthropic.section.assistant.tool.call/server"})
    public void shouldAnthropicSectionAssistantToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.conversation/client",
        "${app}/anthropic.section.conversation/server"})
    public void shouldAnthropicSectionConversation() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.mixed.content/client",
        "${app}/anthropic.section.mixed.content/server"})
    public void shouldAnthropicSectionMixedContent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.rejected.json.malformed/client",
        "${app}/anthropic.section.rejected.json.malformed/server"})
    public void shouldAnthropicSectionRejectedJsonMalformed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.rejected.messages.not.array/client",
        "${app}/anthropic.section.rejected.messages.not.array/server"})
    public void shouldAnthropicSectionRejectedMessagesNotArray() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.rejected.part.type.last/client",
        "${app}/anthropic.section.rejected.part.type.last/server"})
    public void shouldAnthropicSectionRejectedPartTypeLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.rejected.role.last/client",
        "${app}/anthropic.section.rejected.role.last/server"})
    public void shouldAnthropicSectionRejectedRoleLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.rejected.role.missing/client",
        "${app}/anthropic.section.rejected.role.missing/server"})
    public void shouldAnthropicSectionRejectedRoleMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.rejected.tool.result.id.last/client",
        "${app}/anthropic.section.rejected.tool.result.id.last/server"})
    public void shouldAnthropicSectionRejectedToolResultIdLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.rejected.trailing.content/client",
        "${app}/anthropic.section.rejected.trailing.content/server"})
    public void shouldAnthropicSectionRejectedTrailingContent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.server.tool.result/client",
        "${app}/anthropic.section.server.tool.result/server"})
    public void shouldAnthropicSectionServerToolResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.system.instruction/client",
        "${app}/anthropic.section.system.instruction/server"})
    public void shouldAnthropicSectionSystemInstruction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.system.instruction.blocks/client",
        "${app}/anthropic.section.system.instruction.blocks/server"})
    public void shouldAnthropicSectionSystemInstructionBlocks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.tool.definition/client",
        "${app}/anthropic.section.tool.definition/server"})
    public void shouldAnthropicSectionToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.tool.result/client",
        "${app}/anthropic.section.tool.result/server"})
    public void shouldAnthropicSectionToolResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.tool.result.100k/client",
        "${app}/anthropic.section.tool.result.100k/server"})
    public void shouldAnthropicSectionToolResult100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.tool.result.blocks/client",
        "${app}/anthropic.section.tool.result.blocks/server"})
    public void shouldAnthropicSectionToolResultBlocks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.unknown.part/client",
        "${app}/anthropic.section.unknown.part/server"})
    public void shouldAnthropicSectionUnknownPart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.document/client",
        "${app}/anthropic.section.user.document/server"})
    public void shouldAnthropicSectionUserDocument() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.document.title.context/client",
        "${app}/anthropic.section.user.document.title.context/server"})
    public void shouldAnthropicSectionUserDocumentTitleContext() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.document.title.context.first/client",
        "${app}/anthropic.section.user.document.title.context.first/server"})
    public void shouldAnthropicSectionUserDocumentTitleContextFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.image/client",
        "${app}/anthropic.section.user.image/server"})
    public void shouldAnthropicSectionUserImage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.output.config/client",
        "${app}/anthropic.request.output.config/server"})
    public void shouldAnthropicRequestOutputConfig() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.output.config.10k/client",
        "${app}/anthropic.request.output.config.10k/server"})
    public void shouldAnthropicRequestOutputConfig10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.output.config.100k/client",
        "${app}/anthropic.request.output.config.100k/server"})
    public void shouldAnthropicRequestOutputConfig100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.options/client",
        "${app}/anthropic.request.options/server"})
    public void shouldAnthropicRequestOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.options.early/client",
        "${app}/anthropic.request.options.early/server"})
    public void shouldAnthropicRequestOptionsEarly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.options.leading/client",
        "${app}/anthropic.request.options.leading/server"})
    public void shouldAnthropicRequestOptionsLeading() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.options.verbatim/client",
        "${app}/anthropic.request.options.verbatim/server"})
    public void shouldAnthropicRequestOptionsVerbatim() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.text/client",
        "${app}/anthropic.section.user.text/server"})
    public void shouldAnthropicSectionUserText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.text.empty/client",
        "${app}/anthropic.section.user.text.empty/server"})
    public void shouldAnthropicSectionUserTextEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.text.empty/client",
        "${app}/anthropic.section.assistant.text.empty/server"})
    public void shouldAnthropicSectionAssistantTextEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.text.100k/client",
        "${app}/anthropic.section.user.text.100k/server"})
    public void shouldAnthropicSectionUserText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.text.10k/client",
        "${app}/anthropic.section.user.text.10k/server"})
    public void shouldAnthropicSectionUserText10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.text.blocks/client",
        "${app}/anthropic.section.user.text.blocks/server"})
    public void shouldAnthropicSectionUserTextBlocks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.text.escaped/client",
        "${app}/anthropic.section.user.text.escaped/server"})
    public void shouldAnthropicSectionUserTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.text.multibyte/client",
        "${app}/anthropic.section.user.text.multibyte/server"})
    public void shouldAnthropicSectionUserTextMultibyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.user.image.inline/client",
        "${app}/openai.section.user.image.inline/server"})
    public void shouldOpenaiSectionUserImageInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.user.document.inline/client",
        "${app}/openai.section.user.document.inline/server"})
    public void shouldOpenaiSectionUserDocumentInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.image.inline/client",
        "${app}/anthropic.section.user.image.inline/server"})
    public void shouldAnthropicSectionUserImageInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.image.reference/client",
        "${app}/anthropic.section.user.image.reference/server"})
    public void shouldAnthropicSectionUserImageReference() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.document.inline/client",
        "${app}/anthropic.section.user.document.inline/server"})
    public void shouldAnthropicSectionUserDocumentInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.tool.result.error/client",
        "${app}/anthropic.section.tool.result.error/server"})
    public void shouldAnthropicSectionToolResultError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.tool.result.error.first/client",
        "${app}/anthropic.section.tool.result.error.first/server"})
    public void shouldAnthropicSectionToolResultErrorFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.tool.result.error.last/client",
        "${app}/anthropic.section.tool.result.error.last/server"})
    public void shouldAnthropicSectionToolResultErrorLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.text.shorthand/client",
        "${app}/anthropic.section.user.text.shorthand/server"})
    public void shouldAnthropicSectionUserTextShorthand() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.system.instruction.shorthand/client",
        "${app}/anthropic.section.system.instruction.shorthand/server"})
    public void shouldAnthropicSectionSystemInstructionShorthand() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.tool.result.shorthand/client",
        "${app}/anthropic.section.tool.result.shorthand/server"})
    public void shouldAnthropicSectionToolResultShorthand() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.user.document.filename.last/client",
        "${app}/openai.section.user.document.filename.last/server"})
    public void shouldOpenaiSectionUserDocumentFilenameLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.user.audio.format.last/client",
        "${app}/openai.section.user.audio.format.last/server"})
    public void shouldOpenaiSectionUserAudioFormatLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.tool.definition.name.last/client",
        "${app}/openai.section.tool.definition.name.last/server"})
    public void shouldOpenaiSectionToolDefinitionNameLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.tool.definition.name.last/client",
        "${app}/anthropic.section.tool.definition.name.last/server"})
    public void shouldAnthropicSectionToolDefinitionNameLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.document.reference/client",
        "${app}/anthropic.section.user.document.reference/server"})
    public void shouldAnthropicSectionUserDocumentReference() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.image.10k/client",
        "${app}/anthropic.section.user.image.10k/server"})
    public void shouldAnthropicSectionUserImage10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.user.image.url.10k/client",
        "${app}/openai.section.user.image.url.10k/server"})
    public void shouldOpenaiSectionUserImageUrl10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.user.image.url.100k/client",
        "${app}/openai.section.user.image.url.100k/server"})
    public void shouldOpenaiSectionUserImageUrl100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.image.url.10k/client",
        "${app}/anthropic.section.user.image.url.10k/server"})
    public void shouldAnthropicSectionUserImageUrl10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.image.url.100k/client",
        "${app}/anthropic.section.user.image.url.100k/server"})
    public void shouldAnthropicSectionUserImageUrl100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.text.streaming/client",
        "${app}/anthropic.section.assistant.text.streaming/server"})
    public void shouldDecodeAnthropicResponseStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.reasoning.streaming/client",
        "${app}/anthropic.section.assistant.reasoning.streaming/server"})
    public void shouldAnthropicSectionAssistantReasoningStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.reasoning.redacted.streaming/client",
        "${app}/anthropic.section.assistant.reasoning.redacted.streaming/server"})
    public void shouldAnthropicSectionAssistantReasoningRedactedStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.rejected.streaming/client",
        "${app}/anthropic.rejected.streaming/server"})
    public void shouldDecodeAnthropicResponseStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.tool.call.streaming/client",
        "${app}/anthropic.section.assistant.tool.call.streaming/server"})
    public void shouldDecodeAnthropicResponseStreamingToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.text.members/client",
        "${app}/anthropic.section.assistant.text.members/server"})
    public void shouldAnthropicResponseDecodeOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.text.100k/client",
        "${app}/anthropic.section.assistant.text.100k/server"})
    public void shouldDecodeAnthropicResponseText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.text.escaped/client",
        "${app}/anthropic.section.assistant.text.escaped/server"})
    public void shouldDecodeAnthropicResponseTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.response.streaming.multibyte/client",
        "${app}/anthropic.transform.response.streaming.multibyte/server"})
    public void shouldEncodeAnthropicResponseStreamingMultibyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.tool.call.100k/client",
        "${app}/anthropic.section.assistant.tool.call.100k/server"})
    public void shouldAnthropicSectionAssistantToolCall100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.tool.call.10k/client",
        "${app}/anthropic.section.assistant.tool.call.10k/server"})
    public void shouldAnthropicSectionAssistantToolCall10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.tool.call.escaped/client",
        "${app}/anthropic.section.assistant.tool.call.escaped/server"})
    public void shouldAnthropicSectionAssistantToolCallEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.tool.call.id.last/client",
        "${app}/anthropic.section.assistant.tool.call.id.last/server"})
    public void shouldAnthropicSectionAssistantToolCallIdLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.tool.call.name.last/client",
        "${app}/anthropic.section.assistant.tool.call.name.last/server"})
    public void shouldAnthropicSectionAssistantToolCallNameLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.tool.definition.100k/client",
        "${app}/anthropic.section.tool.definition.100k/server"})
    public void shouldAnthropicSectionToolDefinition100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.tool.definition.10k/client",
        "${app}/anthropic.section.tool.definition.10k/server"})
    public void shouldAnthropicSectionToolDefinition10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.tool.definition.parameters.100k/client",
        "${app}/anthropic.section.tool.definition.parameters.100k/server"})
    public void shouldAnthropicSectionToolDefinitionParameters100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.tool.definition.parameters.10k/client",
        "${app}/anthropic.section.tool.definition.parameters.10k/server"})
    public void shouldAnthropicSectionToolDefinitionParameters10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.tool.call.100k/client",
        "${app}/openai.section.tool.call.100k/server"})
    public void shouldOpenaiSectionToolCall100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.tool.call.10k/client",
        "${app}/openai.section.tool.call.10k/server"})
    public void shouldOpenaiSectionToolCall10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.tool.call.escaped/client",
        "${app}/openai.section.tool.call.escaped/server"})
    public void shouldOpenaiSectionToolCallEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.tool.call.id.last/client",
        "${app}/openai.section.tool.call.id.last/server"})
    public void shouldOpenaiSectionToolCallIdLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.tool.call.name.last/client",
        "${app}/openai.section.tool.call.name.last/server"})
    public void shouldOpenaiSectionToolCallNameLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.tool.definition.100k/client",
        "${app}/openai.section.tool.definition.100k/server"})
    public void shouldOpenaiSectionToolDefinition100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.tool.definition.10k/client",
        "${app}/openai.section.tool.definition.10k/server"})
    public void shouldOpenaiSectionToolDefinition10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.tool.definition.parameters.100k/client",
        "${app}/openai.section.tool.definition.parameters.100k/server"})
    public void shouldOpenaiSectionToolDefinitionParameters100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.tool.definition.parameters.10k/client",
        "${app}/openai.section.tool.definition.parameters.10k/server"})
    public void shouldOpenaiSectionToolDefinitionParameters10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.model.last/client",
        "${app}/openai.section.model.last/server"})
    public void shouldOpenaiSectionModelLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.model.last/client",
        "${app}/anthropic.section.model.last/server"})
    public void shouldAnthropicSectionModelLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.model.last.system.tools/client",
        "${app}/anthropic.section.model.last.system.tools/server"})
    public void shouldAnthropicSectionModelLastSystemTools() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.model.last.text.10k/client",
        "${app}/anthropic.section.model.last.text.10k/server"})
    public void shouldAnthropicSectionModelLastText10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.assistant.text.logprobs/client",
        "${app}/openai.section.assistant.text.logprobs/server"})
    public void shouldDecodeOpenaiResponseTextLogprobs() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.response.streaming.logprobs.foreign/client",
        "${app}/openai.transform.response.streaming.logprobs.foreign/server"})
    public void shouldEncodeOpenaiResponseStreamingLogprobsForeign() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.message.name/client",
        "${app}/openai.section.message.name/server"})
    public void shouldDecodeOpenaiSectionMessageName() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.message.name.after/client",
        "${app}/openai.section.message.name.after/server"})
    public void shouldDecodeOpenaiSectionMessageNameAfter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.message.name.tool.result/client",
        "${app}/openai.section.message.name.tool.result/server"})
    public void shouldDecodeOpenaiSectionMessageNameToolResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.assistant.text.members.large/client",
        "${app}/openai.section.assistant.text.members.large/server"})
    public void shouldEncodeOpenaiResponseMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.text.members.streaming/client",
        "${app}/anthropic.section.assistant.text.members.streaming/server"})
    public void shouldEncodeAnthropicResponseStreamingMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.assistant.text.members.streaming/client",
        "${app}/openai.section.assistant.text.members.streaming/server"})
    public void shouldEncodeOpenaiResponseStreamingMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.beta/client",
        "${app}/anthropic.request.beta/server"})
    public void shouldForwardAnthropicRequestBeta() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.user.image/client",
        "${app}/transform.user.image/server"})
    public void shouldTransformUserImage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.user.document.reference/client",
        "${app}/transform.user.document.reference/server"})
    public void shouldTransformUserDocumentReference() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.mixed.content/client",
        "${app}/transform.mixed.content/server"})
    public void shouldTransformMixedContent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.conversation/client",
        "${app}/openai.transform.conversation/server"})
    public void shouldCarryAnthropicTransformOpenaiConversation() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.user.image.inline/client",
        "${app}/transform.user.image.inline/server"})
    public void shouldTransformUserImageInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.user.document.inline/client",
        "${app}/transform.user.document.inline/server"})
    public void shouldTransformUserDocumentInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.user.image.10k/client",
        "${app}/transform.user.image.10k/server"})
    public void shouldTransformUserImage10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.user.image.url.10k/client",
        "${app}/transform.user.image.url.10k/server"})
    public void shouldTransformUserImageUrl10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.user.image.url.100k/client",
        "${app}/transform.user.image.url.100k/server"})
    public void shouldTransformUserImageUrl100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.server.tool.result/client",
        "${app}/anthropic.transform.server.tool.result/server"})
    public void shouldCarryOpenaiTransformAnthropicServerToolResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.assistant.text.citations/client",
        "${app}/anthropic.transform.assistant.text.citations/server"})
    public void shouldCarryOpenaiTransformAnthropicAssistantTextCitations() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.assistant.text.citations/client",
        "${app}/openai.transform.assistant.text.citations/server"})
    public void shouldCarryAnthropicTransformOpenaiAssistantTextCitations() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.assistant.text.citations.skipped/client",
        "${app}/anthropic.transform.assistant.text.citations.skipped/server"})
    public void shouldCarryOpenaiTransformAnthropicAssistantTextCitationsSkipped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.streaming.complete/client",
        "${app}/openai.streaming.complete/server"})
    public void shouldCompleteEachOpenaiStreamingEvent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.assistant.reasoning.streaming/client",
        "${app}/openai.section.assistant.reasoning.streaming/server"})
    public void shouldStreamOpenaiSectionAssistantReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.assistant.reasoning.streaming.delta/client",
        "${app}/openai.section.assistant.reasoning.streaming.delta/server"})
    public void shouldStreamOpenaiSectionAssistantReasoningDelta() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.stream.options.absent/client",
        "${app}/openai.stream.options.absent/server"})
    public void shouldRelayOpenaiStreamOptionsAbsent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.stream.options.requested/client",
        "${app}/openai.stream.options.requested/server"})
    public void shouldRelayOpenaiStreamOptionsRequested() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.stream.options.disabled/client",
        "${app}/openai.stream.options.disabled/server"})
    public void shouldRelayOpenaiStreamOptionsDisabled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.stream.options.members/client",
        "${app}/openai.stream.options.members/server"})
    public void shouldRelayOpenaiStreamOptionsMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.stream.options.null/client",
        "${app}/openai.stream.options.null/server"})
    public void shouldRelayOpenaiStreamOptionsNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.streaming.complete/client",
        "${app}/anthropic.streaming.complete/server"})
    public void shouldCompleteEachAnthropicStreamingEvent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.streaming.fragmented/client",
        "${app}/anthropic.streaming.fragmented/server"})
    public void shouldNameAnthropicStreamingEventSplitInsideItsType() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.rejected.user.audio/client",
        "${app}/openai.transform.rejected.user.audio/server"})
    public void shouldAnthropicTransformOpenaiRejectedUserAudio() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.sections/client",
        "${app}/openai.proxy.sections/server"})
    public void shouldOpenaiProxySections() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.user.text/client",
        "${app}/transform.user.text/server"})
    public void shouldTransformUserText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.system.instruction/client",
        "${app}/transform.system.instruction/server"})
    public void shouldTransformSystemInstruction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.system.instruction.last/client",
        "${app}/transform.system.instruction.last/server"})
    public void shouldTransformSystemInstructionLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.system.instruction.last.100k/client",
        "${app}/transform.system.instruction.last.100k/server"})
    public void shouldTransformSystemInstructionLast100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.system.instruction.after.tool.definition/client",
        "${app}/transform.rejected.system.instruction.after.tool.definition/server"})
    public void shouldTransformRejectedSystemInstructionAfterToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.system.instruction.last/client",
        "${app}/anthropic.transform.system.instruction.last/server"})
    public void shouldAnthropicTransformSystemInstructionLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.assistant.text/client",
        "${app}/transform.assistant.text/server"})
    public void shouldTransformAssistantText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.tool.definition/client",
        "${app}/transform.tool.definition/server"})
    public void shouldTransformToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.tool.definition.cache.control/client",
        "${app}/anthropic.transform.tool.definition.cache.control/server"})
    public void shouldAnthropicTransformToolDefinitionCacheControl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.tool.definition.100k/client",
        "${app}/transform.tool.definition.100k/server"})
    public void shouldTransformToolDefinition100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.tool.definition.10k/client",
        "${app}/transform.tool.definition.10k/server"})
    public void shouldTransformToolDefinition10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.tool.definition.name.last/client",
        "${app}/transform.tool.definition.name.last/server"})
    public void shouldTransformToolDefinitionNameLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.tool.definition.parameters.100k/client",
        "${app}/transform.tool.definition.parameters.100k/server"})
    public void shouldTransformToolDefinitionParameters100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.tool.definition.parameters.10k/client",
        "${app}/transform.tool.definition.parameters.10k/server"})
    public void shouldTransformToolDefinitionParameters10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.tool.definition.name/client",
        "${app}/transform.rejected.tool.definition.name/server"})
    public void shouldRejectToolDefinitionName() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.assistant.tool.call/client",
        "${app}/transform.assistant.tool.call/server"})
    public void shouldTransformAssistantToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.assistant.tool.call.100k/client",
        "${app}/transform.assistant.tool.call.100k/server"})
    public void shouldTransformAssistantToolCall100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.assistant.tool.call.10k/client",
        "${app}/transform.assistant.tool.call.10k/server"})
    public void shouldTransformAssistantToolCall10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.assistant.tool.call.escaped/client",
        "${app}/transform.assistant.tool.call.escaped/server"})
    public void shouldTransformAssistantToolCallEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.assistant.tool.call.id.last/client",
        "${app}/transform.assistant.tool.call.id.last/server"})
    public void shouldTransformAssistantToolCallIdLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.assistant.tool.call.name.last/client",
        "${app}/transform.assistant.tool.call.name.last/server"})
    public void shouldTransformAssistantToolCallNameLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.assistant.text.and.tool.call/client",
        "${app}/transform.assistant.text.and.tool.call/server"})
    public void shouldTransformAssistantTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.tool.call.arguments/client",
        "${app}/transform.rejected.tool.call.arguments/server"})
    public void shouldRejectToolCallArguments() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.tool.call.name/client",
        "${app}/transform.rejected.tool.call.name/server"})
    public void shouldRejectToolCallName() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.tool.result/client",
        "${app}/transform.tool.result/server"})
    public void shouldTransformToolResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.assistant.reasoning/client",
        "${app}/transform.assistant.reasoning/server"})
    public void shouldTransformAssistantReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.assistant.refusal/client",
        "${app}/transform.assistant.refusal/server"})
    public void shouldTransformAssistantRefusal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.tool.result.blocks/client",
        "${app}/transform.tool.result.blocks/server"})
    public void shouldTransformToolResultBlocks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.tool.result.error/client",
        "${app}/transform.tool.result.error/server"})
    public void shouldTransformToolResultError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.frames/client",
        "${app}/transform.request.frames/server"})
    public void shouldTransformRequestFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.frames/client",
        "${app}/transform.response.frames/server"})
    public void shouldTransformResponseFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.fallback/client",
        "${app}/openai.proxy.fallback/server"})
    public void shouldTellCallerTheFallbackModelOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.fallback.exhausted/client",
        "${app}/openai.proxy.fallback.exhausted/server"})
    public void shouldAbortCallerWhenFallbackExhaustedOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.fallback.refused/client",
        "${app}/openai.proxy.fallback.refused/server"})
    public void shouldRefuseOpenaiProxyFallbackAttempt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.fallback.accepted/client",
        "${app}/openai.proxy.fallback.accepted/server"})
    public void shouldAcceptOpenaiProxyFallbackAttempt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.attributes/client",
        "${app}/openai.proxy.attributes/server"})
    public void shouldRouteOpenaiWithAttributes() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.fallback.attributes/client",
        "${app}/openai.proxy.fallback.attributes/server"})
    public void shouldTellCallerTheFallbackModelOpenaiWithAttributes() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.fallback.attributes.accepted/client",
        "${app}/openai.proxy.fallback.attributes.accepted/server"})
    public void shouldAcceptOpenaiProxyFallbackAttemptWithAttributes() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.rejected.unreachable/client",
        "${app}/openai.rejected.unreachable/server"})
    public void shouldRejectOpenaiUnreachable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.rejected.unreachable/client",
        "${app}/anthropic.rejected.unreachable/server"})
    public void shouldRejectAnthropicUnreachable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.user.video/client",
        "${app}/transform.rejected.user.video/server"})
    public void shouldTransformRejectedUserVideo() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.text/client",
        "${app}/transform.response.text/server"})
    public void shouldTransformResponseText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.text.escaped/client",
        "${app}/transform.response.text.escaped/server"})
    public void shouldTransformResponseTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.text.100k/client",
        "${app}/transform.response.text.100k/server"})
    public void shouldTransformResponseText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.text.multibyte/client",
        "${app}/transform.response.text.multibyte/server"})
    public void shouldTransformResponseTextMultibyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.text.and.tool.call/client",
        "${app}/transform.response.text.and.tool.call/server"})
    public void shouldTransformResponseTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.usage/client",
        "${app}/transform.response.usage/server"})
    public void shouldTransformResponseUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.reasoning/client",
        "${app}/transform.response.reasoning/server"})
    public void shouldTransformResponseReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.reasoning.tag/client",
        "${app}/transform.response.reasoning.tag/server"})
    public void shouldTransformResponseReasoningTag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.reasoning.tag.fragmented/client",
        "${app}/transform.response.reasoning.tag.fragmented/server"})
    public void shouldTransformResponseReasoningTagFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.reasoning.tag.multiple/client",
        "${app}/transform.response.reasoning.tag.multiple/server"})
    public void shouldTransformResponseReasoningTagMultiple() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.reasoning.tag.unclosed/client",
        "${app}/transform.response.reasoning.tag.unclosed/server"})
    public void shouldTransformResponseReasoningTagUnclosed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.reasoning.tag.absent/client",
        "${app}/transform.response.reasoning.tag.absent/server"})
    public void shouldTransformResponseReasoningTagAbsent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.assistant.text.reasoning/client",
        "${app}/openai.section.assistant.text.reasoning/server"})
    public void shouldDecodeOpenaiSectionAssistantTextReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.rejected.reasoning/client",
        "${app}/openai.proxy.guardrail.response.rejected.reasoning/server"})
    public void shouldRejectGuardedResponseSectionAcrossReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.rejected.reasoning.exit/client",
        "${app}/openai.proxy.guardrail.response.rejected.reasoning.exit/server"})
    public void shouldRejectGuardedResponseSectionAcrossReasoningExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.refusal/client",
        "${app}/transform.response.refusal/server"})
    public void shouldTransformResponseRefusal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.streaming/client",
        "${app}/transform.response.streaming/server"})
    public void shouldTransformResponseStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.streaming.tool.call/client",
        "${app}/transform.response.streaming.tool.call/server"})
    public void shouldTransformResponseStreamingToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.streaming.error/client",
        "${app}/transform.response.streaming.error/server"})
    public void shouldTransformResponseStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.max.output.tokens/client",
        "${app}/transform.request.max.output.tokens/server"})
    public void shouldTransformRequestMaxOutputTokens() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.rejected.error.type/client",
        "${app}/anthropic.rejected.error.type/server"})
    public void shouldPreserveAnthropicRejectedErrorType() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.rejected.error.code/client",
        "${app}/openai.rejected.error.code/server"})
    public void shouldPreserveOpenaiRejectedErrorCode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.rejected.streaming.error.code/client",
        "${app}/openai.rejected.streaming.error.code/server"})
    public void shouldPreserveOpenaiRejectedStreamingErrorCode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.content.type.charset/client",
        "${app}/openai.content.type.charset/server"})
    public void shouldAcceptOpenaiRequestContentTypeWithParameters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.tool.call.cache.control/client",
        "${app}/anthropic.section.assistant.tool.call.cache.control/server"})
    public void shouldAnthropicSectionAssistantToolCallCacheControl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.assistant.tool.call.cache.control/client",
        "${app}/anthropic.transform.assistant.tool.call.cache.control/server"})
    public void shouldAnthropicTransformAssistantToolCallCacheControl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.tool.call.members/client",
        "${app}/openai.section.tool.call.members/server"})
    public void shouldOpenaiSectionToolCallMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.assistant.tool.call.members/client",
        "${app}/openai.transform.assistant.tool.call.members/server"})
    public void shouldOpenaiTransformAssistantToolCallMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.assistant.tool.call.streaming.error/client",
        "${app}/openai.section.assistant.tool.call.streaming.error/server"})
    public void shouldOpenaiSectionAssistantToolCallStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.tool.call.streaming.error/client",
        "${app}/anthropic.section.assistant.tool.call.streaming.error/server"})
    public void shouldAnthropicSectionAssistantToolCallStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.tool.call/client",
        "${app}/transform.response.tool.call/server"})
    public void shouldTransformResponseToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.tool.calls/client",
        "${app}/transform.response.tool.calls/server"})
    public void shouldTransformResponseToolCalls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.text.error/client",
        "${app}/transform.response.text.error/server"})
    public void shouldTransformResponseTextError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.tool.call.error/client",
        "${app}/transform.response.tool.call.error/server"})
    public void shouldTransformResponseToolCallError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.tool.call.empty/client",
        "${app}/transform.response.tool.call.empty/server"})
    public void shouldTransformResponseToolCallEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.tool.calls.overflow/client",
        "${app}/transform.response.tool.calls.overflow/server"})
    public void shouldTransformResponseToolCallsOverflow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.accepted/client",
        "${app}/openai.proxy.guardrail.response.accepted/server"})
    public void shouldGuardOpenaiResponseAcceptedNet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.accepted.exit/client",
        "${app}/openai.proxy.guardrail.response.accepted.exit/server"})
    public void shouldGuardOpenaiResponseAcceptedExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.transformed/client",
        "${app}/openai.proxy.guardrail.response.transformed/server"})
    public void shouldGuardOpenaiResponseTransformedNet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.transformed.exit/client",
        "${app}/openai.proxy.guardrail.response.transformed.exit/server"})
    public void shouldGuardOpenaiResponseTransformedExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.accepted.reasoning/client",
        "${app}/openai.proxy.guardrail.response.accepted.reasoning/server"})
    public void shouldGuardOpenaiResponseAcceptedReasoningNet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.accepted.reasoning.exit/client",
        "${app}/openai.proxy.guardrail.response.accepted.reasoning.exit/server"})
    public void shouldGuardOpenaiResponseAcceptedReasoningExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.transformed.reasoning/client",
        "${app}/openai.proxy.guardrail.response.transformed.reasoning/server"})
    public void shouldGuardOpenaiResponseTransformedReasoningNet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.transformed.reasoning.exit/client",
        "${app}/openai.proxy.guardrail.response.transformed.reasoning.exit/server"})
    public void shouldGuardOpenaiResponseTransformedReasoningExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.transformed.tool.call/client",
        "${app}/openai.proxy.guardrail.response.transformed.tool.call/server"})
    public void shouldGuardOpenaiResponseTransformedToolCallNet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.transformed.tool.call.exit/client",
        "${app}/openai.proxy.guardrail.response.transformed.tool.call.exit/server"})
    public void shouldGuardOpenaiResponseTransformedToolCallExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.transformed.tool.call.member/client",
        "${app}/openai.proxy.guardrail.response.transformed.tool.call.member/server"})
    public void shouldGuardOpenaiResponseTransformedToolCallMemberNet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.transformed.tool.call.member.exit/client",
        "${app}/openai.proxy.guardrail.response.transformed.tool.call.member.exit/server"})
    public void shouldGuardOpenaiResponseTransformedToolCallMemberExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.transform.consecutive/client",
        "${app}/openai.proxy.guardrail.transform.consecutive/server"})
    public void shouldGuardOpenaiTransformConsecutiveNet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.transform.consecutive.exit/client",
        "${app}/openai.proxy.guardrail.transform.consecutive.exit/server"})
    public void shouldGuardOpenaiTransformConsecutiveExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.transform.tool.call/client",
        "${app}/openai.proxy.guardrail.transform.tool.call/server"})
    public void shouldGuardOpenaiTransformToolCallNet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.transform.tool.call.exit/client",
        "${app}/openai.proxy.guardrail.transform.tool.call.exit/server"})
    public void shouldGuardOpenaiTransformToolCallExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.accepted.streaming.exit/client",
        "${app}/openai.proxy.guardrail.response.accepted.streaming.exit/server"})
    public void shouldGuardOpenaiResponseAcceptedStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.classified.streaming.exit/client",
        "${app}/openai.proxy.guardrail.response.classified.streaming.exit/server"})
    public void shouldGuardOpenaiResponseClassifiedStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.classified.paused.exit/client",
        "${app}/openai.proxy.guardrail.response.classified.paused.exit/server"})
    public void shouldGuardOpenaiResponseClassifiedPausedExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.classified.tool.call.paused.exit/client",
        "${app}/openai.proxy.guardrail.response.classified.tool.call.paused.exit/server"})
    public void shouldGuardOpenaiResponseClassifiedToolCallPausedExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.rejected.streaming.exit/client",
        "${app}/openai.proxy.guardrail.response.rejected.streaming.exit/server"})
    public void shouldGuardOpenaiResponseRejectedStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.transformed.streaming.exit/client",
        "${app}/openai.proxy.guardrail.response.transformed.streaming.exit/server"})
    public void shouldGuardOpenaiResponseTransformedStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.accepted.reasoning.streaming.exit/client",
        "${app}/openai.proxy.guardrail.response.accepted.reasoning.streaming.exit/server"})
    public void shouldGuardOpenaiResponseAcceptedReasoningStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.rejected.reasoning.streaming.exit/client",
        "${app}/openai.proxy.guardrail.response.rejected.reasoning.streaming.exit/server"})
    public void shouldGuardOpenaiResponseRejectedReasoningStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.transformed.reasoning.streaming.exit/client",
        "${app}/openai.proxy.guardrail.response.transformed.reasoning.streaming.exit/server"})
    public void shouldGuardOpenaiResponseTransformedReasoningStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.accepted.tool.call.streaming.exit/client",
        "${app}/openai.proxy.guardrail.response.accepted.tool.call.streaming.exit/server"})
    public void shouldGuardOpenaiResponseAcceptedToolCallStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.rejected.tool.call.streaming.exit/client",
        "${app}/openai.proxy.guardrail.response.rejected.tool.call.streaming.exit/server"})
    public void shouldGuardOpenaiResponseRejectedToolCallStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.proxy.guardrail.response.transformed.tool.call.streaming.exit/client",
        "${app}/openai.proxy.guardrail.response.transformed.tool.call.streaming.exit/server"})
    public void shouldGuardOpenaiResponseTransformedToolCallStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.section.assistant.text.flush.ignored/client",
        "${app}/openai.section.assistant.text.flush.ignored/server"})
    public void shouldOpenaiSectionAssistantTextFlushIgnored() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.assistant.text.flush.ignored/client",
        "${app}/anthropic.section.assistant.text.flush.ignored/server"})
    public void shouldAnthropicSectionAssistantTextFlushIgnored() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.request.members/client",
        "${app}/openai.transform.request.members/server"})
    public void shouldOpenaiTransformRequestMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.response.streaming/client",
        "${app}/openai.transform.response.streaming/server"})
    public void shouldOpenaiTransformResponseStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.response.streaming.tool.call/client",
        "${app}/openai.transform.response.streaming.tool.call/server"})
    public void shouldOpenaiTransformResponseStreamingToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.response.streaming.error/client",
        "${app}/openai.transform.response.streaming.error/server"})
    public void shouldOpenaiTransformResponseStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.response.streaming.reasoning/client",
        "${app}/openai.transform.response.streaming.reasoning/server"})
    public void shouldOpenaiTransformResponseStreamingReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.response.streaming.reasoning.tag/client",
        "${app}/openai.transform.response.streaming.reasoning.tag/server"})
    public void shouldOpenaiTransformResponseStreamingReasoningTag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.response.streaming.reasoning.tag.fragmented/client",
        "${app}/openai.transform.response.streaming.reasoning.tag.fragmented/server"})
    public void shouldOpenaiTransformResponseStreamingReasoningTagFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.response.text.100k/client",
        "${app}/openai.transform.response.text.100k/server"})
    public void shouldOpenaiTransformResponseText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.response.refusal/client",
        "${app}/openai.transform.response.refusal/server"})
    public void shouldOpenaiTransformResponseRefusal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.response.reasoning/client",
        "${app}/openai.transform.response.reasoning/server"})
    public void shouldOpenaiTransformResponseReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.response.text.and.tool.call/client",
        "${app}/openai.transform.response.text.and.tool.call/server"})
    public void shouldOpenaiTransformResponseTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.response.tool.calls/client",
        "${app}/openai.transform.response.tool.calls/server"})
    public void shouldOpenaiTransformResponseToolCalls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.response.text/client",
        "${app}/openai.transform.response.text/server"})
    public void shouldOpenaiTransformResponseText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.response.tool.call.empty/client",
        "${app}/openai.transform.response.tool.call.empty/server"})
    public void shouldOpenaiTransformResponseToolCallEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.response.usage/client",
        "${app}/openai.transform.response.usage/server"})
    public void shouldOpenaiTransformResponseUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.response.tool.call/client",
        "${app}/openai.transform.response.tool.call/server"})
    public void shouldOpenaiTransformResponseToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.response.text.escaped/client",
        "${app}/openai.transform.response.text.escaped/server"})
    public void shouldOpenaiTransformResponseTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.response.frames/client",
        "${app}/openai.transform.response.frames/server"})
    public void shouldOpenaiTransformResponseFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.response.streaming.tool.call/client",
        "${app}/anthropic.transform.response.streaming.tool.call/server"})
    public void shouldAnthropicTransformResponseStreamingToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.response.streaming.error/client",
        "${app}/anthropic.transform.response.streaming.error/server"})
    public void shouldAnthropicTransformResponseStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.response.streaming.reasoning/client",
        "${app}/anthropic.transform.response.streaming.reasoning/server"})
    public void shouldAnthropicTransformResponseStreamingReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.response.streaming.reasoning.tag/client",
        "${app}/anthropic.transform.response.streaming.reasoning.tag/server"})
    public void shouldAnthropicTransformResponseStreamingReasoningTag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.response.streaming.reasoning.tag.fragmented/client",
        "${app}/anthropic.transform.response.streaming.reasoning.tag.fragmented/server"})
    public void shouldAnthropicTransformResponseStreamingReasoningTagFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.request.members/client",
        "${app}/anthropic.transform.request.members/server"})
    public void shouldAnthropicTransformRequestMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.response.streaming/client",
        "${app}/anthropic.transform.response.streaming/server"})
    public void shouldAnthropicTransformResponseStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.response.frames/client",
        "${app}/anthropic.transform.response.frames/server"})
    public void shouldAnthropicTransformResponseFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.response.reasoning/client",
        "${app}/anthropic.transform.response.reasoning/server"})
    public void shouldAnthropicTransformResponseReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.response.text/client",
        "${app}/anthropic.transform.response.text/server"})
    public void shouldAnthropicTransformResponseText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.response.text.100k/client",
        "${app}/anthropic.transform.response.text.100k/server"})
    public void shouldAnthropicTransformResponseText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.response.text.and.tool.call/client",
        "${app}/anthropic.transform.response.text.and.tool.call/server"})
    public void shouldAnthropicTransformResponseTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.response.text.escaped/client",
        "${app}/anthropic.transform.response.text.escaped/server"})
    public void shouldAnthropicTransformResponseTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.response.tool.call/client",
        "${app}/anthropic.transform.response.tool.call/server"})
    public void shouldAnthropicTransformResponseToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.response.tool.call.empty/client",
        "${app}/anthropic.transform.response.tool.call.empty/server"})
    public void shouldAnthropicTransformResponseToolCallEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.response.tool.calls/client",
        "${app}/anthropic.transform.response.tool.calls/server"})
    public void shouldAnthropicTransformResponseToolCalls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.extension/client",
        "${app}/transform.request.extension/server"})
    public void shouldTransformRequestExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini/client",
        "${app}/gemini/server"})
    public void shouldForwardGeminiNonstreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.system.instruction/client",
        "${app}/gemini.section.system.instruction/server"})
    public void shouldForwardGeminiSectionSystemInstruction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.system.instruction.last/client",
        "${app}/gemini.section.system.instruction.last/server"})
    public void shouldForwardGeminiSectionSystemInstructionLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.system.instruction.parts/client",
        "${app}/gemini.section.system.instruction.parts/server"})
    public void shouldForwardGeminiSectionSystemInstructionParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.conversation/client",
        "${app}/gemini.section.conversation/server"})
    public void shouldForwardGeminiSectionConversation() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.text.parts/client",
        "${app}/gemini.section.user.text.parts/server"})
    public void shouldForwardGeminiSectionUserTextParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.text.multibyte/client",
        "${app}/gemini.section.user.text.multibyte/server"})
    public void shouldForwardGeminiSectionUserTextMultibyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.text.escaped/client",
        "${app}/gemini.section.user.text.escaped/server"})
    public void shouldForwardGeminiSectionUserTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.text.empty/client",
        "${app}/gemini.section.user.text.empty/server"})
    public void shouldForwardGeminiSectionUserTextEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.assistant.text.multibyte/client",
        "${app}/gemini.section.assistant.text.multibyte/server"})
    public void shouldForwardGeminiSectionAssistantTextMultibyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.assistant.text.escaped/client",
        "${app}/gemini.section.assistant.text.escaped/server"})
    public void shouldForwardGeminiSectionAssistantTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.text.split/client",
        "${app}/gemini.section.user.text.split/server"})
    public void shouldForwardGeminiSectionUserTextSplit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.options/client",
        "${app}/gemini.request.options/server"})
    public void shouldForwardGeminiRequestOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.options.leading/client",
        "${app}/gemini.request.options.leading/server"})
    public void shouldForwardGeminiRequestOptionsLeading() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.options.verbatim/client",
        "${app}/gemini.request.options.verbatim/server"})
    public void shouldForwardGeminiRequestOptionsVerbatim() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.authorized/client",
        "${app}/gemini.authorized/server"})
    public void shouldForwardGeminiAuthorized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.rejected.rate.limit/client",
        "${app}/gemini.rejected.rate.limit/server"})
    public void shouldForwardGeminiRejectedRateLimit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.rejected.model.not.found/client",
        "${app}/gemini.rejected.model.not.found/server"})
    public void shouldForwardGeminiRejectedModelNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.usage/client",
        "${app}/gemini.usage/server"})
    public void shouldForwardGeminiUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.usage.cached/client",
        "${app}/gemini.usage.cached/server"})
    public void shouldForwardGeminiUsageCached() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.assistant.text.members/client",
        "${app}/gemini.section.assistant.text.members/server"})
    public void shouldForwardGeminiSectionAssistantTextMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.assistant.text.empty/client",
        "${app}/gemini.section.assistant.text.empty/server"})
    public void shouldForwardGeminiSectionAssistantTextEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.streaming.complete/client",
        "${app}/gemini.streaming.complete/server"})
    public void shouldForwardGeminiStreamingComplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.streaming.array/client",
        "${app}/gemini.streaming.array/server"})
    public void shouldForwardGeminiStreamingArray() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.streaming.usage/client",
        "${app}/gemini.streaming.usage/server"})
    public void shouldForwardGeminiStreamingUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.response.text/client",
        "${app}/gemini.response.text/server"})
    public void shouldForwardGeminiResponseText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.request.frames/client",
        "${app}/gemini.transform.request.frames/server"})
    public void shouldForwardGeminiTransformRequestFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.system.instruction/client",
        "${app}/gemini.transform.system.instruction/server"})
    public void shouldForwardGeminiTransformSystemInstruction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.assistant.text/client",
        "${app}/gemini.transform.assistant.text/server"})
    public void shouldForwardGeminiTransformAssistantText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.request.max.output.tokens/client",
        "${app}/gemini.transform.request.max.output.tokens/server"})
    public void shouldForwardGeminiTransformRequestMaxOutputTokens() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.role.absent/client",
        "${app}/gemini.section.role.absent/server"})
    public void shouldForwardGeminiSectionRoleAbsent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.role.after.parts/client",
        "${app}/gemini.section.role.after.parts/server"})
    public void shouldForwardGeminiSectionRoleAfterParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.role.model.after.parts/client",
        "${app}/gemini.section.role.model.after.parts/server"})
    public void shouldForwardGeminiSectionRoleModelAfterParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.model.colon/client",
        "${app}/gemini.model.colon/server"})
    public void shouldForwardGeminiModelColon() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.role.model.after.parts.split/client",
        "${app}/gemini.section.role.model.after.parts.split/server"})
    public void shouldForwardGeminiSectionRoleModelAfterPartsSplit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.role.after.parts.text.image.100k/client",
        "${app}/gemini.section.role.after.parts.text.image.100k/server"})
    public void shouldForwardGeminiSectionRoleAfterPartsTextImage100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.model.colon.encoded/client",
        "${app}/gemini.model.colon.encoded/server"})
    public void shouldForwardGeminiModelColonEncoded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.10k/client",
        "${app}/gemini.10k/server"})
    public void shouldForwardGemini10K() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.100k/client",
        "${app}/gemini.100k/server"})
    public void shouldForwardGemini100K() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.rejected.streaming/client",
        "${app}/gemini.rejected.streaming/server"})
    public void shouldForwardGeminiRejectedStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.streaming.error/client",
        "${app}/gemini.streaming.error/server"})
    public void shouldForwardGeminiStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.response.streaming/client",
        "${app}/gemini.transform.response.streaming/server"})
    public void shouldForwardGeminiTransformResponseStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.tool.definition/client",
        "${app}/gemini.section.tool.definition/server"})
    public void shouldForwardGeminiSectionToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.tool.definition.leading/client",
        "${app}/gemini.section.tool.definition.leading/server"})
    public void shouldForwardGeminiSectionToolDefinitionLeading() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.tool.definition.multiple/client",
        "${app}/gemini.section.tool.definition.multiple/server"})
    public void shouldForwardGeminiSectionToolDefinitionMultiple() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.tool.definition.tools/client",
        "${app}/gemini.section.tool.definition.tools/server"})
    public void shouldForwardGeminiSectionToolDefinitionTools() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.tool.definition.parameters/client",
        "${app}/gemini.section.tool.definition.parameters/server"})
    public void shouldForwardGeminiSectionToolDefinitionParameters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.tool.definition.minimal/client",
        "${app}/gemini.section.tool.definition.minimal/server"})
    public void shouldForwardGeminiSectionToolDefinitionMinimal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.tool.definition.name.last/client",
        "${app}/gemini.section.tool.definition.name.last/server"})
    public void shouldForwardGeminiSectionToolDefinitionNameLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.tool.definition.parameters.10k/client",
        "${app}/gemini.section.tool.definition.parameters.10k/server"})
    public void shouldForwardGeminiSectionToolDefinitionParameters10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.tool.definition.parameters.100k/client",
        "${app}/gemini.section.tool.definition.parameters.100k/server"})
    public void shouldForwardGeminiSectionToolDefinitionParameters100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.assistant.tool.call/client",
        "${app}/gemini.section.assistant.tool.call/server"})
    public void shouldForwardGeminiSectionAssistantToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.assistant.text.and.tool.call/client",
        "${app}/gemini.section.assistant.text.and.tool.call/server"})
    public void shouldForwardGeminiSectionAssistantTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.assistant.tool.calls/client",
        "${app}/gemini.section.assistant.tool.calls/server"})
    public void shouldForwardGeminiSectionAssistantToolCalls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.assistant.tool.call.empty/client",
        "${app}/gemini.section.assistant.tool.call.empty/server"})
    public void shouldForwardGeminiSectionAssistantToolCallEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.assistant.tool.call.signature/client",
        "${app}/gemini.section.assistant.tool.call.signature/server"})
    public void shouldForwardGeminiSectionAssistantToolCallSignature() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.tool.call.history/client",
        "${app}/gemini.section.tool.call.history/server"})
    public void shouldForwardGeminiSectionToolCallHistory() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.tool.call.history.synthesized/client",
        "${app}/gemini.section.tool.call.history.synthesized/server"})
    public void shouldForwardGeminiSectionToolCallHistorySynthesized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.streaming.tool.call/client",
        "${app}/gemini.streaming.tool.call/server"})
    public void shouldForwardGeminiStreamingToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.streaming.tool.call.fragmented/client",
        "${app}/gemini.streaming.tool.call.fragmented/server"})
    public void shouldForwardGeminiStreamingToolCallFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.assistant.tool.call/client",
        "${app}/gemini.transform.assistant.tool.call/server"})
    public void shouldForwardGeminiTransformAssistantToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.assistant.text.and.tool.call/client",
        "${app}/gemini.transform.assistant.text.and.tool.call/server"})
    public void shouldForwardGeminiTransformAssistantTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.tool.result/client",
        "${app}/gemini.section.tool.result/server"})
    public void shouldForwardGeminiSectionToolResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.tool.result.error/client",
        "${app}/gemini.section.tool.result.error/server"})
    public void shouldForwardGeminiSectionToolResultError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.tool.result.escaped/client",
        "${app}/gemini.section.tool.result.escaped/server"})
    public void shouldForwardGeminiSectionToolResultEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.tool.results/client",
        "${app}/gemini.section.tool.results/server"})
    public void shouldForwardGeminiSectionToolResults() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.tool.result.object/client",
        "${app}/gemini.section.tool.result.object/server"})
    public void shouldForwardGeminiSectionToolResultObject() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.tool.result.paired/client",
        "${app}/gemini.section.tool.result.paired/server"})
    public void shouldForwardGeminiSectionToolResultPaired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.tool.result/client",
        "${app}/gemini.transform.tool.result/server"})
    public void shouldForwardGeminiTransformToolResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.tool.result.error/client",
        "${app}/gemini.transform.tool.result.error/server"})
    public void shouldForwardGeminiTransformToolResultError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.image.inline/client",
        "${app}/gemini.section.user.image.inline/server"})
    public void shouldForwardGeminiSectionUserImageInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.image.inline.data.first/client",
        "${app}/gemini.section.user.image.inline.data.first/server"})
    public void shouldForwardGeminiSectionUserImageInlineDataFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.image.inline.snake/client",
        "${app}/gemini.section.user.image.inline.snake/server"})
    public void shouldForwardGeminiSectionUserImageInlineSnake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.image.inline.text/client",
        "${app}/gemini.section.user.image.inline.text/server"})
    public void shouldForwardGeminiSectionUserImageInlineText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.image.inline.text.first/client",
        "${app}/gemini.section.user.image.inline.text.first/server"})
    public void shouldForwardGeminiSectionUserImageInlineTextFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.image.url/client",
        "${app}/gemini.section.user.image.url/server"})
    public void shouldForwardGeminiSectionUserImageUrl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.image.url.first/client",
        "${app}/gemini.section.user.image.url.first/server"})
    public void shouldForwardGeminiSectionUserImageUrlFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.image.reference/client",
        "${app}/gemini.section.user.image.reference/server"})
    public void shouldForwardGeminiSectionUserImageReference() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.image.reference.storage/client",
        "${app}/gemini.section.user.image.reference.storage/server"})
    public void shouldForwardGeminiSectionUserImageReferenceStorage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.document.inline/client",
        "${app}/gemini.section.user.document.inline/server"})
    public void shouldForwardGeminiSectionUserDocumentInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.document.url/client",
        "${app}/gemini.section.user.document.url/server"})
    public void shouldForwardGeminiSectionUserDocumentUrl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.document.reference/client",
        "${app}/gemini.section.user.document.reference/server"})
    public void shouldForwardGeminiSectionUserDocumentReference() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.document.text/client",
        "${app}/gemini.section.user.document.text/server"})
    public void shouldForwardGeminiSectionUserDocumentText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.audio.inline/client",
        "${app}/gemini.section.user.audio.inline/server"})
    public void shouldForwardGeminiSectionUserAudioInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.audio.url/client",
        "${app}/gemini.section.user.audio.url/server"})
    public void shouldForwardGeminiSectionUserAudioUrl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.audio.mpeg/client",
        "${app}/gemini.section.user.audio.mpeg/server"})
    public void shouldForwardGeminiSectionUserAudioMpeg() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.video.inline/client",
        "${app}/gemini.section.user.video.inline/server"})
    public void shouldForwardGeminiSectionUserVideoInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.video.url/client",
        "${app}/gemini.section.user.video.url/server"})
    public void shouldForwardGeminiSectionUserVideoUrl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.video.reference/client",
        "${app}/gemini.section.user.video.reference/server"})
    public void shouldForwardGeminiSectionUserVideoReference() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.video.metadata/client",
        "${app}/gemini.section.user.video.metadata/server"})
    public void shouldForwardGeminiSectionUserVideoMetadata() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.image.display.name/client",
        "${app}/gemini.section.user.image.display.name/server"})
    public void shouldForwardGeminiSectionUserImageDisplayName() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.image.inline.10k/client",
        "${app}/gemini.section.user.image.inline.10k/server"})
    public void shouldForwardGeminiUserImageInline10K() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.image.inline.100k/client",
        "${app}/gemini.section.user.image.inline.100k/server"})
    public void shouldForwardGeminiUserImageInline100K() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.user.image.inline/client",
        "${app}/gemini.transform.user.image.inline/server"})
    public void shouldForwardGeminiTransformUserImageInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.user.image.url/client",
        "${app}/gemini.transform.user.image.url/server"})
    public void shouldForwardGeminiTransformUserImageUrl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.user.image.url.untyped/client",
        "${app}/gemini.transform.user.image.url.untyped/server"})
    public void shouldForwardGeminiTransformUserImageUrlUntyped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.user.image.reference/client",
        "${app}/gemini.transform.user.image.reference/server"})
    public void shouldForwardGeminiTransformUserImageReference() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.user.image.detail/client",
        "${app}/gemini.transform.user.image.detail/server"})
    public void shouldForwardGeminiTransformUserImageDetail() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.user.document.inline/client",
        "${app}/gemini.transform.user.document.inline/server"})
    public void shouldForwardGeminiTransformUserDocumentInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.user.document.filename/client",
        "${app}/gemini.transform.user.document.filename/server"})
    public void shouldForwardGeminiTransformUserDocumentFilename() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.user.document.reference/client",
        "${app}/gemini.transform.user.document.reference/server"})
    public void shouldForwardGeminiTransformUserDocumentReference() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.user.audio.inline/client",
        "${app}/gemini.transform.user.audio.inline/server"})
    public void shouldForwardGeminiTransformUserAudioInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.user.video.url/client",
        "${app}/gemini.transform.user.video.url/server"})
    public void shouldForwardGeminiTransformUserVideoUrl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.assistant.reasoning/client",
        "${app}/gemini.section.assistant.reasoning/server"})
    public void shouldForwardGeminiSectionAssistantReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.assistant.reasoning.signature/client",
        "${app}/gemini.section.assistant.reasoning.signature/server"})
    public void shouldForwardGeminiSectionAssistantReasoningSignature() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.assistant.reasoning.thought.first/client",
        "${app}/gemini.section.assistant.reasoning.thought.first/server"})
    public void shouldForwardGeminiSectionAssistantReasoningThoughtFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.assistant.text.signature/client",
        "${app}/gemini.section.assistant.text.signature/server"})
    public void shouldForwardGeminiSectionAssistantTextSignature() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.assistant.reasoning.tool.call/client",
        "${app}/gemini.section.assistant.reasoning.tool.call/server"})
    public void shouldForwardGeminiSectionAssistantReasoningToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.streaming.assistant.reasoning/client",
        "${app}/gemini.streaming.assistant.reasoning/server"})
    public void shouldForwardGeminiStreamingAssistantReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.streaming.assistant.reasoning.split/client",
        "${app}/gemini.streaming.assistant.reasoning.split/server"})
    public void shouldForwardGeminiStreamingAssistantReasoningSplit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.reasoning.history/client",
        "${app}/gemini.section.reasoning.history/server"})
    public void shouldForwardGeminiSectionReasoningHistory() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.reasoning.history.signature/client",
        "${app}/gemini.section.reasoning.history.signature/server"})
    public void shouldForwardGeminiSectionReasoningHistorySignature() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.reasoning.history.thought.first/client",
        "${app}/gemini.section.reasoning.history.thought.first/server"})
    public void shouldForwardGeminiSectionReasoningHistoryThoughtFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.reasoning.history.tool.call/client",
        "${app}/gemini.section.reasoning.history.tool.call/server"})
    public void shouldForwardGeminiSectionReasoningHistoryToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.user.text.thought/client",
        "${app}/gemini.section.user.text.thought/server"})
    public void shouldForwardGeminiSectionUserTextThought() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.assistant.reasoning/client",
        "${app}/gemini.transform.assistant.reasoning/server"})
    public void shouldForwardGeminiTransformAssistantReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.assistant.text.signature/client",
        "${app}/gemini.transform.assistant.text.signature/server"})
    public void shouldForwardGeminiTransformAssistantTextSignature() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.server.tool.definition/client",
        "${app}/gemini.section.server.tool.definition/server"})
    public void shouldForwardGeminiSectionServerToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.server.tool.definition.leading/client",
        "${app}/gemini.section.server.tool.definition.leading/server"})
    public void shouldForwardGeminiSectionServerToolDefinitionLeading() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.server.tool.definition.code.execution/client",
        "${app}/gemini.section.server.tool.definition.code.execution/server"})
    public void shouldForwardGeminiSectionServerToolDefinitionCodeExecution() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.server.tool.definition.url.context/client",
        "${app}/gemini.section.server.tool.definition.url.context/server"})
    public void shouldForwardGeminiSectionServerToolDefinitionUrlContext() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.server.tool.definition.snake/client",
        "${app}/gemini.section.server.tool.definition.snake/server"})
    public void shouldForwardGeminiSectionServerToolDefinitionSnake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.server.tool.definition.options/client",
        "${app}/gemini.section.server.tool.definition.options/server"})
    public void shouldForwardGeminiSectionServerToolDefinitionOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.server.tool.definition.multiple/client",
        "${app}/gemini.section.server.tool.definition.multiple/server"})
    public void shouldForwardGeminiSectionServerToolDefinitionMultiple() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.server.tool.definition.mixed/client",
        "${app}/gemini.section.server.tool.definition.mixed/server"})
    public void shouldForwardGeminiSectionServerToolDefinitionMixed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.server.tool.definition.mixed.reversed/client",
        "${app}/gemini.section.server.tool.definition.mixed.reversed/server"})
    public void shouldForwardGeminiSectionServerToolDefinitionMixedReversed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.server.tool.definition.mixed.between/client",
        "${app}/gemini.section.server.tool.definition.mixed.between/server"})
    public void shouldForwardGeminiSectionServerToolDefinitionMixedBetween() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.proxy/client",
        "${app}/gemini.proxy/server"})
    public void shouldForwardGeminiProxy() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.proxy.guardrail.accepted/client",
        "${app}/gemini.proxy.guardrail.accepted/server"})
    public void shouldForwardGeminiProxyGuardrailAccepted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.proxy.guardrail.rejected/client",
        "${app}/gemini.proxy.guardrail.rejected/server"})
    public void shouldForwardGeminiProxyGuardrailRejected() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.proxy.guardrail.rejected.exit/client",
        "${app}/gemini.proxy.guardrail.rejected.exit/server"})
    public void shouldForwardGeminiProxyGuardrailRejectedExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.assistant.server.tool.call/client",
        "${app}/gemini.section.assistant.server.tool.call/server"})
    public void shouldForwardGeminiSectionAssistantServerToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.streaming.assistant.server.tool.call/client",
        "${app}/gemini.streaming.assistant.server.tool.call/server"})
    public void shouldForwardGeminiStreamingAssistantServerToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.assistant.server.tool.call.signature/client",
        "${app}/gemini.section.assistant.server.tool.call.signature/server"})
    public void shouldForwardGeminiSectionAssistantServerToolCallSignature() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.server.tool.call.history/client",
        "${app}/gemini.section.server.tool.call.history/server"})
    public void shouldForwardGeminiSectionServerToolCallHistory() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.server.tool.call.history.no.id/client",
        "${app}/gemini.section.server.tool.call.history.no.id/server"})
    public void shouldForwardGeminiSectionServerToolCallHistoryNoId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.section.server.tool.call.history.snake/client",
        "${app}/gemini.section.server.tool.call.history.snake/server"})
    public void shouldForwardGeminiSectionServerToolCallHistorySnake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.server.tool.call.skipped/client",
        "${app}/gemini.transform.server.tool.call.skipped/server"})
    public void shouldForwardGeminiTransformServerToolCallSkipped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.rejected.model.retired/client",
        "${app}/gemini.rejected.model.retired/server"})
    public void shouldForwardGeminiRejectedModelRetired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.request.extension/client",
        "${app}/gemini.transform.request.extension/server"})
    public void shouldForwardGeminiTransformRequestExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.server.tool.definition/client",
        "${app}/anthropic.transform.server.tool.definition/server"})
    public void shouldAnthropicTransformServerToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.server.tool.definition/client",
        "${app}/transform.server.tool.definition/server"})
    public void shouldTransformServerToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.finish.reason/client",
        "${app}/transform.response.finish.reason/server"})
    public void shouldTransformResponseFinishReason() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.response.finish.reason/client",
        "${app}/anthropic.transform.response.finish.reason/server"})
    public void shouldAnthropicTransformResponseFinishReason() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.response.finish.reason.stop.sequence/client",
        "${app}/anthropic.transform.response.finish.reason.stop.sequence/server"})
    public void shouldAnthropicTransformResponseFinishReasonStopSequence() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.response.finish.reason/client",
        "${app}/openai.transform.response.finish.reason/server"})
    public void shouldOpenaiTransformResponseFinishReason() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.text/client",
        "${app}/openai.responses.text/server"})
    public void shouldOpenaiResponsesText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.text.stream/client",
        "${app}/openai.responses.text.stream/server"})
    public void shouldOpenaiResponsesTextStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.text.stream.no.obfuscation/client",
        "${app}/openai.responses.text.stream.no.obfuscation/server"})
    public void shouldOpenaiResponsesTextStreamNoObfuscation() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.incomplete.max.output.tokens/client",
        "${app}/openai.responses.incomplete.max.output.tokens/server"})
    public void shouldOpenaiResponsesIncompleteMaxOutputTokens() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.incomplete.max.output.tokens.stream/client",
        "${app}/openai.responses.incomplete.max.output.tokens.stream/server"})
    public void shouldOpenaiResponsesIncompleteMaxOutputTokensStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.incomplete.content.filter/client",
        "${app}/openai.responses.incomplete.content.filter/server"})
    public void shouldOpenaiResponsesIncompleteContentFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.incomplete.max.messages/client",
        "${app}/openai.responses.incomplete.max.messages/server"})
    public void shouldOpenaiResponsesIncompleteMaxMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.usage.cache/client",
        "${app}/openai.responses.usage.cache/server"})
    public void shouldOpenaiResponsesUsageCache() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.usage.cache.stream/client",
        "${app}/openai.responses.usage.cache.stream/server"})
    public void shouldOpenaiResponsesUsageCacheStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.system.instruction/client",
        "${app}/openai.responses.section.system.instruction/server"})
    public void shouldOpenaiResponsesSectionSystemInstruction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.user.text.parts/client",
        "${app}/openai.responses.section.user.text.parts/server"})
    public void shouldOpenaiResponsesSectionUserTextParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.assistant.text/client",
        "${app}/openai.responses.section.assistant.text/server"})
    public void shouldOpenaiResponsesSectionAssistantText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.conversation/client",
        "${app}/openai.responses.section.conversation/server"})
    public void shouldOpenaiResponsesSectionConversation() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.controls/client",
        "${app}/openai.responses.request.controls/server"})
    public void shouldOpenaiResponsesRequestControls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.response.controls/client",
        "${app}/openai.responses.response.controls/server"})
    public void shouldOpenaiResponsesResponseControls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.response.controls.stream/client",
        "${app}/openai.responses.response.controls.stream/server"})
    public void shouldOpenaiResponsesResponseControlsStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.rejected.error.code/client",
        "${app}/openai.responses.rejected.error.code/server"})
    public void shouldOpenaiResponsesRejectedErrorCode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.rejected.error.code.streaming/client",
        "${app}/openai.responses.rejected.error.code.streaming/server"})
    public void shouldOpenaiResponsesRejectedErrorCodeStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.rejected.error.param/client",
        "${app}/openai.responses.rejected.error.param/server"})
    public void shouldOpenaiResponsesRejectedErrorParam() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.rejected.rate.limit/client",
        "${app}/openai.responses.rejected.rate.limit/server"})
    public void shouldOpenaiResponsesRejectedRateLimit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.rejected.section/client",
        "${app}/openai.responses.rejected.section/server"})
    public void shouldOpenaiResponsesRejectedSection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.rejected.bad.gateway/client",
        "${app}/openai.responses.rejected.bad.gateway/server"})
    public void shouldOpenaiResponsesRejectedBadGateway() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.rejected.unreachable/client",
        "${app}/openai.responses.rejected.unreachable/server"})
    public void shouldOpenaiResponsesRejectedUnreachable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.rejected.reset/client",
        "${app}/openai.responses.rejected.reset/server"})
    public void shouldOpenaiResponsesRejectedReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.rejected.streaming/client",
        "${app}/openai.responses.rejected.streaming/server"})
    public void shouldOpenaiResponsesRejectedStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.rejected.streaming.failed/client",
        "${app}/openai.responses.rejected.streaming.failed/server"})
    public void shouldOpenaiResponsesRejectedStreamingFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.input.string/client",
        "${app}/openai.responses.section.input.string/server"})
    public void shouldOpenaiResponsesSectionInputString() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.developer.instruction/client",
        "${app}/openai.responses.section.developer.instruction/server"})
    public void shouldOpenaiResponsesSectionDeveloperInstruction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.instructions.input.items/client",
        "${app}/openai.responses.section.instructions.input.items/server"})
    public void shouldOpenaiResponsesSectionInstructionsInputItems() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.instructions.last/client",
        "${app}/openai.responses.section.instructions.last/server"})
    public void shouldOpenaiResponsesSectionInstructionsLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.rejected.input.item.type/client",
        "${app}/openai.responses.rejected.input.item.type/server"})
    public void shouldOpenaiResponsesRejectedInputItemType() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.rejected.content.part.type/client",
        "${app}/openai.responses.rejected.content.part.type/server"})
    public void shouldOpenaiResponsesRejectedContentPartType() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.response.text/client",
        "${app}/openai.responses.transform.response.text/server"})
    public void shouldOpenaiResponsesTransformResponseText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.response.usage/client",
        "${app}/openai.responses.transform.response.usage/server"})
    public void shouldOpenaiResponsesTransformResponseUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy/client",
        "${app}/openai.responses.proxy/server"})
    public void shouldOpenaiResponsesProxy() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.text.captured/client",
        "${app}/openai.responses.text.captured/server"})
    public void shouldOpenaiResponsesTextCaptured() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.text.stream.captured/client",
        "${app}/openai.responses.text.stream.captured/server"})
    public void shouldOpenaiResponsesTextStreamCaptured() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.rejected.output.item.type/client",
        "${app}/openai.responses.rejected.output.item.type/server"})
    public void shouldOpenaiResponsesRejectedOutputItemType() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.rejected.output.item.type.stream/client",
        "${app}/openai.responses.rejected.output.item.type.stream/server"})
    public void shouldOpenaiResponsesRejectedOutputItemTypeStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.function.call/client",
        "${app}/openai.responses.function.call/server"})
    public void shouldOpenaiResponsesFunctionCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.function.call.stream/client",
        "${app}/openai.responses.function.call.stream/server"})
    public void shouldOpenaiResponsesFunctionCallStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.function.call.text/client",
        "${app}/openai.responses.function.call.text/server"})
    public void shouldOpenaiResponsesFunctionCallText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.function.call.text.stream/client",
        "${app}/openai.responses.function.call.text.stream/server"})
    public void shouldOpenaiResponsesFunctionCallTextStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.function.calls/client",
        "${app}/openai.responses.function.calls/server"})
    public void shouldOpenaiResponsesFunctionCalls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.function.calls.stream/client",
        "${app}/openai.responses.function.calls.stream/server"})
    public void shouldOpenaiResponsesFunctionCallsStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.function.calls.interleaved.overflow.stream/client",
        "${app}/openai.responses.function.calls.interleaved.overflow.stream/server"})
    public void shouldOpenaiResponsesFunctionCallsInterleavedOverflowStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.custom.tool.call/client",
        "${app}/openai.responses.custom.tool.call/server"})
    public void shouldOpenaiResponsesCustomToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.custom.tool.call.stream/client",
        "${app}/openai.responses.custom.tool.call.stream/server"})
    public void shouldOpenaiResponsesCustomToolCallStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.web.search/client",
        "${app}/openai.responses.web.search/server"})
    public void shouldOpenaiResponsesWebSearch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.web.search.stream/client",
        "${app}/openai.responses.web.search.stream/server"})
    public void shouldOpenaiResponsesWebSearchStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.code.interpreter/client",
        "${app}/openai.responses.code.interpreter/server"})
    public void shouldOpenaiResponsesCodeInterpreter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.code.interpreter.stream/client",
        "${app}/openai.responses.code.interpreter.stream/server"})
    public void shouldOpenaiResponsesCodeInterpreterStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.mcp/client",
        "${app}/openai.responses.mcp/server"})
    public void shouldOpenaiResponsesMcp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.mcp.stream/client",
        "${app}/openai.responses.mcp.stream/server"})
    public void shouldOpenaiResponsesMcpStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.image.generation/client",
        "${app}/openai.responses.image.generation/server"})
    public void shouldOpenaiResponsesImageGeneration() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.image.generation.stream/client",
        "${app}/openai.responses.image.generation.stream/server"})
    public void shouldOpenaiResponsesImageGenerationStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.tool.definition/client",
        "${app}/openai.responses.section.tool.definition/server"})
    public void shouldOpenaiResponsesSectionToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.tool.definition.members/client",
        "${app}/openai.responses.section.tool.definition.members/server"})
    public void shouldOpenaiResponsesSectionToolDefinitionMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.tool.definition.100k/client",
        "${app}/openai.responses.section.tool.definition.100k/server"})
    public void shouldOpenaiResponsesSectionToolDefinition100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.server.tool.definition/client",
        "${app}/openai.responses.section.server.tool.definition/server"})
    public void shouldOpenaiResponsesSectionServerToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.tool.call/client",
        "${app}/openai.responses.section.tool.call/server"})
    public void shouldOpenaiResponsesSectionToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.tool.calls/client",
        "${app}/openai.responses.section.tool.calls/server"})
    public void shouldOpenaiResponsesSectionToolCalls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.assistant.text.and.tool.call/client",
        "${app}/openai.responses.section.assistant.text.and.tool.call/server"})
    public void shouldOpenaiResponsesSectionAssistantTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.tool.call.members/client",
        "${app}/openai.responses.section.tool.call.members/server"})
    public void shouldOpenaiResponsesSectionToolCallMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.tool.call.ignored/client",
        "${app}/openai.responses.section.tool.call.ignored/server"})
    public void shouldOpenaiResponsesSectionToolCallIgnored() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.tool.call.escaped/client",
        "${app}/openai.responses.section.tool.call.escaped/server"})
    public void shouldOpenaiResponsesSectionToolCallEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.tool.result/client",
        "${app}/openai.responses.section.tool.result/server"})
    public void shouldOpenaiResponsesSectionToolResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.tool.result.parts/client",
        "${app}/openai.responses.section.tool.result.parts/server"})
    public void shouldOpenaiResponsesSectionToolResultParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.server.tool.call/client",
        "${app}/openai.responses.section.server.tool.call/server"})
    public void shouldOpenaiResponsesSectionServerToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.response.tool.call/client",
        "${app}/openai.responses.transform.response.tool.call/server"})
    public void shouldOpenaiResponsesTransformResponseToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.response.tool.call.empty/client",
        "${app}/openai.responses.transform.response.tool.call.empty/server"})
    public void shouldOpenaiResponsesTransformResponseToolCallEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.response.tool.calls/client",
        "${app}/openai.responses.transform.response.tool.calls/server"})
    public void shouldOpenaiResponsesTransformResponseToolCalls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.response.text.and.tool.call/client",
        "${app}/openai.responses.transform.response.text.and.tool.call/server"})
    public void shouldOpenaiResponsesTransformResponseTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.function.call.large/client",
        "${app}/openai.responses.function.call.large/server"})
    public void shouldOpenaiResponsesFunctionCallLarge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.function.call.large.stream/client",
        "${app}/openai.responses.function.call.large.stream/server"})
    public void shouldOpenaiResponsesFunctionCallLargeStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.function.call.huge/client",
        "${app}/openai.responses.function.call.huge/server"})
    public void shouldOpenaiResponsesFunctionCallHuge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.function.call.huge.stream/client",
        "${app}/openai.responses.function.call.huge.stream/server"})
    public void shouldOpenaiResponsesFunctionCallHugeStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.text.stream.large/client",
        "${app}/openai.responses.text.stream.large/server"})
    public void shouldOpenaiResponsesTextStreamLarge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.text.stream.huge/client",
        "${app}/openai.responses.text.stream.huge/server"})
    public void shouldOpenaiResponsesTextStreamHuge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.tool.call.attributes.last/client",
        "${app}/openai.responses.section.tool.call.attributes.last/server"})
    public void shouldOpenaiResponsesSectionToolCallAttributesLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.function.call.huge.late/client",
        "${app}/openai.responses.function.call.huge.late/server"})
    public void shouldOpenaiResponsesFunctionCallHugeLate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.reasoning/client",
        "${app}/openai.responses.reasoning/server"})
    public void shouldOpenaiResponsesReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.reasoning.stream/client",
        "${app}/openai.responses.reasoning.stream/server"})
    public void shouldOpenaiResponsesReasoningStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.reasoning.no.summary/client",
        "${app}/openai.responses.reasoning.no.summary/server"})
    public void shouldOpenaiResponsesReasoningNoSummary() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.reasoning.no.summary.stream/client",
        "${app}/openai.responses.reasoning.no.summary.stream/server"})
    public void shouldOpenaiResponsesReasoningNoSummaryStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.reasoning.tool.call/client",
        "${app}/openai.responses.reasoning.tool.call/server"})
    public void shouldOpenaiResponsesReasoningToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.reasoning.tool.call.stream/client",
        "${app}/openai.responses.reasoning.tool.call.stream/server"})
    public void shouldOpenaiResponsesReasoningToolCallStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.reasoning.id/client",
        "${app}/openai.responses.reasoning.id/server"})
    public void shouldOpenaiResponsesReasoningId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.reasoning.id.stream/client",
        "${app}/openai.responses.reasoning.id.stream/server"})
    public void shouldOpenaiResponsesReasoningIdStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.reasoning.large/client",
        "${app}/openai.responses.reasoning.large/server"})
    public void shouldOpenaiResponsesReasoningLarge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.reasoning.large.stream/client",
        "${app}/openai.responses.reasoning.large.stream/server"})
    public void shouldOpenaiResponsesReasoningLargeStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.reasoning.real/client",
        "${app}/openai.responses.reasoning.real/server"})
    public void shouldOpenaiResponsesReasoningReal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.reasoning.real.huge/client",
        "${app}/openai.responses.reasoning.real.huge/server"})
    public void shouldOpenaiResponsesReasoningRealHuge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.reasoning.real.stream/client",
        "${app}/openai.responses.reasoning.real.stream/server"})
    public void shouldOpenaiResponsesReasoningRealStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.refusal/client",
        "${app}/openai.responses.refusal/server"})
    public void shouldOpenaiResponsesRefusal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.refusal.stream/client",
        "${app}/openai.responses.refusal.stream/server"})
    public void shouldOpenaiResponsesRefusalStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.text.citations/client",
        "${app}/openai.responses.text.citations/server"})
    public void shouldOpenaiResponsesTextCitations() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.text.citations.stream/client",
        "${app}/openai.responses.text.citations.stream/server"})
    public void shouldOpenaiResponsesTextCitationsStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.text.citations.members/client",
        "${app}/openai.responses.text.citations.members/server"})
    public void shouldOpenaiResponsesTextCitationsMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.text.citations.members.stream/client",
        "${app}/openai.responses.text.citations.members.stream/server"})
    public void shouldOpenaiResponsesTextCitationsMembersStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.reasoning/client",
        "${app}/openai.responses.section.reasoning/server"})
    public void shouldOpenaiResponsesSectionReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.reasoning.no.summary/client",
        "${app}/openai.responses.section.reasoning.no.summary/server"})
    public void shouldOpenaiResponsesSectionReasoningNoSummary() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.reasoning.parts/client",
        "${app}/openai.responses.section.reasoning.parts/server"})
    public void shouldOpenaiResponsesSectionReasoningParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.reasoning.items/client",
        "${app}/openai.responses.section.reasoning.items/server"})
    public void shouldOpenaiResponsesSectionReasoningItems() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.reasoning.large/client",
        "${app}/openai.responses.section.reasoning.large/server"})
    public void shouldOpenaiResponsesSectionReasoningLarge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.reasoning.encrypted.first/client",
        "${app}/openai.responses.section.reasoning.encrypted.first/server"})
    public void shouldOpenaiResponsesSectionReasoningEncryptedFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.reasoning.encrypted.first.10k/client",
        "${app}/openai.responses.section.reasoning.encrypted.first.10k/server"})
    public void shouldOpenaiResponsesSectionReasoningEncryptedFirst10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.reasoning.content/client",
        "${app}/openai.responses.section.reasoning.content/server"})
    public void shouldOpenaiResponsesSectionReasoningContent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.assistant.refusal/client",
        "${app}/openai.responses.section.assistant.refusal/server"})
    public void shouldOpenaiResponsesSectionAssistantRefusal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.assistant.text.citations/client",
        "${app}/openai.responses.section.assistant.text.citations/server"})
    public void shouldOpenaiResponsesSectionAssistantTextCitations() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.assistant.text.citations.annotations.first/client",
        "${app}/openai.responses.section.assistant.text.citations.annotations.first/server"})
    public void shouldOpenaiResponsesSectionAssistantTextCitationsAnnotationsFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.assistant.text.citations.members/client",
        "${app}/openai.responses.section.assistant.text.citations.members/server"})
    public void shouldOpenaiResponsesSectionAssistantTextCitationsMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.assistant.text.citations.extra/client",
        "${app}/openai.responses.section.assistant.text.citations.extra/server"})
    public void shouldOpenaiResponsesSectionAssistantTextCitationsExtra() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.assistant.text.parts/client",
        "${app}/openai.responses.section.assistant.text.parts/server"})
    public void shouldOpenaiResponsesSectionAssistantTextParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.user.image/client",
        "${app}/openai.responses.section.user.image/server"})
    public void shouldOpenaiResponsesSectionUserImage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.user.image.inline/client",
        "${app}/openai.responses.section.user.image.inline/server"})
    public void shouldOpenaiResponsesSectionUserImageInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.user.image.reference/client",
        "${app}/openai.responses.section.user.image.reference/server"})
    public void shouldOpenaiResponsesSectionUserImageReference() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.user.image.detail.first/client",
        "${app}/openai.responses.section.user.image.detail.first/server"})
    public void shouldOpenaiResponsesSectionUserImageDetailFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.user.image.10k/client",
        "${app}/openai.responses.section.user.image.10k/server"})
    public void shouldOpenaiResponsesSectionUserImage10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.user.image.url.10k/client",
        "${app}/openai.responses.section.user.image.url.10k/server"})
    public void shouldOpenaiResponsesSectionUserImageUrl10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.user.image.url.100k/client",
        "${app}/openai.responses.section.user.image.url.100k/server"})
    public void shouldOpenaiResponsesSectionUserImageUrl100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.user.document/client",
        "${app}/openai.responses.section.user.document/server"})
    public void shouldOpenaiResponsesSectionUserDocument() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.user.document.inline/client",
        "${app}/openai.responses.section.user.document.inline/server"})
    public void shouldOpenaiResponsesSectionUserDocumentInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.user.document.reference/client",
        "${app}/openai.responses.section.user.document.reference/server"})
    public void shouldOpenaiResponsesSectionUserDocumentReference() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.user.document.detail/client",
        "${app}/openai.responses.section.user.document.detail/server"})
    public void shouldOpenaiResponsesSectionUserDocumentDetail() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.section.user.audio/client",
        "${app}/openai.responses.section.user.audio/server"})
    public void shouldOpenaiResponsesSectionUserAudio() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.assistant.reasoning.members/client",
        "${app}/openai.responses.transform.assistant.reasoning.members/server"})
    public void shouldOpenaiResponsesTransformAssistantReasoningMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.response.reasoning/client",
        "${app}/openai.responses.transform.response.reasoning/server"})
    public void shouldOpenaiResponsesTransformResponseReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.request.members/client",
        "${app}/openai.responses.transform.request.members/server"})
    public void shouldOpenaiResponsesTransformRequestMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.conversation/client",
        "${app}/openai.responses.transform.conversation/server"})
    public void shouldOpenaiResponsesTransformConversation() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.assistant.tool.call.members/client",
        "${app}/openai.responses.transform.assistant.tool.call.members/server"})
    public void shouldOpenaiResponsesTransformAssistantToolCallMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.tool.definition.members/client",
        "${app}/openai.responses.transform.tool.definition.members/server"})
    public void shouldOpenaiResponsesTransformToolDefinitionMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.user.document.members/client",
        "${app}/openai.responses.transform.user.document.members/server"})
    public void shouldOpenaiResponsesTransformUserDocumentMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.assistant.text.citations.members/client",
        "${app}/openai.responses.transform.assistant.text.citations.members/server"})
    public void shouldOpenaiResponsesTransformAssistantTextCitationsMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.server.tool/client",
        "${app}/openai.responses.transform.server.tool/server"})
    public void shouldOpenaiResponsesTransformServerTool() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.response.text.100k/client",
        "${app}/openai.responses.transform.response.text.100k/server"})
    public void shouldOpenaiResponsesTransformResponseText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.response.text.escaped/client",
        "${app}/openai.responses.transform.response.text.escaped/server"})
    public void shouldOpenaiResponsesTransformResponseTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.response.refusal/client",
        "${app}/openai.responses.transform.response.refusal/server"})
    public void shouldOpenaiResponsesTransformResponseRefusal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.response.streaming/client",
        "${app}/openai.responses.transform.response.streaming/server"})
    public void shouldOpenaiResponsesTransformResponseStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.response.text.error/client",
        "${app}/openai.responses.transform.response.text.error/server"})
    public void shouldOpenaiResponsesTransformResponseTextError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.response.tool.call.error/client",
        "${app}/openai.responses.transform.response.tool.call.error/server"})
    public void shouldOpenaiResponsesTransformResponseToolCallError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.response.usage.cache.write/client",
        "${app}/openai.responses.transform.response.usage.cache.write/server"})
    public void shouldOpenaiResponsesTransformResponseUsageCacheWrite() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.assistant.tool.call.attributes.last/client",
        "${app}/openai.responses.transform.assistant.tool.call.attributes.last/server"})
    public void shouldOpenaiResponsesTransformAssistantToolCallAttributesLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.usage/client",
        "${app}/openai.responses.usage/server"})
    public void shouldOpenaiResponsesUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.fallback/client",
        "${app}/openai.responses.proxy.fallback/server"})
    public void shouldOpenaiResponsesProxyFallback() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.fallback.accepted/client",
        "${app}/openai.responses.proxy.fallback.accepted/server"})
    public void shouldOpenaiResponsesProxyFallbackAccepted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.transform.rejected.user.audio/client",
        "${app}/openai.responses.transform.rejected.user.audio/server"})
    public void shouldOpenaiResponsesTransformRejectedUserAudio() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.accepted/client",
        "${app}/openai.responses.proxy.guardrail.accepted/server"})
    public void shouldOpenaiResponsesProxyGuardrailAccepted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.rejected/client",
        "${app}/openai.responses.proxy.guardrail.rejected/server"})
    public void shouldOpenaiResponsesProxyGuardrailRejected() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.rejected.exit/client",
        "${app}/openai.responses.proxy.guardrail.rejected.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailRejectedExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.transform/client",
        "${app}/openai.responses.proxy.guardrail.transform/server"})
    public void shouldOpenaiResponsesProxyGuardrailTransform() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.transform.exit/client",
        "${app}/openai.responses.proxy.guardrail.transform.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailTransformExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.transform.streaming/client",
        "${app}/openai.responses.proxy.guardrail.transform.streaming/server"})
    public void shouldOpenaiResponsesProxyGuardrailTransformStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.transform.streaming.exit/client",
        "${app}/openai.responses.proxy.guardrail.transform.streaming.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailTransformStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.transform.consecutive/client",
        "${app}/openai.responses.proxy.guardrail.transform.consecutive/server"})
    public void shouldOpenaiResponsesProxyGuardrailTransformConsecutive() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.transform.consecutive.exit/client",
        "${app}/openai.responses.proxy.guardrail.transform.consecutive.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailTransformConsecutiveExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.transform.tool.call/client",
        "${app}/openai.responses.proxy.guardrail.transform.tool.call/server"})
    public void shouldOpenaiResponsesProxyGuardrailTransformToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.transform.tool.call.exit/client",
        "${app}/openai.responses.proxy.guardrail.transform.tool.call.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailTransformToolCallExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.chain.accepted/client",
        "${app}/openai.responses.proxy.guardrail.chain.accepted/server"})
    public void shouldOpenaiResponsesProxyGuardrailChainAccepted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.chain.accepted.exit/client",
        "${app}/openai.responses.proxy.guardrail.chain.accepted.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailChainAcceptedExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.chain.rejected/client",
        "${app}/openai.responses.proxy.guardrail.chain.rejected/server"})
    public void shouldOpenaiResponsesProxyGuardrailChainRejected() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.chain.rejected.exit/client",
        "${app}/openai.responses.proxy.guardrail.chain.rejected.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailChainRejectedExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.fallback/client",
        "${app}/openai.responses.proxy.guardrail.fallback/server"})
    public void shouldOpenaiResponsesProxyGuardrailFallback() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.fallback.exit/client",
        "${app}/openai.responses.proxy.guardrail.fallback.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailFallbackExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseAccepted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseAcceptedExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.streaming.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.streaming.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseAcceptedStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.rejected/client",
        "${app}/openai.responses.proxy.guardrail.response.rejected/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseRejected() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.rejected.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.rejected.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseRejectedExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.rejected.streaming.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.rejected.streaming.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseRejectedStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.transformed/client",
        "${app}/openai.responses.proxy.guardrail.response.transformed/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseTransformed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.transformed.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.transformed.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseTransformedExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.transformed.streaming.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.transformed.streaming.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseTransformedStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.tool.call/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.tool.call/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseAcceptedToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.tool.call.streaming.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.tool.call.streaming.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseAcceptedToolCallStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.rejected.tool.call/client",
        "${app}/openai.responses.proxy.guardrail.response.rejected.tool.call/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseRejectedToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.rejected.tool.call.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.rejected.tool.call.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseRejectedToolCallExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.rejected.tool.call.streaming.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.rejected.tool.call.streaming.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseRejectedToolCallStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.transformed.tool.call/client",
        "${app}/openai.responses.proxy.guardrail.response.transformed.tool.call/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseTransformedToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.transformed.tool.call.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.transformed.tool.call.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseTransformedToolCallExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.transformed.tool.call.streaming.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.transformed.tool.call.streaming.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseTransformedToolCallStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.refusal/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.refusal/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseAcceptedRefusal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.refusal.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.refusal.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseAcceptedRefusalExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.refusal.streaming.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.refusal.streaming.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseAcceptedRefusalStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.rejected.refusal/client",
        "${app}/openai.responses.proxy.guardrail.response.rejected.refusal/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseRejectedRefusal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.rejected.refusal.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.rejected.refusal.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseRejectedRefusalExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.rejected.refusal.streaming.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.rejected.refusal.streaming.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseRejectedRefusalStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.transformed.refusal/client",
        "${app}/openai.responses.proxy.guardrail.response.transformed.refusal/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseTransformedRefusal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.transformed.refusal.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.transformed.refusal.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseTransformedRefusalExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.transformed.refusal.streaming.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.transformed.refusal.streaming.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseTransformedRefusalStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.control/client",
        "${app}/openai.responses.proxy.guardrail.control/server"})
    public void shouldOpenaiResponsesProxyGuardrailControl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseAcceptedReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseAcceptedReasoningExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.streaming.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.streaming.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseAcceptedReasoningStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.transformed.reasoning.parts/client",
        "${app}/openai.responses.proxy.guardrail.response.transformed.reasoning.parts/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseTransformedReasoningParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.transformed.reasoning.parts.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.transformed.reasoning.parts.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseTransformedReasoningPartsExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.transformed.reasoning.parts.streaming.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.transformed.reasoning.parts.streaming.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseTransformedReasoningPartsStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.rejected.reasoning.parts/client",
        "${app}/openai.responses.proxy.guardrail.response.rejected.reasoning.parts/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseRejectedReasoningParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.rejected.reasoning.parts.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.rejected.reasoning.parts.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseRejectedReasoningPartsExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.rejected.reasoning.parts.streaming.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.rejected.reasoning.parts.streaming.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseRejectedReasoningPartsStreamingExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.large/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.large/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseAcceptedReasoningLarge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.large.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.large.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseAcceptedReasoningLargeExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.large.small.window/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.large.small.window/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseAcceptedReasoningLargeSmallWindow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.transformed.reasoning.large/client",
        "${app}/openai.responses.proxy.guardrail.response.transformed.reasoning.large/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseTransformedReasoningLarge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.transformed.reasoning.large.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.transformed.reasoning.large.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseTransformedReasoningLargeExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.members.first/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.members.first/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseAcceptedReasoningMembersFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.members.first.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.members.first.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseAcceptedReasoningMembersFirstExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.members.first.large/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.members.first.large/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseAcceptedReasoningMembersFirstLarge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.members.first.large.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.members.first.large.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseAcceptedReasoningMembersFirstLargeExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.no.summary/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.no.summary/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseAcceptedReasoningNoSummary() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.no.summary.exit/client",
        "${app}/openai.responses.proxy.guardrail.response.accepted.reasoning.no.summary.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailResponseAcceptedReasoningNoSummaryExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.proxy.guardrail.transformed.exit/client",
        "${app}/openai.responses.proxy.guardrail.transformed.exit/server"})
    public void shouldOpenaiResponsesProxyGuardrailTransformedExit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.text.citations.long/client",
        "${app}/openai.responses.text.citations.long/server"})
    public void shouldOpenaiResponsesTextCitationsLong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.response.controls.inline/client",
        "${app}/openai.responses.response.controls.inline/server"})
    public void shouldOpenaiResponsesResponseControlsInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.response.controls.held.large/client",
        "${app}/openai.responses.response.controls.held.large/server"})
    public void shouldOpenaiResponsesResponseControlsHeldLarge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.response.controls.held.small.slot/client",
        "${app}/openai.responses.response.controls.held.small.slot/server"})
    public void shouldOpenaiResponsesResponseControlsHeldSmallSlot() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.sampling/client",
        "${app}/transform.request.sampling/server"})
    public void shouldTransformRequestSampling() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.sampling.top.k/client",
        "${app}/transform.request.sampling.top.k/server"})
    public void shouldTransformRequestSamplingTopK() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.sampling.seed/client",
        "${app}/transform.request.sampling.seed/server"})
    public void shouldTransformRequestSamplingSeed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.sampling.penalties/client",
        "${app}/transform.request.sampling.penalties/server"})
    public void shouldTransformRequestSamplingPenalties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.sampling.stop/client",
        "${app}/transform.request.sampling.stop/server"})
    public void shouldTransformRequestSamplingStop() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.sampling.clamp/client",
        "${app}/transform.request.sampling.clamp/server"})
    public void shouldTransformRequestSamplingClamp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.sampling.stop/client",
        "${app}/transform.rejected.sampling.stop/server"})
    public void shouldTransformRejectedSamplingStop() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.sampling.stop.excess/client",
        "${app}/transform.rejected.sampling.stop.excess/server"})
    public void shouldTransformRejectedSamplingStopExcess() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.sampling/client",
        "${app}/gemini.request.sampling/server"})
    public void shouldForwardGeminiRequestSampling() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.sampling.residue.absent/client",
        "${app}/gemini.request.sampling.residue.absent/server"})
    public void shouldForwardGeminiRequestSamplingResidueAbsent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.sampling.empty/client",
        "${app}/gemini.request.sampling.empty/server"})
    public void shouldForwardGeminiRequestSamplingEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.sampling.interleaved/client",
        "${app}/gemini.request.sampling.interleaved/server"})
    public void shouldForwardGeminiRequestSamplingInterleaved() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.sampling.snake/client",
        "${app}/gemini.request.sampling.snake/server"})
    public void shouldForwardGeminiRequestSamplingSnake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.sampling.seed.range/client",
        "${app}/transform.request.sampling.seed.range/server"})
    public void shouldTransformRequestSamplingSeedRange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.sampling.stop.limit/client",
        "${app}/transform.request.sampling.stop.limit/server"})
    public void shouldTransformRequestSamplingStopLimit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.sampling.stop.entries/client",
        "${app}/transform.rejected.sampling.stop.entries/server"})
    public void shouldTransformRejectedSamplingStopEntries() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.sampling.seed.max/client",
        "${app}/transform.request.sampling.seed.max/server"})
    public void shouldTransformRequestSamplingSeedMax() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.request.tool.choice.auto/client",
        "${app}/openai.request.tool.choice.auto/server"})
    public void shouldForwardOpenaiRequestToolChoiceAuto() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.request.tool.choice.required/client",
        "${app}/openai.request.tool.choice.required/server"})
    public void shouldForwardOpenaiRequestToolChoiceRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.request.tool.choice.none/client",
        "${app}/openai.request.tool.choice.none/server"})
    public void shouldForwardOpenaiRequestToolChoiceNone() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.request.tool.choice.function/client",
        "${app}/openai.request.tool.choice.function/server"})
    public void shouldForwardOpenaiRequestToolChoiceFunction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.request.tool.choice.allowed/client",
        "${app}/openai.request.tool.choice.allowed/server"})
    public void shouldForwardOpenaiRequestToolChoiceAllowed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.request.tool.choice.parallel/client",
        "${app}/openai.request.tool.choice.parallel/server"})
    public void shouldForwardOpenaiRequestToolChoiceParallel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.tool.choice.auto/client",
        "${app}/openai.responses.request.tool.choice.auto/server"})
    public void shouldForwardOpenaiResponsesRequestToolChoiceAuto() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.tool.choice.required/client",
        "${app}/openai.responses.request.tool.choice.required/server"})
    public void shouldForwardOpenaiResponsesRequestToolChoiceRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.tool.choice.none/client",
        "${app}/openai.responses.request.tool.choice.none/server"})
    public void shouldForwardOpenaiResponsesRequestToolChoiceNone() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.tool.choice.function/client",
        "${app}/openai.responses.request.tool.choice.function/server"})
    public void shouldForwardOpenaiResponsesRequestToolChoiceFunction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.tool.choice.allowed/client",
        "${app}/openai.responses.request.tool.choice.allowed/server"})
    public void shouldForwardOpenaiResponsesRequestToolChoiceAllowed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.tool.choice.hosted/client",
        "${app}/openai.responses.request.tool.choice.hosted/server"})
    public void shouldForwardOpenaiResponsesRequestToolChoiceHosted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.tool.choice.parallel/client",
        "${app}/openai.responses.request.tool.choice.parallel/server"})
    public void shouldForwardOpenaiResponsesRequestToolChoiceParallel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.tool.choice.auto/client",
        "${app}/anthropic.request.tool.choice.auto/server"})
    public void shouldForwardAnthropicRequestToolChoiceAuto() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.tool.choice.any/client",
        "${app}/anthropic.request.tool.choice.any/server"})
    public void shouldForwardAnthropicRequestToolChoiceAny() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.tool.choice.none/client",
        "${app}/anthropic.request.tool.choice.none/server"})
    public void shouldForwardAnthropicRequestToolChoiceNone() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.tool.choice.tool/client",
        "${app}/anthropic.request.tool.choice.tool/server"})
    public void shouldForwardAnthropicRequestToolChoiceTool() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.tool.choice.parallel.disabled/client",
        "${app}/anthropic.request.tool.choice.parallel.disabled/server"})
    public void shouldForwardAnthropicRequestToolChoiceParallelDisabled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.tool.choice.parallel.enabled/client",
        "${app}/anthropic.request.tool.choice.parallel.enabled/server"})
    public void shouldForwardAnthropicRequestToolChoiceParallelEnabled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.tool.choice.parallel.only/client",
        "${app}/anthropic.request.tool.choice.parallel.only/server"})
    public void shouldForwardAnthropicRequestToolChoiceParallelOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.tool.choice.residue/client",
        "${app}/anthropic.request.tool.choice.residue/server"})
    public void shouldForwardAnthropicRequestToolChoiceResidue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.tool.choice.unknown/client",
        "${app}/anthropic.request.tool.choice.unknown/server"})
    public void shouldForwardAnthropicRequestToolChoiceUnknown() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.tool.choice.auto/client",
        "${app}/gemini.request.tool.choice.auto/server"})
    public void shouldForwardGeminiRequestToolChoiceAuto() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.tool.choice.any/client",
        "${app}/gemini.request.tool.choice.any/server"})
    public void shouldForwardGeminiRequestToolChoiceAny() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.tool.choice.none/client",
        "${app}/gemini.request.tool.choice.none/server"})
    public void shouldForwardGeminiRequestToolChoiceNone() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.tool.choice.function/client",
        "${app}/gemini.request.tool.choice.function/server"})
    public void shouldForwardGeminiRequestToolChoiceFunction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.tool.choice.allowed.required/client",
        "${app}/gemini.request.tool.choice.allowed.required/server"})
    public void shouldForwardGeminiRequestToolChoiceAllowedRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.tool.choice.allowed.auto/client",
        "${app}/gemini.request.tool.choice.allowed.auto/server"})
    public void shouldForwardGeminiRequestToolChoiceAllowedAuto() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.tool.choice.validated/client",
        "${app}/gemini.request.tool.choice.validated/server"})
    public void shouldForwardGeminiRequestToolChoiceValidated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.tool.choice.residue/client",
        "${app}/gemini.request.tool.choice.residue/server"})
    public void shouldForwardGeminiRequestToolChoiceResidue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.tool.choice.snake/client",
        "${app}/gemini.request.tool.choice.snake/server"})
    public void shouldForwardGeminiRequestToolChoiceSnake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.tool.choice.auto/client",
        "${app}/transform.request.tool.choice.auto/server"})
    public void shouldTransformRequestToolChoiceAuto() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.tool.choice.required/client",
        "${app}/transform.request.tool.choice.required/server"})
    public void shouldTransformRequestToolChoiceRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.tool.choice.none/client",
        "${app}/transform.request.tool.choice.none/server"})
    public void shouldTransformRequestToolChoiceNone() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.tool.choice.function/client",
        "${app}/transform.request.tool.choice.function/server"})
    public void shouldTransformRequestToolChoiceFunction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.tool.choice.allowed/client",
        "${app}/transform.request.tool.choice.allowed/server"})
    public void shouldTransformRequestToolChoiceAllowed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.tool.choice.allowed.required/client",
        "${app}/transform.request.tool.choice.allowed.required/server"})
    public void shouldTransformRequestToolChoiceAllowedRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.tool.choice.parallel/client",
        "${app}/transform.request.tool.choice.parallel/server"})
    public void shouldTransformRequestToolChoiceParallel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.tool.choice.parallel.true/client",
        "${app}/transform.request.tool.choice.parallel.true/server"})
    public void shouldTransformRequestToolChoiceParallelTrue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.tool.choice.choice.parallel/client",
        "${app}/transform.request.tool.choice.choice.parallel/server"})
    public void shouldTransformRequestToolChoiceChoiceParallel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.tool.choice.parallel.first/client",
        "${app}/transform.request.tool.choice.parallel.first/server"})
    public void shouldTransformRequestToolChoiceParallelFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.tool.choice.none.parallel/client",
        "${app}/transform.request.tool.choice.none.parallel/server"})
    public void shouldTransformRequestToolChoiceNoneParallel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.tool.choice.allowed/client",
        "${app}/transform.rejected.tool.choice.allowed/server"})
    public void shouldTransformRejectedToolChoiceAllowed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.tool.choice.value/client",
        "${app}/transform.rejected.tool.choice.value/server"})
    public void shouldTransformRejectedToolChoiceValue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.parallel.tool.calls/client",
        "${app}/transform.rejected.parallel.tool.calls/server"})
    public void shouldTransformRejectedParallelToolCalls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.request.tool.choice.mixed/client",
        "${app}/openai.request.tool.choice.mixed/server"})
    public void shouldForwardOpenaiRequestToolChoiceMixed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.tool.choice.mixed/client",
        "${app}/openai.responses.request.tool.choice.mixed/server"})
    public void shouldForwardOpenaiResponsesRequestToolChoiceMixed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.tool.choice.mixed/client",
        "${app}/anthropic.request.tool.choice.mixed/server"})
    public void shouldForwardAnthropicRequestToolChoiceMixed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.tool.choice.mixed/client",
        "${app}/gemini.request.tool.choice.mixed/server"})
    public void shouldForwardGeminiRequestToolChoiceMixed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.request.response.format.text/client",
        "${app}/openai.request.response.format.text/server"})
    public void shouldForwardOpenaiRequestResponseFormatText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.request.response.format.json.object/client",
        "${app}/openai.request.response.format.json.object/server"})
    public void shouldForwardOpenaiRequestResponseFormatJsonObject() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.response.format.text/client",
        "${app}/openai.responses.request.response.format.text/server"})
    public void shouldForwardOpenaiResponsesRequestResponseFormatText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.response.format.json.object/client",
        "${app}/openai.responses.request.response.format.json.object/server"})
    public void shouldForwardOpenaiResponsesRequestResponseFormatJsonObject() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.response.format.json.schema/client",
        "${app}/openai.responses.request.response.format.json.schema/server"})
    public void shouldForwardOpenaiResponsesRequestResponseFormatJsonSchema() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.response.format.residue/client",
        "${app}/openai.responses.request.response.format.residue/server"})
    public void shouldForwardOpenaiResponsesRequestResponseFormatResidue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.response.format.residue.split/client",
        "${app}/openai.responses.request.response.format.residue.split/server"})
    public void shouldForwardOpenaiResponsesRequestResponseFormatResidueSplit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.response.format.residue.only/client",
        "${app}/openai.responses.request.response.format.residue.only/server"})
    public void shouldForwardOpenaiResponsesRequestResponseFormatResidueOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.response.format.json.schema/client",
        "${app}/anthropic.request.response.format.json.schema/server"})
    public void shouldForwardAnthropicRequestResponseFormatJsonSchema() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.response.format.residue/client",
        "${app}/anthropic.request.response.format.residue/server"})
    public void shouldForwardAnthropicRequestResponseFormatResidue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.response.format.residue.split/client",
        "${app}/anthropic.request.response.format.residue.split/server"})
    public void shouldForwardAnthropicRequestResponseFormatResidueSplit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.response.format.residue.only/client",
        "${app}/anthropic.request.response.format.residue.only/server"})
    public void shouldForwardAnthropicRequestResponseFormatResidueOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.response.format.json.object/client",
        "${app}/gemini.request.response.format.json.object/server"})
    public void shouldForwardGeminiRequestResponseFormatJsonObject() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.response.format.json.schema/client",
        "${app}/gemini.request.response.format.json.schema/server"})
    public void shouldForwardGeminiRequestResponseFormatJsonSchema() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.response.format.residue/client",
        "${app}/gemini.request.response.format.residue/server"})
    public void shouldForwardGeminiRequestResponseFormatResidue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.response.format.enum/client",
        "${app}/gemini.request.response.format.enum/server"})
    public void shouldForwardGeminiRequestResponseFormatEnum() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.response.format.schema.first/client",
        "${app}/gemini.request.response.format.schema.first/server"})
    public void shouldForwardGeminiRequestResponseFormatSchemaFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.response.format.deprecated.schema.first/client",
        "${app}/gemini.request.response.format.deprecated.schema.first/server"})
    public void shouldForwardGeminiRequestResponseFormatDeprecatedSchemaFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.response.format.text/client",
        "${app}/transform.request.response.format.text/server"})
    public void shouldTransformRequestResponseFormatText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.response.format.json.object/client",
        "${app}/transform.request.response.format.json.object/server"})
    public void shouldTransformRequestResponseFormatJsonObject() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.response.format.json.schema/client",
        "${app}/transform.request.response.format.json.schema/server"})
    public void shouldTransformRequestResponseFormatJsonSchema() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.response.format.json.schema.type.last/client",
        "${app}/transform.request.response.format.json.schema.type.last/server"})
    public void shouldTransformRequestResponseFormatJsonSchemaTypeLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.response.format.json.schema.names/client",
        "${app}/transform.request.response.format.json.schema.names/server"})
    public void shouldTransformRequestResponseFormatJsonSchemaNames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.response.format.json.object/client",
        "${app}/transform.rejected.response.format.json.object/server"})
    public void shouldTransformRejectedResponseFormatJsonObject() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.response.format.type/client",
        "${app}/transform.rejected.response.format.type/server"})
    public void shouldTransformRejectedResponseFormatType() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.response.format.keyword.minimum/client",
        "${app}/transform.rejected.response.format.keyword.minimum/server"})
    public void shouldTransformRejectedResponseFormatKeywordMinimum() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.response.format.keyword.min.length/client",
        "${app}/transform.rejected.response.format.keyword.min.length/server"})
    public void shouldTransformRejectedResponseFormatKeywordMinLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.response.format.keyword.max.items/client",
        "${app}/transform.rejected.response.format.keyword.max.items/server"})
    public void shouldTransformRejectedResponseFormatKeywordMaxItems() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.response.format.keyword.min.items/client",
        "${app}/transform.rejected.response.format.keyword.min.items/server"})
    public void shouldTransformRejectedResponseFormatKeywordMinItems() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.response.format.keyword.external.ref/client",
        "${app}/transform.rejected.response.format.keyword.external.ref/server"})
    public void shouldTransformRejectedResponseFormatKeywordExternalRef() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.response.format.keyword.recursive.ref/client",
        "${app}/transform.rejected.response.format.keyword.recursive.ref/server"})
    public void shouldTransformRejectedResponseFormatKeywordRecursiveRef() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.response.format.keyword.pattern.properties/client",
        "${app}/transform.rejected.response.format.keyword.pattern.properties/server"})
    public void shouldTransformRejectedResponseFormatKeywordPatternProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.response.format.keyword.not/client",
        "${app}/transform.rejected.response.format.keyword.not/server"})
    public void shouldTransformRejectedResponseFormatKeywordNot() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.response.format.rejected.bound/client",
        "${app}/openai.responses.request.response.format.rejected.bound/server"})
    public void shouldForwardOpenaiResponsesRequestResponseFormatRejectedBound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${app}/transform.request.reasoning.effort/server"})
    public void shouldTransformRequestReasoningEffort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.reasoning.budget/client",
        "${app}/transform.request.reasoning.budget/server"})
    public void shouldTransformRequestReasoningBudget() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.reasoning.effort.budget/client",
        "${app}/transform.request.reasoning.effort.budget/server"})
    public void shouldTransformRequestReasoningEffortBudget() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.sampling.members/client",
        "${app}/transform.request.sampling.members/server"})
    public void shouldTransformRequestSamplingMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.reasoning.effort/client",
        "${app}/transform.rejected.reasoning.effort/server"})
    public void shouldTransformRejectedReasoningEffort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.reasoning.budget/client",
        "${app}/transform.rejected.reasoning.budget/server"})
    public void shouldTransformRejectedReasoningBudget() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.reasoning.residue/client",
        "${app}/openai.responses.request.reasoning.residue/server"})
    public void shouldForwardOpenaiResponsesRequestReasoningResidue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.reasoning.residue.only/client",
        "${app}/openai.responses.request.reasoning.residue.only/server"})
    public void shouldForwardOpenaiResponsesRequestReasoningResidueOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.reasoning.residue.split/client",
        "${app}/openai.responses.request.reasoning.residue.split/server"})
    public void shouldForwardOpenaiResponsesRequestReasoningResidueSplit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.reasoning.absent/client",
        "${app}/openai.responses.request.reasoning.absent/server"})
    public void shouldForwardOpenaiResponsesRequestReasoningAbsent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.reasoning.thinking.residue/client",
        "${app}/anthropic.request.reasoning.thinking.residue/server"})
    public void shouldForwardAnthropicRequestReasoningThinkingResidue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.reasoning.budget.residue/client",
        "${app}/anthropic.request.reasoning.budget.residue/server"})
    public void shouldForwardAnthropicRequestReasoningBudgetResidue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.reasoning.enabled.only/client",
        "${app}/anthropic.request.reasoning.enabled.only/server"})
    public void shouldForwardAnthropicRequestReasoningEnabledOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.reasoning.disabled.effort/client",
        "${app}/anthropic.request.reasoning.disabled.effort/server"})
    public void shouldForwardAnthropicRequestReasoningDisabledEffort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.reasoning.level/client",
        "${app}/gemini.request.reasoning.level/server"})
    public void shouldForwardGeminiRequestReasoningLevel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.reasoning.budget/client",
        "${app}/gemini.request.reasoning.budget/server"})
    public void shouldForwardGeminiRequestReasoningBudget() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.reasoning.budget.automatic/client",
        "${app}/gemini.request.reasoning.budget.automatic/server"})
    public void shouldForwardGeminiRequestReasoningBudgetAutomatic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.reasoning.residue.only/client",
        "${app}/gemini.request.reasoning.residue.only/server"})
    public void shouldForwardGeminiRequestReasoningResidueOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.reasoning.empty/client",
        "${app}/gemini.request.reasoning.empty/server"})
    public void shouldForwardGeminiRequestReasoningEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.reasoning.level.residue.first/client",
        "${app}/gemini.request.reasoning.level.residue.first/server"})
    public void shouldForwardGeminiRequestReasoningLevelResidueFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.reasoning.budget.zero/client",
        "${app}/gemini.request.reasoning.budget.zero/server"})
    public void shouldForwardGeminiRequestReasoningBudgetZero() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.reasoning.snake/client",
        "${app}/gemini.request.reasoning.snake/server"})
    public void shouldForwardGeminiRequestReasoningSnake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.reasoning.sampling/client",
        "${app}/gemini.request.reasoning.sampling/server"})
    public void shouldForwardGeminiRequestReasoningSampling() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.user/client",
        "${app}/transform.request.user/server"})
    public void shouldTransformRequestUser() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.service.tier/client",
        "${app}/transform.request.service.tier/server"})
    public void shouldTransformRequestServiceTier() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.request.user/client",
        "${app}/openai.request.user/server"})
    public void shouldForwardOpenaiRequestUser() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.request.service.tier/client",
        "${app}/openai.request.service.tier/server"})
    public void shouldForwardOpenaiRequestServiceTier() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.request.service.tier.residue/client",
        "${app}/openai.request.service.tier.residue/server"})
    public void shouldForwardOpenaiRequestServiceTierResidue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.user/client",
        "${app}/openai.responses.request.user/server"})
    public void shouldForwardOpenaiResponsesRequestUser() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.service.tier/client",
        "${app}/openai.responses.request.service.tier/server"})
    public void shouldForwardOpenaiResponsesRequestServiceTier() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.service.tier.residue/client",
        "${app}/openai.responses.request.service.tier.residue/server"})
    public void shouldForwardOpenaiResponsesRequestServiceTierResidue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.user/client",
        "${app}/anthropic.request.user/server"})
    public void shouldForwardAnthropicRequestUser() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.user.residue/client",
        "${app}/anthropic.request.user.residue/server"})
    public void shouldForwardAnthropicRequestUserResidue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.service.tier/client",
        "${app}/anthropic.request.service.tier/server"})
    public void shouldForwardAnthropicRequestServiceTier() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.service.tier.residue/client",
        "${app}/anthropic.request.service.tier.residue/server"})
    public void shouldForwardAnthropicRequestServiceTierResidue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.service.tier/client",
        "${app}/gemini.request.service.tier/server"})
    public void shouldForwardGeminiRequestServiceTier() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.service.tier.residue/client",
        "${app}/gemini.request.service.tier.residue/server"})
    public void shouldForwardGeminiRequestServiceTierResidue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.cache.breakpoint/client",
        "${app}/transform.request.cache.breakpoint/server"})
    public void shouldForwardTransformRequestCacheBreakpoint() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.cache.breakpoint.default/client",
        "${app}/transform.request.cache.breakpoint.default/server"})
    public void shouldForwardTransformRequestCacheBreakpointDefault() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.cache.breakpoint.mode/client",
        "${app}/transform.request.cache.breakpoint.mode/server"})
    public void shouldForwardTransformRequestCacheBreakpointMode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.cache.breakpoint.limit/client",
        "${app}/transform.request.cache.breakpoint.limit/server"})
    public void shouldForwardTransformRequestCacheBreakpointLimit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.tool.definition.strict/client",
        "${app}/anthropic.section.tool.definition.strict/server"})
    public void shouldForwardAnthropicSectionToolDefinitionStrict() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.image.cache.control/client",
        "${app}/anthropic.section.user.image.cache.control/server"})
    public void shouldForwardAnthropicSectionUserImageCacheControl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.document.citations/client",
        "${app}/anthropic.section.user.document.citations/server"})
    public void shouldForwardAnthropicSectionUserDocumentCitations() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.tool.definition.strict/client",
        "${app}/transform.tool.definition.strict/server"})
    public void shouldForwardTransformToolDefinitionStrict() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.user.document.citations/client",
        "${app}/transform.user.document.citations/server"})
    public void shouldForwardTransformUserDocumentCitations() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.assistant.reasoning.redacted/client",
        "${app}/transform.assistant.reasoning.redacted/server"})
    public void shouldForwardTransformAssistantReasoningRedacted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.assistant.reasoning.redacted/client",
        "${app}/gemini.transform.assistant.reasoning.redacted/server"})
    public void shouldForwardGeminiTransformAssistantReasoningRedacted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.citations.response.format/client",
        "${app}/transform.rejected.citations.response.format/server"})
    public void shouldForwardTransformRejectedCitationsResponseFormat() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.response.format.citations/client",
        "${app}/transform.rejected.response.format.citations/server"})
    public void shouldForwardTransformRejectedResponseFormatCitations() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.transform.user.document.citations/client",
        "${app}/gemini.transform.user.document.citations/server"})
    public void shouldForwardGeminiTransformUserDocumentCitations() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.text.cache.control.split/client",
        "${app}/anthropic.section.user.text.cache.control.split/server"})
    public void shouldForwardAnthropicSectionUserTextCacheControlSplit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.text.held.close/client",
        "${app}/anthropic.section.user.text.held.close/server"})
    public void shouldForwardAnthropicSectionUserTextHeldClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.text.cache.control.fragmented/client",
        "${app}/anthropic.section.user.text.cache.control.fragmented/server"})
    public void shouldForwardAnthropicSectionUserTextCacheControlFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.text.cache.control.10k/client",
        "${app}/anthropic.section.user.text.cache.control.10k/server"})
    public void shouldForwardAnthropicSectionUserTextCacheControl10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.section.user.text.cache.control.window/client",
        "${app}/anthropic.section.user.text.cache.control.window/server"})
    public void shouldForwardAnthropicSectionUserTextCacheControlWindow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @ScriptProperty({"model \"claude-3-opus-20240229xx\""})
    @Specification({
        "${app}/anthropic.section.user.text.cache.control.window/client",
        "${app}/anthropic.section.user.text.cache.control.window/server"})
    public void shouldForwardAnthropicSectionUserTextCacheControlWindowModelPlus2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.member/client",
        "${app}/transform.request.member/server"})
    public void shouldTransformRequestMember() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.rejected.member/client",
        "${app}/transform.rejected.member/server"})
    public void shouldTransformRejectedMember() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.request.shared.members/client",
        "${app}/transform.request.shared.members/server"})
    public void shouldTransformRequestSharedMembers() throws Exception
        "${app}/openai.transform.request.shared.members/client",
        "${app}/openai.transform.request.shared.members/server"})
    public void shouldOpenaiTransformRequestSharedMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.request.controls.split/client",
        "${app}/openai.request.controls.split/server"})
    public void shouldForwardOpenaiRequestControlsSplit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.request.controls.split/client",
        "${app}/openai.responses.request.controls.split/server"})
    public void shouldForwardOpenaiResponsesRequestControlsSplit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.request.controls.split/client",
        "${app}/anthropic.request.controls.split/server"})
    public void shouldForwardAnthropicRequestControlsSplit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.request.controls.split/client",
        "${app}/gemini.request.controls.split/server"})
    public void shouldForwardGeminiRequestControlsSplit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.controls.many/client",
        "${app}/openai.response.controls.many/server"})
    public void shouldOpenaiResponseControlsMany() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.controls.many.stream/client",
        "${app}/openai.response.controls.many.stream/server"})
    public void shouldOpenaiResponseControlsManyStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.controls.grouped/client",
        "${app}/openai.response.controls.grouped/server"})
    public void shouldOpenaiResponseControlsGrouped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.controls.many/client",
        "${app}/anthropic.response.controls.many/server"})
    public void shouldAnthropicResponseControlsMany() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.controls.many.stream/client",
        "${app}/anthropic.response.controls.many.stream/server"})
    public void shouldAnthropicResponseControlsManyStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.controls.grouped/client",
        "${app}/anthropic.response.controls.grouped/server"})
    public void shouldAnthropicResponseControlsGrouped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.response.controls.many/client",
        "${app}/gemini.response.controls.many/server"})
    public void shouldGeminiResponseControlsMany() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.response.controls.many.stream/client",
        "${app}/gemini.response.controls.many.stream/server"})
    public void shouldGeminiResponseControlsManyStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gemini.response.controls.grouped/client",
        "${app}/gemini.response.controls.grouped/server"})
    public void shouldGeminiResponseControlsGrouped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.response.controls.many/client",
        "${app}/openai.responses.response.controls.many/server"})
    public void shouldOpenaiResponsesResponseControlsMany() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.response.controls.many.stream/client",
        "${app}/openai.responses.response.controls.many.stream/server"})
    public void shouldOpenaiResponsesResponseControlsManyStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.responses.response.controls.grouped/client",
        "${app}/openai.responses.response.controls.grouped/server"})
    public void shouldOpenaiResponsesResponseControlsGrouped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.streaming.controls.many/client",
        "${app}/openai.streaming.controls.many/server"})
    public void shouldOpenaiStreamingControlsMany() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.streaming.controls.many/client",
        "${app}/anthropic.streaming.controls.many/server"})
    public void shouldAnthropicStreamingControlsMany() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.controls.message.stream/client",
        "${app}/openai.response.controls.message.stream/server"})
    public void shouldOpenaiResponseControlsMessageStream() throws Exception
        "${app}/transform.rejected.prefixed.member/client",
        "${app}/transform.rejected.prefixed.member/server"})
    public void shouldTransformRejectedPrefixedMember() throws Exception
    {
        k3po.finish();
    }
}
