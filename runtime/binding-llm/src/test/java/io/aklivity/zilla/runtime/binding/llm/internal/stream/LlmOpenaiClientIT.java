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
    private static final String USER_128 = "0123456789abcdef0123456789abcdef" +
        "0123456789abcdef0123456789abcdef" +
        "0123456789abcdef0123456789abcdef" +
        "0123456789abcdef0123456789abcdef";
    private static final String USER_129 = USER_128 + "x";

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
        "${app}/openai.section.user.image.10k/client",
        "${net}/openai.section.user.image.10k/server"})
    public void shouldEncodeOpenaiSectionUserImage10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.user.image/client",
        "${net}/openai.transform.user.image/server"})
    public void shouldTransformUserImageAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.assistant.tool.call/client",
        "${net}/openai.transform.assistant.tool.call/server"})
    public void shouldTransformAssistantToolCallAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.tool.definition/client",
        "${net}/openai.transform.tool.definition/server"})
    public void shouldTransformToolDefinitionAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/anthropic.transform.tool.definition.cache.control/client",
        "${net}/openai.transform.tool.definition/server"})
    public void shouldDropAnthropicToolDefinitionCacheControlAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.mixed.content/client",
        "${net}/openai.transform.mixed.content/server"})
    public void shouldTransformMixedContentAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/anthropic.transform.server.tool.result/client",
        "${net}/openai.transform.server.tool.result/server"})
    public void shouldTransformAnthropicSectionServerToolResultAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/anthropic.transform.assistant.text.citations/client",
        "${net}/openai.transform.assistant.text.citations/server"})
    public void shouldTransformAssistantTextCitationsAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
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
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.user.image.inline/client",
        "${net}/openai.transform.user.image.inline/server"})
    public void shouldTransformUserImageInlineAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.user.document.inline/client",
        "${net}/openai.transform.user.document.inline/server"})
    public void shouldTransformUserDocumentInlineAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.user.document.reference/client",
        "${net}/openai.transform.user.document.reference/server"})
    public void shouldTransformUserDocumentReferenceAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
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
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.user.image.url.10k/client",
        "${net}/openai.transform.user.image.url.10k/server"})
    public void shouldTransformUserImageUrl10kAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
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
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.assistant.tool.call.100k/client",
        "${net}/openai.transform.assistant.tool.call.100k/server"})
    public void shouldTransformAssistantToolCall100kAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.assistant.tool.call.10k/client",
        "${net}/openai.transform.assistant.tool.call.10k/server"})
    public void shouldTransformAssistantToolCall10kAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.assistant.tool.call.escaped/client",
        "${net}/openai.transform.assistant.tool.call.escaped/server"})
    public void shouldTransformAssistantToolCallEscapedAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.assistant.tool.call.id.last/client",
        "${net}/openai.transform.assistant.tool.call.id.last/server"})
    public void shouldTransformAssistantToolCallIdLastAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.assistant.tool.call.name.last/client",
        "${net}/openai.transform.assistant.tool.call.name.last/server"})
    public void shouldTransformAssistantToolCallNameLastAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.rejected.tool.call.name/client",
        "${net}/openai.transform.rejected.tool.call.name/server"})
    public void shouldRejectToolCallNameAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/anthropic.transform.server.tool.definition/client",
        "${net}/openai.transform.server.tool.definition/server"})
    public void shouldDropAnthropicServerToolDefinitionAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.tool.definition.100k/client",
        "${net}/openai.transform.tool.definition.100k/server"})
    public void shouldTransformToolDefinition100kAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.tool.definition.10k/client",
        "${net}/openai.transform.tool.definition.10k/server"})
    public void shouldTransformToolDefinition10kAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.tool.definition.name.last/client",
        "${net}/openai.transform.tool.definition.name.last/server"})
    public void shouldTransformToolDefinitionNameLastAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.tool.definition.parameters.100k/client",
        "${net}/openai.transform.tool.definition.parameters.100k/server"})
    public void shouldTransformToolDefinitionParameters100kAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
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
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.user.text/client",
        "${net}/openai.transform.user.text/server"})
    public void shouldTransformUserTextAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.system.instruction/client",
        "${net}/openai.transform.system.instruction/server"})
    public void shouldTransformSystemInstructionAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.system.instruction.last/client",
        "${net}/openai.transform.system.instruction.last/server"})
    public void shouldTransformSystemInstructionLastAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/anthropic.transform.system.instruction.last/client",
        "${net}/openai.transform.system.instruction.last/server"})
    public void shouldTransformAnthropicSystemInstructionLastAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.system.instruction.last.100k/client",
        "${net}/openai.transform.system.instruction.last.100k/server"})
    public void shouldTransformSystemInstructionLast100kAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.rejected.system.instruction.after.tool.definition/client",
        "${net}/openai.transform.rejected.system.instruction.after.tool.definition/server"})
    public void shouldRejectSystemInstructionAfterToolDefinitionAsOpenaiNamingSectionAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.assistant.text/client",
        "${net}/openai.transform.assistant.text/server"})
    public void shouldTransformAssistantTextAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.tool.result/client",
        "${net}/openai.transform.tool.result/server"})
    public void shouldTransformToolResultAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
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
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.tool.result.blocks/client",
        "${net}/openai.transform.tool.result.blocks/server"})
    public void shouldTransformToolResultBlocksAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.tool.result.error/client",
        "${net}/openai.transform.tool.result.error/server"})
    public void shouldTransformToolResultErrorAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/openai.transform.response.frames/client",
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
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.rejected.user.video/client",
        "${net}/openai.transform.rejected.user.video/server"})
    public void shouldRejectUserVideoAsOpenaiNamingSectionAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/openai.transform.response.text/client",
        "${net}/openai.transform.response.text/server"})
    public void shouldTransformResponseTextAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/openai.transform.response.text.escaped/client",
        "${net}/openai.transform.response.text.escaped/server"})
    public void shouldTransformResponseTextEscapedAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/openai.transform.response.text.100k/client",
        "${net}/openai.transform.response.text.100k/server"})
    public void shouldTransformResponseText100kAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/openai.transform.response.text.and.tool.call/client",
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
    @ScriptProperty({"target \"openai/ccs\""})
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

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.tool.call.members/client",
        "${net}/openai.section.tool.call.members/server"})
    public void shouldEncodeOpenaiSectionToolCallMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/anthropic.transform.assistant.tool.call.cache.control/client",
        "${net}/openai.transform.assistant.tool.call/server"})
    public void shouldDropAnthropicToolCallCacheControlAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.assistant.tool.call.streaming/client",
        "${net}/openai.section.assistant.tool.call.streaming.split/server"})
    public void shouldEncodeOpenaiSectionAssistantToolCallStreamingSplit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.assistant.tool.call.streaming.error/client",
        "${net}/openai.section.assistant.tool.call.streaming.error/server"})
    public void shouldAbortOpenaiSectionAssistantToolCallStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/openai.transform.response.text/client",
        "${net}/openai.transform.response.text.sse/server"})
    public void shouldTransformResponseTextSseAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/openai.transform.response.reasoning/client",
        "${net}/openai.transform.response.reasoning.sse/server"})
    public void shouldTransformResponseReasoningSseAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/openai.transform.response.text.and.tool.call/client",
        "${net}/openai.transform.response.text.and.tool.call.sse/server"})
    public void shouldTransformResponseTextAndToolCallSseAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/openai.transform.response.refusal/client",
        "${net}/openai.transform.response.refusal.sse/server"})
    public void shouldTransformResponseRefusalSseAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/openai.transform.response.tool.call/client",
        "${net}/openai.transform.response.tool.call/server"})
    public void shouldTransformResponseToolCallAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/openai.transform.response.tool.call/client",
        "${net}/openai.transform.response.tool.call.sse/server"})
    public void shouldTransformResponseToolCallSseAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/openai.transform.response.tool.calls/client",
        "${net}/openai.transform.response.tool.calls/server"})
    public void shouldTransformResponseToolCallsAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/openai.transform.response.tool.calls/client",
        "${net}/openai.transform.response.tool.calls.sse/server"})
    public void shouldTransformResponseToolCallsSseAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/openai.transform.response.tool.calls/client",
        "${net}/openai.transform.response.tool.calls.reused.index.sse/server"})
    public void shouldTransformResponseToolCallsReusedIndexSseAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/openai.transform.response.tool.calls/client",
        "${net}/openai.transform.response.tool.calls.whole.sse/server"})
    public void shouldTransformResponseToolCallsWholeSseAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/openai.transform.response.tool.calls/client",
        "${net}/openai.transform.response.tool.calls.omitted.sse/server"})
    public void shouldTransformResponseToolCallsOmittedSseAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/openai.transform.response.tool.calls/client",
        "${net}/openai.transform.response.tool.calls.interleaved.sse/server"})
    public void shouldTransformResponseToolCallsInterleavedSseAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.response.text.error/client",
        "${net}/openai.transform.response.text.error.sse/server"})
    public void shouldTransformResponseTextErrorSseAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.response.tool.call.error/client",
        "${net}/openai.transform.response.tool.call.error.sse/server"})
    public void shouldTransformResponseToolCallErrorSseAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/openai.transform.response.tool.call.empty/client",
        "${net}/openai.transform.response.tool.call.empty/server"})
    public void shouldTransformResponseToolCallEmptyAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/openai.transform.response.tool.call.empty/client",
        "${net}/openai.transform.response.tool.call.empty.sse/server"})
    public void shouldTransformResponseToolCallEmptySseAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/openai.transform.response.usage/client",
        "${net}/openai.transform.response.usage.sse/server"})
    public void shouldTransformResponseUsageSseAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\""})
    @Specification({
        "${app}/transform.response.tool.calls.overflow/client",
        "${net}/openai.transform.response.tool.calls.overflow.sse/server"})
    public void shouldTransformResponseToolCallsOverflowSseAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"target \"openai/ccs\"", "reason \"openai/ccs:insufficient_system_resource\""})
    @Specification({
        "${app}/openai.transform.response.finish.reason/client",
        "${net}/openai.transform.response.finish.reason.unknown/server"})
    public void shouldPrefixUnknownFinishReasonAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.sampling/client",
        "${net}/openai.transform.request.sampling/server"})
    public void shouldTransformRequestSamplingAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"openai/ccs\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.sampling/client",
        "${net}/openai.transform.request.sampling/server"})
    public void shouldTransformRequestSamplingFromOpenaiAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"openai/ccs\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.sampling.seed/client",
        "${net}/openai.transform.request.sampling.seed/server"})
    public void shouldTransformRequestSamplingSeedAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"openai/ccs\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.sampling.penalties/client",
        "${net}/openai.transform.request.sampling.penalties/server"})
    public void shouldTransformRequestSamplingPenaltiesAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.sampling.stop/client",
        "${net}/openai.transform.request.sampling.stop/server"})
    public void shouldTransformRequestSamplingStopAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"openai/ccs\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.sampling.stop/client",
        "${net}/openai.transform.request.sampling.stop/server"})
    public void shouldTransformRequestSamplingStopFromOpenaiAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.sampling.top.k/client",
        "${net}/openai.transform.request.max.output.tokens/server"})
    public void shouldDropRequestSamplingTopKAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.sampling.clamp/client",
        "${net}/openai.transform.request.sampling.clamp/server"})
    public void shouldPassRequestSamplingTemperatureAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.rejected.sampling.stop.excess/client",
        "${net}/openai.transform.rejected.sampling.stop.excess/server"})
    public void shouldRejectRequestSamplingStopExcessAsOpenaiNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"gemini\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.sampling/client",
        "${net}/openai.transform.request.sampling/server"})
    public void shouldTransformRequestSamplingFromGeminiAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"gemini\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.sampling.stop/client",
        "${net}/openai.transform.request.sampling.stop/server"})
    public void shouldTransformRequestSamplingStopFromGeminiAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"gemini\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.sampling.top.k/client",
        "${net}/openai.transform.request.max.output.tokens/server"})
    public void shouldDropRequestSamplingTopKFromGeminiAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.tool.choice.auto/client",
        "${net}/openai.transform.request.tool.choice.auto/server"})
    public void shouldTransformRequestToolChoiceAutoAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"openai/ccs\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.tool.choice.auto/client",
        "${net}/openai.transform.request.tool.choice.auto/server"})
    public void shouldTransformRequestToolChoiceAutoFromOpenaiAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.tool.choice.required/client",
        "${net}/openai.transform.request.tool.choice.required/server"})
    public void shouldTransformRequestToolChoiceRequiredAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.tool.choice.none/client",
        "${net}/openai.transform.request.tool.choice.none/server"})
    public void shouldTransformRequestToolChoiceNoneAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.tool.choice.function/client",
        "${net}/openai.transform.request.tool.choice.function/server"})
    public void shouldTransformRequestToolChoiceFunctionAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"openai/ccs\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.tool.choice.function/client",
        "${net}/openai.transform.request.tool.choice.function/server"})
    public void shouldTransformRequestToolChoiceFunctionFromOpenaiAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.tool.choice.allowed/client",
        "${net}/openai.transform.request.tool.choice.allowed/server"})
    public void shouldTransformRequestToolChoiceAllowedAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"openai/ccs\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.tool.choice.allowed/client",
        "${net}/openai.transform.request.tool.choice.allowed/server"})
    public void shouldTransformRequestToolChoiceAllowedFromOpenaiAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.tool.choice.allowed.required/client",
        "${net}/openai.transform.request.tool.choice.allowed.required/server"})
    public void shouldTransformRequestToolChoiceAllowedRequiredAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.tool.choice.parallel/client",
        "${net}/openai.transform.request.tool.choice.parallel/server"})
    public void shouldTransformRequestToolChoiceParallelAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"openai/ccs\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.tool.choice.parallel/client",
        "${net}/openai.transform.request.tool.choice.parallel/server"})
    public void shouldTransformRequestToolChoiceParallelFromOpenaiAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.tool.choice.parallel.true/client",
        "${net}/openai.transform.request.tool.choice.parallel.true/server"})
    public void shouldTransformRequestToolChoiceParallelTrueAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.tool.choice.choice.parallel/client",
        "${net}/openai.transform.request.tool.choice.choice.parallel/server"})
    public void shouldTransformRequestToolChoiceChoiceParallelAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"openai/ccs\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.tool.choice.choice.parallel/client",
        "${net}/openai.transform.request.tool.choice.choice.parallel/server"})
    public void shouldTransformRequestToolChoiceChoiceParallelFromOpenaiAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.tool.choice.parallel.first/client",
        "${net}/openai.transform.request.tool.choice.parallel.first/server"})
    public void shouldTransformRequestToolChoiceParallelFirstAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.tool.choice.none.parallel/client",
        "${net}/openai.transform.request.tool.choice.none.parallel/server"})
    public void shouldTransformRequestToolChoiceNoneParallelAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.request.tool.choice.auto/client",
        "${net}/openai.request.tool.choice.auto/server"})
    public void shouldRestoreOpenaiRequestToolChoiceAuto() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.request.tool.choice.required/client",
        "${net}/openai.request.tool.choice.required/server"})
    public void shouldRestoreOpenaiRequestToolChoiceRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.request.tool.choice.none/client",
        "${net}/openai.request.tool.choice.none/server"})
    public void shouldRestoreOpenaiRequestToolChoiceNone() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.request.tool.choice.function/client",
        "${net}/openai.request.tool.choice.function/server"})
    public void shouldRestoreOpenaiRequestToolChoiceFunction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.request.tool.choice.allowed/client",
        "${net}/openai.request.tool.choice.allowed/server"})
    public void shouldRestoreOpenaiRequestToolChoiceAllowed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.request.tool.choice.parallel/client",
        "${net}/openai.request.tool.choice.parallel/server"})
    public void shouldRestoreOpenaiRequestToolChoiceParallel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.request.tool.choice.mixed/client",
        "${net}/openai.request.tool.choice.mixed/server"})
    public void shouldRestoreOpenaiRequestToolChoiceMixed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.request.response.format.text/client",
        "${net}/openai.request.response.format.text/server"})
    public void shouldRestoreOpenaiRequestResponseFormatText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.request.response.format.json.object/client",
        "${net}/openai.request.response.format.json.object/server"})
    public void shouldRestoreOpenaiRequestResponseFormatJsonObject() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.response.format.text/client",
        "${net}/openai.transform.request.response.format.text/server"})
    public void shouldTransformRequestResponseFormatTextAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"openai/ccs\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.response.format.text/client",
        "${net}/openai.transform.request.response.format.text/server"})
    public void shouldTransformRequestResponseFormatTextFromOpenaiAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.response.format.json.object/client",
        "${net}/openai.transform.request.response.format.json.object/server"})
    public void shouldTransformRequestResponseFormatJsonObjectAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"openai/ccs\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.response.format.json.object/client",
        "${net}/openai.transform.request.response.format.json.object/server"})
    public void shouldTransformRequestResponseFormatJsonObjectFromOpenaiAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.response.format.json.schema/client",
        "${net}/openai.transform.request.response.format.json.schema/server"})
    public void shouldTransformRequestResponseFormatJsonSchemaAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"openai/ccs\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.response.format.json.schema/client",
        "${net}/openai.transform.request.response.format.json.schema/server"})
    public void shouldTransformRequestResponseFormatJsonSchemaFromOpenaiAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\"", "effort \"none\"", "wire \"none\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/openai.transform.request.reasoning.effort/server"})
    public void shouldTransformReasoningEffortNoneAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\"", "effort \"minimal\"", "wire \"minimal\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/openai.transform.request.reasoning.effort/server"})
    public void shouldTransformReasoningEffortMinimalAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\"", "effort \"low\"", "wire \"low\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/openai.transform.request.reasoning.effort/server"})
    public void shouldTransformReasoningEffortLowAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\"", "effort \"medium\"", "wire \"medium\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/openai.transform.request.reasoning.effort/server"})
    public void shouldTransformReasoningEffortMediumAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"openai/ccs\"", "target \"openai/ccs\"", "effort \"high\"", "wire \"high\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/openai.transform.request.reasoning.effort/server"})
    public void shouldTransformReasoningEffortHighAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\"", "effort \"xhigh\"", "wire \"xhigh\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/openai.transform.request.reasoning.effort/server"})
    public void shouldTransformReasoningEffortXhighAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\"", "effort \"max\"", "wire \"xhigh\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/openai.transform.request.reasoning.effort/server"})
    public void shouldClampReasoningEffortMaxAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"gemini\"", "target \"openai/ccs\"", "budget \"0\"", "wire \"none\""})
    @Specification({
        "${app}/transform.request.reasoning.budget/client",
        "${net}/openai.transform.request.reasoning.effort/server"})
    public void shouldDeriveReasoningEffortNoneFromBudget0AsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"gemini\"", "target \"openai/ccs\"", "budget \"512\"", "wire \"minimal\""})
    @Specification({
        "${app}/transform.request.reasoning.budget/client",
        "${net}/openai.transform.request.reasoning.effort/server"})
    public void shouldDeriveReasoningEffortMinimalFromBudget512AsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"gemini\"", "target \"openai/ccs\"", "budget \"1024\"", "wire \"low\""})
    @Specification({
        "${app}/transform.request.reasoning.budget/client",
        "${net}/openai.transform.request.reasoning.effort/server"})
    public void shouldDeriveReasoningEffortLowFromBudget1024AsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"gemini\"", "target \"openai/ccs\"", "budget \"2048\"", "wire \"low\""})
    @Specification({
        "${app}/transform.request.reasoning.budget/client",
        "${net}/openai.transform.request.reasoning.effort/server"})
    public void shouldDeriveReasoningEffortLowFromBudget2048AsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"gemini\"", "target \"openai/ccs\"", "budget \"10000\"", "wire \"medium\""})
    @Specification({
        "${app}/transform.request.reasoning.budget/client",
        "${net}/openai.transform.request.reasoning.effort/server"})
    public void shouldDeriveReasoningEffortMediumFromBudget10000AsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"gemini\"", "target \"openai/ccs\"", "budget \"30000\"", "wire \"high\""})
    @Specification({
        "${app}/transform.request.reasoning.budget/client",
        "${net}/openai.transform.request.reasoning.effort/server"})
    public void shouldDeriveReasoningEffortHighFromBudget30000AsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\"", "effort \"low\"", "budget \"30000\"", "wire \"low\""})
    @Specification({
        "${app}/transform.request.reasoning.effort.budget/client",
        "${net}/openai.transform.request.reasoning.effort/server"})
    public void shouldPreferReasoningEffortOverBudgetAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.models.yaml")
    @ScriptProperty({
        "source \"gemini\"",
        "target \"openai/ccs\"",
        "model \"ladder-model\"",
        "budget \"3500\"",
        "wire \"medium\""})
    @Specification({
        "${app}/transform.request.reasoning.budget/client",
        "${net}/openai.transform.request.reasoning.effort/server"})
    public void shouldDeriveReasoningEffortFromBudgetWithConfiguredLadderAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.models.yaml")
    @ScriptProperty({
        "source \"gemini\"",
        "target \"openai/ccs\"",
        "model \"ladder-model\"",
        "budget \"1000\"",
        "wire \"minimal\""})
    @Specification({
        "${app}/transform.request.reasoning.budget/client",
        "${net}/openai.transform.request.reasoning.effort/server"})
    public void shouldDeriveReasoningEffortFromBudgetBelowConfiguredLadderAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.rejected.reasoning.effort/client",
        "${net}/openai.transform.rejected.reasoning/server"})
    public void shouldRejectReasoningEffortAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.rejected.reasoning.budget/client",
        "${net}/openai.transform.rejected.reasoning/server"})
    public void shouldRejectReasoningBudgetAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\"", "model \"o3-mini\""})
    @Specification({
        "${app}/transform.request.sampling.members/client",
        "${net}/openai.transform.request.sampling.unsupported/server"})
    public void shouldDropSamplingAsOpenaiModelRejectingSampling() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.models.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\"", "model \"o3-mini\""})
    @Specification({
        "${app}/transform.request.sampling.members/client",
        "${net}/openai.transform.request.sampling.supported/server"})
    public void shouldTransformSamplingAsOpenaiModelOverridingSampling() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.sampling.members/client",
        "${net}/openai.transform.request.sampling.supported/server"})
    public void shouldTransformSamplingAsOpenaiModelWithoutEntry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.shared.members/client",
        "${net}/openai.transform.request.shared.members/server"})
    public void shouldTransformSharedMembersAsOpenaiAndDropForeignMember() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"openai/ccs\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.shared.members/client",
        "${net}/openai.request.shared.members/server"})
    public void shouldRestoreSharedMembersAsOpenaiWithoutLimit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\"", "user \"user-123\"", "wire \"user-123\""})
    @Specification({
        "${app}/transform.request.user/client",
        "${net}/openai.transform.request.user/server"})
    public void shouldTransformUserAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\"", "user \"" + USER_128 + "\"", "wire \"" + USER_128 + "\""})
    @Specification({
        "${app}/transform.request.user/client",
        "${net}/openai.transform.request.user/server"})
    public void shouldTransformUserAtLimitAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\"", "user \"" + USER_129 + "\""})
    @Specification({
        "${app}/transform.request.user/client",
        "${net}/openai.transform.request.dropped/server"})
    public void shouldDropUserOverLimitAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\"", "tier \"auto\"", "wire \"auto\""})
    @Specification({
        "${app}/transform.request.service.tier/client",
        "${net}/openai.transform.request.service.tier/server"})
    public void shouldTransformServiceTierAutoAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\"", "tier \"default\"", "wire \"default\""})
    @Specification({
        "${app}/transform.request.service.tier/client",
        "${net}/openai.transform.request.service.tier/server"})
    public void shouldTransformServiceTierDefaultAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\"", "tier \"flex\"", "wire \"flex\""})
    @Specification({
        "${app}/transform.request.service.tier/client",
        "${net}/openai.transform.request.service.tier/server"})
    public void shouldTransformServiceTierFlexAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\"", "tier \"priority\"", "wire \"priority\""})
    @Specification({
        "${app}/transform.request.service.tier/client",
        "${net}/openai.transform.request.service.tier/server"})
    public void shouldTransformServiceTierPriorityAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.tiers.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\"", "model \"no-flex\"", "tier \"flex\""})
    @Specification({
        "${app}/transform.request.service.tier/client",
        "${net}/openai.transform.request.dropped/server"})
    public void shouldDropServiceTierNotConfiguredAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.tiers.yaml")
    @ScriptProperty({
        "source \"anthropic\"",
        "target \"openai/ccs\"",
        "model \"no-flex\"",
        "tier \"priority\"",
        "wire \"priority\""})
    @Specification({
        "${app}/transform.request.service.tier/client",
        "${net}/openai.transform.request.service.tier/server"})
    public void shouldTransformServiceTierConfiguredAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.tiers.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\"", "model \"other-model\"", "tier \"flex\"", "wire \"flex\""})
    @Specification({
        "${app}/transform.request.service.tier/client",
        "${net}/openai.transform.request.service.tier/server"})
    public void shouldTransformServiceTierWithoutModelEntryAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.request.user/client",
        "${net}/openai.request.user/server"})
    public void shouldEncodeOpenaiRequestUser() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"tier \"auto\"", "wire \"auto\""})
    @Specification({
        "${app}/openai.request.service.tier/client",
        "${net}/openai.request.service.tier/server"})
    public void shouldEncodeOpenaiRequestServiceTierAuto() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"tier \"flex\"", "wire \"flex\""})
    @Specification({
        "${app}/openai.request.service.tier/client",
        "${net}/openai.request.service.tier/server"})
    public void shouldEncodeOpenaiRequestServiceTierFlex() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"wire \"scale\""})
    @Specification({
        "${app}/openai.request.service.tier.residue/client",
        "${net}/openai.request.service.tier.residue/server"})
    public void shouldEncodeOpenaiRequestServiceTierResidueScale() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.cache.breakpoint/client",
        "${net}/openai.transform.user.text/server"})
    public void shouldDropCacheBreakpointAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.cache.breakpoint.default/client",
        "${net}/openai.transform.user.text/server"})
    public void shouldDropCacheBreakpointWithoutTtlAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.tool.definition.strict/client",
        "${net}/openai.transform.tool.definition.strict/server"})
    public void shouldTransformToolDefinitionStrictAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.user.document.citations/client",
        "${net}/openai.transform.user.document.reference/server"})
    public void shouldDropUserDocumentCitationsAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.assistant.reasoning.redacted/client",
        "${net}/openai.transform.assistant.reasoning/server"})
    public void shouldDropRedactedReasoningAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.unsupported.yaml")
    @ScriptProperty({
        "source \"anthropic\"",
        "target \"openai/ccs\"",
        "model \"strict-model\"",
        "member \"parallel_tool_calls\"",
        "value \"false\""})
    @Specification({
        "${app}/transform.rejected.member/client",
        "${net}/openai.transform.rejected.member/server"})
    public void shouldRejectUnsupportedParallelToolCallsFalseAsOpenaiNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.unsupported.yaml")
    @ScriptProperty({
        "source \"anthropic\"",
        "target \"openai/ccs\"",
        "model \"strict-model\"",
        "member \"response_format\"",
        "value \"{\\\"type\\\":\\\"json_object\\\"}\""})
    @Specification({
        "${app}/transform.rejected.member/client",
        "${net}/openai.transform.rejected.member/server"})
    public void shouldRejectUnsupportedResponseFormatJsonObjectAsOpenaiNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.unsupported.yaml")
    @ScriptProperty({
        "source \"anthropic\"",
        "target \"openai/ccs\"",
        "model \"strict-model\"",
        "member \"response_format\"",
        "value \"{\\\"type\\\":\\\"json_schema\\\",\\\"json_schema\\\":{\\\"name\\\":\\\"n\\\",\\\"schema\\\":{}}}\""})
    @Specification({
        "${app}/transform.rejected.member/client",
        "${net}/openai.transform.rejected.member/server"})
    public void shouldRejectUnsupportedResponseFormatJsonSchemaAsOpenaiNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.unsupported.yaml")
    @ScriptProperty({
        "source \"anthropic\"",
        "target \"openai/ccs\"",
        "model \"strict-model\"",
        "member \"tool_choice\"",
        "value \"\\\"required\\\"\""})
    @Specification({
        "${app}/transform.rejected.member/client",
        "${net}/openai.transform.rejected.member/server"})
    public void shouldRejectUnsupportedToolChoiceRequiredAsOpenaiNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.unsupported.yaml")
    @ScriptProperty({
        "source \"anthropic\"",
        "target \"openai/ccs\"",
        "model \"strict-model\"",
        "member \"stop\"",
        "value \"[\\\"END\\\"]\""})
    @Specification({
        "${app}/transform.rejected.member/client",
        "${net}/openai.transform.rejected.member/server"})
    public void shouldRejectUnsupportedStopAsOpenaiNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.unsupported.yaml")
    @ScriptProperty({
        "source \"anthropic\"",
        "target \"openai/ccs\"",
        "model \"strict-model\"",
        "member \"parallel_tool_calls\"",
        "value \"true\""})
    @Specification({
        "${app}/transform.request.member/client",
        "${net}/openai.transform.request.dropped/server"})
    public void shouldDropUnsupportedParallelToolCallsTrueAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.unsupported.yaml")
    @ScriptProperty({
        "source \"anthropic\"",
        "target \"openai/ccs\"",
        "model \"strict-model\"",
        "member \"response_format\"",
        "value \"{\\\"type\\\":\\\"text\\\"}\""})
    @Specification({
        "${app}/transform.request.member/client",
        "${net}/openai.transform.request.dropped/server"})
    public void shouldDropUnsupportedResponseFormatTextAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.unsupported.yaml")
    @ScriptProperty({
        "source \"anthropic\"",
        "target \"openai/ccs\"",
        "model \"strict-model\"",
        "member \"tool_choice\"",
        "value \"\\\"auto\\\"\""})
    @Specification({
        "${app}/transform.request.member/client",
        "${net}/openai.transform.request.dropped/server"})
    public void shouldDropUnsupportedToolChoiceAutoAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.unsupported.yaml")
    @ScriptProperty({
        "source \"anthropic\"",
        "target \"openai/ccs\"",
        "model \"strict-model\"",
        "member \"stop\"",
        "value \"null\""})
    @Specification({
        "${app}/transform.request.member/client",
        "${net}/openai.transform.request.dropped/server"})
    public void shouldDropUnsupportedStopNullAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.unsupported.yaml")
    @ScriptProperty({
        "source \"anthropic\"",
        "target \"openai/ccs\"",
        "model \"strict-model\"",
        "member \"parallel_tool_calls\"",
        "value \"null\""})
    @Specification({
        "${app}/transform.request.member/client",
        "${net}/openai.transform.request.dropped/server"})
    public void shouldDropUnsupportedParallelToolCallsNullAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.unsupported.yaml")
    @ScriptProperty({
        "source \"anthropic\"",
        "target \"openai/ccs\"",
        "model \"strict-model\"",
        "member \"user\"",
        "value \"\\\"user-1\\\"\""})
    @Specification({
        "${app}/transform.request.member/client",
        "${net}/openai.transform.request.dropped/server"})
    public void shouldDropUnsupportedUserAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.unsupported.yaml")
    @ScriptProperty({
        "source \"anthropic\"",
        "target \"openai/ccs\"",
        "model \"strict-model\"",
        "member \"reasoning_effort\"",
        "value \"\\\"high\\\"\""})
    @Specification({
        "${app}/transform.request.member/client",
        "${net}/openai.transform.request.dropped/server"})
    public void shouldDropUnsupportedReasoningEffortAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.unsupported.yaml")
    @ScriptProperty({
        "source \"anthropic\"",
        "target \"openai/ccs\"",
        "member \"seed\"",
        "value \"42\""})
    @Specification({
        "${app}/transform.request.member/client",
        "${net}/openai.transform.request.dropped/server"})
    public void shouldDropUnsupportedSeedByDefaultAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.unsupported.yaml")
    @ScriptProperty({
        "source \"anthropic\"",
        "target \"openai/ccs\""})
    @Specification({
        "${app}/transform.request.tool.choice.parallel/client",
        "${net}/openai.transform.request.tool.choice.parallel/server"})
    public void shouldTransformParallelToolCallsFalseWhenNotUnsupportedAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.unsupported.yaml")
    @ScriptProperty({
        "source \"anthropic\"",
        "target \"openai/ccs\"",
        "model \"lenient-model\""})
    @Specification({
        "${app}/transform.request.sampling.seed/client",
        "${net}/openai.transform.request.sampling.seed/server"})
    public void shouldTransformSeedWhenModelEntryClearsUnsupportedAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.unsupported.yaml")
    @ScriptProperty({
        "source \"anthropic\"",
        "target \"openai/ccs\"",
        "model \"strict-model\""})
    @Specification({
        "${app}/transform.request.sampling.seed/client",
        "${net}/openai.transform.request.sampling.seed/server"})
    public void shouldTransformSeedWhenModelEntryReplacesUnsupportedAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.exclusive.yaml")
    @ScriptProperty({
        "source \"anthropic\"",
        "target \"openai/ccs\"",
        "effort \"high\"",
        "budget \"8192\"",
        "wire \"medium\""})
    @Specification({
        "${app}/transform.request.reasoning.effort.budget/client",
        "${net}/openai.transform.request.reasoning.effort/server"})
    public void shouldTransformReasoningEffortAndBudgetBudgetFirstByDefaultAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.exclusive.yaml")
    @ScriptProperty({
        "source \"anthropic\"",
        "target \"openai/ccs\"",
        "model \"effort-first\"",
        "effort \"high\"",
        "budget \"8192\"",
        "wire \"high\""})
    @Specification({
        "${app}/transform.request.reasoning.effort.budget/client",
        "${net}/openai.transform.request.reasoning.effort/server"})
    public void shouldTransformReasoningEffortAndBudgetEffortFirstAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.streaming.controls.many/client",
        "${net}/openai.streaming.controls.many/server"})
    public void shouldDecodeOpenaiStreamingControlsMany() throws Exception
    @Configuration("client.openai.chat.yaml")
    @Specification({
        "${app}/openai.responses.transform.request.members/client",
        "${net}/openai.responses.transform.request.members.chat/server"})
    public void shouldRestoreSharedAndDropResponsesRequestMembersAsOpenaiChatCompletionsWhenApisRestricted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.chat.yaml")
    @ScriptProperty({
        "source \"openai/rs\"",
        "target \"openai/ccs\"",
        "model \"model\"",
        "member \"openai/rs:previous_response_id\"",
        "value \"\\\"resp_1\\\"\""})
    @Specification({
        "${app}/transform.rejected.prefixed.member/client",
        "${net}/openai.transform.rejected.member/server"})
    public void shouldRejectBehaviouralMemberOfResponsesAsOpenaiChatCompletions() throws Exception
    {
        k3po.finish();
    }
}
