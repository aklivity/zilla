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

public class LlmGeminiServerIT
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
        "${net}/gemini/client",
        "${app}/gemini/server"})
    public void shouldForwardGeminiNonstreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.system.instruction/client",
        "${app}/gemini.section.system.instruction/server"})
    public void shouldForwardGeminiSectionSystemInstruction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.system.instruction.last/client",
        "${app}/gemini.section.system.instruction.last/server"})
    public void shouldForwardGeminiSectionSystemInstructionLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.system.instruction.parts/client",
        "${app}/gemini.section.system.instruction.parts/server"})
    public void shouldForwardGeminiSectionSystemInstructionParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.conversation/client",
        "${app}/gemini.section.conversation/server"})
    public void shouldForwardGeminiSectionConversation() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.user.text.parts/client",
        "${app}/gemini.section.user.text.parts/server"})
    public void shouldForwardGeminiSectionUserTextParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.user.text.multibyte/client",
        "${app}/gemini.section.user.text.multibyte/server"})
    public void shouldForwardGeminiSectionUserTextMultibyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.user.text.escaped/client",
        "${app}/gemini.section.user.text.escaped/server"})
    public void shouldForwardGeminiSectionUserTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.user.text.empty/client",
        "${app}/gemini.section.user.text.empty/server"})
    public void shouldForwardGeminiSectionUserTextEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.assistant.text.multibyte/client",
        "${app}/gemini.section.assistant.text.multibyte/server"})
    public void shouldForwardGeminiSectionAssistantTextMultibyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.assistant.text.escaped/client",
        "${app}/gemini.section.assistant.text.escaped/server"})
    public void shouldForwardGeminiSectionAssistantTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.user.text.split/client",
        "${app}/gemini.section.user.text.split/server"})
    public void shouldForwardGeminiSectionUserTextSplit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.request.options/client",
        "${app}/gemini.request.options/server"})
    public void shouldForwardGeminiRequestOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.request.options.leading/client",
        "${app}/gemini.request.options.leading/server"})
    public void shouldForwardGeminiRequestOptionsLeading() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.request.options.verbatim/client",
        "${app}/gemini.request.options.verbatim/server"})
    public void shouldForwardGeminiRequestOptionsVerbatim() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.guarded.yaml")
    @Specification({
        "${net}/gemini.authorized/client",
        "${app}/gemini.authorized/server"})
    public void shouldForwardGeminiAuthorized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.guarded.yaml")
    @Specification({
        "${net}/gemini.rejected.authorization/client"})
    public void shouldForwardGeminiRejectedAuthorization() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.guarded.yaml")
    @Specification({
        "${net}/gemini.rejected.authorization.missing/client"})
    public void shouldForwardGeminiRejectedAuthorizationMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.rejected.rate.limit/client",
        "${app}/gemini.rejected.rate.limit/server"})
    public void shouldForwardGeminiRejectedRateLimit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.rejected.model.not.found/client",
        "${app}/gemini.rejected.model.not.found/server"})
    public void shouldForwardGeminiRejectedModelNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.usage/client",
        "${app}/gemini.usage/server"})
    public void shouldForwardGeminiUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.usage.cached/client",
        "${app}/gemini.usage.cached/server"})
    public void shouldForwardGeminiUsageCached() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.assistant.text.members/client",
        "${app}/gemini.section.assistant.text.members/server"})
    public void shouldForwardGeminiSectionAssistantTextMembers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.streaming.complete/client",
        "${app}/gemini.streaming.complete/server"})
    public void shouldForwardGeminiStreamingComplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.streaming.array/client",
        "${app}/gemini.streaming.array/server"})
    public void shouldForwardGeminiStreamingArray() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.response.text/client",
        "${app}/gemini.response.text/server"})
    public void shouldForwardGeminiResponseText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.rejected.json.malformed/client",
        "${app}/gemini.section.rejected.json.malformed/server"})
    public void shouldForwardGeminiSectionRejectedJsonMalformed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.rejected.contents.not.array/client",
        "${app}/gemini.section.rejected.contents.not.array/server"})
    public void shouldForwardGeminiSectionRejectedContentsNotArray() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.rejected.part.invalid/client",
        "${app}/gemini.section.rejected.part.invalid/server"})
    public void shouldForwardGeminiSectionRejectedPartInvalid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.rejected.part.text.invalid/client",
        "${app}/gemini.section.rejected.part.text.invalid/server"})
    public void shouldForwardGeminiSectionRejectedPartTextInvalid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.rejected.role.invalid/client",
        "${app}/gemini.section.rejected.role.invalid/server"})
    public void shouldForwardGeminiSectionRejectedRoleInvalid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.rejected.role.order/client",
        "${app}/gemini.section.rejected.role.order/server"})
    public void shouldForwardGeminiSectionRejectedRoleOrder() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.rejected.trailing.content/client",
        "${app}/gemini.section.rejected.trailing.content/server"})
    public void shouldForwardGeminiSectionRejectedTrailingContent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.request.unsupported.content.type/client"})
    public void shouldForwardGeminiRequestUnsupportedContentType() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.role.absent/client",
        "${app}/gemini.section.role.absent/server"})
    public void shouldForwardGeminiSectionRoleAbsent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.role.after.parts/client",
        "${app}/gemini.section.role.after.parts/server"})
    public void shouldForwardGeminiSectionRoleAfterParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.10k/client",
        "${app}/gemini.10k/server"})
    public void shouldForwardGemini10K() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.100k/client",
        "${app}/gemini.100k/server"})
    public void shouldForwardGemini100K() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.tool.definition/client",
        "${app}/gemini.section.tool.definition/server"})
    public void shouldForwardGeminiSectionToolDefinition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.tool.definition.leading/client",
        "${app}/gemini.section.tool.definition.leading/server"})
    public void shouldForwardGeminiSectionToolDefinitionLeading() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.tool.definition.multiple/client",
        "${app}/gemini.section.tool.definition.multiple/server"})
    public void shouldForwardGeminiSectionToolDefinitionMultiple() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.tool.definition.tools/client",
        "${app}/gemini.section.tool.definition.tools/server"})
    public void shouldForwardGeminiSectionToolDefinitionTools() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.tool.definition.parameters/client",
        "${app}/gemini.section.tool.definition.parameters/server"})
    public void shouldForwardGeminiSectionToolDefinitionParameters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.tool.definition.minimal/client",
        "${app}/gemini.section.tool.definition.minimal/server"})
    public void shouldForwardGeminiSectionToolDefinitionMinimal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.tool.definition.name.last/client",
        "${app}/gemini.section.tool.definition.name.last/server"})
    public void shouldForwardGeminiSectionToolDefinitionNameLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.tool.definition.parameters.10k/client",
        "${app}/gemini.section.tool.definition.parameters.10k/server"})
    public void shouldForwardGeminiSectionToolDefinitionParameters10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/gemini.section.tool.definition.parameters.100k/client",
        "${app}/gemini.section.tool.definition.parameters.100k/server"})
    public void shouldForwardGeminiSectionToolDefinitionParameters100k() throws Exception
    {
        k3po.finish();
    }
}
