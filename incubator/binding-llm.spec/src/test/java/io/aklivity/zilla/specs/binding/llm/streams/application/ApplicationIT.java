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
        "${app}/canonical.block.single/client",
        "${app}/canonical.block.single/server"})
    public void shouldCanonicalBlockSingle() throws Exception
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
        "${app}/openai.response.decode.options/client",
        "${app}/openai.response.decode.options/server"})
    public void shouldOpenaiResponseDecodeOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.decode.text/client",
        "${app}/openai.response.decode.text/server"})
    public void shouldDecodeOpenaiResponseText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.decode.text.and.tool.call/client",
        "${app}/openai.response.decode.text.and.tool.call/server"})
    public void shouldDecodeOpenaiResponseTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.decode.refusal/client",
        "${app}/openai.response.decode.refusal/server"})
    public void shouldDecodeOpenaiResponseRefusal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.decode.streaming/client",
        "${app}/openai.response.decode.streaming/server"})
    public void shouldDecodeOpenaiResponseStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.decode.streaming.tool.call/client",
        "${app}/openai.response.decode.streaming.tool.call/server"})
    public void shouldDecodeOpenaiResponseStreamingToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.decode.streaming.usage/client",
        "${app}/openai.response.decode.streaming.usage/server"})
    public void shouldDecodeOpenaiResponseStreamingUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.decode.streaming.error/client",
        "${app}/openai.response.decode.streaming.error/server"})
    public void shouldDecodeOpenaiResponseStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.decode.text.100k/client",
        "${app}/openai.response.decode.text.100k/server"})
    public void shouldDecodeOpenaiResponseText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.encode.options/client",
        "${app}/openai.response.encode.options/server"})
    public void shouldOpenaiResponseEncodeOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.encode.text/client",
        "${app}/openai.response.encode.text/server"})
    public void shouldEncodeOpenaiResponseText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.encode.text.and.tool.call/client",
        "${app}/openai.response.encode.text.and.tool.call/server"})
    public void shouldEncodeOpenaiResponseTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.encode.refusal/client",
        "${app}/openai.response.encode.refusal/server"})
    public void shouldEncodeOpenaiResponseRefusal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.encode.streaming/client",
        "${app}/openai.response.encode.streaming/server"})
    public void shouldEncodeOpenaiResponseStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.encode.streaming.tool.call/client",
        "${app}/openai.response.encode.streaming.tool.call/server"})
    public void shouldEncodeOpenaiResponseStreamingToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.encode.streaming.error/client",
        "${app}/openai.response.encode.streaming.error/server"})
    public void shouldEncodeOpenaiResponseStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.encode.text.100k/client",
        "${app}/openai.response.encode.text.100k/server"})
    public void shouldEncodeOpenaiResponseText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.encode.reasoning/client",
        "${app}/openai.response.encode.reasoning/server"})
    public void shouldEncodeOpenaiResponseReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.decode.text.escaped/client",
        "${app}/openai.response.decode.text.escaped/server"})
    public void shouldDecodeOpenaiResponseTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.encode.text.escaped/client",
        "${app}/openai.response.encode.text.escaped/server"})
    public void shouldEncodeOpenaiResponseTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.encode.text.multibyte/client",
        "${app}/openai.response.encode.text.multibyte/server"})
    public void shouldEncodeOpenaiResponseTextMultibyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.encode.streaming.multibyte/client",
        "${app}/openai.response.encode.streaming.multibyte/server"})
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
        "${app}/anthropic.response.decode.reasoning/client",
        "${app}/anthropic.response.decode.reasoning/server"})
    public void shouldDecodeAnthropicResponseReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.decode.streaming/client",
        "${app}/anthropic.response.decode.streaming/server"})
    public void shouldDecodeAnthropicResponseStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.decode.streaming.error/client",
        "${app}/anthropic.response.decode.streaming.error/server"})
    public void shouldDecodeAnthropicResponseStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.decode.streaming.tool.call/client",
        "${app}/anthropic.response.decode.streaming.tool.call/server"})
    public void shouldDecodeAnthropicResponseStreamingToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.decode.options/client",
        "${app}/anthropic.response.decode.options/server"})
    public void shouldAnthropicResponseDecodeOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.decode.text/client",
        "${app}/anthropic.response.decode.text/server"})
    public void shouldDecodeAnthropicResponseText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.decode.text.100k/client",
        "${app}/anthropic.response.decode.text.100k/server"})
    public void shouldDecodeAnthropicResponseText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.decode.text.and.tool.call/client",
        "${app}/anthropic.response.decode.text.and.tool.call/server"})
    public void shouldDecodeAnthropicResponseTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.decode.text.escaped/client",
        "${app}/anthropic.response.decode.text.escaped/server"})
    public void shouldDecodeAnthropicResponseTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.encode.reasoning/client",
        "${app}/anthropic.response.encode.reasoning/server"})
    public void shouldEncodeAnthropicResponseReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.encode.refusal/client",
        "${app}/anthropic.response.encode.refusal/server"})
    public void shouldEncodeAnthropicResponseRefusal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.encode.streaming/client",
        "${app}/anthropic.response.encode.streaming/server"})
    public void shouldEncodeAnthropicResponseStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.encode.streaming.error/client",
        "${app}/anthropic.response.encode.streaming.error/server"})
    public void shouldEncodeAnthropicResponseStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.encode.streaming.multibyte/client",
        "${app}/anthropic.response.encode.streaming.multibyte/server"})
    public void shouldEncodeAnthropicResponseStreamingMultibyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.encode.streaming.tool.call/client",
        "${app}/anthropic.response.encode.streaming.tool.call/server"})
    public void shouldEncodeAnthropicResponseStreamingToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.encode.options/client",
        "${app}/anthropic.response.encode.options/server"})
    public void shouldAnthropicResponseEncodeOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.encode.text/client",
        "${app}/anthropic.response.encode.text/server"})
    public void shouldEncodeAnthropicResponseText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.encode.text.100k/client",
        "${app}/anthropic.response.encode.text.100k/server"})
    public void shouldEncodeAnthropicResponseText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.encode.text.and.tool.call/client",
        "${app}/anthropic.response.encode.text.and.tool.call/server"})
    public void shouldEncodeAnthropicResponseTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.encode.text.escaped/client",
        "${app}/anthropic.response.encode.text.escaped/server"})
    public void shouldEncodeAnthropicResponseTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.encode.text.multibyte/client",
        "${app}/anthropic.response.encode.text.multibyte/server"})
    public void shouldEncodeAnthropicResponseTextMultibyte() throws Exception
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
        "${app}/openai.transform.rejected.tool.call.arguments/client",
        "${app}/openai.transform.rejected.tool.call.arguments/server"})
    public void shouldAnthropicTransformOpenaiRejectedToolCallArguments() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.rejected.tool.definition.name/client",
        "${app}/openai.transform.rejected.tool.definition.name/server"})
    public void shouldAnthropicTransformOpenaiRejectedToolDefinitionName() throws Exception
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
        "${app}/anthropic.transform.rejected.tool.call.name/client",
        "${app}/anthropic.transform.rejected.tool.call.name/server"})
    public void shouldOpenaiTransformAnthropicRejectedToolCallName() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.rejected.tool.definition.server/client",
        "${app}/anthropic.transform.rejected.tool.definition.server/server"})
    public void shouldOpenaiTransformAnthropicRejectedToolDefinitionServer() throws Exception
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
        "${app}/openai.response.decode.streaming.logprobs/client",
        "${app}/openai.response.decode.streaming.logprobs/server"})
    public void shouldDecodeOpenaiResponseStreamingLogprobs() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.decode.text.logprobs/client",
        "${app}/openai.response.decode.text.logprobs/server"})
    public void shouldDecodeOpenaiResponseTextLogprobs() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.encode.streaming.logprobs/client",
        "${app}/openai.response.encode.streaming.logprobs/server"})
    public void shouldEncodeOpenaiResponseStreamingLogprobs() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.encode.text.logprobs/client",
        "${app}/openai.response.encode.text.logprobs/server"})
    public void shouldEncodeOpenaiResponseTextLogprobs() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.encode.streaming.logprobs.foreign/client",
        "${app}/openai.response.encode.streaming.logprobs.foreign/server"})
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
        "${app}/openai.response.encode.members/client",
        "${app}/openai.response.encode.members/server"})
    public void shouldEncodeOpenaiResponseMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.response.encode.streaming.members/client",
        "${app}/anthropic.response.encode.streaming.members/server"})
    public void shouldEncodeAnthropicResponseStreamingMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.encode.streaming.members/client",
        "${app}/openai.response.encode.streaming.members/server"})
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
        "${app}/openai.transform.user.image/client",
        "${app}/openai.transform.user.image/server"})
    public void shouldCarryAnthropicTransformOpenaiUserImage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.user.document/client",
        "${app}/openai.transform.user.document/server"})
    public void shouldCarryAnthropicTransformOpenaiUserDocument() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.assistant.text.and.tool.call/client",
        "${app}/openai.transform.assistant.text.and.tool.call/server"})
    public void shouldCarryAnthropicTransformOpenaiAssistantTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.tool.definition/client",
        "${app}/openai.transform.tool.definition/server"})
    public void shouldCarryAnthropicTransformOpenaiToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.mixed.content/client",
        "${app}/openai.transform.mixed.content/server"})
    public void shouldCarryAnthropicTransformOpenaiMixedContent() throws Exception
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
        "${app}/openai.transform.user.image.inline/client",
        "${app}/openai.transform.user.image.inline/server"})
    public void shouldCarryAnthropicTransformOpenaiUserImageInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.user.document.inline/client",
        "${app}/openai.transform.user.document.inline/server"})
    public void shouldCarryAnthropicTransformOpenaiUserDocumentInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.user.image.10k/client",
        "${app}/openai.transform.user.image.10k/server"})
    public void shouldCarryAnthropicTransformOpenaiUserImage10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.user.image.url.10k/client",
        "${app}/openai.transform.user.image.url.10k/server"})
    public void shouldCarryAnthropicTransformOpenaiUserImageUrl10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.user.image.url.100k/client",
        "${app}/openai.transform.user.image.url.100k/server"})
    public void shouldCarryAnthropicTransformOpenaiUserImageUrl100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.tool.call/client",
        "${app}/openai.transform.tool.call/server"})
    public void shouldCarryAnthropicTransformOpenaiToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.tool.call.100k/client",
        "${app}/openai.transform.tool.call.100k/server"})
    public void shouldCarryAnthropicTransformOpenaiToolCall100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.tool.call.10k/client",
        "${app}/openai.transform.tool.call.10k/server"})
    public void shouldCarryAnthropicTransformOpenaiToolCall10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.tool.call.escaped/client",
        "${app}/openai.transform.tool.call.escaped/server"})
    public void shouldCarryAnthropicTransformOpenaiToolCallEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.tool.call.id.last/client",
        "${app}/openai.transform.tool.call.id.last/server"})
    public void shouldCarryAnthropicTransformOpenaiToolCallIdLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.tool.call.name.last/client",
        "${app}/openai.transform.tool.call.name.last/server"})
    public void shouldCarryAnthropicTransformOpenaiToolCallNameLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.tool.definition.100k/client",
        "${app}/openai.transform.tool.definition.100k/server"})
    public void shouldCarryAnthropicTransformOpenaiToolDefinition100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.tool.definition.10k/client",
        "${app}/openai.transform.tool.definition.10k/server"})
    public void shouldCarryAnthropicTransformOpenaiToolDefinition10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.tool.definition.name.last/client",
        "${app}/openai.transform.tool.definition.name.last/server"})
    public void shouldCarryAnthropicTransformOpenaiToolDefinitionNameLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.tool.definition.parameters.100k/client",
        "${app}/openai.transform.tool.definition.parameters.100k/server"})
    public void shouldCarryAnthropicTransformOpenaiToolDefinitionParameters100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transform.tool.definition.parameters.10k/client",
        "${app}/openai.transform.tool.definition.parameters.10k/server"})
    public void shouldCarryAnthropicTransformOpenaiToolDefinitionParameters10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.user.image/client",
        "${app}/anthropic.transform.user.image/server"})
    public void shouldCarryOpenaiTransformAnthropicUserImage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.assistant.tool.call/client",
        "${app}/anthropic.transform.assistant.tool.call/server"})
    public void shouldCarryOpenaiTransformAnthropicAssistantToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.tool.definition/client",
        "${app}/anthropic.transform.tool.definition/server"})
    public void shouldCarryOpenaiTransformAnthropicToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.mixed.content/client",
        "${app}/anthropic.transform.mixed.content/server"})
    public void shouldCarryOpenaiTransformAnthropicMixedContent() throws Exception
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
        "${app}/anthropic.transform.user.image.inline/client",
        "${app}/anthropic.transform.user.image.inline/server"})
    public void shouldCarryOpenaiTransformAnthropicUserImageInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.user.document.inline/client",
        "${app}/anthropic.transform.user.document.inline/server"})
    public void shouldCarryOpenaiTransformAnthropicUserDocumentInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.user.document.reference/client",
        "${app}/anthropic.transform.user.document.reference/server"})
    public void shouldCarryOpenaiTransformAnthropicUserDocumentReference() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.user.image.10k/client",
        "${app}/anthropic.transform.user.image.10k/server"})
    public void shouldCarryOpenaiTransformAnthropicUserImage10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.user.image.url.10k/client",
        "${app}/anthropic.transform.user.image.url.10k/server"})
    public void shouldCarryOpenaiTransformAnthropicUserImageUrl10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.user.image.url.100k/client",
        "${app}/anthropic.transform.user.image.url.100k/server"})
    public void shouldCarryOpenaiTransformAnthropicUserImageUrl100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.assistant.tool.call.100k/client",
        "${app}/anthropic.transform.assistant.tool.call.100k/server"})
    public void shouldCarryOpenaiTransformAnthropicAssistantToolCall100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.assistant.tool.call.10k/client",
        "${app}/anthropic.transform.assistant.tool.call.10k/server"})
    public void shouldCarryOpenaiTransformAnthropicAssistantToolCall10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.assistant.tool.call.escaped/client",
        "${app}/anthropic.transform.assistant.tool.call.escaped/server"})
    public void shouldCarryOpenaiTransformAnthropicAssistantToolCallEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.assistant.tool.call.id.last/client",
        "${app}/anthropic.transform.assistant.tool.call.id.last/server"})
    public void shouldCarryOpenaiTransformAnthropicAssistantToolCallIdLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.assistant.tool.call.name.last/client",
        "${app}/anthropic.transform.assistant.tool.call.name.last/server"})
    public void shouldCarryOpenaiTransformAnthropicAssistantToolCallNameLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.tool.definition.100k/client",
        "${app}/anthropic.transform.tool.definition.100k/server"})
    public void shouldCarryOpenaiTransformAnthropicToolDefinition100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.tool.definition.10k/client",
        "${app}/anthropic.transform.tool.definition.10k/server"})
    public void shouldCarryOpenaiTransformAnthropicToolDefinition10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.tool.definition.name.last/client",
        "${app}/anthropic.transform.tool.definition.name.last/server"})
    public void shouldCarryOpenaiTransformAnthropicToolDefinitionNameLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.tool.definition.parameters.100k/client",
        "${app}/anthropic.transform.tool.definition.parameters.100k/server"})
    public void shouldCarryOpenaiTransformAnthropicToolDefinitionParameters100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transform.tool.definition.parameters.10k/client",
        "${app}/anthropic.transform.tool.definition.parameters.10k/server"})
    public void shouldCarryOpenaiTransformAnthropicToolDefinitionParameters10k() throws Exception
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
        "${app}/transform.assistant.text/client",
        "${app}/transform.assistant.text/server"})
    public void shouldTransformAssistantText() throws Exception
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
        "${app}/transform.request.options/client",
        "${app}/transform.request.options/server"})
    public void shouldTransformRequestOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/transform.response.options/client",
        "${app}/transform.response.options/server"})
    public void shouldTransformResponseOptions() throws Exception
    {
        k3po.finish();
    }
}
