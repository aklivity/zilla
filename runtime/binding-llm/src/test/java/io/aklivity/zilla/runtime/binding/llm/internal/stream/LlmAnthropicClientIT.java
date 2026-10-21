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
        "${app}/anthropic.section.tool.result.cache.control.part/client",
        "${net}/anthropic.section.tool.result.cache.control.part/server"})
    public void shouldEncodeAnthropicSectionToolResultCacheControlPart() throws Exception
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
        "${app}/anthropic.section.server.tool.definition/client",
        "${net}/anthropic.section.server.tool.definition/server"})
    public void shouldEncodeAnthropicSectionServerToolDefinition() throws Exception
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
        "${app}/anthropic.section.assistant.text.citations.url/client",
        "${net}/anthropic.section.assistant.text.citations.url/server"})
    public void shouldEncodeAnthropicSectionAssistantTextCitationsUrl() throws Exception
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
        "${app}/anthropic.section.user.text/client",
        "${net}/anthropic.section.user.text/server"})
    @ScriptProperty({"model \"claude-3-5-sonnet-20241022\""})
    public void shouldEncodeAnthropicSectionUserTextModelOverride() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.user.text.empty/client",
        "${net}/anthropic.section.user.text.empty/server"})
    public void shouldEncodeAnthropicSectionUserTextEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.text.empty/client",
        "${net}/anthropic.section.assistant.text.empty/server"})
    public void shouldEncodeAnthropicSectionAssistantTextEmpty() throws Exception
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
        "${app}/openai.transform.assistant.text.citations/client",
        "${net}/anthropic.transform.assistant.text.citations/server"})
    public void shouldTransformAssistantTextCitationsAsAnthropic() throws Exception
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
    @Specification({
        "${app}/anthropic.streaming.complete/client",
        "${net}/anthropic.streaming.ping/server"})
    public void shouldDropAnthropicStreamingPingAndUnknownEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.control.foreign.request/client",
        "${net}/anthropic.section.user.text/server"})
    public void shouldDropAnthropicForeignRequestControls() throws Exception
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
        "${app}/anthropic.transform.response.frames/client",
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
        "${app}/anthropic.transform.response.text/client",
        "${net}/anthropic.transform.response.text/server"})
    public void shouldTransformResponseTextAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/anthropic.transform.response.text.escaped/client",
        "${net}/anthropic.transform.response.text.escaped/server"})
    public void shouldTransformResponseTextEscapedAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/anthropic.transform.response.text.100k/client",
        "${net}/anthropic.transform.response.text.100k/server"})
    public void shouldTransformResponseText100kAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/anthropic.transform.response.text.and.tool.call/client",
        "${net}/anthropic.transform.response.text.and.tool.call/server"})
    public void shouldTransformResponseTextAndToolCallAsAnthropic() throws Exception
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

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.tool.call.cache.control/client",
        "${net}/anthropic.section.assistant.tool.call.cache.control/server"})
    public void shouldEncodeAnthropicSectionAssistantToolCallCacheControl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/openai.transform.assistant.tool.call.members/client",
        "${net}/anthropic.transform.assistant.tool.call/server"})
    public void shouldDropOpenaiToolCallMembersAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.tool.call.streaming/client",
        "${net}/anthropic.section.assistant.tool.call.streaming.split/server"})
    public void shouldEncodeAnthropicSectionAssistantToolCallStreamingSplit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.tool.call.streaming.error/client",
        "${net}/anthropic.section.assistant.tool.call.streaming.error/server"})
    public void shouldAbortAnthropicSectionAssistantToolCallStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/anthropic.transform.response.text/client",
        "${net}/anthropic.transform.response.text.sse/server"})
    public void shouldTransformResponseTextSseAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/anthropic.transform.response.reasoning/client",
        "${net}/anthropic.transform.response.reasoning.sse/server"})
    public void shouldTransformResponseReasoningSseAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/anthropic.transform.response.text.and.tool.call/client",
        "${net}/anthropic.transform.response.text.and.tool.call.sse/server"})
    public void shouldTransformResponseTextAndToolCallSseAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/anthropic.transform.response.tool.call/client",
        "${net}/anthropic.transform.response.tool.call/server"})
    public void shouldTransformResponseToolCallAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/anthropic.transform.response.tool.call/client",
        "${net}/anthropic.transform.response.tool.call.sse/server"})
    public void shouldTransformResponseToolCallSseAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/anthropic.transform.response.tool.calls/client",
        "${net}/anthropic.transform.response.tool.calls/server"})
    public void shouldTransformResponseToolCallsAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/anthropic.transform.response.tool.calls/client",
        "${net}/anthropic.transform.response.tool.calls.sse/server"})
    public void shouldTransformResponseToolCallsSseAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.response.text.error/client",
        "${net}/anthropic.transform.response.text.error.sse/server"})
    public void shouldTransformResponseTextErrorSseAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.response.tool.call.error/client",
        "${net}/anthropic.transform.response.tool.call.error.sse/server"})
    public void shouldTransformResponseToolCallErrorSseAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/anthropic.transform.response.tool.call.empty/client",
        "${net}/anthropic.transform.response.tool.call.empty/server"})
    public void shouldTransformResponseToolCallEmptyAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/anthropic.transform.response.tool.call.empty/client",
        "${net}/anthropic.transform.response.tool.call.empty.sse/server"})
    public void shouldTransformResponseToolCallEmptySseAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/anthropic.transform.response.finish.reason.stop.sequence/client",
        "${net}/anthropic.transform.response.finish.reason.stop.sequence/server"})
    public void shouldPrefixStopSequenceFinishReasonAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\"", "reason \"anthropic:pause_turn\""})
    @Specification({
        "${app}/anthropic.transform.response.finish.reason/client",
        "${net}/anthropic.transform.response.finish.reason.pause.turn/server"})
    public void shouldPrefixPauseTurnFinishReasonAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/transform.server.tool.definition/client",
        "${net}/anthropic.transform.server.tool.definition/server"})
    public void shouldDropServerToolDefinitionAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai/responses\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.user.text/client",
        "${net}/anthropic.transform.user.text/server"})
    public void shouldTransformUserTextFromOpenaiResponsesAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai/responses\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.system.instruction/client",
        "${net}/anthropic.transform.system.instruction/server"})
    public void shouldTransformSystemInstructionFromOpenaiResponsesAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai/responses\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.assistant.text/client",
        "${net}/anthropic.transform.assistant.text/server"})
    public void shouldTransformAssistantTextFromOpenaiResponsesAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai/responses\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.tool.definition/client",
        "${net}/anthropic.transform.tool.definition/server"})
    public void shouldTransformToolDefinitionFromOpenaiResponsesAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai/responses\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.assistant.tool.call/client",
        "${net}/anthropic.transform.assistant.tool.call/server"})
    public void shouldTransformAssistantToolCallFromOpenaiResponsesAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai/responses\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.tool.result/client",
        "${net}/anthropic.transform.tool.result/server"})
    public void shouldTransformToolResultFromOpenaiResponsesAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai/responses\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.user.image/client",
        "${net}/anthropic.transform.user.image/server"})
    public void shouldTransformUserImageFromOpenaiResponsesAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai/responses\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.user.document.inline/client",
        "${net}/anthropic.transform.user.document.inline/server"})
    public void shouldTransformUserDocumentInlineFromOpenaiResponsesAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai/responses\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.mixed.content/client",
        "${net}/anthropic.transform.mixed.content/server"})
    public void shouldTransformMixedContentFromOpenaiResponsesAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai/responses\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.max.output.tokens/client",
        "${net}/anthropic.transform.request.max.output.tokens/server"})
    public void shouldTransformRequestMaxOutputTokensFromOpenaiResponsesAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai/responses\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.assistant.text.and.tool.call/client",
        "${net}/anthropic.transform.assistant.text.and.tool.call/server"})
    public void shouldTransformAssistantTextAndToolCallFromOpenaiResponsesAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai/responses\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.rejected.user.video/client",
        "${net}/anthropic.transform.rejected.user.video/server"})
    public void shouldRejectUserVideoFromOpenaiResponsesAsAnthropicNamingSectionAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"target \"anthropic\""})
    @Specification({
        "${app}/openai.responses.transform.rejected.user.audio/client",
        "${net}/anthropic.transform.rejected.user.audio/server"})
    public void shouldRejectUserAudioFromOpenaiResponsesAsAnthropicNamingSectionAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.responses.transform.conversation/client",
        "${net}/anthropic.transform.conversation/server"})
    public void shouldTransformConversationFromOpenaiResponsesAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.responses.transform.request.members/client",
        "${net}/anthropic.transform.request.members/server"})
    public void shouldDropPrefixedRequestMembersFromOpenaiResponsesAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.responses.transform.assistant.tool.call.members/client",
        "${net}/anthropic.transform.assistant.tool.call/server"})
    public void shouldDropToolCallMembersFromOpenaiResponsesAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.responses.transform.tool.definition.members/client",
        "${net}/anthropic.transform.tool.definition/server"})
    public void shouldDropToolDefinitionMembersFromOpenaiResponsesAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.responses.transform.assistant.text.citations.members/client",
        "${net}/anthropic.transform.assistant.text.citations/server"})
    public void shouldDropAssistantTextCitationsMembersFromOpenaiResponsesAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.responses.transform.user.document.members/client",
        "${net}/anthropic.transform.user.document.inline/server"})
    public void shouldDropUserDocumentMembersFromOpenaiResponsesAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.responses.transform.server.tool/client",
        "${net}/anthropic.transform.server.tool.definition/server"})
    public void shouldDropServerToolDefinitionAndCallFromOpenaiResponsesAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.responses.transform.assistant.reasoning.members/client",
        "${net}/anthropic.transform.assistant.reasoning.unsigned/server"})
    public void shouldDropAssistantReasoningMembersFromOpenaiResponsesAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({
        "source \"gemini\"",
        "extension \"streamGenerateContent\""})
    @Specification({
        "${app}/transform.request.extension/client",
        "${net}/anthropic.transform.request.extension/server"})
    public void shouldIgnoreBeginExtensionFromAnotherDialect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.sampling/client",
        "${net}/anthropic.transform.request.sampling/server"})
    public void shouldTransformRequestSamplingAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.sampling/client",
        "${net}/anthropic.transform.request.sampling/server"})
    public void shouldTransformRequestSamplingFromAnthropicAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.sampling.top.k/client",
        "${net}/anthropic.transform.request.sampling.top.k/server"})
    public void shouldTransformRequestSamplingTopKAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.sampling.top.k/client",
        "${net}/anthropic.transform.request.sampling.top.k/server"})
    public void shouldTransformRequestSamplingTopKFromAnthropicAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.sampling.stop/client",
        "${net}/anthropic.transform.request.sampling.stop/server"})
    public void shouldTransformRequestSamplingStopAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.sampling.stop/client",
        "${net}/anthropic.transform.request.sampling.stop/server"})
    public void shouldTransformRequestSamplingStopFromAnthropicAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.sampling.seed/client",
        "${net}/anthropic.transform.request.max.output.tokens/server"})
    public void shouldDropRequestSamplingSeedAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.sampling.penalties/client",
        "${net}/anthropic.transform.request.max.output.tokens/server"})
    public void shouldDropRequestSamplingPenaltiesAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.sampling.clamp/client",
        "${net}/anthropic.transform.request.sampling.clamp/server"})
    public void shouldClampRequestSamplingTemperatureAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"gemini\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.sampling.top.k/client",
        "${net}/anthropic.transform.request.sampling.top.k/server"})
    public void shouldTransformRequestSamplingTopKFromGeminiAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"gemini\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.sampling.stop/client",
        "${net}/anthropic.transform.request.sampling.stop/server"})
    public void shouldTransformRequestSamplingStopFromGeminiAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"gemini\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.sampling.clamp/client",
        "${net}/anthropic.transform.request.sampling.clamp/server"})
    public void shouldClampRequestSamplingTemperatureFromGeminiAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.tool.choice.auto/client",
        "${net}/anthropic.transform.request.tool.choice.auto/server"})
    public void shouldTransformRequestToolChoiceAutoAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.tool.choice.auto/client",
        "${net}/anthropic.transform.request.tool.choice.auto/server"})
    public void shouldTransformRequestToolChoiceAutoFromAnthropicAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.tool.choice.required/client",
        "${net}/anthropic.transform.request.tool.choice.required/server"})
    public void shouldTransformRequestToolChoiceRequiredAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.tool.choice.required/client",
        "${net}/anthropic.transform.request.tool.choice.required/server"})
    public void shouldTransformRequestToolChoiceRequiredFromAnthropicAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.tool.choice.none/client",
        "${net}/anthropic.transform.request.tool.choice.none/server"})
    public void shouldTransformRequestToolChoiceNoneAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.tool.choice.none/client",
        "${net}/anthropic.transform.request.tool.choice.none/server"})
    public void shouldTransformRequestToolChoiceNoneFromAnthropicAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.tool.choice.function/client",
        "${net}/anthropic.transform.request.tool.choice.function/server"})
    public void shouldTransformRequestToolChoiceFunctionAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.tool.choice.function/client",
        "${net}/anthropic.transform.request.tool.choice.function/server"})
    public void shouldTransformRequestToolChoiceFunctionFromAnthropicAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.tool.choice.parallel/client",
        "${net}/anthropic.transform.request.tool.choice.parallel/server"})
    public void shouldTransformRequestToolChoiceParallelAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.tool.choice.parallel/client",
        "${net}/anthropic.transform.request.tool.choice.parallel/server"})
    public void shouldTransformRequestToolChoiceParallelFromAnthropicAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.tool.choice.parallel.true/client",
        "${net}/anthropic.transform.request.tool.choice.parallel.true/server"})
    public void shouldTransformRequestToolChoiceParallelTrueAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.tool.choice.parallel.true/client",
        "${net}/anthropic.transform.request.tool.choice.parallel.true/server"})
    public void shouldTransformRequestToolChoiceParallelTrueFromAnthropicAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.tool.choice.choice.parallel/client",
        "${net}/anthropic.transform.request.tool.choice.choice.parallel/server"})
    public void shouldTransformRequestToolChoiceChoiceParallelAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.tool.choice.choice.parallel/client",
        "${net}/anthropic.transform.request.tool.choice.choice.parallel/server"})
    public void shouldTransformRequestToolChoiceChoiceParallelFromAnthropicAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.tool.choice.parallel.first/client",
        "${net}/anthropic.transform.request.tool.choice.parallel.first/server"})
    public void shouldTransformRequestToolChoiceParallelFirstAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.tool.choice.parallel.first/client",
        "${net}/anthropic.transform.request.tool.choice.parallel.first/server"})
    public void shouldTransformRequestToolChoiceParallelFirstFromAnthropicAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.tool.choice.none.parallel/client",
        "${net}/anthropic.transform.request.tool.choice.none.parallel/server"})
    public void shouldTransformRequestToolChoiceNoneParallelAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.tool.choice.none.parallel/client",
        "${net}/anthropic.transform.request.tool.choice.none.parallel/server"})
    public void shouldTransformRequestToolChoiceNoneParallelFromAnthropicAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.rejected.tool.choice.allowed/client",
        "${net}/anthropic.transform.rejected.tool.choice/server"})
    public void shouldRejectRequestToolChoiceAllowedFromOpenaiAsAnthropicNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.rejected.tool.choice.allowed/client",
        "${net}/anthropic.transform.rejected.tool.choice/server"})
    public void shouldRejectRequestToolChoiceAllowedFromAnthropicAsAnthropicNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.rejected.tool.choice.value/client",
        "${net}/anthropic.transform.rejected.tool.choice/server"})
    public void shouldRejectRequestToolChoiceValueFromOpenaiAsAnthropicNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.rejected.tool.choice.value/client",
        "${net}/anthropic.transform.rejected.tool.choice/server"})
    public void shouldRejectRequestToolChoiceValueFromAnthropicAsAnthropicNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.tool.choice.auto/client",
        "${net}/anthropic.request.tool.choice.auto/server"})
    public void shouldRestoreAnthropicRequestToolChoiceAuto() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.tool.choice.any/client",
        "${net}/anthropic.request.tool.choice.any/server"})
    public void shouldRestoreAnthropicRequestToolChoiceAny() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.tool.choice.none/client",
        "${net}/anthropic.request.tool.choice.none/server"})
    public void shouldRestoreAnthropicRequestToolChoiceNone() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.tool.choice.tool/client",
        "${net}/anthropic.request.tool.choice.tool/server"})
    public void shouldRestoreAnthropicRequestToolChoiceTool() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.tool.choice.parallel.disabled/client",
        "${net}/anthropic.request.tool.choice.parallel.disabled/server"})
    public void shouldRestoreAnthropicRequestToolChoiceParallelDisabled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.tool.choice.parallel.enabled/client",
        "${net}/anthropic.request.tool.choice.parallel.enabled/server"})
    public void shouldRestoreAnthropicRequestToolChoiceParallelEnabled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.tool.choice.residue/client",
        "${net}/anthropic.request.tool.choice.residue/server"})
    public void shouldRestoreAnthropicRequestToolChoiceResidue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.tool.choice.unknown/client",
        "${net}/anthropic.request.tool.choice.unknown/server"})
    public void shouldRestoreAnthropicRequestToolChoiceUnknown() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.response.format.json.schema/client",
        "${net}/anthropic.request.response.format.json.schema/server"})
    public void shouldRestoreAnthropicRequestResponseFormatJsonSchema() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.response.format.residue/client",
        "${net}/anthropic.request.response.format.residue/server"})
    public void shouldRestoreAnthropicRequestResponseFormatResidue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.response.format.residue.split/client",
        "${net}/anthropic.request.response.format.residue.split/server"})
    public void shouldRestoreAnthropicRequestResponseFormatResidueSplit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.response.format.residue.only/client",
        "${net}/anthropic.request.response.format.residue.only/server"})
    public void shouldRestoreAnthropicRequestResponseFormatResidueOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.response.format.text/client",
        "${net}/anthropic.transform.request.response.format.text/server"})
    public void shouldTransformRequestResponseFormatTextAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.response.format.text/client",
        "${net}/anthropic.transform.request.response.format.text/server"})
    public void shouldTransformRequestResponseFormatTextFromAnthropicAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.response.format.json.schema/client",
        "${net}/anthropic.transform.request.response.format.json.schema/server"})
    public void shouldTransformRequestResponseFormatJsonSchemaAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.response.format.json.schema/client",
        "${net}/anthropic.transform.request.response.format.json.schema/server"})
    public void shouldTransformRequestResponseFormatJsonSchemaFromAnthropicAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.response.format.json.schema.names/client",
        "${net}/anthropic.transform.request.response.format.json.schema.names/server"})
    public void shouldTransformRequestResponseFormatJsonSchemaNamesAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.response.format.json.schema.names/client",
        "${net}/anthropic.transform.request.response.format.json.schema.names/server"})
    public void shouldTransformRequestResponseFormatJsonSchemaNamesFromAnthropicAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.response.format.json.schema.type.last/client",
        "${net}/anthropic.transform.request.response.format.json.schema/server"})
    public void shouldTransformRequestResponseFormatJsonSchemaTypeLastAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.response.format.json.schema.type.last/client",
        "${net}/anthropic.transform.request.response.format.json.schema/server"})
    public void shouldTransformRequestResponseFormatJsonSchemaTypeLastFromAnthropicAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.rejected.response.format.json.object/client",
        "${net}/anthropic.transform.rejected.tool.choice/server"})
    public void shouldRejectResponseFormatJsonObjectAsAnthropicNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.rejected.response.format.type/client",
        "${net}/anthropic.transform.rejected.tool.choice/server"})
    public void shouldRejectResponseFormatTypeAsAnthropicNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.rejected.response.format.keyword.minimum/client",
        "${net}/anthropic.transform.rejected.tool.choice/server"})
    public void shouldRejectResponseFormatKeywordMinimumAsAnthropicNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.rejected.response.format.keyword.min.length/client",
        "${net}/anthropic.transform.rejected.tool.choice/server"})
    public void shouldRejectResponseFormatKeywordMinLengthAsAnthropicNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.rejected.response.format.keyword.max.items/client",
        "${net}/anthropic.transform.rejected.tool.choice/server"})
    public void shouldRejectResponseFormatKeywordMaxItemsAsAnthropicNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.rejected.response.format.keyword.min.items/client",
        "${net}/anthropic.transform.rejected.tool.choice/server"})
    public void shouldRejectResponseFormatKeywordMinItemsAsAnthropicNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.rejected.response.format.keyword.external.ref/client",
        "${net}/anthropic.transform.rejected.tool.choice/server"})
    public void shouldRejectResponseFormatKeywordExternalRefAsAnthropicNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.rejected.response.format.keyword.recursive.ref/client",
        "${net}/anthropic.transform.rejected.tool.choice/server"})
    public void shouldRejectResponseFormatKeywordRecursiveRefAsAnthropicNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\"", "effort \"low\"", "wire \"low\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.effort/server"})
    public void shouldTransformReasoningEffortLowAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\"", "effort \"medium\"", "wire \"medium\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.effort/server"})
    public void shouldTransformReasoningEffortMediumAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\"", "effort \"high\"", "wire \"high\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.effort/server"})
    public void shouldTransformReasoningEffortHighAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\"", "effort \"xhigh\"", "wire \"xhigh\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.effort/server"})
    public void shouldTransformReasoningEffortXhighAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\"", "effort \"max\"", "wire \"max\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.effort/server"})
    public void shouldTransformReasoningEffortMaxAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\"", "effort \"minimal\"", "wire \"low\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.effort/server"})
    public void shouldClampReasoningEffortMinimalAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\"", "effort \"none\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.disabled/server"})
    public void shouldTransformReasoningNoneAsAnthropicWithDefaultForm() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\"", "effort \"none\"", "model \"thinking-adaptive-disabled\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.disabled/server"})
    public void shouldTransformReasoningNoneAsAnthropicAdaptiveDisabled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\"", "effort \"none\"", "model \"thinking-adaptive-enabled\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.disabled/server"})
    public void shouldTransformReasoningNoneAsAnthropicAdaptiveEnabled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"anthropic\"",
        "effort \"none\"",
        "model \"thinking-adaptive\"",
        "wire \"low\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.effort/server"})
    public void shouldTransformReasoningNoneAsAnthropicAdaptive() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\"", "effort \"none\"", "model \"thinking-enabled\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.omitted/server"})
    public void shouldTransformReasoningNoneAsAnthropicEnabled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"anthropic\"",
        "effort \"none\"",
        "model \"claude-opus-4-7\"",
        "wire \"low\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.effort/server"})
    public void shouldTransformReasoningNoneAsAnthropicDefaultModelAdaptive() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\"", "effort \"none\"", "model \"claude-opus-4-7\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.disabled/server"})
    public void shouldTransformReasoningNoneAsAnthropicOverriddenModelForm() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"gemini\"", "target \"anthropic\"", "budget \"0\""})
    @Specification({
        "${app}/transform.request.reasoning.budget/client",
        "${net}/anthropic.transform.request.reasoning.disabled/server"})
    public void shouldTransformReasoningBudgetZeroAsAnthropicNone() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({
        "source \"gemini\"",
        "target \"anthropic\"",
        "budget \"1536\"",
        "model \"thinking-enabled\"",
        "wire \"1536\""})
    @Specification({
        "${app}/transform.request.reasoning.budget/client",
        "${net}/anthropic.transform.request.reasoning.budget/server"})
    public void shouldTransformReasoningBudget1536AsAnthropicThinkingEnabled1536() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({
        "source \"gemini\"",
        "target \"anthropic\"",
        "budget \"1536\"",
        "model \"thinking-adaptive-enabled\"",
        "wire \"1536\""})
    @Specification({
        "${app}/transform.request.reasoning.budget/client",
        "${net}/anthropic.transform.request.reasoning.budget/server"})
    public void shouldTransformReasoningBudget1536AsAnthropicThinkingAdaptiveEnabled1536() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({
        "source \"gemini\"",
        "target \"anthropic\"",
        "budget \"512\"",
        "model \"thinking-enabled\"",
        "wire \"1024\""})
    @Specification({
        "${app}/transform.request.reasoning.budget/client",
        "${net}/anthropic.transform.request.reasoning.budget/server"})
    public void shouldTransformReasoningBudget512AsAnthropicThinkingEnabled1024() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({
        "source \"gemini\"",
        "target \"anthropic\"",
        "budget \"2048\"",
        "model \"thinking-enabled\"",
        "wire \"2047\""})
    @Specification({
        "${app}/transform.request.reasoning.budget/client",
        "${net}/anthropic.transform.request.reasoning.budget/server"})
    public void shouldTransformReasoningBudget2048AsAnthropicThinkingEnabled2047() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({
        "source \"gemini\"",
        "target \"anthropic\"",
        "budget \"4096\"",
        "model \"thinking-enabled\"",
        "wire \"2047\""})
    @Specification({
        "${app}/transform.request.reasoning.budget/client",
        "${net}/anthropic.transform.request.reasoning.budget/server"})
    public void shouldTransformReasoningBudget4096AsAnthropicThinkingEnabled2047() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({"source \"gemini\"", "target \"anthropic\"", "budget \"1536\"", "model \"thinking-adaptive-disabled\""})
    @Specification({
        "${app}/transform.request.reasoning.budget/client",
        "${net}/anthropic.transform.request.reasoning.omitted/server"})
    public void shouldDropReasoningBudgetAsAnthropicThinkingAdaptiveDisabled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({"source \"gemini\"", "target \"anthropic\"", "budget \"1536\"", "model \"thinking-adaptive\""})
    @Specification({
        "${app}/transform.request.reasoning.budget/client",
        "${net}/anthropic.transform.request.reasoning.omitted/server"})
    public void shouldDropReasoningBudgetAsAnthropicThinkingAdaptive() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"gemini\"", "target \"anthropic\"", "budget \"1536\""})
    @Specification({
        "${app}/transform.request.reasoning.budget/client",
        "${net}/anthropic.transform.request.reasoning.omitted/server"})
    public void shouldDropReasoningBudgetAsAnthropicDefaultForm() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"anthropic\"",
        "effort \"minimal\"",
        "model \"thinking-enabled\"",
        "wire \"1024\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.budget/server"})
    public void shouldDeriveReasoningBudget1024FromEffortMinimalAsAnthropicEnabled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"anthropic\"",
        "effort \"low\"",
        "model \"thinking-enabled\"",
        "wire \"1024\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.budget/server"})
    public void shouldDeriveReasoningBudget1024FromEffortLowAsAnthropicEnabled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"anthropic\"",
        "effort \"medium\"",
        "model \"thinking-enabled\"",
        "wire \"1535\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.budget/server"})
    public void shouldDeriveReasoningBudget1535FromEffortMediumAsAnthropicEnabled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"anthropic\"",
        "effort \"high\"",
        "model \"thinking-enabled\"",
        "wire \"2047\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.budget/server"})
    public void shouldDeriveReasoningBudget2047FromEffortHighAsAnthropicEnabled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"anthropic\"",
        "effort \"xhigh\"",
        "model \"thinking-enabled\"",
        "wire \"2047\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.budget/server"})
    public void shouldDeriveReasoningBudget2047FromEffortXhighAsAnthropicEnabled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"anthropic\"",
        "effort \"max\"",
        "model \"thinking-enabled\"",
        "wire \"2047\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.budget/server"})
    public void shouldDeriveReasoningBudget2047FromEffortMaxAsAnthropicEnabled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"anthropic\"",
        "effort \"minimal\"",
        "model \"thinking-ladder\"",
        "wire \"1100\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.budget/server"})
    public void shouldDeriveReasoningBudget1100FromEffortMinimalAsAnthropicConfiguredLadder() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"anthropic\"",
        "effort \"medium\"",
        "model \"thinking-ladder\"",
        "wire \"1300\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.budget/server"})
    public void shouldDeriveReasoningBudget1300FromEffortMediumAsAnthropicConfiguredLadder() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"anthropic\"",
        "effort \"high\"",
        "model \"thinking-ladder\"",
        "wire \"1500\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.budget/server"})
    public void shouldDeriveReasoningBudget1500FromEffortHighAsAnthropicConfiguredLadder() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"anthropic\"",
        "effort \"max\"",
        "model \"thinking-ladder\"",
        "wire \"1500\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.budget/server"})
    public void shouldDeriveReasoningBudget1500FromEffortMaxAsAnthropicConfiguredLadder() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\"", "effort \"high\"", "model \"thinking-floor\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/anthropic.transform.request.reasoning.omitted/server"})
    public void shouldDropReasoningEffortAsAnthropicWithoutUsableLadder() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"anthropic\"",
        "effort \"medium\"",
        "budget \"1536\"",
        "model \"thinking-adaptive-enabled\"",
        "wire \"medium\"",
        "wireBudget \"1536\""})
    @Specification({
        "${app}/transform.request.reasoning.effort.budget/client",
        "${net}/anthropic.transform.request.reasoning.effort.budget/server"})
    public void shouldTransformReasoningEffortAndBudgetAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.rejected.reasoning.effort/client",
        "${net}/anthropic.transform.rejected.reasoning/server"})
    public void shouldRejectReasoningEffortAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.rejected.reasoning.budget/client",
        "${net}/anthropic.transform.rejected.reasoning/server"})
    public void shouldRejectReasoningBudgetAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\"", "model \"claude-opus-4-7\""})
    @Specification({
        "${app}/transform.request.sampling.members/client",
        "${net}/anthropic.transform.request.sampling.unsupported/server"})
    public void shouldDropSamplingAsAnthropicModelRejectingSampling() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\"", "model \"sampling-rejected\""})
    @Specification({
        "${app}/transform.request.sampling.members/client",
        "${net}/anthropic.transform.request.sampling.unsupported/server"})
    public void shouldDropSamplingAsAnthropicModelConfiguredToRejectSampling() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.models.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\"", "model \"claude-opus-4-7\""})
    @Specification({
        "${app}/transform.request.sampling.members/client",
        "${net}/anthropic.transform.request.sampling.supported/server"})
    public void shouldTransformSamplingAsAnthropicModelOverridingSampling() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\""})
    @Specification({
        "${app}/transform.request.sampling.members/client",
        "${net}/anthropic.transform.request.sampling.supported/server"})
    public void shouldTransformSamplingAsAnthropicModelWithoutEntry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.reasoning.thinking.residue/client",
        "${net}/anthropic.request.reasoning.thinking.residue/server"})
    public void shouldEncodeAnthropicRequestReasoningThinkingResidue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.reasoning.budget.residue/client",
        "${net}/anthropic.request.reasoning.budget.residue/server"})
    public void shouldEncodeAnthropicRequestReasoningBudgetResidue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.reasoning.enabled.only/client",
        "${net}/anthropic.request.reasoning.enabled.only/server"})
    public void shouldEncodeAnthropicRequestReasoningEnabledOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\"", "user \"user-123\"", "wire \"user-123\""})
    @Specification({
        "${app}/transform.request.user/client",
        "${net}/anthropic.transform.request.user/server"})
    public void shouldTransformUserAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\"", "tier \"auto\"", "wire \"auto\""})
    @Specification({
        "${app}/transform.request.service.tier/client",
        "${net}/anthropic.transform.request.service.tier/server"})
    public void shouldTransformServiceTierAutoAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\"", "tier \"default\"", "wire \"standard_only\""})
    @Specification({
        "${app}/transform.request.service.tier/client",
        "${net}/anthropic.transform.request.service.tier/server"})
    public void shouldTransformServiceTierDefaultAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\"", "tier \"flex\""})
    @Specification({
        "${app}/transform.request.service.tier/client",
        "${net}/anthropic.transform.request.dropped/server"})
    public void shouldDropServiceTierFlexAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\"", "tier \"priority\""})
    @Specification({
        "${app}/transform.request.service.tier/client",
        "${net}/anthropic.transform.request.dropped/server"})
    public void shouldDropServiceTierPriorityAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.tiers.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\"", "model \"standard-only\"", "tier \"auto\""})
    @Specification({
        "${app}/transform.request.service.tier/client",
        "${net}/anthropic.transform.request.dropped/server"})
    public void shouldDropServiceTierNotConfiguredAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.tiers.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"anthropic\"",
        "model \"standard-only\"",
        "tier \"default\"",
        "wire \"standard_only\""})
    @Specification({
        "${app}/transform.request.service.tier/client",
        "${net}/anthropic.transform.request.service.tier/server"})
    public void shouldTransformServiceTierConfiguredAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.tiers.yaml")
    @ScriptProperty({"source \"openai\"", "target \"anthropic\"", "model \"other-model\"", "tier \"auto\"", "wire \"auto\""})
    @Specification({
        "${app}/transform.request.service.tier/client",
        "${net}/anthropic.transform.request.service.tier/server"})
    public void shouldTransformServiceTierWithoutModelEntryAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.user/client",
        "${net}/anthropic.request.user/server"})
    public void shouldEncodeAnthropicRequestUser() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.request.user.residue/client",
        "${net}/anthropic.request.user.residue/server"})
    public void shouldEncodeAnthropicRequestUserResidue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"tier \"default\"", "wire \"standard_only\""})
    @Specification({
        "${app}/anthropic.request.service.tier/client",
        "${net}/anthropic.request.service.tier/server"})
    public void shouldEncodeAnthropicRequestServiceTierDefault() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"tier \"auto\"", "wire \"auto\""})
    @Specification({
        "${app}/anthropic.request.service.tier/client",
        "${net}/anthropic.request.service.tier/server"})
    public void shouldEncodeAnthropicRequestServiceTierAuto() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @ScriptProperty({"wire \"other\""})
    @Specification({
        "${app}/anthropic.request.service.tier.residue/client",
        "${net}/anthropic.request.service.tier.residue/server"})
    public void shouldEncodeAnthropicRequestServiceTierResidueOther() throws Exception
    {
        k3po.finish();
    }
}
