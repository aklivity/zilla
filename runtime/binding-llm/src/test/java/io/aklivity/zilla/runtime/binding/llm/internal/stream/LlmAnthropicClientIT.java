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
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class LlmAnthropicClientIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/llm/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/llm/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/llm/config")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.streaming.usage/client",
        "${net}/anthropic.streaming.usage/server"})
    public void shouldForwardAnthropicStreamingUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.streaming.usage.padded/client",
        "${net}/anthropic.streaming.usage/server"})
    public void shouldForwardAnthropicStreamingUsagePadded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.streaming.abort/client",
        "${net}/anthropic.streaming.abort/server"})
    public void shouldAbortAnthropicStreamingWithPartialUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.usage/client",
        "${net}/anthropic.usage/server"})
    public void shouldForwardAnthropicNonstreamingUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.usage.padded/client",
        "${net}/anthropic.usage/server"})
    public void shouldForwardAnthropicNonstreamingUsagePadded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic/client",
        "${net}/anthropic/server"})
    public void shouldForwardAnthropicNonstreamingSameDialect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.10k/client",
        "${net}/anthropic.10k/server"})
    public void shouldForwardAnthropic10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.100k/client",
        "${net}/anthropic.100k/server"})
    public void shouldForwardAnthropic100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.streaming.10k/client",
        "${net}/anthropic.streaming.10k/server"})
    public void shouldForwardAnthropicStreaming10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.streaming.100k/client",
        "${net}/anthropic.streaming.100k/server"})
    public void shouldForwardAnthropicStreaming100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.rejected.rate.limit/client",
        "${net}/anthropic.rejected.rate.limit/server"})
    public void shouldResetAnthropicRequestRateLimited() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.reasoning.redacted/client",
        "${net}/anthropic.section.assistant.reasoning.redacted/server"})
    public void shouldEncodeAnthropicSectionAssistantReasoningRedacted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.text/client",
        "${net}/anthropic.section.assistant.text/server"})
    public void shouldEncodeAnthropicSectionAssistantText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.tool.result.cache.control.part.first/client",
        "${net}/anthropic.section.tool.result.cache.control.part.first/server"})
    public void shouldEncodeAnthropicSectionToolResultCacheControlPartFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.tool.result.cache.control.first/client",
        "${net}/anthropic.section.tool.result.cache.control.first/server"})
    public void shouldEncodeAnthropicSectionToolResultCacheControlFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.tool.result.cache.control.part/client",
        "${net}/anthropic.section.tool.result.cache.control.part/server"})
    public void shouldEncodeAnthropicSectionToolResultCacheControlPart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.tool.result.cache.control/client",
        "${net}/anthropic.section.tool.result.cache.control/server"})
    public void shouldEncodeAnthropicSectionToolResultCacheControl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.tool.definition.cache.control/client",
        "${net}/anthropic.section.tool.definition.cache.control/server"})
    public void shouldEncodeAnthropicSectionToolDefinitionCacheControl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.other.blocks/client",
        "${net}/anthropic.section.other.blocks/server"})
    public void shouldEncodeAnthropicSectionOtherBlocks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.system.instruction.cache.control/client",
        "${net}/anthropic.section.system.instruction.cache.control/server"})
    public void shouldEncodeAnthropicSectionSystemInstructionCacheControl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.user.text.cache.control.first/client",
        "${net}/anthropic.section.user.text.cache.control.first/server"})
    public void shouldEncodeAnthropicSectionUserTextCacheControlFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.user.text.cache.control/client",
        "${net}/anthropic.section.user.text.cache.control/server"})
    public void shouldEncodeAnthropicSectionUserTextCacheControl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.text.and.tool.call/client",
        "${net}/anthropic.section.assistant.text.and.tool.call/server"})
    public void shouldEncodeAnthropicSectionAssistantTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.text.citations/client",
        "${net}/anthropic.section.assistant.text.citations/server"})
    public void shouldEncodeAnthropicSectionAssistantTextCitations() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.tool.call/client",
        "${net}/anthropic.section.assistant.tool.call/server"})
    public void shouldEncodeAnthropicSectionAssistantToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.conversation/client",
        "${net}/anthropic.section.conversation/server"})
    public void shouldEncodeAnthropicSectionConversation() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.mixed.content/client",
        "${net}/anthropic.section.mixed.content/server"})
    public void shouldEncodeAnthropicSectionMixedContent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.server.tool.result/client",
        "${net}/anthropic.section.server.tool.result/server"})
    public void shouldEncodeAnthropicSectionServerToolResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.system.instruction/client",
        "${net}/anthropic.section.system.instruction/server"})
    public void shouldEncodeAnthropicSectionSystemInstruction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.system.instruction.blocks/client",
        "${net}/anthropic.section.system.instruction.blocks/server"})
    public void shouldEncodeAnthropicSectionSystemInstructionBlocks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.tool.definition/client",
        "${net}/anthropic.section.tool.definition/server"})
    public void shouldEncodeAnthropicSectionToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.tool.result/client",
        "${net}/anthropic.section.tool.result/server"})
    public void shouldEncodeAnthropicSectionToolResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.tool.result.100k/client",
        "${net}/anthropic.section.tool.result.100k/server"})
    public void shouldEncodeAnthropicSectionToolResult100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.tool.result.blocks/client",
        "${net}/anthropic.section.tool.result.blocks/server"})
    public void shouldEncodeAnthropicSectionToolResultBlocks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.unknown.part/client",
        "${net}/anthropic.section.unknown.part/server"})
    public void shouldEncodeAnthropicSectionUnknownPart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.user.document/client",
        "${net}/anthropic.section.user.document/server"})
    public void shouldEncodeAnthropicSectionUserDocument() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.user.document.title.context/client",
        "${net}/anthropic.section.user.document.title.context/server"})
    public void shouldEncodeAnthropicSectionUserDocumentTitleContext() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.user.document.title.context.first/client",
        "${net}/anthropic.section.user.document.title.context.first/server"})
    public void shouldEncodeAnthropicSectionUserDocumentTitleContextFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.user.image/client",
        "${net}/anthropic.section.user.image/server"})
    public void shouldEncodeAnthropicSectionUserImage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.output.config/client",
        "${net}/anthropic.request.output.config/server"})
    public void shouldEncodeAnthropicRequestOutputConfig() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.output.config.10k/client",
        "${net}/anthropic.request.output.config.10k/server"})
    public void shouldEncodeAnthropicRequestOutputConfig10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.output.config.100k/client",
        "${net}/anthropic.request.output.config.100k/server"})
    public void shouldEncodeAnthropicRequestOutputConfig100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.options/client",
        "${net}/anthropic.request.options/server"})
    public void shouldEncodeAnthropicRequestOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.options.leading/client",
        "${net}/anthropic.request.options.leading/server"})
    public void shouldEncodeAnthropicRequestOptionsLeading() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.user.text/client",
        "${net}/anthropic.section.user.text/server"})
    public void shouldEncodeAnthropicSectionUserText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.user.text.100k/client",
        "${net}/anthropic.section.user.text.100k/server"})
    public void shouldEncodeAnthropicSectionUserText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.user.text.10k/client",
        "${net}/anthropic.section.user.text.10k/server"})
    public void shouldEncodeAnthropicSectionUserText10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.user.text.blocks/client",
        "${net}/anthropic.section.user.text.blocks/server"})
    public void shouldEncodeAnthropicSectionUserTextBlocks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.user.text.escaped/client",
        "${net}/anthropic.section.user.text.escaped/server"})
    public void shouldEncodeAnthropicSectionUserTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.user.text.multibyte/client",
        "${net}/anthropic.section.user.text.multibyte/server"})
    public void shouldEncodeAnthropicSectionUserTextMultibyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.user.image/client",
        "${net}/anthropic.transform.user.image/server"})
    public void shouldTransformUserImageAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.user.document.reference/client",
        "${net}/anthropic.transform.user.document.reference/server"})
    public void shouldTransformUserDocumentReferenceAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.assistant.text.and.tool.call/client",
        "${net}/anthropic.transform.assistant.text.and.tool.call/server"})
    public void shouldTransformAssistantTextAndToolCallAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.tool.definition/client",
        "${net}/anthropic.transform.tool.definition/server"})
    public void shouldTransformToolDefinitionAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.mixed.content/client",
        "${net}/anthropic.transform.mixed.content/server"})
    public void shouldTransformMixedContentAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/openai.transform.conversation/client",
        "${net}/anthropic.transform.conversation/server"})
    public void shouldTransformOpenaiSectionConversationAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.user.image.inline/client",
        "${net}/anthropic.section.user.image.inline/server"})
    public void shouldEncodeAnthropicSectionUserImageInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.user.image.reference/client",
        "${net}/anthropic.section.user.image.reference/server"})
    public void shouldEncodeAnthropicSectionUserImageReference() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.user.document.inline/client",
        "${net}/anthropic.section.user.document.inline/server"})
    public void shouldEncodeAnthropicSectionUserDocumentInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.tool.result.error/client",
        "${net}/anthropic.section.tool.result.error/server"})
    public void shouldEncodeAnthropicSectionToolResultError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.tool.result.error.last/client",
        "${net}/anthropic.section.tool.result.error.last/server"})
    public void shouldEncodeAnthropicSectionToolResultErrorLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.tool.definition.name.last/client",
        "${net}/anthropic.section.tool.definition.name.last/server"})
    public void shouldEncodeAnthropicSectionToolDefinitionNameLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.user.document.reference/client",
        "${net}/anthropic.section.user.document.reference/server"})
    public void shouldEncodeAnthropicSectionUserDocumentReference() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.user.image.10k/client",
        "${net}/anthropic.section.user.image.10k/server"})
    public void shouldEncodeAnthropicSectionUserImage10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.user.image.inline/client",
        "${net}/anthropic.transform.user.image.inline/server"})
    public void shouldTransformUserImageInlineAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.user.document.inline/client",
        "${net}/anthropic.transform.user.document.inline/server"})
    public void shouldTransformUserDocumentInlineAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.user.image.10k/client",
        "${net}/anthropic.transform.user.image.10k/server"})
    public void shouldTransformUserImage10kAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.user.image.url.10k/client",
        "${net}/anthropic.section.user.image.url.10k/server"})
    public void shouldEncodeAnthropicSectionUserImageUrl10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.user.image.url.100k/client",
        "${net}/anthropic.section.user.image.url.100k/server"})
    public void shouldEncodeAnthropicSectionUserImageUrl100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.user.image.url.10k/client",
        "${net}/anthropic.transform.user.image.url.10k/server"})
    public void shouldTransformUserImageUrl10kAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.user.image.url.100k/client",
        "${net}/anthropic.transform.user.image.url.100k/server"})
    public void shouldTransformUserImageUrl100kAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.text.streaming/client",
        "${net}/anthropic.section.assistant.text.streaming/server"})
    public void shouldEncodeAnthropicSectionAssistantTextStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.reasoning.streaming/client",
        "${net}/anthropic.section.assistant.reasoning.streaming/server"})
    public void shouldEncodeAnthropicSectionAssistantReasoningStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.reasoning.redacted.streaming/client",
        "${net}/anthropic.section.assistant.reasoning.redacted.streaming/server"})
    public void shouldEncodeAnthropicSectionAssistantReasoningRedactedStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.rejected.streaming/client",
        "${net}/anthropic.rejected.streaming/server"})
    public void shouldEncodeAnthropicRejectedStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.tool.call.streaming/client",
        "${net}/anthropic.section.assistant.tool.call.streaming/server"})
    public void shouldEncodeAnthropicSectionAssistantToolCallStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.text.members/client",
        "${net}/anthropic.section.assistant.text.members/server"})
    public void shouldEncodeAnthropicSectionAssistantTextMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.text.100k/client",
        "${net}/anthropic.section.assistant.text.100k/server"})
    public void shouldEncodeAnthropicSectionAssistantText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.text.escaped/client",
        "${net}/anthropic.section.assistant.text.escaped/server"})
    public void shouldEncodeAnthropicSectionAssistantTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.tool.call.100k/client",
        "${net}/anthropic.section.assistant.tool.call.100k/server"})
    public void shouldEncodeAnthropicSectionAssistantToolCall100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.tool.call.10k/client",
        "${net}/anthropic.section.assistant.tool.call.10k/server"})
    public void shouldEncodeAnthropicSectionAssistantToolCall10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.tool.call.escaped/client",
        "${net}/anthropic.section.assistant.tool.call.escaped/server"})
    public void shouldEncodeAnthropicSectionAssistantToolCallEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.tool.call.id.last/client",
        "${net}/anthropic.section.assistant.tool.call.id.last/server"})
    public void shouldEncodeAnthropicSectionAssistantToolCallIdLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.tool.call.name.last/client",
        "${net}/anthropic.section.assistant.tool.call.name.last/server"})
    public void shouldEncodeAnthropicSectionAssistantToolCallNameLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.tool.definition.100k/client",
        "${net}/anthropic.section.tool.definition.100k/server"})
    public void shouldEncodeAnthropicSectionToolDefinition100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.tool.definition.10k/client",
        "${net}/anthropic.section.tool.definition.10k/server"})
    public void shouldEncodeAnthropicSectionToolDefinition10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.tool.definition.parameters.100k/client",
        "${net}/anthropic.section.tool.definition.parameters.100k/server"})
    public void shouldEncodeAnthropicSectionToolDefinitionParameters100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.tool.definition.parameters.10k/client",
        "${net}/anthropic.section.tool.definition.parameters.10k/server"})
    public void shouldEncodeAnthropicSectionToolDefinitionParameters10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.rejected.tool.call.arguments/client",
        "${net}/anthropic.transform.rejected.tool.call.arguments/server"})
    public void shouldRejectToolCallArgumentsAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.rejected.tool.definition.name/client",
        "${net}/anthropic.transform.rejected.tool.definition.name/server"})
    public void shouldRejectToolDefinitionNameAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.assistant.tool.call/client",
        "${net}/anthropic.transform.assistant.tool.call/server"})
    public void shouldTransformAssistantToolCallAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.assistant.tool.call.100k/client",
        "${net}/anthropic.transform.assistant.tool.call.100k/server"})
    public void shouldTransformAssistantToolCall100kAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.assistant.tool.call.10k/client",
        "${net}/anthropic.transform.assistant.tool.call.10k/server"})
    public void shouldTransformAssistantToolCall10kAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.assistant.tool.call.escaped/client",
        "${net}/anthropic.transform.assistant.tool.call.escaped/server"})
    public void shouldTransformAssistantToolCallEscapedAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.assistant.tool.call.id.last/client",
        "${net}/anthropic.transform.assistant.tool.call.id.last/server"})
    public void shouldTransformAssistantToolCallIdLastAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.assistant.tool.call.name.last/client",
        "${net}/anthropic.transform.assistant.tool.call.name.last/server"})
    public void shouldTransformAssistantToolCallNameLastAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.tool.definition.100k/client",
        "${net}/anthropic.transform.tool.definition.100k/server"})
    public void shouldTransformToolDefinition100kAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.tool.definition.10k/client",
        "${net}/anthropic.transform.tool.definition.10k/server"})
    public void shouldTransformToolDefinition10kAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.tool.definition.name.last/client",
        "${net}/anthropic.transform.tool.definition.name.last/server"})
    public void shouldTransformToolDefinitionNameLastAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.tool.definition.parameters.100k/client",
        "${net}/anthropic.transform.tool.definition.parameters.100k/server"})
    public void shouldTransformToolDefinitionParameters100kAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.tool.definition.parameters.10k/client",
        "${net}/anthropic.transform.tool.definition.parameters.10k/server"})
    public void shouldTransformToolDefinitionParameters10kAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.options.early/client",
        "${net}/anthropic.request.options.early/server"})
    public void shouldEncodeAnthropicRequestOptionsEarly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.streaming.abort.nocredit/client",
        "${net}/anthropic.streaming.abort/server"})
    public void shouldAbortAnthropicStreamingWithPartialUsageWithoutCredit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.beta/client",
        "${net}/anthropic.request.beta/server"})
    public void shouldEncodeAnthropicBetaFromBeginExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.streaming.complete/client",
        "${net}/anthropic.streaming.complete/server"})
    public void shouldMarkAnthropicStreamingEventsComplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/openai.transform.rejected.user.audio/client",
        "${net}/anthropic.transform.rejected.user.audio/server"})
    public void shouldRejectOpenaiSectionUserAudioAsAnthropicNamingSectionAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.user.text/client",
        "${net}/anthropic.transform.user.text/server"})
    public void shouldTransformUserTextAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.system.instruction/client",
        "${net}/anthropic.transform.system.instruction/server"})
    public void shouldTransformSystemInstructionAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.assistant.text/client",
        "${net}/anthropic.transform.assistant.text/server"})
    public void shouldTransformAssistantTextAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.tool.result/client",
        "${net}/anthropic.transform.tool.result/server"})
    public void shouldTransformToolResultAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.assistant.refusal/client",
        "${net}/anthropic.transform.assistant.refusal/server"})
    public void shouldTransformAssistantRefusalAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.response.frames/client",
        "${net}/anthropic.transform.response.frames/server"})
    public void shouldTransformResponseFramesAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.rejected.unreachable/client",
        "${net}/anthropic.rejected.unreachable/server"})
    public void shouldResetAnthropicRequestWhenServerUnreachable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.rejected.user.video/client",
        "${net}/anthropic.transform.rejected.user.video/server"})
    public void shouldRejectUserVideoAsAnthropicNamingSectionAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.reasoning/client",
        "${net}/anthropic.section.assistant.reasoning/server"})
    public void shouldEncodeAnthropicSectionAssistantReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.reasoning.signature.first/client",
        "${net}/anthropic.section.assistant.reasoning.signature.first/server"})
    public void shouldEncodeAnthropicSectionAssistantReasoningSignatureFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.reasoning.10k/client",
        "${net}/anthropic.section.assistant.reasoning.10k/server"})
    public void shouldEncodeAnthropicSectionAssistantReasoning10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.response.text/client",
        "${net}/anthropic.transform.response.text/server"})
    public void shouldTransformResponseTextAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.response.text.escaped/client",
        "${net}/anthropic.transform.response.text.escaped/server"})
    public void shouldTransformResponseTextEscapedAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.response.text.100k/client",
        "${net}/anthropic.transform.response.text.100k/server"})
    public void shouldTransformResponseText100kAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.response.text.and.tool.call/client",
        "${net}/anthropic.transform.response.text.and.tool.call/server"})
    public void shouldTransformResponseTextAndToolCallAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.text.members.streaming/client",
        "${net}/anthropic.section.assistant.text.members.streaming/server"})
    public void shouldEncodeAnthropicSectionAssistantTextMembersStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.request.max.output.tokens/client",
        "${net}/anthropic.transform.request.max.output.tokens/server"})
    public void shouldTransformRequestMaxOutputTokensAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Configure(name = "zilla.binding.llm.anthropic.max.tokens", value = "8192")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.user.text/client",
        "${net}/anthropic.transform.request.max.output.tokens.configured/server"})
    public void shouldTransformRequestMaxOutputTokensConfiguredAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.rejected.error.type/client",
        "${net}/anthropic.rejected.error.type/server"})
    public void shouldPassAnthropicRejectedErrorType() throws Exception
    {
        k3po.finish();
    }
}
