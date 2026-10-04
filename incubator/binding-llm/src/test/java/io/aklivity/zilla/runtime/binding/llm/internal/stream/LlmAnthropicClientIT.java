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
        "${app}/anthropic.streaming/client",
        "${net}/anthropic.streaming/server"})
    public void shouldForwardAnthropicStreamingSameDialect() throws Exception
    {
        k3po.finish();
    }

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
        "${app}/anthropic/client",
        "${net}/anthropic/server"})
    public void shouldForwardAnthropicNonstreamingSameDialect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.streaming.transformed/client",
        "${net}/anthropic.streaming.transformed/server"})
    @ScriptProperty({ "model \"gpt-4\"", "id \"chatcmpl_1\"" })
    public void shouldTransformOpenaiToAnthropicStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.streaming.transformed.100k/client",
        "${net}/anthropic.streaming.transformed.100k/server"})
    @ScriptProperty({ "model \"claude-3-opus-20240229\"", "id \"msg_01\"" })
    public void shouldTransformOpenaiToAnthropicStreaming100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.transformed/client",
        "${net}/anthropic.transformed/server"})
    @ScriptProperty({ "model \"gpt-4\"", "id \"chatcmpl_2\"" })
    public void shouldTransformOpenaiToAnthropicNonstreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.transformed.100k/client",
        "${net}/anthropic.transformed.100k/server"})
    @ScriptProperty({ "model \"gpt-4\"", "id \"chatcmpl_2\"" })
    public void shouldTransformOpenaiToAnthropic100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.transformed.tool.only/client",
        "${net}/anthropic.transformed.tool.only/server"})
    @ScriptProperty({ "model \"gpt-4\"", "id \"chatcmpl_2\"" })
    public void shouldTransformOpenaiToAnthropicToolOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.transformed.multi.tool/client",
        "${net}/anthropic.transformed.multi.tool/server"})
    @ScriptProperty({ "model \"gpt-4\"", "id \"chatcmpl_2\"" })
    public void shouldTransformOpenaiToAnthropicMultiTool() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.guarded.yaml")
    @Specification({
        "${app}/anthropic.authorized/client",
        "${net}/anthropic.authorized/server"})
    public void shouldForwardAnthropicRequestWithAuthorizedCredentials() throws Exception
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
        "${app}/openai.transformed.rejected.rate.limit/client",
        "${net}/anthropic.transformed.rejected.rate.limit/server"})
    @ScriptProperty({ "model \"gpt-4\"" })
    public void shouldResetOpenaiToAnthropicRequestRateLimited() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.streaming.error/client",
        "${net}/anthropic.streaming.error/server"})
    public void shouldAbortAnthropicStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.streaming.transformed.error/client",
        "${net}/anthropic.streaming.transformed.error/server"})
    @ScriptProperty({ "model \"gpt-4\"", "id \"chatcmpl_1\"" })
    public void shouldAbortOpenaiToAnthropicStreamingError() throws Exception
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
        "${app}/anthropic.section.user.image/client",
        "${net}/anthropic.section.user.image/server"})
    public void shouldEncodeAnthropicSectionUserImage() throws Exception
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
    @Specification({
        "${app}/openai.section.system.instruction/client",
        "${net}/anthropic.transform.openai.system.instruction/server"})
    public void shouldTransformOpenaiSectionSystemInstructionAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.section.user.text/client",
        "${net}/anthropic.transform.openai.user.text/server"})
    public void shouldTransformOpenaiSectionUserTextAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.section.user.image/client",
        "${net}/anthropic.transform.openai.user.image/server"})
    public void shouldTransformOpenaiSectionUserImageAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.section.user.document/client",
        "${net}/anthropic.transform.openai.user.document/server"})
    public void shouldTransformOpenaiSectionUserDocumentAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.section.assistant.text/client",
        "${net}/anthropic.transform.openai.assistant.text/server"})
    public void shouldTransformOpenaiSectionAssistantTextAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.section.assistant.refusal/client",
        "${net}/anthropic.transform.openai.assistant.refusal/server"})
    public void shouldTransformOpenaiSectionAssistantRefusalAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.section.assistant.text.and.tool.call/client",
        "${net}/anthropic.transform.openai.assistant.text.and.tool.call/server"})
    public void shouldTransformOpenaiSectionAssistantTextAndToolCallAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.section.tool.definition/client",
        "${net}/anthropic.transform.openai.tool.definition/server"})
    public void shouldTransformOpenaiSectionToolDefinitionAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.section.tool.result/client",
        "${net}/anthropic.transform.openai.tool.result/server"})
    public void shouldTransformOpenaiSectionToolResultAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.section.mixed.content/client",
        "${net}/anthropic.transform.openai.mixed.content/server"})
    public void shouldTransformOpenaiSectionMixedContentAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.section.conversation/client",
        "${net}/anthropic.transform.openai.conversation/server"})
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
    @Specification({
        "${app}/openai.section.user.image.inline/client",
        "${net}/anthropic.transform.openai.user.image.inline/server"})
    public void shouldTransformOpenaiSectionUserImageInlineAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.section.user.document.inline/client",
        "${net}/anthropic.transform.openai.user.document.inline/server"})
    public void shouldTransformOpenaiSectionUserDocumentInlineAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.section.user.image.10k/client",
        "${net}/anthropic.transform.openai.user.image.10k/server"})
    public void shouldTransformOpenaiSectionUserImage10kAsAnthropic() throws Exception
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
    @Specification({
        "${app}/openai.section.user.image.url.10k/client",
        "${net}/anthropic.transform.openai.user.image.url.10k/server"})
    public void shouldTransformOpenaiSectionUserImageUrl10kAsAnthropic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.section.user.image.url.100k/client",
        "${net}/anthropic.transform.openai.user.image.url.100k/server"})
    public void shouldTransformOpenaiSectionUserImageUrl100kAsAnthropic() throws Exception
    {
        k3po.finish();
    }
}
