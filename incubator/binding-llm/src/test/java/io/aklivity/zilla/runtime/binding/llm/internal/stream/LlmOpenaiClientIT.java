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
        "${app}/openai.streaming/client",
        "${net}/openai.streaming/server"})
    public void shouldForwardOpenaiStreamingSameDialect() throws Exception
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
        "${app}/anthropic.streaming.transformed/client",
        "${net}/openai.streaming.transformed/server"})
    @ScriptProperty({ "model \"claude-3-opus-20240229\"", "id \"msg_01\"" })
    public void shouldTransformAnthropicToOpenaiStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/anthropic.transformed/client",
        "${net}/openai.transformed/server"})
    @ScriptProperty({ "model \"claude-3-opus-20240229\"", "id \"msg_01\"" })
    public void shouldTransformAnthropicToOpenaiNonstreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/anthropic.transformed.100k/client",
        "${net}/openai.transformed.100k/server"})
    @ScriptProperty({ "model \"claude-3-opus-20240229\"", "id \"msg_01\"" })
    public void shouldTransformAnthropicToOpenai100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/anthropic.transformed.tool.only/client",
        "${net}/openai.transformed.tool.only/server"})
    @ScriptProperty({ "model \"claude-3-opus-20240229\"", "id \"msg_01\"" })
    public void shouldTransformAnthropicToOpenaiToolOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.guarded.yaml")
    @Specification({
        "${app}/openai.authorized/client",
        "${net}/openai.authorized/server"})
    public void shouldForwardOpenaiRequestWithAuthorizedCredentials() throws Exception
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
        "${app}/openai.section.user.text/client",
        "${net}/openai.section.user.text/server"})
    public void shouldEncodeOpenaiSectionUserText() throws Exception
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
        "${app}/openai.section.user.text.100k/client",
        "${net}/openai.section.user.text.100k.parts/server"})
    public void shouldEncodeOpenaiSectionUserText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.section.tool.result.100k/client",
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
        "${app}/openai.section.user.image.50k/client",
        "${net}/openai.section.user.image.50k/server"})
    public void shouldEncodeOpenaiSectionUserImage50k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/anthropic.section.system.instruction/client",
        "${net}/openai.transform.anthropic.system.instruction/server"})
    public void shouldTransformAnthropicSectionSystemInstructionAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/anthropic.section.user.text/client",
        "${net}/openai.transform.anthropic.user.text/server"})
    public void shouldTransformAnthropicSectionUserTextAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/anthropic.section.user.image/client",
        "${net}/openai.transform.anthropic.user.image/server"})
    public void shouldTransformAnthropicSectionUserImageAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.text/client",
        "${net}/openai.transform.anthropic.assistant.text/server"})
    public void shouldTransformAnthropicSectionAssistantTextAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.tool.call/client",
        "${net}/openai.transform.anthropic.assistant.tool.call/server"})
    public void shouldTransformAnthropicSectionAssistantToolCallAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/anthropic.section.tool.result/client",
        "${net}/openai.transform.anthropic.tool.result/server"})
    public void shouldTransformAnthropicSectionToolResultAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/anthropic.section.tool.result.blocks/client",
        "${net}/openai.transform.anthropic.tool.result.blocks/server"})
    public void shouldTransformAnthropicSectionToolResultBlocksAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/anthropic.section.tool.definition/client",
        "${net}/openai.transform.anthropic.tool.definition/server"})
    public void shouldTransformAnthropicSectionToolDefinitionAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/anthropic.section.mixed.content/client",
        "${net}/openai.transform.anthropic.mixed.content/server"})
    public void shouldTransformAnthropicSectionMixedContentAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.reasoning/client",
        "${net}/openai.transform.anthropic.assistant.reasoning/server"})
    public void shouldTransformAnthropicSectionAssistantReasoningAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/anthropic.section.server.tool.result/client",
        "${net}/openai.transform.anthropic.server.tool.result/server"})
    public void shouldTransformAnthropicSectionServerToolResultAsOpenai() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/anthropic.section.assistant.text.citations/client",
        "${net}/openai.transform.anthropic.assistant.text.citations/server"})
    public void shouldTransformAnthropicSectionAssistantTextCitationsAsOpenai() throws Exception
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
        "${app}/anthropic.section.tool.result.error/client",
        "${net}/openai.transform.anthropic.tool.result.error/server"})
    public void shouldTransformAnthropicSectionToolResultErrorAsOpenai() throws Exception
    {
        k3po.finish();
    }
}
