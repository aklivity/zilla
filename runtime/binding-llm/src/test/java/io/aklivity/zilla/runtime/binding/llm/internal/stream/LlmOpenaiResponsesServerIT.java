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
    @Configuration("server.openai.responses.yaml")
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
}
