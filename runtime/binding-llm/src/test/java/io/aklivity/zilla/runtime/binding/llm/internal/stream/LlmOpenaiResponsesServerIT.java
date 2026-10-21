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

import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_BUFFER_SLOT_CAPACITY_NAME;
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

public class LlmOpenaiResponsesServerIT
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
        "${net}/openai.responses.text/client",
        "${app}/openai.responses.text/server"})
    public void shouldDetectOpenaiResponsesDialectFromPath() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.openai.yaml")
    @Specification({
        "${net}/openai.responses.text/client",
        "${app}/openai.responses.text/server"})
    public void shouldDetectOpenaiResponsesWithFixedOpenaiDialect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.openai.yaml")
    @Specification({
        "${net}/openai.section.assistant.text/client",
        "${app}/openai.section.assistant.text/server"})
    public void shouldDetectOpenaiChatCompletionsWithFixedOpenaiDialect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.text/client",
        "${app}/openai.responses.text/server"})
    public void shouldDetectOpenaiResponsesWithFixedOpenaiResponsesDialect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.openai.yaml")
    @Specification({
        "${net}/openai.completions.unsupported/client"})
    public void shouldRejectOpenaiCompletionsWithFixedOpenaiDialect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.text.stream/client",
        "${app}/openai.responses.text.stream/server"})
    public void shouldEncodeOpenaiResponsesStreamWithObfuscation() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.text.stream.no.obfuscation/client",
        "${app}/openai.responses.text.stream.no.obfuscation/server"})
    public void shouldEncodeOpenaiResponsesStreamWithoutObfuscation() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.incomplete.max.output.tokens/client",
        "${app}/openai.responses.incomplete.max.output.tokens/server"})
    public void shouldEncodeOpenaiResponsesIncompleteMaxOutputTokens() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.incomplete.max.output.tokens.stream/client",
        "${app}/openai.responses.incomplete.max.output.tokens.stream/server"})
    public void shouldEncodeOpenaiResponsesIncompleteMaxOutputTokensStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.incomplete.content.filter/client",
        "${app}/openai.responses.incomplete.content.filter/server"})
    public void shouldEncodeOpenaiResponsesIncompleteContentFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.incomplete.max.messages/client",
        "${app}/openai.responses.incomplete.max.messages/server"})
    public void shouldRestoreOpenaiResponsesIncompleteMaxMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.usage.cache/client",
        "${app}/openai.responses.usage.cache/server"})
    public void shouldEncodeOpenaiResponsesUsageCache() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.usage.cache.stream/client",
        "${app}/openai.responses.usage.cache.stream/server"})
    public void shouldEncodeOpenaiResponsesUsageCacheStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.system.instruction/client",
        "${app}/openai.responses.section.system.instruction/server"})
    public void shouldDecodeOpenaiResponsesSectionSystemInstruction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.user.text.parts/client",
        "${app}/openai.responses.section.user.text.parts/server"})
    public void shouldDecodeOpenaiResponsesSectionUserTextParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.assistant.text/client",
        "${app}/openai.responses.section.assistant.text/server"})
    public void shouldDecodeOpenaiResponsesSectionAssistantText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.conversation/client",
        "${app}/openai.responses.section.conversation/server"})
    public void shouldDecodeOpenaiResponsesSectionConversation() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.input.string/client",
        "${app}/openai.responses.section.input.string/server"})
    public void shouldDecodeOpenaiResponsesSectionInputString() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.developer.instruction/client",
        "${app}/openai.responses.section.developer.instruction/server"})
    public void shouldDecodeOpenaiResponsesSectionDeveloperInstruction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.instructions.input.items/client",
        "${app}/openai.responses.section.instructions.input.items/server"})
    public void shouldDecodeOpenaiResponsesSectionInstructionsInputItems() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.instructions.last/client",
        "${app}/openai.responses.section.instructions.last/server"})
    public void shouldDecodeOpenaiResponsesSectionInstructionsLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.request.controls/client",
        "${app}/openai.responses.request.controls/server"})
    public void shouldDecodeOpenaiResponsesRequestControls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.response.controls/client",
        "${app}/openai.responses.response.controls/server"})
    public void shouldRestoreOpenaiResponsesResponseControls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.response.controls.stream/client",
        "${app}/openai.responses.response.controls.stream/server"})
    public void shouldRestoreOpenaiResponsesResponseControlsStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.rejected.error.code/client",
        "${app}/openai.responses.rejected.error.code/server"})
    public void shouldPreserveOpenaiResponsesRejectedErrorCode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.rejected.error.code.streaming/client",
        "${app}/openai.responses.rejected.error.code.streaming/server"})
    public void shouldPreserveOpenaiResponsesRejectedErrorCodeStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.rejected.error.param/client",
        "${app}/openai.responses.rejected.error.param/server"})
    public void shouldPreserveOpenaiResponsesRejectedErrorParam() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.rejected.rate.limit/client",
        "${app}/openai.responses.rejected.rate.limit/server"})
    public void shouldRejectOpenaiResponsesRequestRateLimited() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.rejected.section/client",
        "${app}/openai.responses.rejected.section/server"})
    public void shouldRejectOpenaiResponsesRequestSection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.rejected.streaming/client",
        "${app}/openai.responses.rejected.streaming/server"})
    public void shouldEncodeOpenaiResponsesStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.rejected.input.item.type/client",
        "${app}/openai.responses.rejected.input.item.type/server"})
    public void shouldRejectOpenaiResponsesInputItemType() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.rejected.content.part.type/client",
        "${app}/openai.responses.rejected.content.part.type/server"})
    public void shouldRejectOpenaiResponsesContentPartType() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.user.text/client",
        "${app}/transform.user.text/server"})
    public void shouldTransformUserTextFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.assistant.text/client",
        "${app}/transform.assistant.text/server"})
    public void shouldTransformAssistantTextFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.request.max.output.tokens/client",
        "${app}/transform.request.max.output.tokens/server"})
    public void shouldTransformRequestMaxOutputTokensFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.response.text/client",
        "${app}/transform.response.text/server"})
    public void shouldTransformResponseTextFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\"", "target \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.response.usage/client",
        "${app}/openai.responses.transform.response.usage/server"})
    public void shouldTransformResponseUsageFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({
        "source \"openai/responses\"",
        "target \"openai/responses\"",
        "reason \"openai:insufficient_system_resource\""})
    @Specification({
        "${net}/openai.responses.transform.response.finish.reason.unknown/client",
        "${app}/transform.response.finish.reason/server"})
    public void shouldRestoreUnknownFinishReasonFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\"", "target \"anthropic\"", "reason \"anthropic:stop_sequence\""})
    @Specification({
        "${net}/openai.responses.transform.response.text/client",
        "${app}/transform.response.finish.reason/server"})
    public void shouldDropForeignPrefixedFinishReasonAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\"", "target \"openai/responses\"", "reason \"unprefixed\""})
    @Specification({
        "${net}/openai.responses.transform.response.text/client",
        "${app}/transform.response.finish.reason/server"})
    public void shouldDropUnprefixedFinishReasonAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.guarded.yaml")
    @Specification({
        "${net}/openai.responses.rejected.authorization/client"})
    public void shouldRejectOpenaiResponsesRequestFailingAuthorization() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.function.call/client",
        "${app}/openai.responses.function.call/server"})
    public void shouldEncodeOpenaiResponsesFunctionCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.function.call.stream/client",
        "${app}/openai.responses.function.call.stream/server"})
    public void shouldEncodeOpenaiResponsesFunctionCallStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.function.call.text/client",
        "${app}/openai.responses.function.call.text/server"})
    public void shouldEncodeOpenaiResponsesFunctionCallText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.function.call.text.stream/client",
        "${app}/openai.responses.function.call.text.stream/server"})
    public void shouldEncodeOpenaiResponsesFunctionCallTextStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.function.calls/client",
        "${app}/openai.responses.function.calls/server"})
    public void shouldEncodeOpenaiResponsesFunctionCalls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.function.calls.stream/client",
        "${app}/openai.responses.function.calls.stream/server"})
    public void shouldEncodeOpenaiResponsesFunctionCallsStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.custom.tool.call/client",
        "${app}/openai.responses.custom.tool.call/server"})
    public void shouldEncodeOpenaiResponsesCustomToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.custom.tool.call.stream/client",
        "${app}/openai.responses.custom.tool.call.stream/server"})
    public void shouldEncodeOpenaiResponsesCustomToolCallStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.web.search/client",
        "${app}/openai.responses.web.search/server"})
    public void shouldEncodeOpenaiResponsesWebSearch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.web.search.stream/client",
        "${app}/openai.responses.web.search.stream/server"})
    public void shouldEncodeOpenaiResponsesWebSearchStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.code.interpreter/client",
        "${app}/openai.responses.code.interpreter/server"})
    public void shouldEncodeOpenaiResponsesCodeInterpreter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.code.interpreter.stream/client",
        "${app}/openai.responses.code.interpreter.stream/server"})
    public void shouldEncodeOpenaiResponsesCodeInterpreterStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.mcp/client",
        "${app}/openai.responses.mcp/server"})
    public void shouldEncodeOpenaiResponsesMcp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.mcp.stream/client",
        "${app}/openai.responses.mcp.stream/server"})
    public void shouldEncodeOpenaiResponsesMcpStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.image.generation/client",
        "${app}/openai.responses.image.generation/server"})
    public void shouldEncodeOpenaiResponsesImageGeneration() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.image.generation.stream/client",
        "${app}/openai.responses.image.generation.stream/server"})
    public void shouldEncodeOpenaiResponsesImageGenerationStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.tool.call.ignored/client",
        "${app}/openai.responses.section.tool.call.ignored/server"})
    public void shouldDecodeOpenaiResponsesSectionToolCallIgnored() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.tool.definition/client",
        "${app}/openai.responses.section.tool.definition/server"})
    public void shouldDecodeOpenaiResponsesSectionToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.tool.definition.members/client",
        "${app}/openai.responses.section.tool.definition.members/server"})
    public void shouldDecodeOpenaiResponsesSectionToolDefinitionMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.tool.definition.100k/client",
        "${app}/openai.responses.section.tool.definition.100k/server"})
    public void shouldDecodeOpenaiResponsesSectionToolDefinition100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.server.tool.definition/client",
        "${app}/openai.responses.section.server.tool.definition/server"})
    public void shouldDecodeOpenaiResponsesSectionServerToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.tool.call/client",
        "${app}/openai.responses.section.tool.call/server"})
    public void shouldDecodeOpenaiResponsesSectionToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.tool.calls/client",
        "${app}/openai.responses.section.tool.calls/server"})
    public void shouldDecodeOpenaiResponsesSectionToolCalls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.assistant.text.and.tool.call/client",
        "${app}/openai.responses.section.assistant.text.and.tool.call/server"})
    public void shouldDecodeOpenaiResponsesSectionAssistantTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.tool.call.members/client",
        "${app}/openai.responses.section.tool.call.members/server"})
    public void shouldDecodeOpenaiResponsesSectionToolCallMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.tool.call.escaped/client",
        "${app}/openai.responses.section.tool.call.escaped/server"})
    public void shouldDecodeOpenaiResponsesSectionToolCallEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.tool.result/client",
        "${app}/openai.responses.section.tool.result/server"})
    public void shouldDecodeOpenaiResponsesSectionToolResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.tool.result.parts/client",
        "${app}/openai.responses.section.tool.result.parts/server"})
    public void shouldDecodeOpenaiResponsesSectionToolResultParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.server.tool.call/client",
        "${app}/openai.responses.section.server.tool.call/server"})
    public void shouldDecodeOpenaiResponsesSectionServerToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.tool.definition/client",
        "${app}/transform.tool.definition/server"})
    public void shouldTransformToolDefinitionFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.server.tool.definition/client",
        "${app}/transform.server.tool.definition/server"})
    public void shouldTransformServerToolDefinitionFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.tool.result/client",
        "${app}/transform.tool.result/server"})
    public void shouldTransformToolResultFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.tool.result.blocks/client",
        "${app}/transform.tool.result.blocks/server"})
    public void shouldTransformToolResultBlocksFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.assistant.tool.call/client",
        "${app}/transform.assistant.tool.call/server"})
    public void shouldTransformAssistantToolCallFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.assistant.tool.call.escaped/client",
        "${app}/transform.assistant.tool.call.escaped/server"})
    public void shouldTransformAssistantToolCallEscapedFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.response.tool.call/client",
        "${app}/transform.response.tool.call/server"})
    public void shouldTransformResponseToolCallFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.response.tool.call.empty/client",
        "${app}/transform.response.tool.call.empty/server"})
    public void shouldTransformResponseToolCallEmptyFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.response.tool.calls/client",
        "${app}/transform.response.tool.calls/server"})
    public void shouldTransformResponseToolCallsFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.response.text.and.tool.call/client",
        "${app}/transform.response.text.and.tool.call/server"})
    public void shouldTransformResponseTextAndToolCallFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.response.streaming.tool.call/client",
        "${app}/openai.transform.response.streaming.tool.call/server"})
    public void shouldTransformResponseStreamingToolCallFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.function.call.large/client",
        "${app}/openai.responses.function.call.large/server"})
    public void shouldEncodeOpenaiResponsesFunctionCallLarge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.function.call.large.stream/client",
        "${app}/openai.responses.function.call.large.stream/server"})
    public void shouldEncodeOpenaiResponsesFunctionCallLargeStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.function.call.huge/client",
        "${app}/openai.responses.function.call.huge/server"})
    public void shouldEncodeOpenaiResponsesFunctionCallHuge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.function.call.huge.stream/client",
        "${app}/openai.responses.function.call.huge.stream/server"})
    public void shouldEncodeOpenaiResponsesFunctionCallHugeStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.text.stream.large/client",
        "${app}/openai.responses.text.stream.large/server"})
    public void shouldEncodeOpenaiResponsesTextStreamLarge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.text.stream.huge/client",
        "${app}/openai.responses.text.stream.huge/server"})
    public void shouldEncodeOpenaiResponsesTextStreamHuge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.tool.call.attributes.last/client",
        "${app}/openai.responses.section.tool.call.attributes.last/server"})
    public void shouldDecodeOpenaiResponsesSectionToolCallAttributesLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.reasoning/client",
        "${app}/openai.responses.reasoning/server"})
    public void shouldEncodeOpenaiResponsesReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.reasoning.stream/client",
        "${app}/openai.responses.reasoning.stream/server"})
    public void shouldEncodeOpenaiResponsesReasoningStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.reasoning.no.summary/client",
        "${app}/openai.responses.reasoning.no.summary/server"})
    public void shouldEncodeOpenaiResponsesReasoningNoSummary() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.reasoning.no.summary.stream/client",
        "${app}/openai.responses.reasoning.no.summary.stream/server"})
    public void shouldEncodeOpenaiResponsesReasoningNoSummaryStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.reasoning.tool.call/client",
        "${app}/openai.responses.reasoning.tool.call/server"})
    public void shouldEncodeOpenaiResponsesReasoningToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.reasoning.tool.call.stream/client",
        "${app}/openai.responses.reasoning.tool.call.stream/server"})
    public void shouldEncodeOpenaiResponsesReasoningToolCallStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.reasoning.id/client",
        "${app}/openai.responses.reasoning.id/server"})
    public void shouldEncodeOpenaiResponsesReasoningId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.reasoning.id.stream/client",
        "${app}/openai.responses.reasoning.id.stream/server"})
    public void shouldEncodeOpenaiResponsesReasoningIdStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.reasoning.large/client",
        "${app}/openai.responses.reasoning.large/server"})
    public void shouldEncodeOpenaiResponsesReasoningLarge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.reasoning.large.stream/client",
        "${app}/openai.responses.reasoning.large.stream/server"})
    public void shouldEncodeOpenaiResponsesReasoningLargeStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.refusal/client",
        "${app}/openai.responses.refusal/server"})
    public void shouldEncodeOpenaiResponsesRefusal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.refusal.stream/client",
        "${app}/openai.responses.refusal.stream/server"})
    public void shouldEncodeOpenaiResponsesRefusalStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.text.citations/client",
        "${app}/openai.responses.text.citations/server"})
    public void shouldEncodeOpenaiResponsesTextCitations() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.text.citations.stream/client",
        "${app}/openai.responses.text.citations.stream/server"})
    public void shouldEncodeOpenaiResponsesTextCitationsStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.text.citations.members/client",
        "${app}/openai.responses.text.citations.members/server"})
    public void shouldEncodeOpenaiResponsesTextCitationsMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.text.citations.members.stream/client",
        "${app}/openai.responses.text.citations.members.stream/server"})
    public void shouldEncodeOpenaiResponsesTextCitationsMembersStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.reasoning/client",
        "${app}/openai.responses.section.reasoning/server"})
    public void shouldDecodeOpenaiResponsesSectionReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.reasoning.no.summary/client",
        "${app}/openai.responses.section.reasoning.no.summary/server"})
    public void shouldDecodeOpenaiResponsesSectionReasoningNoSummary() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.reasoning.parts/client",
        "${app}/openai.responses.section.reasoning.parts/server"})
    public void shouldDecodeOpenaiResponsesSectionReasoningParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.reasoning.items/client",
        "${app}/openai.responses.section.reasoning.items/server"})
    public void shouldDecodeOpenaiResponsesSectionReasoningItems() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.reasoning.large/client",
        "${app}/openai.responses.section.reasoning.large/server"})
    public void shouldDecodeOpenaiResponsesSectionReasoningLarge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.assistant.refusal/client",
        "${app}/openai.responses.section.assistant.refusal/server"})
    public void shouldDecodeOpenaiResponsesSectionAssistantRefusal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.assistant.text.citations/client",
        "${app}/openai.responses.section.assistant.text.citations/server"})
    public void shouldDecodeOpenaiResponsesSectionAssistantTextCitations() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.assistant.text.citations.members/client",
        "${app}/openai.responses.section.assistant.text.citations.members/server"})
    public void shouldDecodeOpenaiResponsesSectionAssistantTextCitationsMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.assistant.text.citations.extra/client",
        "${app}/openai.responses.section.assistant.text.citations.extra/server"})
    public void shouldDecodeOpenaiResponsesSectionAssistantTextCitationsExtra() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.assistant.text.parts/client",
        "${app}/openai.responses.section.assistant.text.parts/server"})
    public void shouldDecodeOpenaiResponsesSectionAssistantTextParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.user.image/client",
        "${app}/openai.responses.section.user.image/server"})
    public void shouldDecodeOpenaiResponsesSectionUserImage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.user.image.inline/client",
        "${app}/openai.responses.section.user.image.inline/server"})
    public void shouldDecodeOpenaiResponsesSectionUserImageInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.user.image.reference/client",
        "${app}/openai.responses.section.user.image.reference/server"})
    public void shouldDecodeOpenaiResponsesSectionUserImageReference() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.user.image.detail.first/client",
        "${app}/openai.responses.section.user.image.detail.first/server"})
    public void shouldDecodeOpenaiResponsesSectionUserImageDetailFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.user.image.10k/client",
        "${app}/openai.responses.section.user.image.10k/server"})
    public void shouldDecodeOpenaiResponsesSectionUserImage10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.user.image.url.10k/client",
        "${app}/openai.responses.section.user.image.url.10k/server"})
    public void shouldDecodeOpenaiResponsesSectionUserImageUrl10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.user.image.url.100k/client",
        "${app}/openai.responses.section.user.image.url.100k/server"})
    public void shouldDecodeOpenaiResponsesSectionUserImageUrl100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.user.document/client",
        "${app}/openai.responses.section.user.document/server"})
    public void shouldDecodeOpenaiResponsesSectionUserDocument() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.user.document.inline/client",
        "${app}/openai.responses.section.user.document.inline/server"})
    public void shouldDecodeOpenaiResponsesSectionUserDocumentInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.user.document.reference/client",
        "${app}/openai.responses.section.user.document.reference/server"})
    public void shouldDecodeOpenaiResponsesSectionUserDocumentReference() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.user.document.detail/client",
        "${app}/openai.responses.section.user.document.detail/server"})
    public void shouldDecodeOpenaiResponsesSectionUserDocumentDetail() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.user.audio/client",
        "${app}/openai.responses.section.user.audio/server"})
    public void shouldDecodeOpenaiResponsesSectionUserAudio() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.reasoning.encrypted.first/client",
        "${app}/openai.responses.section.reasoning.encrypted.first/server"})
    public void shouldDecodeOpenaiResponsesSectionReasoningEncryptedFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.reasoning.encrypted.first.10k/client",
        "${app}/openai.responses.section.reasoning.encrypted.first.10k/server"})
    public void shouldDecodeOpenaiResponsesSectionReasoningEncryptedFirst10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.reasoning.content/client",
        "${app}/openai.responses.section.reasoning.content/server"})
    public void shouldDecodeOpenaiResponsesSectionReasoningContent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.section.assistant.text.citations.annotations.first/client",
        "${app}/openai.responses.section.assistant.text.citations.annotations.first/server"})
    public void shouldDecodeOpenaiResponsesSectionAssistantTextCitationsAnnotationsFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({
        "source \"openai/responses\"",
        "target \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.response.reasoning/client",
        "${app}/transform.response.reasoning/server"})
    public void shouldTransformResponseReasoningFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.system.instruction/client",
        "${app}/transform.system.instruction/server"})
    public void shouldTransformSystemInstructionFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.system.instruction.last/client",
        "${app}/transform.system.instruction.last/server"})
    public void shouldTransformSystemInstructionLastFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.system.instruction.last.100k/client",
        "${app}/transform.system.instruction.last.100k/server"})
    public void shouldTransformSystemInstructionLast100kFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.assistant.refusal/client",
        "${app}/transform.assistant.refusal/server"})
    public void shouldTransformAssistantRefusalFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.assistant.text.and.tool.call/client",
        "${app}/transform.assistant.text.and.tool.call/server"})
    public void shouldTransformAssistantTextAndToolCallFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.mixed.content/client",
        "${app}/transform.mixed.content/server"})
    public void shouldTransformMixedContentFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.user.image/client",
        "${app}/transform.user.image/server"})
    public void shouldTransformUserImageFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.user.image.inline/client",
        "${app}/transform.user.image.inline/server"})
    public void shouldTransformUserImageInlineFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.user.image.10k/client",
        "${app}/transform.user.image.10k/server"})
    public void shouldTransformUserImage10kFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.user.image.url.10k/client",
        "${app}/transform.user.image.url.10k/server"})
    public void shouldTransformUserImageUrl10kFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.user.image.url.100k/client",
        "${app}/transform.user.image.url.100k/server"})
    public void shouldTransformUserImageUrl100kFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.user.document.inline/client",
        "${app}/transform.user.document.inline/server"})
    public void shouldTransformUserDocumentInlineFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.user.document.reference/client",
        "${app}/transform.user.document.reference/server"})
    public void shouldTransformUserDocumentReferenceFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.tool.definition.100k/client",
        "${app}/transform.tool.definition.100k/server"})
    public void shouldTransformToolDefinition100kFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.tool.definition.10k/client",
        "${app}/transform.tool.definition.10k/server"})
    public void shouldTransformToolDefinition10kFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.tool.definition.parameters.100k/client",
        "${app}/transform.tool.definition.parameters.100k/server"})
    public void shouldTransformToolDefinitionParameters100kFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.tool.definition.parameters.10k/client",
        "${app}/transform.tool.definition.parameters.10k/server"})
    public void shouldTransformToolDefinitionParameters10kFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.response.refusal/client",
        "${app}/transform.response.refusal/server"})
    public void shouldTransformResponseRefusalFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.response.text.100k/client",
        "${app}/transform.response.text.100k/server"})
    public void shouldTransformResponseText100kFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.response.text.escaped/client",
        "${app}/transform.response.text.escaped/server"})
    public void shouldTransformResponseTextEscapedFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.response.text.multibyte/client",
        "${app}/transform.response.text.multibyte/server"})
    public void shouldTransformResponseTextMultibyteFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.assistant.tool.call.100k.identity.first/client",
        "${app}/transform.assistant.tool.call.100k/server"})
    public void shouldTransformAssistantToolCall100kFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.assistant.tool.call.10k.identity.first/client",
        "${app}/transform.assistant.tool.call.10k/server"})
    public void shouldTransformAssistantToolCall10kFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.assistant.tool.call.id.last/client",
        "${app}/openai.responses.transform.assistant.tool.call.attributes.last/server"})
    public void shouldTransformAssistantToolCallIdLastFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.assistant.tool.call.name.last/client",
        "${app}/openai.responses.transform.assistant.tool.call.attributes.last/server"})
    public void shouldTransformAssistantToolCallNameLastFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\"", "target \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.response.usage.cache.write/client",
        "${app}/openai.responses.transform.response.usage.cache.write/server"})
    public void shouldTransformResponseUsageCacheWriteFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\"", "target \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.response.streaming/client",
        "${app}/openai.transform.response.streaming/server"})
    public void shouldTransformResponseStreamingFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\"", "target \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.response.streaming.error/client",
        "${app}/openai.transform.response.streaming.error/server"})
    public void shouldTransformResponseStreamingErrorFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.transform.request.members/client",
        "${app}/openai.responses.transform.request.members/server"})
    public void shouldTransformRequestMembersFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.transform.conversation/client",
        "${app}/openai.responses.transform.conversation/server"})
    public void shouldTransformConversationFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({
        "source \"openai/responses\"",
        "target \"openai\"",
        "reason \"openai:insufficient_system_resource\""})
    @Specification({
        "${net}/openai.responses.transform.response.text/client",
        "${app}/transform.response.finish.reason/server"})
    public void shouldDropOpenaiFinishReasonFromOpenaiAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({
        "source \"openai/responses\"",
        "target \"anthropic\"",
        "reason \"openai:insufficient_system_resource\""})
    @Specification({
        "${net}/openai.responses.transform.response.text/client",
        "${app}/transform.response.finish.reason/server"})
    public void shouldDropOpenaiFinishReasonFromAnthropicAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\"", "target \"openai\""})
    @Specification({
        "${net}/openai.responses.transform.response.text/client",
        "${app}/openai.transform.response.frames/server"})
    public void shouldDropOpenaiResponseMembersFromOpenaiAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\"", "target \"anthropic\""})
    @Specification({
        "${net}/openai.responses.transform.response.text/client",
        "${app}/anthropic.transform.response.frames/server"})
    public void shouldDropAnthropicResponseMembersFromAnthropicAsOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.text.citations.long/client",
        "${app}/openai.responses.text.citations.long/server"})
    public void shouldEncodeOpenaiResponsesTextCitationsLong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.response.controls.inline/client",
        "${app}/openai.responses.response.controls.inline/server"})
    public void shouldEncodeOpenaiResponsesResponseControlsInline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.response.controls.held.large/client",
        "${app}/openai.responses.response.controls.held.large/server"})
    public void shouldEncodeOpenaiResponsesResponseControlsHeldLarge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Configure(name = ENGINE_BUFFER_SLOT_CAPACITY_NAME, value = "8192")
    @Specification({
        "${net}/openai.responses.response.controls.held.small.slot/client",
        "${app}/openai.responses.response.controls.held.small.slot/server"})
    public void shouldEncodeOpenaiResponsesResponseControlsHeldSmallSlot() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @ScriptProperty({"source \"openai/responses\""})
    @Specification({
        "${net}/openai.responses.transform.request.sampling/client",
        "${app}/transform.request.sampling/server"})
    public void shouldTransformRequestSamplingFromOpenaiResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.request.tool.choice.auto/client",
        "${app}/openai.responses.request.tool.choice.auto/server"})
    public void shouldForwardOpenaiResponsesRequestToolChoiceAuto() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.request.tool.choice.required/client",
        "${app}/openai.responses.request.tool.choice.required/server"})
    public void shouldForwardOpenaiResponsesRequestToolChoiceRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.request.tool.choice.none/client",
        "${app}/openai.responses.request.tool.choice.none/server"})
    public void shouldForwardOpenaiResponsesRequestToolChoiceNone() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.request.tool.choice.function/client",
        "${app}/openai.responses.request.tool.choice.function/server"})
    public void shouldForwardOpenaiResponsesRequestToolChoiceFunction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.request.tool.choice.allowed/client",
        "${app}/openai.responses.request.tool.choice.allowed/server"})
    public void shouldForwardOpenaiResponsesRequestToolChoiceAllowed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.request.tool.choice.hosted/client",
        "${app}/openai.responses.request.tool.choice.hosted/server"})
    public void shouldForwardOpenaiResponsesRequestToolChoiceHosted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.request.tool.choice.parallel/client",
        "${app}/openai.responses.request.tool.choice.parallel/server"})
    public void shouldForwardOpenaiResponsesRequestToolChoiceParallel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.request.tool.choice.mixed/client",
        "${app}/openai.responses.request.tool.choice.mixed/server"})
    public void shouldForwardOpenaiResponsesRequestToolChoiceMixed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.request.response.format.text/client",
        "${app}/openai.responses.request.response.format.text/server"})
    public void shouldForwardOpenaiResponsesRequestResponseFormatText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.request.response.format.json.object/client",
        "${app}/openai.responses.request.response.format.json.object/server"})
    public void shouldForwardOpenaiResponsesRequestResponseFormatJsonObject() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.request.response.format.json.schema/client",
        "${app}/openai.responses.request.response.format.json.schema/server"})
    public void shouldForwardOpenaiResponsesRequestResponseFormatJsonSchema() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.request.response.format.residue/client",
        "${app}/openai.responses.request.response.format.residue/server"})
    public void shouldForwardOpenaiResponsesRequestResponseFormatResidue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.request.response.format.residue.split/client",
        "${app}/openai.responses.request.response.format.residue.split/server"})
    public void shouldForwardOpenaiResponsesRequestResponseFormatResidueSplit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.request.response.format.residue.only/client",
        "${app}/openai.responses.request.response.format.residue.only/server"})
    public void shouldForwardOpenaiResponsesRequestResponseFormatResidueOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.request.response.format.json.schema.type.last/client",
        "${app}/openai.responses.request.response.format.json.schema/server"})
    public void shouldForwardOpenaiResponsesRequestResponseFormatJsonSchemaTypeLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.request.response.format.rejected.bound/client",
        "${app}/openai.responses.request.response.format.rejected.bound/server"})
    public void shouldDecodeOpenaiResponsesRequestResponseFormatRejectedBound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.responses.request.response.format.json.schema.split/client",
        "${app}/openai.responses.request.response.format.json.schema/server"})
    public void shouldForwardOpenaiResponsesRequestResponseFormatJsonSchemaSplit() throws Exception
    {
        k3po.finish();
    }
}
