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

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class LlmOpenaiServerIT
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
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.echo/client",
        "${app}/openai.echo/server"})
    public void shouldDetectOpenaiDialectFromPath() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.invalid/client"})
    public void shouldRejectInvalidOpenaiRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.reply.padded/client",
        "${app}/openai.reply.padded/server"})
    public void shouldForwardOpenaiNonstreamingWithReplyPadding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.10k/client",
        "${app}/openai.10k/server"})
    public void shouldForwardOpenai10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.100k/client",
        "${app}/openai.100k/server"})
    public void shouldForwardOpenai100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.guarded.yaml")
    @Specification({
        "${net}/openai.authorized/client",
        "${app}/openai.authorized/server"})
    public void shouldForwardOpenaiRequestAuthorized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.guarded.yaml")
    @Specification({
        "${net}/openai.rejected.authorization/client"})
    public void shouldRejectOpenaiRequestFailingAuthorization() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.rejected.rate.limit/client",
        "${app}/openai.rejected.rate.limit/server"})
    public void shouldRejectOpenaiRequestRateLimited() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.system.instruction/client",
        "${app}/openai.section.system.instruction/server"})
    public void shouldDecodeOpenaiSectionSystemInstruction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.request.response.format/client",
        "${app}/openai.request.response.format/server"})
    public void shouldDecodeOpenaiRequestResponseFormat() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.request.response.format.10k/client",
        "${app}/openai.request.response.format.10k/server"})
    public void shouldDecodeOpenaiRequestResponseFormat10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.request.response.format.100k/client",
        "${app}/openai.request.response.format.100k/server"})
    public void shouldDecodeOpenaiRequestResponseFormat100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.request.options/client",
        "${app}/openai.request.options/server"})
    public void shouldDecodeOpenaiRequestOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.request.options.early/client",
        "${app}/openai.request.options.early/server"})
    public void shouldDecodeOpenaiRequestOptionsEarly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.request.options.verbatim/client",
        "${app}/openai.request.options.verbatim/server"})
    public void shouldDecodeOpenaiRequestOptionsVerbatim() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.user.text/client",
        "${app}/openai.section.user.text/server"})
    public void shouldDecodeOpenaiSectionUserText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.user.image/client",
        "${app}/openai.section.user.image/server"})
    public void shouldDecodeOpenaiSectionUserImage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.user.image.detail/client",
        "${app}/openai.section.user.image.detail/server"})
    public void shouldDecodeOpenaiSectionUserImageDetail() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.user.image.detail.first/client",
        "${app}/openai.section.user.image.detail.first/server"})
    public void shouldDecodeOpenaiSectionUserImageDetailFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.user.document/client",
        "${app}/openai.section.user.document/server"})
    public void shouldDecodeOpenaiSectionUserDocument() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.user.audio/client",
        "${app}/openai.section.user.audio/server"})
    public void shouldDecodeOpenaiSectionUserAudio() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.assistant.text/client",
        "${app}/openai.section.assistant.text/server"})
    public void shouldDecodeOpenaiSectionAssistantText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.assistant.refusal/client",
        "${app}/openai.section.assistant.refusal/server"})
    public void shouldDecodeOpenaiSectionAssistantRefusal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.tool.definition/client",
        "${app}/openai.section.tool.definition/server"})
    public void shouldDecodeOpenaiSectionToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.tool.call/client",
        "${app}/openai.section.tool.call/server"})
    public void shouldDecodeOpenaiSectionToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.tool.result/client",
        "${app}/openai.section.tool.result/server"})
    public void shouldDecodeOpenaiSectionToolResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.mixed.content/client",
        "${app}/openai.section.mixed.content/server"})
    public void shouldDecodeOpenaiSectionMixedContent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.assistant.text.and.tool.call/client",
        "${app}/openai.section.assistant.text.and.tool.call/server"})
    public void shouldDecodeOpenaiSectionAssistantTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.conversation/client",
        "${app}/openai.section.conversation/server"})
    public void shouldDecodeOpenaiSectionConversation() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.user.text.10k/client",
        "${app}/openai.section.user.text.10k/server"})
    public void shouldDecodeOpenaiSectionUserText10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.user.text.100k/client",
        "${app}/openai.section.user.text.100k/server"})
    public void shouldDecodeOpenaiSectionUserText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.tool.result.100k/client",
        "${app}/openai.section.tool.result.100k/server"})
    public void shouldDecodeOpenaiSectionToolResult100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.user.text.100k.parts/client",
        "${app}/openai.section.user.text.100k/server"})
    public void shouldDecodeOpenaiSectionUserText100kParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.tool.result.100k.parts/client",
        "${app}/openai.section.tool.result.100k/server"})
    public void shouldDecodeOpenaiSectionToolResult100kParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.unknown.part/client",
        "${app}/openai.section.unknown.part/server"})
    public void shouldDecodeOpenaiSectionUnknownPart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.user.image.10k/client",
        "${app}/openai.section.user.image.10k/server"})
    public void shouldDecodeOpenaiSectionUserImage10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.user.text.escaped/client",
        "${app}/openai.section.user.text.escaped/server"})
    public void shouldDecodeOpenaiSectionUserTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.user.text.multibyte/client",
        "${app}/openai.section.user.text.multibyte/server"})
    public void shouldDecodeOpenaiSectionUserTextMultibyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.whitespace.between.tokens/client",
        "${app}/openai.section.whitespace.between.tokens/server"})
    public void shouldDecodeOpenaiSectionWhitespaceBetweenTokens() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.other.blocks/client",
        "${app}/openai.section.other.blocks/server"})
    public void shouldDecodeOpenaiSectionOtherBlocks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.rejected.role.missing/client",
        "${app}/openai.section.rejected.role.missing/server"})
    public void shouldDecodeOpenaiSectionRejectedRoleMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.rejected.messages.not.array/client",
        "${app}/openai.section.rejected.messages.not.array/server"})
    public void shouldDecodeOpenaiSectionRejectedMessagesNotArray() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.rejected.json.malformed/client",
        "${app}/openai.section.rejected.json.malformed/server"})
    public void shouldDecodeOpenaiSectionRejectedJsonMalformed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.rejected.trailing.content/client",
        "${app}/openai.section.rejected.trailing.content/server"})
    public void shouldDecodeOpenaiSectionRejectedTrailingContent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.rejected.model.last.oversize/client"})
    public void shouldDecodeOpenaiSectionRejectedModelLastOversize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.model.last/client",
        "${app}/openai.section.user.text/server"})
    public void shouldDecodeOpenaiSectionModelLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.rejected.role.last/client",
        "${app}/openai.section.rejected.role.last/server"})
    public void shouldDecodeOpenaiSectionRejectedRoleLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.rejected.tool.call.id.last/client",
        "${app}/openai.section.rejected.tool.call.id.last/server"})
    public void shouldDecodeOpenaiSectionRejectedToolCallIdLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.rejected.part.type.last/client",
        "${app}/openai.section.rejected.part.type.last/server"})
    public void shouldDecodeOpenaiSectionRejectedPartTypeLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.rejected.model.missing/client"})
    public void shouldDecodeOpenaiRejectedModelMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.user.image.inline/client",
        "${app}/openai.section.user.image.inline/server"})
    public void shouldDecodeOpenaiSectionUserImageInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.user.document.inline/client",
        "${app}/openai.section.user.document.inline/server"})
    public void shouldDecodeOpenaiSectionUserDocumentInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai/client",
        "${app}/openai/server"})
    public void shouldForwardOpenaiNonstreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.user.document.filename.last/client",
        "${app}/openai.section.user.document.filename.last/server"})
    public void shouldDecodeOpenaiSectionUserDocumentFilenameLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.user.audio.format.last/client",
        "${app}/openai.section.user.audio.format.last/server"})
    public void shouldDecodeOpenaiSectionUserAudioFormatLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.tool.definition.name.last/client",
        "${app}/openai.section.tool.definition.name.last/server"})
    public void shouldDecodeOpenaiSectionToolDefinitionNameLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.user.image.url.10k/client",
        "${app}/openai.section.user.image.url.10k/server"})
    public void shouldDecodeOpenaiSectionUserImageUrl10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.user.image.url.100k/client",
        "${app}/openai.section.user.image.url.100k/server"})
    public void shouldDecodeOpenaiSectionUserImageUrl100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.options/client",
        "${app}/openai.response.encode.options/server"})
    public void shouldEncodeOpenaiResponseOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.text/client",
        "${app}/openai.response.encode.text/server"})
    public void shouldEncodeOpenaiResponseText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.text.and.tool.call/client",
        "${app}/openai.response.encode.text.and.tool.call/server"})
    public void shouldEncodeOpenaiResponseTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.refusal/client",
        "${app}/openai.response.encode.refusal/server"})
    public void shouldEncodeOpenaiResponseRefusal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.streaming/client",
        "${app}/openai.response.encode.streaming/server"})
    public void shouldEncodeOpenaiResponseStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.streaming.tool.call/client",
        "${app}/openai.response.encode.streaming.tool.call/server"})
    public void shouldEncodeOpenaiResponseStreamingToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.streaming.error/client",
        "${app}/openai.response.encode.streaming.error/server"})
    public void shouldEncodeOpenaiResponseStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.text.100k/client",
        "${app}/openai.response.encode.text.100k/server"})
    public void shouldEncodeOpenaiResponseText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.reasoning/client",
        "${app}/openai.response.encode.reasoning/server"})
    public void shouldEncodeOpenaiResponseReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.text.escaped/client",
        "${app}/openai.response.encode.text.escaped/server"})
    public void shouldEncodeOpenaiResponseTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.streaming.multibyte/client",
        "${app}/openai.response.encode.streaming.multibyte/server"})
    public void shouldEncodeOpenaiResponseStreamingMultibyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.tool.call.100k/client",
        "${app}/openai.section.tool.call.100k/server"})
    public void shouldDecodeOpenaiSectionToolCall100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.tool.call.10k/client",
        "${app}/openai.section.tool.call.10k/server"})
    public void shouldDecodeOpenaiSectionToolCall10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.tool.call.escaped/client",
        "${app}/openai.section.tool.call.escaped/server"})
    public void shouldDecodeOpenaiSectionToolCallEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.tool.call.id.last/client",
        "${app}/openai.section.tool.call.id.last/server"})
    public void shouldDecodeOpenaiSectionToolCallIdLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.tool.call.name.last/client",
        "${app}/openai.section.tool.call.name.last/server"})
    public void shouldDecodeOpenaiSectionToolCallNameLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.tool.definition.100k/client",
        "${app}/openai.section.tool.definition.100k/server"})
    public void shouldDecodeOpenaiSectionToolDefinition100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.tool.definition.10k/client",
        "${app}/openai.section.tool.definition.10k/server"})
    public void shouldDecodeOpenaiSectionToolDefinition10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.tool.definition.parameters.100k/client",
        "${app}/openai.section.tool.definition.parameters.100k/server"})
    public void shouldDecodeOpenaiSectionToolDefinitionParameters100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.tool.definition.parameters.10k/client",
        "${app}/openai.section.tool.definition.parameters.10k/server"})
    public void shouldDecodeOpenaiSectionToolDefinitionParameters10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.streaming.logprobs/client",
        "${app}/openai.response.encode.streaming.logprobs/server"})
    public void shouldEncodeOpenaiResponseStreamingLogprobs() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.text.logprobs/client",
        "${app}/openai.response.encode.text.logprobs/server"})
    public void shouldEncodeOpenaiResponseTextLogprobs() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.streaming.logprobs.foreign/client",
        "${app}/openai.response.encode.streaming.logprobs.foreign/server"})
    public void shouldEncodeOpenaiResponseStreamingLogprobsForeign() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.message.name/client",
        "${app}/openai.section.message.name/server"})
    public void shouldDecodeOpenaiSectionMessageName() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.message.name.after/client",
        "${app}/openai.section.message.name.after/server"})
    public void shouldDecodeOpenaiSectionMessageNameAfter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.message.name.tool.result/client",
        "${app}/openai.section.message.name.tool.result/server"})
    public void shouldDecodeOpenaiSectionMessageNameToolResult() throws Exception
    {
        k3po.finish();
    }
}
