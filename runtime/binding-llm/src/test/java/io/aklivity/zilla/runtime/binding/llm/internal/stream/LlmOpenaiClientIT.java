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

public class LlmOpenaiClientIT
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
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.response.invalid.content.type/client",
        "${net}/openai.response.invalid.content.type/server"})
    public void shouldRejectResponseWithInvalidContentType() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/abort/client",
        "${net}/abort/server"})
    public void shouldAbortClientRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.streaming.usage/client",
        "${net}/openai.streaming.usage/server"})
    public void shouldForwardOpenaiStreamingUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.streaming.usage.padded/client",
        "${net}/openai.streaming.usage/server"})
    public void shouldForwardOpenaiStreamingUsagePadded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai/client",
        "${net}/openai/server"})
    public void shouldForwardOpenaiNonstreamingSameDialect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.usage/client",
        "${net}/openai.usage/server"})
    public void shouldForwardOpenaiNonstreamingUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.usage.padded/client",
        "${net}/openai.usage/server"})
    public void shouldForwardOpenaiNonstreamingUsagePadded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.usage/client",
        "${net}/openai.usage.split/server"})
    public void shouldForwardOpenaiNonstreamingUsageSplitNumber() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.request.padded/client",
        "${net}/openai.request.padded/server"})
    public void shouldForwardOpenaiRequestWithReplyPadding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.custom.base.path.yaml")
    @Specification({
        "${app}/openai/client",
        "${net}/openai.custom.base.path/server"})
    public void shouldForwardOpenaiRequestUnderCustomBasePath() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.response.invalid/client",
        "${net}/openai.response.invalid/server"})
    public void shouldRejectInvalidOpenaiResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.10k/client",
        "${net}/openai.10k/server"})
    public void shouldForwardOpenai10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.100k/client",
        "${net}/openai.100k/server"})
    public void shouldForwardOpenai100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.streaming.10k/client",
        "${net}/openai.streaming.10k/server"})
    public void shouldForwardOpenaiStreaming10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.streaming.100k/client",
        "${net}/openai.streaming.100k/server"})
    public void shouldForwardOpenaiStreaming100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.rejected.rate.limit/client",
        "${net}/openai.rejected.rate.limit/server"})
    public void shouldResetOpenaiRequestRateLimited() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.rejected.bad.gateway/client",
        "${net}/openai.rejected.bad.gateway/server"})
    public void shouldResetOpenaiRequestBadGatewayWithoutParsingBody() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.rejected.reset/client",
        "${net}/openai.rejected.reset/server"})
    public void shouldResetOpenaiRequestWhenUpstreamResets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.streaming.error/client",
        "${net}/openai.streaming.error/server"})
    public void shouldAbortOpenaiStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.request.response.format/client",
        "${net}/openai.request.response.format/server"})
    public void shouldEncodeOpenaiRequestResponseFormat() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.request.response.format.10k/client",
        "${net}/openai.request.response.format.10k/server"})
    public void shouldEncodeOpenaiRequestResponseFormat10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.request.response.format.100k/client",
        "${net}/openai.request.response.format.100k/server"})
    public void shouldEncodeOpenaiRequestResponseFormat100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.request.options/client",
        "${net}/openai.request.options/server"})
    public void shouldEncodeOpenaiRequestOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.request.options.leading/client",
        "${net}/openai.request.options.leading/server"})
    public void shouldEncodeOpenaiRequestOptionsLeading() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.user.text/client",
        "${net}/openai.section.user.text/server"})
    public void shouldEncodeOpenaiSectionUserText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.user.text/client",
        "${net}/openai.section.user.text/server"})
    @ScriptProperty({"model \"gpt-4o\""})
    public void shouldEncodeOpenaiSectionUserTextModelOverride() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.user.text.empty/client",
        "${net}/openai.section.user.text.empty/server"})
    public void shouldEncodeOpenaiSectionUserTextEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.assistant.text.empty/client",
        "${net}/openai.section.assistant.text.empty/server"})
    public void shouldEncodeOpenaiSectionAssistantTextEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.user.image/client",
        "${net}/openai.section.user.image/server"})
    public void shouldEncodeOpenaiSectionUserImage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.user.image.detail/client",
        "${net}/openai.section.user.image.detail/server"})
    public void shouldEncodeOpenaiSectionUserImageDetail() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.user.image.detail.first/client",
        "${net}/openai.section.user.image.detail.first/server"})
    public void shouldEncodeOpenaiSectionUserImageDetailFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.user.document/client",
        "${net}/openai.section.user.document/server"})
    public void shouldEncodeOpenaiSectionUserDocument() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.user.audio/client",
        "${net}/openai.section.user.audio/server"})
    public void shouldEncodeOpenaiSectionUserAudio() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.assistant.text/client",
        "${net}/openai.section.assistant.text/server"})
    public void shouldEncodeOpenaiSectionAssistantText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.assistant.text.annotations/client",
        "${net}/openai.section.assistant.text.annotations/server"})
    public void shouldEncodeOpenaiSectionAssistantTextAnnotations() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.assistant.reasoning.history/client",
        "${net}/openai.section.assistant.reasoning.history/server"})
    public void shouldEncodeOpenaiSectionAssistantReasoningHistory() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.assistant.refusal/client",
        "${net}/openai.section.assistant.refusal/server"})
    public void shouldEncodeOpenaiSectionAssistantRefusal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.tool.definition/client",
        "${net}/openai.section.tool.definition/server"})
    public void shouldEncodeOpenaiSectionToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.tool.call/client",
        "${net}/openai.section.tool.call/server"})
    public void shouldEncodeOpenaiSectionToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.tool.result/client",
        "${net}/openai.section.tool.result/server"})
    public void shouldEncodeOpenaiSectionToolResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.mixed.content/client",
        "${net}/openai.section.mixed.content/server"})
    public void shouldEncodeOpenaiSectionMixedContent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.assistant.text.and.tool.call/client",
        "${net}/openai.section.assistant.text.and.tool.call/server"})
    public void shouldEncodeOpenaiSectionAssistantTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.conversation/client",
        "${net}/openai.section.conversation/server"})
    public void shouldEncodeOpenaiSectionConversation() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.user.text.10k/client",
        "${net}/openai.section.user.text.10k/server"})
    public void shouldEncodeOpenaiSectionUserText10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.user.text.100k.parts/client",
        "${net}/openai.section.user.text.100k.parts/server"})
    public void shouldEncodeOpenaiSectionUserText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.tool.result.100k.parts/client",
        "${net}/openai.section.tool.result.100k.parts/server"})
    public void shouldEncodeOpenaiSectionToolResult100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.unknown.part/client",
        "${net}/openai.section.unknown.part/server"})
    public void shouldEncodeOpenaiSectionUnknownPart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.user.image.10k/client",
        "${net}/openai.section.user.image.10k/server"})
    public void shouldEncodeOpenaiSectionUserImage10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.user.image/client",
        "${net}/openai.transform.user.image/server"})
    public void shouldTransformUserImageAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.assistant.tool.call/client",
        "${net}/openai.transform.assistant.tool.call/server"})
    public void shouldTransformAssistantToolCallAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.tool.definition/client",
        "${net}/openai.transform.tool.definition/server"})
    public void shouldTransformToolDefinitionAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/anthropic.transform.tool.definition.cache.control/client",
        "${net}/openai.transform.tool.definition/server"})
    public void shouldDropAnthropicToolDefinitionCacheControlAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.mixed.content/client",
        "${net}/openai.transform.mixed.content/server"})
    public void shouldTransformMixedContentAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/anthropic.transform.server.tool.result/client",
        "${net}/openai.transform.server.tool.result/server"})
    public void shouldTransformAnthropicSectionServerToolResultAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/anthropic.transform.assistant.text.citations/client",
        "${net}/openai.transform.assistant.text.citations/server"})
    public void shouldTransformAssistantTextCitationsAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/anthropic.transform.assistant.text.citations.skipped/client",
        "${net}/openai.transform.assistant.text.citations.skipped/server"})
    public void shouldTransformAssistantTextCitationsAsOpenaiSkipped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.user.image.inline/client",
        "${net}/openai.section.user.image.inline/server"})
    public void shouldEncodeOpenaiSectionUserImageInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.user.document.inline/client",
        "${net}/openai.section.user.document.inline/server"})
    public void shouldEncodeOpenaiSectionUserDocumentInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.user.document.filename.last/client",
        "${net}/openai.section.user.document.filename.last/server"})
    public void shouldEncodeOpenaiSectionUserDocumentFilenameLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.user.audio.format.last/client",
        "${net}/openai.section.user.audio.format.last/server"})
    public void shouldEncodeOpenaiSectionUserAudioFormatLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.tool.definition.name.last/client",
        "${net}/openai.section.tool.definition.name.last/server"})
    public void shouldEncodeOpenaiSectionToolDefinitionNameLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.user.image.inline/client",
        "${net}/openai.transform.user.image.inline/server"})
    public void shouldTransformUserImageInlineAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.user.document.inline/client",
        "${net}/openai.transform.user.document.inline/server"})
    public void shouldTransformUserDocumentInlineAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.user.document.reference/client",
        "${net}/openai.transform.user.document.reference/server"})
    public void shouldTransformUserDocumentReferenceAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.user.image.10k/client",
        "${net}/openai.transform.user.image.10k/server"})
    public void shouldTransformUserImage10kAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.user.image.url.10k/client",
        "${net}/openai.section.user.image.url.10k/server"})
    public void shouldEncodeOpenaiSectionUserImageUrl10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.user.image.url.100k/client",
        "${net}/openai.section.user.image.url.100k/server"})
    public void shouldEncodeOpenaiSectionUserImageUrl100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.user.image.url.10k/client",
        "${net}/openai.transform.user.image.url.10k/server"})
    public void shouldTransformUserImageUrl10kAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.user.image.url.100k/client",
        "${net}/openai.transform.user.image.url.100k/server"})
    public void shouldTransformUserImageUrl100kAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.assistant.text.members/client",
        "${net}/openai.section.assistant.text.members/server"})
    public void shouldEncodeOpenaiSectionAssistantTextMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.assistant.text.streaming/client",
        "${net}/openai.section.assistant.text.streaming/server"})
    public void shouldEncodeOpenaiSectionAssistantTextStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.assistant.tool.call.streaming/client",
        "${net}/openai.section.assistant.tool.call.streaming/server"})
    public void shouldEncodeOpenaiSectionAssistantToolCallStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.assistant.text.usage.streaming/client",
        "${net}/openai.section.assistant.text.usage.streaming/server"})
    public void shouldEncodeOpenaiSectionAssistantTextUsageStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.rejected.streaming/client",
        "${net}/openai.rejected.streaming/server"})
    public void shouldEncodeOpenaiRejectedStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.rejected.streaming/client",
        "${net}/openai.rejected.streaming/server"})
    @ScriptProperty({"model \"gpt-4o\""})
    public void shouldEncodeOpenaiRejectedStreamingModelOverride() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.assistant.text.100k/client",
        "${net}/openai.section.assistant.text.100k/server"})
    public void shouldEncodeOpenaiSectionAssistantText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.assistant.text.escaped/client",
        "${net}/openai.section.assistant.text.escaped/server"})
    public void shouldEncodeOpenaiSectionAssistantTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.tool.call.100k/client",
        "${net}/openai.section.tool.call.100k/server"})
    public void shouldEncodeOpenaiSectionToolCall100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.tool.call.10k/client",
        "${net}/openai.section.tool.call.10k/server"})
    public void shouldEncodeOpenaiSectionToolCall10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.tool.call.escaped/client",
        "${net}/openai.section.tool.call.escaped/server"})
    public void shouldEncodeOpenaiSectionToolCallEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.tool.call.id.last/client",
        "${net}/openai.section.tool.call.id.last/server"})
    public void shouldEncodeOpenaiSectionToolCallIdLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.tool.call.name.last/client",
        "${net}/openai.section.tool.call.name.last/server"})
    public void shouldEncodeOpenaiSectionToolCallNameLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.tool.definition.100k/client",
        "${net}/openai.section.tool.definition.100k/server"})
    public void shouldEncodeOpenaiSectionToolDefinition100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.tool.definition.10k/client",
        "${net}/openai.section.tool.definition.10k/server"})
    public void shouldEncodeOpenaiSectionToolDefinition10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.tool.definition.parameters.100k/client",
        "${net}/openai.section.tool.definition.parameters.100k/server"})
    public void shouldEncodeOpenaiSectionToolDefinitionParameters100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.tool.definition.parameters.10k/client",
        "${net}/openai.section.tool.definition.parameters.10k/server"})
    public void shouldEncodeOpenaiSectionToolDefinitionParameters10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.assistant.tool.call.100k/client",
        "${net}/openai.transform.assistant.tool.call.100k/server"})
    public void shouldTransformAssistantToolCall100kAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.assistant.tool.call.10k/client",
        "${net}/openai.transform.assistant.tool.call.10k/server"})
    public void shouldTransformAssistantToolCall10kAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.assistant.tool.call.escaped/client",
        "${net}/openai.transform.assistant.tool.call.escaped/server"})
    public void shouldTransformAssistantToolCallEscapedAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.assistant.tool.call.id.last/client",
        "${net}/openai.transform.assistant.tool.call.id.last/server"})
    public void shouldTransformAssistantToolCallIdLastAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.assistant.tool.call.name.last/client",
        "${net}/openai.transform.assistant.tool.call.name.last/server"})
    public void shouldTransformAssistantToolCallNameLastAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.rejected.tool.call.name/client",
        "${net}/openai.transform.rejected.tool.call.name/server"})
    public void shouldRejectToolCallNameAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/anthropic.transform.rejected.tool.definition.server/client",
        "${net}/openai.transform.rejected.tool.definition.server/server"})
    public void shouldRejectAnthropicSectionToolDefinitionServerAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.tool.definition.100k/client",
        "${net}/openai.transform.tool.definition.100k/server"})
    public void shouldTransformToolDefinition100kAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.tool.definition.10k/client",
        "${net}/openai.transform.tool.definition.10k/server"})
    public void shouldTransformToolDefinition10kAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.tool.definition.name.last/client",
        "${net}/openai.transform.tool.definition.name.last/server"})
    public void shouldTransformToolDefinitionNameLastAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.tool.definition.parameters.100k/client",
        "${net}/openai.transform.tool.definition.parameters.100k/server"})
    public void shouldTransformToolDefinitionParameters100kAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.tool.definition.parameters.10k/client",
        "${net}/openai.transform.tool.definition.parameters.10k/server"})
    public void shouldTransformToolDefinitionParameters10kAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.assistant.text.logprobs.streaming/client",
        "${net}/openai.section.assistant.text.logprobs.streaming/server"})
    public void shouldEncodeOpenaiSectionAssistantTextLogprobsStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.assistant.text.logprobs/client",
        "${net}/openai.section.assistant.text.logprobs/server"})
    public void shouldEncodeOpenaiSectionAssistantTextLogprobs() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.message.name/client",
        "${net}/openai.section.message.name/server"})
    public void shouldEncodeOpenaiSectionMessageName() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.message.name.after/client",
        "${net}/openai.section.message.name.after/server"})
    public void shouldEncodeOpenaiSectionMessageNameAfter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.message.name.tool.result/client",
        "${net}/openai.section.message.name.tool.result/server"})
    public void shouldEncodeOpenaiSectionMessageNameToolResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.request.options.early/client",
        "${net}/openai.request.options.early/server"})
    public void shouldEncodeOpenaiRequestOptionsEarly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.model.last/client",
        "${net}/openai.section.model.last/server"})
    public void shouldEncodeOpenaiSectionModelLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.streaming.abort/client",
        "${net}/openai.streaming.abort/server"})
    public void shouldAbortOpenaiStreamingWithPartialUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.streaming.abort.nocredit/client",
        "${net}/openai.streaming.abort/server"})
    public void shouldAbortOpenaiStreamingWithPartialUsageWithoutCredit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.streaming.complete/client",
        "${net}/openai.streaming.complete/server"})
    public void shouldMarkOpenaiStreamingEventsComplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.user.text/client",
        "${net}/openai.transform.user.text/server"})
    public void shouldTransformUserTextAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.system.instruction/client",
        "${net}/openai.transform.system.instruction/server"})
    public void shouldTransformSystemInstructionAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.assistant.text/client",
        "${net}/openai.transform.assistant.text/server"})
    public void shouldTransformAssistantTextAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.tool.result/client",
        "${net}/openai.transform.tool.result/server"})
    public void shouldTransformToolResultAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.assistant.reasoning/client",
        "${net}/openai.transform.assistant.reasoning/server"})
    public void shouldTransformAssistantReasoningAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.assistant.reasoning.escaped/client",
        "${net}/openai.section.assistant.reasoning.escaped/server"})
    public void shouldEncodeOpenaiSectionAssistantReasoningEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.assistant.reasoning.streaming/client",
        "${net}/openai.section.assistant.reasoning.streaming/server"})
    public void shouldStreamOpenaiSectionAssistantReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.assistant.reasoning.streaming.delta/client",
        "${net}/openai.section.assistant.reasoning.streaming.delta/server"})
    public void shouldStreamOpenaiSectionAssistantReasoningDelta() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.assistant.text.reasoning/client",
        "${net}/openai.section.assistant.text.reasoning/server"})
    public void shouldEncodeOpenaiSectionAssistantTextReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.tool.result.blocks/client",
        "${net}/openai.transform.tool.result.blocks/server"})
    public void shouldTransformToolResultBlocksAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.tool.result.error/client",
        "${net}/openai.transform.tool.result.error/server"})
    public void shouldTransformToolResultErrorAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.response.frames/client",
        "${net}/openai.transform.response.frames/server"})
    public void shouldTransformResponseFramesAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.rejected.unreachable/client",
        "${net}/openai.rejected.unreachable/server"})
    public void shouldResetOpenaiRequestWhenServerUnreachable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.rejected.user.video/client",
        "${net}/openai.transform.rejected.user.video/server"})
    public void shouldRejectUserVideoAsOpenaiNamingSectionAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.response.text/client",
        "${net}/openai.transform.response.text/server"})
    public void shouldTransformResponseTextAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.response.text.escaped/client",
        "${net}/openai.transform.response.text.escaped/server"})
    public void shouldTransformResponseTextEscapedAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.response.text.100k/client",
        "${net}/openai.transform.response.text.100k/server"})
    public void shouldTransformResponseText100kAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.response.text.and.tool.call/client",
        "${net}/openai.transform.response.text.and.tool.call/server"})
    public void shouldTransformResponseTextAndToolCallAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.assistant.text.members.streaming/client",
        "${net}/openai.section.assistant.text.members.streaming/server"})
    public void shouldEncodeOpenaiSectionAssistantTextMembersStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.assistant.text.members.large/client",
        "${net}/openai.section.assistant.text.members.large/server"})
    public void shouldEncodeOpenaiSectionAssistantTextMembersLarge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.assistant.text.logprobs.streaming.role.first/client",
        "${net}/openai.section.assistant.text.logprobs.streaming.role.first/server"})
    public void shouldEncodeOpenaiSectionAssistantTextLogprobsStreamingRoleFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai\""})
    @Specification({
        "${app}/transform.request.max.output.tokens/client",
        "${net}/openai.transform.request.max.output.tokens/server"})
    public void shouldTransformRequestMaxOutputTokensAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.rejected.error.code/client",
        "${net}/openai.rejected.error.code/server"})
    public void shouldPassOpenaiRejectedErrorCode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.rejected.streaming.error.code/client",
        "${net}/openai.rejected.streaming.error.code/server"})
    public void shouldPassOpenaiRejectedStreamingErrorCode() throws Exception
    {
        k3po.finish();
    }
}
