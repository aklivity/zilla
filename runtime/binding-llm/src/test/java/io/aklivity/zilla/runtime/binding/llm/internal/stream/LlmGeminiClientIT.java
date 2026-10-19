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
        "${net}/gemini.request.options.leading/server"})
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
}
