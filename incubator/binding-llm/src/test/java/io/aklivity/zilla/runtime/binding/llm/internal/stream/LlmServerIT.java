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

public class LlmServerIT
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
        "${net}/anthropic.invalid/client"})
    public void shouldRejectInvalidAnthropicRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/reject.unknown.dialect/client"})
    public void shouldRejectRequestWithUnresolvedDialect() throws Exception
    {
        k3po.finish();
    }

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
        "${net}/anthropic.echo/client",
        "${app}/anthropic.echo/server"})
    public void shouldDetectAnthropicDialectFromPath() throws Exception
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
        "${net}/openai.streaming/client",
        "${app}/openai.streaming/server"})
    public void shouldForwardOpenaiStreaming() throws Exception
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
        "${net}/openai.reply.padded/client",
        "${app}/openai.reply.padded/server"})
    public void shouldForwardOpenaiNonstreamingWithReplyPadding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.streaming/client",
        "${app}/anthropic.streaming/server"})
    public void shouldForwardAnthropicStreaming() throws Exception
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
        "${net}/openai.10k/client",
        "${app}/openai.10k/server"})
    public void shouldForwardOpenai10k() throws Exception
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
        "${net}/openai.100k/client",
        "${app}/openai.100k/server"})
    public void shouldForwardOpenai100k() throws Exception
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
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.streaming.10k/client",
        "${app}/openai.streaming.10k/server"})
    public void shouldForwardOpenaiStreaming10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.streaming.10k/client",
        "${app}/anthropic.streaming.10k/server"})
    public void shouldForwardAnthropicStreaming10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.streaming.100k/client",
        "${app}/openai.streaming.100k/server"})
    public void shouldForwardOpenaiStreaming100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.streaming.100k/client",
        "${app}/anthropic.streaming.100k/server"})
    public void shouldForwardAnthropicStreaming100k() throws Exception
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
        "${net}/anthropic.authorized/client",
        "${app}/anthropic.authorized/server"})
    public void shouldForwardAnthropicRequestAuthorized() throws Exception
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
        "${net}/openai.rejected.rate.limit/client",
        "${app}/openai.rejected.rate.limit/server"})
    public void shouldRejectOpenaiRequestRateLimited() throws Exception
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
        "${net}/openai.streaming.error.aborted/client",
        "${app}/openai.streaming.error/server"})
    public void shouldAbortOpenaiStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.streaming.error.aborted/client",
        "${app}/anthropic.streaming.error/server"})
    public void shouldAbortAnthropicStreamingError() throws Exception
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
        "${net}/openai.section.system.instruction/client",
        "${app}/openai.section.system.instruction/server"})
    public void shouldDecodeOpenaiSectionSystemInstruction() throws Exception
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
        "${net}/openai.section.unknown.part/client",
        "${app}/openai.section.unknown.part/server"})
    public void shouldDecodeOpenaiSectionUnknownPart() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.section.user.image.50k/client",
        "${app}/openai.section.user.image.50k/server"})
    public void shouldDecodeOpenaiSectionUserImage50k() throws Exception
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
        "${net}/openai.section.other.members/client",
        "${app}/openai.section.other.members/server"})
    public void shouldDecodeOpenaiSectionOtherMembers() throws Exception
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
        "${net}/openai.rejected.model.last/client"})
    public void shouldDecodeOpenaiRejectedModelLast() throws Exception
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
}
