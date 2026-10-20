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

public class LlmOpenaiResponsesClientIT
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
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.text/client",
        "${net}/openai.responses.text/server"})
    public void shouldForwardOpenaiResponsesNonstreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.text.stream/client",
        "${net}/openai.responses.text.stream/server"})
    public void shouldForwardOpenaiResponsesStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.text.stream.no.obfuscation/client",
        "${net}/openai.responses.text.stream.no.obfuscation/server"})
    public void shouldForwardOpenaiResponsesStreamWithoutObfuscation() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.incomplete.max.output.tokens/client",
        "${net}/openai.responses.incomplete.max.output.tokens/server"})
    public void shouldForwardOpenaiResponsesIncompleteMaxOutputTokens() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.incomplete.max.output.tokens.stream/client",
        "${net}/openai.responses.incomplete.max.output.tokens.stream/server"})
    public void shouldForwardOpenaiResponsesIncompleteMaxOutputTokensStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.incomplete.content.filter/client",
        "${net}/openai.responses.incomplete.content.filter/server"})
    public void shouldForwardOpenaiResponsesIncompleteContentFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.incomplete.max.messages/client",
        "${net}/openai.responses.incomplete.max.messages/server"})
    public void shouldRestoreOpenaiResponsesIncompleteMaxMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.usage.cache/client",
        "${net}/openai.responses.usage.cache/server"})
    public void shouldForwardOpenaiResponsesUsageCache() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.usage.cache.stream/client",
        "${net}/openai.responses.usage.cache.stream/server"})
    public void shouldForwardOpenaiResponsesUsageCacheStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.section.system.instruction/client",
        "${net}/openai.responses.section.system.instruction/server"})
    public void shouldEncodeOpenaiResponsesSectionSystemInstruction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.section.user.text.parts/client",
        "${net}/openai.responses.section.user.text.parts/server"})
    public void shouldEncodeOpenaiResponsesSectionUserTextParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.section.assistant.text/client",
        "${net}/openai.responses.section.assistant.text/server"})
    public void shouldEncodeOpenaiResponsesSectionAssistantText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.section.conversation/client",
        "${net}/openai.responses.section.conversation/server"})
    public void shouldEncodeOpenaiResponsesSectionConversation() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.section.instructions.last/client",
        "${net}/openai.responses.section.instructions.last/server"})
    public void shouldEncodeOpenaiResponsesSectionInstructionsLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.request.controls/client",
        "${net}/openai.responses.request.controls/server"})
    public void shouldEncodeOpenaiResponsesRequestControls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.response.controls/client",
        "${net}/openai.responses.response.controls/server"})
    public void shouldRestoreOpenaiResponsesResponseControls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.response.controls.stream/client",
        "${net}/openai.responses.response.controls.stream/server"})
    public void shouldRestoreOpenaiResponsesResponseControlsStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.rejected.error.code/client",
        "${net}/openai.responses.rejected.error.code/server"})
    public void shouldPassOpenaiResponsesRejectedErrorCode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.rejected.error.code.streaming/client",
        "${net}/openai.responses.rejected.error.code.streaming/server"})
    public void shouldPassOpenaiResponsesRejectedErrorCodeStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.rejected.error.param/client",
        "${net}/openai.responses.rejected.error.param/server"})
    public void shouldPassOpenaiResponsesRejectedErrorParam() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.rejected.rate.limit/client",
        "${net}/openai.responses.rejected.rate.limit/server"})
    public void shouldPassOpenaiResponsesRejectedRateLimit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.rejected.section/client",
        "${net}/openai.responses.rejected.section/server"})
    public void shouldPassOpenaiResponsesRejectedSection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.rejected.streaming/client",
        "${net}/openai.responses.rejected.streaming/server"})
    public void shouldAbortOpenaiResponsesStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.rejected.streaming.failed/client",
        "${net}/openai.responses.rejected.streaming.failed/server"})
    public void shouldAbortOpenaiResponsesStreamingFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.rejected.bad.gateway/client",
        "${net}/openai.responses.rejected.bad.gateway/server"})
    public void shouldResetOpenaiResponsesRequestWhenBadGateway() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.rejected.reset/client",
        "${net}/openai.responses.rejected.reset/server"})
    public void shouldResetOpenaiResponsesRequestWhenServerReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.rejected.unreachable/client",
        "${net}/openai.responses.rejected.unreachable/server"})
    public void shouldResetOpenaiResponsesRequestWhenServerUnreachable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.text.captured/client",
        "${net}/openai.responses.text.captured/server"})
    public void shouldForwardOpenaiResponsesCaptured() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.text.stream.captured/client",
        "${net}/openai.responses.text.stream.captured/server"})
    public void shouldForwardOpenaiResponsesStreamCaptured() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.text.stream/client",
        "${net}/openai.responses.text.stream.unknown.event/server"})
    public void shouldSkipOpenaiResponsesUnknownStreamEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.rejected.output.item.type/client",
        "${net}/openai.responses.rejected.output.item.type/server"})
    public void shouldRejectOpenaiResponsesUnsupportedOutputItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.rejected.output.item.type.stream/client",
        "${net}/openai.responses.rejected.output.item.type.stream/server"})
    public void shouldRejectOpenaiResponsesUnsupportedOutputItemStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/transform.user.text/client",
        "${net}/openai.responses.transform.user.text/server"})
    public void shouldTransformUserTextAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/transform.system.instruction/client",
        "${net}/openai.responses.transform.system.instruction/server"})
    public void shouldTransformSystemInstructionAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/transform.system.instruction.last/client",
        "${net}/openai.responses.transform.system.instruction.last/server"})
    public void shouldTransformSystemInstructionLastAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/transform.assistant.text/client",
        "${net}/openai.responses.transform.assistant.text/server"})
    public void shouldTransformAssistantTextAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/transform.request.max.output.tokens/client",
        "${net}/openai.responses.transform.request.max.output.tokens/server"})
    public void shouldTransformRequestMaxOutputTokensAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/openai.responses.transform.response.text/client",
        "${net}/openai.responses.transform.response.text/server"})
    public void shouldTransformResponseTextAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/openai.responses.transform.response.text/client",
        "${net}/openai.responses.transform.response.text.sse/server"})
    public void shouldTransformResponseTextSseAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/openai.responses.transform.response.usage/client",
        "${net}/openai.responses.transform.response.usage/server"})
    public void shouldTransformResponseUsageAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\"", "reason \"openai:insufficient_system_resource\""})
    @Specification({
        "${app}/openai.responses.transform.response.finish.reason/client",
        "${net}/openai.responses.transform.response.finish.reason.unknown/server"})
    public void shouldPrefixUnknownFinishReasonAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.function.call/client",
        "${net}/openai.responses.function.call/server"})
    public void shouldForwardOpenaiResponsesFunctionCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.function.call.stream/client",
        "${net}/openai.responses.function.call.stream/server"})
    public void shouldForwardOpenaiResponsesFunctionCallStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.function.call.text/client",
        "${net}/openai.responses.function.call.text/server"})
    public void shouldForwardOpenaiResponsesFunctionCallText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.function.call.text.stream/client",
        "${net}/openai.responses.function.call.text.stream/server"})
    public void shouldForwardOpenaiResponsesFunctionCallTextStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.function.calls/client",
        "${net}/openai.responses.function.calls/server"})
    public void shouldForwardOpenaiResponsesFunctionCalls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.function.calls.stream/client",
        "${net}/openai.responses.function.calls.stream/server"})
    public void shouldForwardOpenaiResponsesFunctionCallsStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.custom.tool.call/client",
        "${net}/openai.responses.custom.tool.call/server"})
    public void shouldForwardOpenaiResponsesCustomToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.custom.tool.call.stream/client",
        "${net}/openai.responses.custom.tool.call.stream/server"})
    public void shouldForwardOpenaiResponsesCustomToolCallStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.web.search/client",
        "${net}/openai.responses.web.search/server"})
    public void shouldForwardOpenaiResponsesWebSearch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.web.search.stream/client",
        "${net}/openai.responses.web.search.stream/server"})
    public void shouldForwardOpenaiResponsesWebSearchStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.code.interpreter/client",
        "${net}/openai.responses.code.interpreter/server"})
    public void shouldForwardOpenaiResponsesCodeInterpreter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.code.interpreter.stream/client",
        "${net}/openai.responses.code.interpreter.stream/server"})
    public void shouldForwardOpenaiResponsesCodeInterpreterStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.mcp/client",
        "${net}/openai.responses.mcp/server"})
    public void shouldForwardOpenaiResponsesMcp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.mcp.stream/client",
        "${net}/openai.responses.mcp.stream/server"})
    public void shouldForwardOpenaiResponsesMcpStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.image.generation/client",
        "${net}/openai.responses.image.generation/server"})
    public void shouldForwardOpenaiResponsesImageGeneration() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.image.generation.stream/client",
        "${net}/openai.responses.image.generation.stream/server"})
    public void shouldForwardOpenaiResponsesImageGenerationStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.function.calls.stream/client",
        "${net}/openai.responses.function.calls.interleaved.stream/server"})
    public void shouldForwardOpenaiResponsesFunctionCallsInterleavedStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.function.calls.interleaved.overflow.stream/client",
        "${net}/openai.responses.function.calls.interleaved.overflow.stream/server"})
    public void shouldFailOpenaiResponsesFunctionCallsInterleavedOverflowStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.image.generation.stream/client",
        "${net}/openai.responses.image.generation.partial.stream/server"})
    public void shouldForwardOpenaiResponsesImageGenerationPartialStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.section.tool.definition/client",
        "${net}/openai.responses.section.tool.definition/server"})
    public void shouldEncodeOpenaiResponsesSectionToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.section.tool.definition.members/client",
        "${net}/openai.responses.section.tool.definition.members/server"})
    public void shouldEncodeOpenaiResponsesSectionToolDefinitionMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.section.tool.definition.100k/client",
        "${net}/openai.responses.section.tool.definition.100k/server"})
    public void shouldEncodeOpenaiResponsesSectionToolDefinition100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.section.server.tool.definition/client",
        "${net}/openai.responses.section.server.tool.definition/server"})
    public void shouldEncodeOpenaiResponsesSectionServerToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.section.tool.call/client",
        "${net}/openai.responses.section.tool.call/server"})
    public void shouldEncodeOpenaiResponsesSectionToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.section.tool.calls/client",
        "${net}/openai.responses.section.tool.calls/server"})
    public void shouldEncodeOpenaiResponsesSectionToolCalls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.section.assistant.text.and.tool.call/client",
        "${net}/openai.responses.section.assistant.text.and.tool.call/server"})
    public void shouldEncodeOpenaiResponsesSectionAssistantTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.section.tool.call.members/client",
        "${net}/openai.responses.section.tool.call.members/server"})
    public void shouldEncodeOpenaiResponsesSectionToolCallMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.section.tool.call.escaped/client",
        "${net}/openai.responses.section.tool.call.escaped/server"})
    public void shouldEncodeOpenaiResponsesSectionToolCallEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.section.tool.result/client",
        "${net}/openai.responses.section.tool.result/server"})
    public void shouldEncodeOpenaiResponsesSectionToolResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.section.tool.result.parts/client",
        "${net}/openai.responses.section.tool.result.parts/server"})
    public void shouldEncodeOpenaiResponsesSectionToolResultParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.section.server.tool.call/client",
        "${net}/openai.responses.section.server.tool.call/server"})
    public void shouldEncodeOpenaiResponsesSectionServerToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/transform.tool.definition/client",
        "${net}/openai.responses.transform.tool.definition/server"})
    public void shouldTransformToolDefinitionAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/transform.tool.result/client",
        "${net}/openai.responses.transform.tool.result/server"})
    public void shouldTransformToolResultAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/transform.tool.result.blocks/client",
        "${net}/openai.responses.transform.tool.result.blocks/server"})
    public void shouldTransformToolResultBlocksAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/transform.assistant.tool.call/client",
        "${net}/openai.responses.transform.assistant.tool.call/server"})
    public void shouldTransformAssistantToolCallAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/transform.assistant.tool.call.escaped/client",
        "${net}/openai.responses.transform.assistant.tool.call.escaped/server"})
    public void shouldTransformAssistantToolCallEscapedAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${app}/transform.server.tool.definition/client",
        "${net}/openai.responses.transform.server.tool.definition/server"})
    public void shouldTransformServerToolDefinitionFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/transform.server.tool.definition/client",
        "${net}/openai.responses.transform.server.tool.definition.dropped/server"})
    public void shouldDropServerToolDefinitionAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/anthropic.transform.server.tool.definition/client",
        "${net}/openai.responses.transform.server.tool.definition.dropped/server"})
    public void shouldDropAnthropicServerToolDefinitionAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/anthropic.transform.server.tool.result/client",
        "${net}/openai.responses.transform.server.tool.definition.dropped/server"})
    public void shouldDropAnthropicServerToolResultAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/anthropic.transform.tool.definition.cache.control/client",
        "${net}/openai.responses.transform.tool.definition/server"})
    public void shouldDropAnthropicToolDefinitionCacheControlAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/openai.transform.assistant.tool.call.members/client",
        "${net}/openai.responses.transform.assistant.tool.call.members/server"})
    public void shouldDropOpenaiToolCallMembersAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/openai.responses.transform.response.tool.call/client",
        "${net}/openai.responses.transform.response.tool.call/server"})
    public void shouldTransformResponseToolCallAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/openai.responses.transform.response.tool.call/client",
        "${net}/openai.responses.transform.response.tool.call.sse/server"})
    public void shouldTransformResponseToolCallSseAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/openai.responses.transform.response.tool.call.empty/client",
        "${net}/openai.responses.transform.response.tool.call.empty/server"})
    public void shouldTransformResponseToolCallEmptyAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/openai.responses.transform.response.tool.calls/client",
        "${net}/openai.responses.transform.response.tool.calls/server"})
    public void shouldTransformResponseToolCallsAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/openai.responses.transform.response.tool.calls/client",
        "${net}/openai.responses.transform.response.tool.calls.sse/server"})
    public void shouldTransformResponseToolCallsSseAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/openai.responses.transform.response.text.and.tool.call/client",
        "${net}/openai.responses.transform.response.text.and.tool.call/server"})
    public void shouldTransformResponseTextAndToolCallAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @ScriptProperty({"target \"openai/responses\""})
    @Specification({
        "${app}/openai.responses.transform.response.text.and.tool.call/client",
        "${net}/openai.responses.transform.response.text.and.tool.call.sse/server"})
    public void shouldTransformResponseTextAndToolCallSseAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.function.call.large/client",
        "${net}/openai.responses.function.call.large/server"})
    public void shouldForwardOpenaiResponsesFunctionCallLarge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.function.call.large.stream/client",
        "${net}/openai.responses.function.call.large.stream/server"})
    public void shouldForwardOpenaiResponsesFunctionCallLargeStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.function.call.huge.late/client",
        "${net}/openai.responses.function.call.huge/server"})
    public void shouldForwardOpenaiResponsesFunctionCallHuge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.function.call.huge.stream/client",
        "${net}/openai.responses.function.call.huge.stream/server"})
    public void shouldForwardOpenaiResponsesFunctionCallHugeStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.text.stream.large/client",
        "${net}/openai.responses.text.stream.large/server"})
    public void shouldForwardOpenaiResponsesTextStreamLarge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.text.stream.huge/client",
        "${net}/openai.responses.text.stream.huge/server"})
    public void shouldForwardOpenaiResponsesTextStreamHuge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.rejected.output.item.type/client",
        "${net}/openai.responses.rejected.output.item.type/server"})
    public void shouldRejectOpenaiResponsesOutputItemTypeFuture() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.responses.yaml")
    @Specification({
        "${app}/openai.responses.section.tool.call.attributes.last/client",
        "${net}/openai.responses.section.tool.call.attributes.last/server"})
    public void shouldEncodeOpenaiResponsesSectionToolCallAttributesLast() throws Exception
    {
        k3po.finish();
    }
}
