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

public class LlmAnthropicServerIT
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
        "${net}/anthropic.echo/client",
        "${app}/anthropic.echo/server"})
    public void shouldDetectAnthropicDialectFromPath() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.10k/client",
        "${app}/anthropic.10k/server"})
    public void shouldForwardAnthropic10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.100k/client",
        "${app}/anthropic.100k/server"})
    public void shouldForwardAnthropic100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.guarded.yaml")
    @Specification({
        "${net}/anthropic.authorized/client",
        "${app}/anthropic.authorized/server"})
    public void shouldForwardAnthropicRequestAuthorized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.guarded.yaml")
    @Specification({
        "${net}/anthropic.rejected.authorization/client"})
    public void shouldRejectAnthropicRequestFailingAuthorization() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.rejected.rate.limit/client",
        "${app}/anthropic.rejected.rate.limit/server"})
    public void shouldRejectAnthropicRequestRateLimited() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.rejected.section/client",
        "${app}/anthropic.rejected.section/server"})
    public void shouldRejectAnthropicRequestSection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.request.unsupported.content.type/client"})
    public void shouldRejectAnthropicRequestWithUnsupportedContentType() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.assistant.reasoning/client",
        "${app}/anthropic.section.assistant.reasoning/server"})
    public void shouldDecodeAnthropicSectionAssistantReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.assistant.reasoning.redacted/client",
        "${app}/anthropic.section.assistant.reasoning.redacted/server"})
    public void shouldDecodeAnthropicSectionAssistantReasoningRedacted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.assistant.text/client",
        "${app}/anthropic.section.assistant.text/server"})
    public void shouldDecodeAnthropicSectionAssistantText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.tool.result.cache.control.part.first/client",
        "${app}/anthropic.section.tool.result.cache.control.part.first/server"})
    public void shouldDecodeAnthropicSectionToolResultCacheControlPartFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.tool.result.cache.control.first/client",
        "${app}/anthropic.section.tool.result.cache.control.first/server"})
    public void shouldDecodeAnthropicSectionToolResultCacheControlFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.tool.result.cache.control.part/client",
        "${app}/anthropic.section.tool.result.cache.control.part/server"})
    public void shouldDecodeAnthropicSectionToolResultCacheControlPart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.tool.result.cache.control/client",
        "${app}/anthropic.section.tool.result.cache.control/server"})
    public void shouldDecodeAnthropicSectionToolResultCacheControl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.tool.definition.cache.control/client",
        "${app}/anthropic.section.tool.definition.cache.control/server"})
    public void shouldDecodeAnthropicSectionToolDefinitionCacheControl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.other.blocks/client",
        "${app}/anthropic.section.other.blocks/server"})
    public void shouldDecodeAnthropicSectionOtherBlocks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.system.instruction.cache.control/client",
        "${app}/anthropic.section.system.instruction.cache.control/server"})
    public void shouldDecodeAnthropicSectionSystemInstructionCacheControl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.user.text.cache.control.first/client",
        "${app}/anthropic.section.user.text.cache.control.first/server"})
    public void shouldDecodeAnthropicSectionUserTextCacheControlFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.user.text.cache.control/client",
        "${app}/anthropic.section.user.text.cache.control/server"})
    public void shouldDecodeAnthropicSectionUserTextCacheControl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.assistant.text.and.tool.call/client",
        "${app}/anthropic.section.assistant.text.and.tool.call/server"})
    public void shouldDecodeAnthropicSectionAssistantTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.assistant.text.citations/client",
        "${app}/anthropic.section.assistant.text.citations/server"})
    public void shouldDecodeAnthropicSectionAssistantTextCitations() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.assistant.tool.call/client",
        "${app}/anthropic.section.assistant.tool.call/server"})
    public void shouldDecodeAnthropicSectionAssistantToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.conversation/client",
        "${app}/anthropic.section.conversation/server"})
    public void shouldDecodeAnthropicSectionConversation() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.mixed.content/client",
        "${app}/anthropic.section.mixed.content/server"})
    public void shouldDecodeAnthropicSectionMixedContent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.rejected.json.malformed/client",
        "${app}/anthropic.section.rejected.json.malformed/server"})
    public void shouldDecodeAnthropicSectionRejectedJsonMalformed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.rejected.messages.not.array/client",
        "${app}/anthropic.section.rejected.messages.not.array/server"})
    public void shouldDecodeAnthropicSectionRejectedMessagesNotArray() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.rejected.part.type.last/client",
        "${app}/anthropic.section.rejected.part.type.last/server"})
    public void shouldDecodeAnthropicSectionRejectedPartTypeLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.rejected.role.last/client",
        "${app}/anthropic.section.rejected.role.last/server"})
    public void shouldDecodeAnthropicSectionRejectedRoleLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.rejected.role.missing/client",
        "${app}/anthropic.section.rejected.role.missing/server"})
    public void shouldDecodeAnthropicSectionRejectedRoleMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.rejected.tool.result.id.last/client",
        "${app}/anthropic.section.rejected.tool.result.id.last/server"})
    public void shouldDecodeAnthropicSectionRejectedToolResultIdLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.rejected.trailing.content/client",
        "${app}/anthropic.section.rejected.trailing.content/server"})
    public void shouldDecodeAnthropicSectionRejectedTrailingContent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.server.tool.result/client",
        "${app}/anthropic.section.server.tool.result/server"})
    public void shouldDecodeAnthropicSectionServerToolResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.system.instruction/client",
        "${app}/anthropic.section.system.instruction/server"})
    public void shouldDecodeAnthropicSectionSystemInstruction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.system.instruction.blocks/client",
        "${app}/anthropic.section.system.instruction.blocks/server"})
    public void shouldDecodeAnthropicSectionSystemInstructionBlocks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.tool.definition/client",
        "${app}/anthropic.section.tool.definition/server"})
    public void shouldDecodeAnthropicSectionToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.tool.result/client",
        "${app}/anthropic.section.tool.result/server"})
    public void shouldDecodeAnthropicSectionToolResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.tool.result.100k/client",
        "${app}/anthropic.section.tool.result.100k/server"})
    public void shouldDecodeAnthropicSectionToolResult100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.tool.result.blocks/client",
        "${app}/anthropic.section.tool.result.blocks/server"})
    public void shouldDecodeAnthropicSectionToolResultBlocks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.unknown.part/client",
        "${app}/anthropic.section.unknown.part/server"})
    public void shouldDecodeAnthropicSectionUnknownPart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.user.document/client",
        "${app}/anthropic.section.user.document/server"})
    public void shouldDecodeAnthropicSectionUserDocument() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.user.document.title.context/client",
        "${app}/anthropic.section.user.document.title.context/server"})
    public void shouldDecodeAnthropicSectionUserDocumentTitleContext() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.user.document.title.context.first/client",
        "${app}/anthropic.section.user.document.title.context.first/server"})
    public void shouldDecodeAnthropicSectionUserDocumentTitleContextFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.user.image/client",
        "${app}/anthropic.section.user.image/server"})
    public void shouldDecodeAnthropicSectionUserImage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.request.output.config/client",
        "${app}/anthropic.request.output.config/server"})
    public void shouldDecodeAnthropicRequestOutputConfig() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.request.output.config.10k/client",
        "${app}/anthropic.request.output.config.10k/server"})
    public void shouldDecodeAnthropicRequestOutputConfig10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.request.output.config.100k/client",
        "${app}/anthropic.request.output.config.100k/server"})
    public void shouldDecodeAnthropicRequestOutputConfig100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.request.options/client",
        "${app}/anthropic.request.options/server"})
    public void shouldDecodeAnthropicRequestOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.request.options.early/client",
        "${app}/anthropic.request.options.early/server"})
    public void shouldDecodeAnthropicRequestOptionsEarly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.request.options.verbatim/client",
        "${app}/anthropic.request.options.verbatim/server"})
    public void shouldDecodeAnthropicRequestOptionsVerbatim() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.user.text/client",
        "${app}/anthropic.section.user.text/server"})
    public void shouldDecodeAnthropicSectionUserText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.user.text.100k/client",
        "${app}/anthropic.section.user.text.100k/server"})
    public void shouldDecodeAnthropicSectionUserText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.user.text.10k/client",
        "${app}/anthropic.section.user.text.10k/server"})
    public void shouldDecodeAnthropicSectionUserText10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.user.text.blocks/client",
        "${app}/anthropic.section.user.text.blocks/server"})
    public void shouldDecodeAnthropicSectionUserTextBlocks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.user.text.escaped/client",
        "${app}/anthropic.section.user.text.escaped/server"})
    public void shouldDecodeAnthropicSectionUserTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.user.text.multibyte/client",
        "${app}/anthropic.section.user.text.multibyte/server"})
    public void shouldDecodeAnthropicSectionUserTextMultibyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.user.image.inline/client",
        "${app}/anthropic.section.user.image.inline/server"})
    public void shouldDecodeAnthropicSectionUserImageInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.user.image.reference/client",
        "${app}/anthropic.section.user.image.reference/server"})
    public void shouldDecodeAnthropicSectionUserImageReference() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.user.document.inline/client",
        "${app}/anthropic.section.user.document.inline/server"})
    public void shouldDecodeAnthropicSectionUserDocumentInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.tool.result.error/client",
        "${app}/anthropic.section.tool.result.error/server"})
    public void shouldDecodeAnthropicSectionToolResultError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.tool.result.error.first/client",
        "${app}/anthropic.section.tool.result.error.first/server"})
    public void shouldDecodeAnthropicSectionToolResultErrorFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.tool.result.error.last/client",
        "${app}/anthropic.section.tool.result.error.last/server"})
    public void shouldDecodeAnthropicSectionToolResultErrorLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.user.text.shorthand/client",
        "${app}/anthropic.section.user.text.shorthand/server"})
    public void shouldDecodeAnthropicSectionUserTextShorthand() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.system.instruction.shorthand/client",
        "${app}/anthropic.section.system.instruction.shorthand/server"})
    public void shouldDecodeAnthropicSectionSystemInstructionShorthand() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.tool.result.shorthand/client",
        "${app}/anthropic.section.tool.result.shorthand/server"})
    public void shouldDecodeAnthropicSectionToolResultShorthand() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic/client",
        "${app}/anthropic/server"})
    public void shouldForwardAnthropicNonstreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.tool.definition.name.last/client",
        "${app}/anthropic.section.tool.definition.name.last/server"})
    public void shouldDecodeAnthropicSectionToolDefinitionNameLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.user.document.reference/client",
        "${app}/anthropic.section.user.document.reference/server"})
    public void shouldDecodeAnthropicSectionUserDocumentReference() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.user.image.10k/client",
        "${app}/anthropic.section.user.image.10k/server"})
    public void shouldDecodeAnthropicSectionUserImage10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.user.image.url.10k/client",
        "${app}/anthropic.section.user.image.url.10k/server"})
    public void shouldDecodeAnthropicSectionUserImageUrl10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.user.image.url.100k/client",
        "${app}/anthropic.section.user.image.url.100k/server"})
    public void shouldDecodeAnthropicSectionUserImageUrl100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.transform.response.streaming.multibyte/client",
        "${app}/anthropic.transform.response.streaming.multibyte/server"})
    public void shouldDecodeAnthropicTransformResponseStreamingMultibyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.assistant.tool.call.100k/client",
        "${app}/anthropic.section.assistant.tool.call.100k/server"})
    public void shouldDecodeAnthropicSectionAssistantToolCall100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.assistant.tool.call.10k/client",
        "${app}/anthropic.section.assistant.tool.call.10k/server"})
    public void shouldDecodeAnthropicSectionAssistantToolCall10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.assistant.tool.call.escaped/client",
        "${app}/anthropic.section.assistant.tool.call.escaped/server"})
    public void shouldDecodeAnthropicSectionAssistantToolCallEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.assistant.tool.call.id.last/client",
        "${app}/anthropic.section.assistant.tool.call.id.last/server"})
    public void shouldDecodeAnthropicSectionAssistantToolCallIdLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.assistant.tool.call.name.last/client",
        "${app}/anthropic.section.assistant.tool.call.name.last/server"})
    public void shouldDecodeAnthropicSectionAssistantToolCallNameLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.tool.definition.100k/client",
        "${app}/anthropic.section.tool.definition.100k/server"})
    public void shouldDecodeAnthropicSectionToolDefinition100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.tool.definition.10k/client",
        "${app}/anthropic.section.tool.definition.10k/server"})
    public void shouldDecodeAnthropicSectionToolDefinition10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.tool.definition.parameters.100k/client",
        "${app}/anthropic.section.tool.definition.parameters.100k/server"})
    public void shouldDecodeAnthropicSectionToolDefinitionParameters100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.tool.definition.parameters.10k/client",
        "${app}/anthropic.section.tool.definition.parameters.10k/server"})
    public void shouldDecodeAnthropicSectionToolDefinitionParameters10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.model.last/client",
        "${app}/anthropic.section.model.last/server"})
    public void shouldDecodeAnthropicSectionModelLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.model.last.system.tools/client",
        "${app}/anthropic.section.model.last.system.tools/server"})
    public void shouldDecodeAnthropicSectionModelLastSystemTools() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.model.last.text.10k/client",
        "${app}/anthropic.section.model.last.text.10k/server"})
    public void shouldDecodeAnthropicSectionModelLastText10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.rejected.model.last.oversize/client"})
    public void shouldDecodeAnthropicSectionRejectedModelLastOversize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.request.version.missing/client"})
    public void shouldRejectAnthropicRequestWithVersionMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.request.version.unsupported/client"})
    public void shouldRejectAnthropicRequestWithVersionUnsupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.assistant.text.members.streaming/client",
        "${app}/anthropic.section.assistant.text.members.streaming/server"})
    public void shouldDecodeAnthropicSectionAssistantTextMembersStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.request.beta/client",
        "${app}/anthropic.request.beta/server"})
    public void shouldCarryAnthropicBetaOnBeginExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.streaming.complete/client",
        "${app}/anthropic.streaming.complete/server"})
    public void shouldEndAnthropicStreamingEventsOnComplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.streaming.fragmented/client",
        "${app}/anthropic.streaming.fragmented/server"})
    public void shouldNameAnthropicStreamingEventSplitInsideItsType() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.user.text/client",
        "${app}/transform.user.text/server"})
    public void shouldTransformUserTextFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.system.instruction/client",
        "${app}/transform.system.instruction/server"})
    public void shouldTransformSystemInstructionFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.assistant.text/client",
        "${app}/transform.assistant.text/server"})
    public void shouldTransformAssistantTextFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.tool.result/client",
        "${app}/transform.tool.result/server"})
    public void shouldTransformToolResultFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.request.frames/client",
        "${app}/transform.request.frames/server"})
    public void shouldTransformRequestFramesFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.user.text/client",
        "${app}/anthropic.section.model.override/server"})
    public void shouldReplaceAnthropicModelWhenChallengedWithModel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.model.last/client",
        "${app}/anthropic.section.model.last.override/server"})
    public void shouldReplaceAnthropicModelLastWhenChallengedWithModel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.response.frames/client",
        "${app}/transform.response.frames/server"})
    public void shouldTransformResponseFramesFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.assistant.text.100k/client",
        "${app}/anthropic.section.assistant.text.100k/server"})
    public void shouldDecodeAnthropicSectionAssistantText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.assistant.text.escaped/client",
        "${app}/anthropic.section.assistant.text.escaped/server"})
    public void shouldDecodeAnthropicSectionAssistantTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.assistant.text.members/client",
        "${app}/anthropic.section.assistant.text.members/server"})
    public void shouldDecodeAnthropicSectionAssistantTextMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.assistant.text.streaming/client",
        "${app}/anthropic.section.assistant.text.streaming/server"})
    public void shouldDecodeAnthropicSectionAssistantTextStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.assistant.tool.call.streaming/client",
        "${app}/anthropic.section.assistant.tool.call.streaming/server"})
    public void shouldDecodeAnthropicSectionAssistantToolCallStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.section.tool.definition.streaming/client",
        "${app}/anthropic.section.tool.definition.streaming/server"})
    public void shouldDecodeAnthropicSectionToolDefinitionStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.rejected.streaming/client",
        "${app}/anthropic.rejected.streaming/server"})
    public void shouldDecodeAnthropicRejectedStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.response.text/client",
        "${app}/transform.response.text/server"})
    public void shouldTransformResponseTextFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.response.text.escaped/client",
        "${app}/transform.response.text.escaped/server"})
    public void shouldTransformResponseTextEscapedFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.response.text.100k/client",
        "${app}/transform.response.text.100k/server"})
    public void shouldTransformResponseText100kFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.response.text.multibyte/client",
        "${app}/transform.response.text.multibyte/server"})
    public void shouldTransformResponseTextMultibyteFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.response.text.and.tool.call/client",
        "${app}/transform.response.text.and.tool.call/server"})
    public void shouldTransformResponseTextAndToolCallFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.response.usage/client",
        "${app}/transform.response.usage/server"})
    public void shouldTransformResponseUsageFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.response.reasoning/client",
        "${app}/transform.response.reasoning/server"})
    public void shouldTransformResponseReasoningFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.response.reasoning.tag/client",
        "${app}/transform.response.reasoning.tag/server"})
    public void shouldTransformResponseReasoningTagFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.response.streaming.reasoning.tag/client",
        "${app}/transform.response.reasoning.tag/server"})
    public void shouldTransformResponseStreamingReasoningTagFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.response.reasoning.tag.fragmented/client",
        "${app}/transform.response.reasoning.tag.fragmented/server"})
    public void shouldTransformResponseReasoningTagFragmentedFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.response.streaming.reasoning.tag.fragmented/client",
        "${app}/transform.response.reasoning.tag.fragmented/server"})
    public void shouldTransformResponseStreamingReasoningTagFragmentedFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.response.reasoning.tag.multiple/client",
        "${app}/transform.response.reasoning.tag.multiple/server"})
    public void shouldTransformResponseReasoningTagMultipleFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.response.reasoning.tag.unclosed/client",
        "${app}/transform.response.reasoning.tag.unclosed/server"})
    public void shouldTransformResponseReasoningTagUnclosedFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.response.reasoning.tag.absent/client",
        "${app}/transform.response.reasoning.tag.absent/server"})
    public void shouldTransformResponseReasoningTagAbsentFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.response.refusal/client",
        "${app}/transform.response.refusal/server"})
    public void shouldTransformResponseRefusalFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.response.streaming/client",
        "${app}/transform.response.streaming/server"})
    public void shouldTransformResponseStreamingFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.response.streaming.tool.call/client",
        "${app}/transform.response.streaming.tool.call/server"})
    public void shouldTransformResponseStreamingToolCallFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.response.streaming.error/client",
        "${app}/transform.response.streaming.error/server"})
    public void shouldTransformResponseStreamingErrorFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"anthropic\""})
    @Specification({
        "${net}/anthropic.transform.request.max.output.tokens/client",
        "${app}/transform.request.max.output.tokens/server"})
    public void shouldTransformRequestMaxOutputTokensFromAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.guarded.route.yaml")
    @Specification({
        "${net}/anthropic.authorized/client",
        "${app}/anthropic.authorized/server"})
    public void shouldForwardAnthropicRequestRoutedByGuardAuthorization() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.rejected.error.type/client",
        "${app}/anthropic.rejected.error.type/server"})
    public void shouldPreserveAnthropicRejectedErrorType() throws Exception
    {
        k3po.finish();
    }
}
