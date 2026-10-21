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

public class LlmGeminiClientIT
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
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini/client",
        "${net}/gemini/server"})
    public void shouldForwardGeminiNonstreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.system.instruction/client",
        "${net}/gemini.section.system.instruction/server"})
    public void shouldForwardGeminiSectionSystemInstruction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.system.instruction.last/client",
        "${net}/gemini.section.system.instruction.last/server"})
    public void shouldForwardGeminiSectionSystemInstructionLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.system.instruction.parts/client",
        "${net}/gemini.section.system.instruction.parts/server"})
    public void shouldForwardGeminiSectionSystemInstructionParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.conversation/client",
        "${net}/gemini.section.conversation/server"})
    public void shouldForwardGeminiSectionConversation() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.user.text.parts/client",
        "${net}/gemini.section.user.text.parts/server"})
    public void shouldForwardGeminiSectionUserTextParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.user.text.multibyte/client",
        "${net}/gemini.section.user.text.multibyte/server"})
    public void shouldForwardGeminiSectionUserTextMultibyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.user.text.escaped/client",
        "${net}/gemini.section.user.text.escaped/server"})
    public void shouldForwardGeminiSectionUserTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.user.text.empty/client",
        "${net}/gemini.section.user.text.empty/server"})
    public void shouldForwardGeminiSectionUserTextEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.assistant.text.multibyte/client",
        "${net}/gemini.section.assistant.text.multibyte/server"})
    public void shouldForwardGeminiSectionAssistantTextMultibyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.assistant.text.escaped/client",
        "${net}/gemini.section.assistant.text.escaped/server"})
    public void shouldForwardGeminiSectionAssistantTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.options/client",
        "${net}/gemini.request.options/server"})
    public void shouldForwardGeminiRequestOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.options.leading/client",
        "${net}/gemini.request.options/server"})
    public void shouldForwardGeminiRequestOptionsLeading() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.options.verbatim/client",
        "${net}/gemini.request.options.verbatim/server"})
    public void shouldForwardGeminiRequestOptionsVerbatim() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.guarded.yaml")
    @Specification({
        "${app}/gemini.authorized/client",
        "${net}/gemini.authorized/server"})
    public void shouldForwardGeminiAuthorized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.rejected.rate.limit/client",
        "${net}/gemini.rejected.rate.limit/server"})
    public void shouldForwardGeminiRejectedRateLimit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.rejected.model.not.found/client",
        "${net}/gemini.rejected.model.not.found/server"})
    public void shouldForwardGeminiRejectedModelNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.usage/client",
        "${net}/gemini.usage/server"})
    public void shouldForwardGeminiUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.usage.cached/client",
        "${net}/gemini.usage.cached/server"})
    public void shouldForwardGeminiUsageCached() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.assistant.text.members/client",
        "${net}/gemini.section.assistant.text.members/server"})
    public void shouldForwardGeminiSectionAssistantTextMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.assistant.text.empty/client",
        "${net}/gemini.section.assistant.text.empty/server"})
    public void shouldForwardGeminiSectionAssistantTextEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.streaming.complete/client",
        "${net}/gemini.streaming.complete/server"})
    public void shouldForwardGeminiStreamingComplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.streaming.usage/client",
        "${net}/gemini.streaming.usage/server"})
    public void shouldForwardGeminiStreamingUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.response.text/client",
        "${net}/gemini.response.text/server"})
    public void shouldForwardGeminiResponseText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.response.text/client",
        "${net}/gemini.response.text.split/server"})
    public void shouldForwardGeminiResponseTextSplit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"operation \"streamGenerateContent\""})
    @Specification({
        "${app}/gemini.response.text/client",
        "${net}/gemini.response.text.sse/server"})
    public void shouldForwardGeminiResponseTextSse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"operation \"streamGenerateContent\""})
    @Specification({
        "${app}/gemini.response.text/client",
        "${net}/gemini.response.text.sse.split/server"})
    public void shouldForwardGeminiResponseTextSseSplit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"operation \"streamGenerateContent\""})
    @Specification({
        "${app}/gemini.response.text/client",
        "${net}/gemini.response.text.array/server"})
    public void shouldForwardGeminiResponseTextArray() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.transform.request.frames/client",
        "${net}/gemini.transform.request.frames/server"})
    public void shouldTransformGeminiTransformRequestFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.transform.system.instruction/client",
        "${net}/gemini.transform.system.instruction/server"})
    public void shouldTransformGeminiTransformSystemInstruction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.transform.assistant.text/client",
        "${net}/gemini.transform.assistant.text/server"})
    public void shouldTransformGeminiTransformAssistantText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.transform.request.max.output.tokens/client",
        "${net}/gemini.transform.request.max.output.tokens/server"})
    public void shouldTransformGeminiTransformRequestMaxOutputTokens() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.10k/client",
        "${net}/gemini.10k/server"})
    public void shouldForwardGemini10K() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.100k/client",
        "${net}/gemini.100k/server"})
    public void shouldForwardGemini100K() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.rejected.streaming/client",
        "${net}/gemini.rejected.streaming/server"})
    public void shouldForwardGeminiRejectedStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.rejected.unreachable/client"})
    public void shouldForwardGeminiRejectedUnreachable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.streaming.error/client",
        "${net}/gemini.streaming.error/server"})
    public void shouldForwardGeminiStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.transform.response.streaming/client",
        "${net}/gemini.transform.response.streaming/server"})
    public void shouldForwardGeminiTransformResponseStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"gemini\"", "target \"gemini\"", "reason \"gemini:MALFORMED_FUNCTION_CALL\""})
    @Specification({
        "${app}/gemini.transform.response.finish.reason/client",
        "${net}/gemini.transform.response.finish.reason.unknown/server"})
    public void shouldPrefixUnknownFinishReasonAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.tool.definition/client",
        "${net}/gemini.section.tool.definition/server"})
    public void shouldForwardGeminiSectionToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.tool.definition.leading/client",
        "${net}/gemini.section.tool.definition.leading/server"})
    public void shouldForwardGeminiSectionToolDefinitionLeading() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.tool.definition.multiple/client",
        "${net}/gemini.section.tool.definition.multiple/server"})
    public void shouldForwardGeminiSectionToolDefinitionMultiple() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.tool.definition.minimal/client",
        "${net}/gemini.section.tool.definition.minimal/server"})
    public void shouldForwardGeminiSectionToolDefinitionMinimal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.tool.definition.parameters.10k/client",
        "${net}/gemini.section.tool.definition.parameters.10k/server"})
    public void shouldForwardGeminiSectionToolDefinitionParameters10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.tool.definition.parameters.100k/client",
        "${net}/gemini.section.tool.definition.parameters.100k/server"})
    public void shouldForwardGeminiSectionToolDefinitionParameters100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.assistant.tool.call/client",
        "${net}/gemini.section.assistant.tool.call/server"})
    public void shouldForwardGeminiSectionAssistantToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.assistant.text.and.tool.call/client",
        "${net}/gemini.section.assistant.text.and.tool.call/server"})
    public void shouldForwardGeminiSectionAssistantTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.assistant.tool.calls/client",
        "${net}/gemini.section.assistant.tool.calls/server"})
    public void shouldForwardGeminiSectionAssistantToolCalls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.assistant.tool.call.empty/client",
        "${net}/gemini.section.assistant.tool.call.empty/server"})
    public void shouldForwardGeminiSectionAssistantToolCallEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.assistant.tool.call.signature/client",
        "${net}/gemini.section.assistant.tool.call.signature/server"})
    public void shouldForwardGeminiSectionAssistantToolCallSignature() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.tool.call.history/client",
        "${net}/gemini.section.tool.call.history/server"})
    public void shouldForwardGeminiSectionToolCallHistory() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.streaming.tool.call/client",
        "${net}/gemini.streaming.tool.call/server"})
    public void shouldForwardGeminiStreamingToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.transform.assistant.tool.call/client",
        "${net}/gemini.transform.assistant.tool.call/server"})
    public void shouldForwardGeminiTransformAssistantToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.transform.assistant.text.and.tool.call/client",
        "${net}/gemini.transform.assistant.text.and.tool.call/server"})
    public void shouldForwardGeminiTransformAssistantTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.tool.result/client",
        "${net}/gemini.section.tool.result/server"})
    public void shouldForwardGeminiSectionToolResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.tool.result.error/client",
        "${net}/gemini.section.tool.result.error/server"})
    public void shouldForwardGeminiSectionToolResultError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.tool.result.escaped/client",
        "${net}/gemini.section.tool.result.escaped/server"})
    public void shouldForwardGeminiSectionToolResultEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.tool.results/client",
        "${net}/gemini.section.tool.results/server"})
    public void shouldForwardGeminiSectionToolResults() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.transform.tool.result/client",
        "${net}/gemini.transform.tool.result/server"})
    public void shouldForwardGeminiTransformToolResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.transform.tool.result.error/client",
        "${net}/gemini.transform.tool.result.error/server"})
    public void shouldForwardGeminiTransformToolResultError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.user.image.inline/client",
        "${net}/gemini.section.user.image.inline/server"})
    public void shouldForwardGeminiSectionUserImageInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.user.image.inline.text/client",
        "${net}/gemini.section.user.image.inline.text/server"})
    public void shouldForwardGeminiSectionUserImageInlineText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.user.image.inline.text.first/client",
        "${net}/gemini.section.user.image.inline.text.first/server"})
    public void shouldForwardGeminiSectionUserImageInlineTextFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.user.image.url/client",
        "${net}/gemini.section.user.image.url/server"})
    public void shouldForwardGeminiSectionUserImageUrl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.user.image.reference/client",
        "${net}/gemini.section.user.image.reference/server"})
    public void shouldForwardGeminiSectionUserImageReference() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.user.image.reference.storage/client",
        "${net}/gemini.section.user.image.reference.storage/server"})
    public void shouldForwardGeminiSectionUserImageReferenceStorage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.user.document.inline/client",
        "${net}/gemini.section.user.document.inline/server"})
    public void shouldForwardGeminiSectionUserDocumentInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.user.document.url/client",
        "${net}/gemini.section.user.document.url/server"})
    public void shouldForwardGeminiSectionUserDocumentUrl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.user.document.reference/client",
        "${net}/gemini.section.user.document.reference/server"})
    public void shouldForwardGeminiSectionUserDocumentReference() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.user.document.text/client",
        "${net}/gemini.section.user.document.text/server"})
    public void shouldForwardGeminiSectionUserDocumentText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.user.audio.inline/client",
        "${net}/gemini.section.user.audio.inline/server"})
    public void shouldForwardGeminiSectionUserAudioInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.user.audio.url/client",
        "${net}/gemini.section.user.audio.url/server"})
    public void shouldForwardGeminiSectionUserAudioUrl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.user.video.inline/client",
        "${net}/gemini.section.user.video.inline/server"})
    public void shouldForwardGeminiSectionUserVideoInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.user.video.url/client",
        "${net}/gemini.section.user.video.url/server"})
    public void shouldForwardGeminiSectionUserVideoUrl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.user.video.reference/client",
        "${net}/gemini.section.user.video.reference/server"})
    public void shouldForwardGeminiSectionUserVideoReference() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.user.video.metadata/client",
        "${net}/gemini.section.user.video.metadata/server"})
    public void shouldForwardGeminiSectionUserVideoMetadata() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.user.image.display.name/client",
        "${net}/gemini.section.user.image.display.name/server"})
    public void shouldForwardGeminiSectionUserImageDisplayName() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.user.image.inline.10k/client",
        "${net}/gemini.section.user.image.inline.10k/server"})
    public void shouldForwardGeminiUserImageInline10K() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.user.image.inline.100k/client",
        "${net}/gemini.section.user.image.inline.100k/server"})
    public void shouldForwardGeminiUserImageInline100K() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.transform.user.image.inline/client",
        "${net}/gemini.transform.user.image.inline/server"})
    public void shouldForwardGeminiTransformUserImageInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.transform.user.image.url/client",
        "${net}/gemini.transform.user.image.url/server"})
    public void shouldForwardGeminiTransformUserImageUrl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.transform.user.image.url.untyped/client",
        "${net}/gemini.transform.user.image.url.untyped/server"})
    public void shouldForwardGeminiTransformUserImageUrlUntyped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.transform.user.image.reference/client",
        "${net}/gemini.transform.user.image.reference/server"})
    public void shouldForwardGeminiTransformUserImageReference() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.transform.user.image.detail/client",
        "${net}/gemini.transform.user.image.detail/server"})
    public void shouldForwardGeminiTransformUserImageDetail() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.transform.user.document.inline/client",
        "${net}/gemini.transform.user.document.inline/server"})
    public void shouldForwardGeminiTransformUserDocumentInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.transform.user.document.filename/client",
        "${net}/gemini.transform.user.document.filename/server"})
    public void shouldForwardGeminiTransformUserDocumentFilename() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.transform.user.document.reference/client",
        "${net}/gemini.transform.user.document.reference/server"})
    public void shouldForwardGeminiTransformUserDocumentReference() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.transform.user.audio.inline/client",
        "${net}/gemini.transform.user.audio.inline/server"})
    public void shouldForwardGeminiTransformUserAudioInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.transform.user.video.url/client",
        "${net}/gemini.transform.user.video.url/server"})
    public void shouldForwardGeminiTransformUserVideoUrl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.assistant.reasoning/client",
        "${net}/gemini.section.assistant.reasoning/server"})
    public void shouldForwardGeminiSectionAssistantReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.assistant.reasoning.signature/client",
        "${net}/gemini.section.assistant.reasoning.signature/server"})
    public void shouldForwardGeminiSectionAssistantReasoningSignature() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.assistant.reasoning.thought.first/client",
        "${net}/gemini.section.assistant.reasoning.thought.first/server"})
    public void shouldForwardGeminiSectionAssistantReasoningThoughtFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.assistant.text.signature/client",
        "${net}/gemini.section.assistant.text.signature/server"})
    public void shouldForwardGeminiSectionAssistantTextSignature() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.assistant.reasoning.tool.call/client",
        "${net}/gemini.section.assistant.reasoning.tool.call/server"})
    public void shouldForwardGeminiSectionAssistantReasoningToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.streaming.assistant.reasoning/client",
        "${net}/gemini.streaming.assistant.reasoning/server"})
    public void shouldForwardGeminiStreamingAssistantReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.streaming.assistant.reasoning.split/client",
        "${net}/gemini.streaming.assistant.reasoning.split/server"})
    public void shouldForwardGeminiStreamingAssistantReasoningSplit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.reasoning.history/client",
        "${net}/gemini.section.reasoning.history/server"})
    public void shouldForwardGeminiSectionReasoningHistory() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.reasoning.history.signature/client",
        "${net}/gemini.section.reasoning.history.signature/server"})
    public void shouldForwardGeminiSectionReasoningHistorySignature() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.reasoning.history.tool.call/client",
        "${net}/gemini.section.reasoning.history.tool.call/server"})
    public void shouldForwardGeminiSectionReasoningHistoryToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.transform.assistant.reasoning/client",
        "${net}/gemini.transform.assistant.reasoning/server"})
    public void shouldForwardGeminiTransformAssistantReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.transform.assistant.text.signature/client",
        "${net}/gemini.transform.assistant.text.signature/server"})
    public void shouldForwardGeminiTransformAssistantTextSignature() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.server.tool.definition/client",
        "${net}/gemini.section.server.tool.definition/server"})
    public void shouldForwardGeminiSectionServerToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.server.tool.definition.leading/client",
        "${net}/gemini.section.server.tool.definition.leading/server"})
    public void shouldForwardGeminiSectionServerToolDefinitionLeading() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.server.tool.definition.code.execution/client",
        "${net}/gemini.section.server.tool.definition.code.execution/server"})
    public void shouldForwardGeminiSectionServerToolDefinitionCodeExecution() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.server.tool.definition.url.context/client",
        "${net}/gemini.section.server.tool.definition.url.context/server"})
    public void shouldForwardGeminiSectionServerToolDefinitionUrlContext() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.server.tool.definition.snake/client",
        "${net}/gemini.section.server.tool.definition.snake/server"})
    public void shouldForwardGeminiSectionServerToolDefinitionSnake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.server.tool.definition.options/client",
        "${net}/gemini.section.server.tool.definition.options/server"})
    public void shouldForwardGeminiSectionServerToolDefinitionOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.server.tool.definition.multiple/client",
        "${net}/gemini.section.server.tool.definition.multiple/server"})
    public void shouldForwardGeminiSectionServerToolDefinitionMultiple() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.server.tool.definition.mixed/client",
        "${net}/gemini.section.server.tool.definition.mixed/server"})
    public void shouldForwardGeminiSectionServerToolDefinitionMixed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.server.tool.definition.mixed.reversed/client",
        "${net}/gemini.section.server.tool.definition.mixed.reversed/server"})
    public void shouldForwardGeminiSectionServerToolDefinitionMixedReversed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.server.tool.definition.mixed.between/client",
        "${net}/gemini.section.server.tool.definition.mixed.between/server"})
    public void shouldForwardGeminiSectionServerToolDefinitionMixedBetween() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/transform.server.tool.definition/client",
        "${net}/gemini.transform.server.tool.definition/server"})
    public void shouldDropServerToolDefinitionAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.assistant.server.tool.call/client",
        "${net}/gemini.section.assistant.server.tool.call/server"})
    public void shouldForwardGeminiSectionAssistantServerToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.streaming.assistant.server.tool.call/client",
        "${net}/gemini.streaming.assistant.server.tool.call/server"})
    public void shouldForwardGeminiStreamingAssistantServerToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.assistant.server.tool.call.signature/client",
        "${net}/gemini.section.assistant.server.tool.call.signature/server"})
    public void shouldForwardGeminiSectionAssistantServerToolCallSignature() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.server.tool.call.history/client",
        "${net}/gemini.section.server.tool.call.history/server"})
    public void shouldForwardGeminiSectionServerToolCallHistory() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.section.server.tool.call.history.no.id/client",
        "${net}/gemini.section.server.tool.call.history.no.id/server"})
    public void shouldForwardGeminiSectionServerToolCallHistoryNoId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.transform.server.tool.call.skipped/client",
        "${net}/gemini.transform.server.tool.call.skipped/server"})
    public void shouldForwardGeminiTransformServerToolCallSkipped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.rejected.model.retired/client",
        "${net}/gemini.rejected.model.retired/server"})
    public void shouldForwardGeminiRejectedModelRetired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.transform.request.extension/client",
        "${net}/gemini.transform.request.extension/server"})
    public void shouldForwardGeminiTransformRequestExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.sampling/client",
        "${net}/gemini.request.sampling/server"})
    public void shouldForwardGeminiRequestSampling() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.sampling.residue.absent/client",
        "${net}/gemini.request.sampling.residue.absent/server"})
    public void shouldForwardGeminiRequestSamplingResidueAbsent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.sampling.empty/client",
        "${net}/gemini.request.sampling.empty/server"})
    public void shouldForwardGeminiRequestSamplingEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.sampling.interleaved/client",
        "${net}/gemini.request.sampling.interleaved/server"})
    public void shouldForwardGeminiRequestSamplingInterleaved() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.sampling/client",
        "${net}/gemini.transform.request.sampling/server"})
    public void shouldTransformRequestSamplingAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.sampling.top.k/client",
        "${net}/gemini.transform.request.sampling.top.k/server"})
    public void shouldTransformRequestSamplingTopKAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.sampling.seed/client",
        "${net}/gemini.transform.request.sampling.seed/server"})
    public void shouldTransformRequestSamplingSeedAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.sampling.seed.range/client",
        "${net}/gemini.transform.request.max.output.tokens/server"})
    public void shouldDropRequestSamplingSeedOutOfRangeAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.sampling.penalties/client",
        "${net}/gemini.transform.request.sampling.penalties/server"})
    public void shouldTransformRequestSamplingPenaltiesAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.sampling.stop/client",
        "${net}/gemini.transform.request.sampling.stop/server"})
    public void shouldTransformRequestSamplingStopAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.sampling.stop.limit/client",
        "${net}/gemini.transform.request.sampling.stop.limit/server"})
    public void shouldTransformRequestSamplingStopLimitAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.sampling.clamp/client",
        "${net}/gemini.transform.request.sampling.clamp/server"})
    public void shouldPassRequestSamplingTemperatureAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.rejected.sampling.stop.entries/client",
        "${net}/gemini.transform.rejected.sampling.stop.entries/server"})
    public void shouldRejectRequestSamplingStopEntriesAsGeminiNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.sampling.penalties/client",
        "${net}/gemini.transform.request.sampling.penalties/server"})
    public void shouldTransformRequestSamplingFromOpenaiAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"anthropic\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.sampling.seed.max/client",
        "${net}/gemini.transform.request.sampling.seed.max/server"})
    public void shouldTransformRequestSamplingSeedMaxAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.tool.choice.auto/client",
        "${net}/gemini.transform.request.tool.choice.auto/server"})
    public void shouldTransformRequestToolChoiceAutoAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.tool.choice.required/client",
        "${net}/gemini.transform.request.tool.choice.required/server"})
    public void shouldTransformRequestToolChoiceRequiredAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.tool.choice.none/client",
        "${net}/gemini.transform.request.tool.choice.none/server"})
    public void shouldTransformRequestToolChoiceNoneAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.tool.choice.function/client",
        "${net}/gemini.transform.request.tool.choice.function/server"})
    public void shouldTransformRequestToolChoiceFunctionAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.tool.choice.allowed/client",
        "${net}/gemini.transform.request.tool.choice.allowed/server"})
    public void shouldTransformRequestToolChoiceAllowedAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.tool.choice.allowed.required/client",
        "${net}/gemini.transform.request.tool.choice.allowed.required/server"})
    public void shouldTransformRequestToolChoiceAllowedRequiredAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.tool.choice.parallel.true/client",
        "${net}/gemini.transform.request.tool.choice.parallel.true/server"})
    public void shouldTransformRequestToolChoiceParallelTrueAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.rejected.tool.choice.value/client",
        "${net}/gemini.transform.rejected.tool.choice/server"})
    public void shouldRejectRequestToolChoiceValueAsGeminiNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.rejected.parallel.tool.calls/client",
        "${net}/gemini.transform.rejected.tool.choice/server"})
    public void shouldRejectRequestToolChoiceParallelAsGeminiNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.tool.choice.auto/client",
        "${net}/gemini.request.tool.choice.auto/server"})
    public void shouldRestoreGeminiRequestToolChoiceAuto() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.tool.choice.any/client",
        "${net}/gemini.request.tool.choice.any/server"})
    public void shouldRestoreGeminiRequestToolChoiceAny() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.tool.choice.none/client",
        "${net}/gemini.request.tool.choice.none/server"})
    public void shouldRestoreGeminiRequestToolChoiceNone() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.tool.choice.function/client",
        "${net}/gemini.request.tool.choice.function/server"})
    public void shouldRestoreGeminiRequestToolChoiceFunction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.tool.choice.allowed.required/client",
        "${net}/gemini.request.tool.choice.allowed.required/server"})
    public void shouldRestoreGeminiRequestToolChoiceAllowedRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.tool.choice.allowed.auto/client",
        "${net}/gemini.request.tool.choice.allowed.auto/server"})
    public void shouldRestoreGeminiRequestToolChoiceAllowedAuto() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.tool.choice.validated/client",
        "${net}/gemini.request.tool.choice.validated/server"})
    public void shouldRestoreGeminiRequestToolChoiceValidated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.tool.choice.residue/client",
        "${net}/gemini.request.tool.choice.residue/server"})
    public void shouldRestoreGeminiRequestToolChoiceResidue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.response.format.json.object/client",
        "${net}/gemini.request.response.format.json.object/server"})
    public void shouldRestoreGeminiRequestResponseFormatJsonObject() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.response.format.json.schema/client",
        "${net}/gemini.request.response.format.json.schema/server"})
    public void shouldRestoreGeminiRequestResponseFormatJsonSchema() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.response.format.residue/client",
        "${net}/gemini.request.response.format.residue/server"})
    public void shouldRestoreGeminiRequestResponseFormatResidue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.response.format.enum/client",
        "${net}/gemini.request.response.format.enum/server"})
    public void shouldRestoreGeminiRequestResponseFormatEnum() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.response.format.schema.first/client",
        "${net}/gemini.request.response.format.schema.first/server"})
    public void shouldRestoreGeminiRequestResponseFormatSchemaFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.response.format.deprecated.schema.first/client",
        "${net}/gemini.request.response.format.deprecated.schema.first/server"})
    public void shouldRestoreGeminiRequestResponseFormatDeprecatedSchemaFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.response.format.text/client",
        "${net}/gemini.transform.request.response.format.text/server"})
    public void shouldTransformRequestResponseFormatTextAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.response.format.json.object/client",
        "${net}/gemini.transform.request.response.format.json.object/server"})
    public void shouldTransformRequestResponseFormatJsonObjectAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.response.format.json.schema/client",
        "${net}/gemini.transform.request.response.format.json.schema/server"})
    public void shouldTransformRequestResponseFormatJsonSchemaAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.response.format.json.schema.names/client",
        "${net}/gemini.transform.request.response.format.json.schema.names/server"})
    public void shouldTransformRequestResponseFormatJsonSchemaNamesAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.response.format.json.schema.type.last/client",
        "${net}/gemini.transform.request.response.format.json.schema/server"})
    public void shouldTransformRequestResponseFormatJsonSchemaTypeLastAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.rejected.response.format.type/client",
        "${net}/gemini.transform.rejected.tool.choice/server"})
    public void shouldRejectResponseFormatTypeAsGeminiNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.rejected.response.format.keyword.pattern.properties/client",
        "${net}/gemini.transform.rejected.tool.choice/server"})
    public void shouldRejectResponseFormatKeywordPatternPropertiesAsGeminiNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.rejected.response.format.keyword.not/client",
        "${net}/gemini.transform.rejected.tool.choice/server"})
    public void shouldRejectResponseFormatKeywordNotAsGeminiNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.rejected.response.format.keyword.external.ref/client",
        "${net}/gemini.transform.rejected.tool.choice/server"})
    public void shouldRejectResponseFormatKeywordExternalRefAsGeminiNamingMemberAndDialects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "effort \"minimal\"",
        "wire \"MINIMAL\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/gemini.transform.request.reasoning.effort/server"})
    public void shouldTransformReasoningEffortMinimalAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "effort \"low\"",
        "wire \"LOW\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/gemini.transform.request.reasoning.effort/server"})
    public void shouldTransformReasoningEffortLowAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "effort \"medium\"",
        "wire \"MEDIUM\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/gemini.transform.request.reasoning.effort/server"})
    public void shouldTransformReasoningEffortMediumAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "effort \"high\"",
        "wire \"HIGH\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/gemini.transform.request.reasoning.effort/server"})
    public void shouldTransformReasoningEffortHighAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "effort \"xhigh\"",
        "wire \"HIGH\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/gemini.transform.request.reasoning.effort/server"})
    public void shouldTransformReasoningEffortXhighAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "effort \"max\"",
        "wire \"HIGH\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/gemini.transform.request.reasoning.effort/server"})
    public void shouldTransformReasoningEffortMaxAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "effort \"none\"",
        "wire \"MINIMAL\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/gemini.transform.request.reasoning.effort/server"})
    public void shouldTransformReasoningEffortNoneAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "effort \"high\"",
        "model \"budget-model\"",
        "wire \"24576\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/gemini.transform.request.reasoning.budget/server"})
    public void shouldDeriveReasoningBudget24576FromEffortHighAsGeminiBudgetModel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "effort \"medium\"",
        "model \"budget-model\"",
        "wire \"8192\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/gemini.transform.request.reasoning.budget/server"})
    public void shouldDeriveReasoningBudget8192FromEffortMediumAsGeminiBudgetModel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "effort \"low\"",
        "model \"budget-model\"",
        "wire \"1024\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/gemini.transform.request.reasoning.budget/server"})
    public void shouldDeriveReasoningBudget1024FromEffortLowAsGeminiBudgetModel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "effort \"minimal\"",
        "model \"budget-model\"",
        "wire \"1024\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/gemini.transform.request.reasoning.budget/server"})
    public void shouldDeriveReasoningBudget1024FromEffortMinimalAsGeminiBudgetModel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "effort \"xhigh\"",
        "model \"budget-model\"",
        "wire \"24576\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/gemini.transform.request.reasoning.budget/server"})
    public void shouldDeriveReasoningBudget24576FromEffortXhighAsGeminiBudgetModel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "effort \"max\"",
        "model \"budget-model\"",
        "wire \"24576\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/gemini.transform.request.reasoning.budget/server"})
    public void shouldDeriveReasoningBudget24576FromEffortMaxAsGeminiBudgetModel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "effort \"none\"",
        "model \"gemini-2.5-flash\"",
        "wire \"0\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/gemini.transform.request.reasoning.budget/server"})
    public void shouldDeriveReasoningBudget0FromEffortNoneAsGeminiGemini25Flash() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "effort \"none\"",
        "model \"gemini-2.5-pro\"",
        "wire \"128\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/gemini.transform.request.reasoning.budget/server"})
    public void shouldDeriveReasoningBudget128FromEffortNoneAsGeminiGemini25Pro() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "effort \"high\"",
        "model \"gemini-2.5-pro\"",
        "wire \"24576\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/gemini.transform.request.reasoning.budget/server"})
    public void shouldDeriveReasoningBudget24576FromEffortHighAsGeminiGemini25Pro() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "effort \"high\"",
        "model \"ladder-model\"",
        "wire \"700\""})
    @Specification({
        "${app}/transform.request.reasoning.effort/client",
        "${net}/gemini.transform.request.reasoning.budget/server"})
    public void shouldDeriveReasoningBudgetFromEffortAsGeminiConfiguredLadder() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "budget \"4096\"",
        "model \"budget-model\"",
        "wire \"4096\""})
    @Specification({
        "${app}/transform.request.reasoning.budget/client",
        "${net}/gemini.transform.request.reasoning.budget/server"})
    public void shouldTransformReasoningBudget4096AsGeminiBudgetModel4096() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "budget \"100000\"",
        "model \"budget-model\"",
        "wire \"24576\""})
    @Specification({
        "${app}/transform.request.reasoning.budget/client",
        "${net}/gemini.transform.request.reasoning.budget/server"})
    public void shouldTransformReasoningBudget100000AsGeminiBudgetModel24576() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "budget \"64\"",
        "model \"gemini-2.5-pro\"",
        "wire \"128\""})
    @Specification({
        "${app}/transform.request.reasoning.budget/client",
        "${net}/gemini.transform.request.reasoning.budget/server"})
    public void shouldTransformReasoningBudget64AsGeminiGemini25Pro128() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "budget \"4096\"",
        "model \"gemini-3.6-flash\"",
        "wire \"4096\""})
    @Specification({
        "${app}/transform.request.reasoning.budget/client",
        "${net}/gemini.transform.request.reasoning.budget/server"})
    public void shouldTransformReasoningBudget4096AsGeminiGemini36Flash4096() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "budget \"0\"",
        "model \"gemini-2.5-flash\"",
        "wire \"0\""})
    @Specification({
        "${app}/transform.request.reasoning.budget/client",
        "${net}/gemini.transform.request.reasoning.budget/server"})
    public void shouldTransformReasoningBudgetZeroAsGeminiNone() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "effort \"low\"",
        "budget \"4096\"",
        "wire \"LOW\""})
    @Specification({
        "${app}/transform.request.reasoning.effort.budget/client",
        "${net}/gemini.transform.request.reasoning.effort/server"})
    public void shouldPreferReasoningLevelOverBudgetAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "effort \"low\"",
        "budget \"4096\"",
        "model \"budget-model\"",
        "wire \"4096\""})
    @Specification({
        "${app}/transform.request.reasoning.effort.budget/client",
        "${net}/gemini.transform.request.reasoning.budget/server"})
    public void shouldPreferReasoningBudgetOverLevelAsGeminiBudgetModel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.rejected.reasoning.effort/client",
        "${net}/gemini.transform.rejected.reasoning/server"})
    public void shouldRejectReasoningEffortAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.rejected.reasoning.budget/client",
        "${net}/gemini.transform.rejected.reasoning/server"})
    public void shouldRejectReasoningBudgetAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.models.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "errorType \"INVALID_ARGUMENT\"",
        "model \"sampling-rejected\""})
    @Specification({
        "${app}/transform.request.sampling.members/client",
        "${net}/gemini.transform.request.sampling.unsupported/server"})
    public void shouldDropSamplingAsGeminiModelConfiguredToRejectSampling() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.sampling.members/client",
        "${net}/gemini.transform.request.sampling.supported/server"})
    public void shouldTransformSamplingAsGeminiModelWithoutEntry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.reasoning.level/client",
        "${net}/gemini.request.reasoning.level/server"})
    public void shouldEncodeGeminiRequestReasoningLevel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.reasoning.budget/client",
        "${net}/gemini.request.reasoning.budget/server"})
    public void shouldEncodeGeminiRequestReasoningBudget() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.reasoning.budget.automatic/client",
        "${net}/gemini.request.reasoning.budget.automatic/server"})
    public void shouldEncodeGeminiRequestReasoningBudgetAutomatic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.reasoning.residue.only/client",
        "${net}/gemini.request.reasoning.residue.only/server"})
    public void shouldEncodeGeminiRequestReasoningResidueOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @Specification({
        "${app}/gemini.request.reasoning.empty/client",
        "${net}/gemini.request.reasoning.empty/server"})
    public void shouldEncodeGeminiRequestReasoningEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "user \"user-123\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.user/client",
        "${net}/gemini.transform.request.dropped/server"})
    public void shouldDropUserAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"source \"openai\"", "target \"gemini\"", "tier \"auto\"", "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.service.tier/client",
        "${net}/gemini.transform.request.dropped/server"})
    public void shouldDropServiceTierAutoAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "tier \"default\"",
        "errorType \"INVALID_ARGUMENT\"",
        "wire \"standard\""})
    @Specification({
        "${app}/transform.request.service.tier/client",
        "${net}/gemini.transform.request.service.tier/server"})
    public void shouldTransformServiceTierDefaultAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "tier \"flex\"",
        "errorType \"INVALID_ARGUMENT\"",
        "wire \"flex\""})
    @Specification({
        "${app}/transform.request.service.tier/client",
        "${net}/gemini.transform.request.service.tier/server"})
    public void shouldTransformServiceTierFlexAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "tier \"priority\"",
        "errorType \"INVALID_ARGUMENT\"",
        "wire \"priority\""})
    @Specification({
        "${app}/transform.request.service.tier/client",
        "${net}/gemini.transform.request.service.tier/server"})
    public void shouldTransformServiceTierPriorityAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.tiers.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "model \"no-priority\"",
        "tier \"priority\"",
        "errorType \"INVALID_ARGUMENT\""})
    @Specification({
        "${app}/transform.request.service.tier/client",
        "${net}/gemini.transform.request.dropped/server"})
    public void shouldDropServiceTierNotConfiguredAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.tiers.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "model \"no-priority\"",
        "tier \"flex\"",
        "errorType \"INVALID_ARGUMENT\"",
        "wire \"flex\""})
    @Specification({
        "${app}/transform.request.service.tier/client",
        "${net}/gemini.transform.request.service.tier/server"})
    public void shouldTransformServiceTierConfiguredAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.tiers.yaml")
    @ScriptProperty({
        "source \"openai\"",
        "target \"gemini\"",
        "model \"other-model\"",
        "tier \"priority\"",
        "errorType \"INVALID_ARGUMENT\"",
        "wire \"priority\""})
    @Specification({
        "${app}/transform.request.service.tier/client",
        "${net}/gemini.transform.request.service.tier/server"})
    public void shouldTransformServiceTierWithoutModelEntryAsGemini() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"tier \"default\"", "wire \"standard\""})
    @Specification({
        "${app}/gemini.request.service.tier/client",
        "${net}/gemini.request.service.tier/server"})
    public void shouldEncodeGeminiRequestServiceTierDefault() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"tier \"flex\"", "wire \"flex\""})
    @Specification({
        "${app}/gemini.request.service.tier/client",
        "${net}/gemini.request.service.tier/server"})
    public void shouldEncodeGeminiRequestServiceTierFlex() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"tier \"priority\"", "wire \"priority\""})
    @Specification({
        "${app}/gemini.request.service.tier/client",
        "${net}/gemini.request.service.tier/server"})
    public void shouldEncodeGeminiRequestServiceTierPriority() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"wire \"TIER_X\""})
    @Specification({
        "${app}/gemini.request.service.tier.residue/client",
        "${net}/gemini.request.service.tier.residue/server"})
    public void shouldEncodeGeminiRequestServiceTierResidueUnknown() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.gemini.yaml")
    @ScriptProperty({"wire \"unspecified\""})
    @Specification({
        "${app}/gemini.request.service.tier.residue/client",
        "${net}/gemini.request.service.tier.residue/server"})
    public void shouldEncodeGeminiRequestServiceTierResidueUnspecified() throws Exception
    {
        k3po.finish();
    }
}
