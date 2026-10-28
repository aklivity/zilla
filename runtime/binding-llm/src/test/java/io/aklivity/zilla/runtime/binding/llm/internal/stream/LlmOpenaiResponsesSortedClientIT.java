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

public class LlmOpenaiResponsesSortedClientIT
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
        "${app}/openai.responses.text/client",
        "${net}/openai.responses.sorted.text/server"})
    public void shouldForwardOpenaiResponsesSortedNonstreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.text.stream/client",
        "${net}/openai.responses.sorted.text.stream/server"})
    public void shouldForwardOpenaiResponsesSortedStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.function.call/client",
        "${net}/openai.responses.sorted.function.call/server"})
    public void shouldForwardOpenaiResponsesSortedFunctionCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.function.call.stream/client",
        "${net}/openai.responses.sorted.function.call.stream/server"})
    public void shouldForwardOpenaiResponsesSortedFunctionCallStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.function.calls/client",
        "${net}/openai.responses.sorted.function.calls/server"})
    public void shouldForwardOpenaiResponsesSortedFunctionCalls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.function.calls.stream/client",
        "${net}/openai.responses.sorted.function.calls.stream/server"})
    public void shouldForwardOpenaiResponsesSortedFunctionCallsStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.function.call.text/client",
        "${net}/openai.responses.sorted.function.call.text/server"})
    public void shouldForwardOpenaiResponsesSortedFunctionCallText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.function.call.text.stream/client",
        "${net}/openai.responses.sorted.function.call.text.stream/server"})
    public void shouldForwardOpenaiResponsesSortedFunctionCallTextStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.reasoning/client",
        "${net}/openai.responses.sorted.reasoning/server"})
    public void shouldForwardOpenaiResponsesSortedReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.reasoning.stream/client",
        "${net}/openai.responses.sorted.reasoning.stream/server"})
    public void shouldForwardOpenaiResponsesSortedReasoningStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.reasoning.tool.call/client",
        "${net}/openai.responses.sorted.reasoning.tool.call/server"})
    public void shouldForwardOpenaiResponsesSortedReasoningToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.reasoning.tool.call.stream/client",
        "${net}/openai.responses.sorted.reasoning.tool.call.stream/server"})
    public void shouldForwardOpenaiResponsesSortedReasoningToolCallStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.reasoning.id/client",
        "${net}/openai.responses.sorted.reasoning.id/server"})
    public void shouldForwardOpenaiResponsesSortedReasoningId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.reasoning.id.stream/client",
        "${net}/openai.responses.sorted.reasoning.id.stream/server"})
    public void shouldForwardOpenaiResponsesSortedReasoningIdStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.refusal/client",
        "${net}/openai.responses.sorted.refusal/server"})
    public void shouldForwardOpenaiResponsesSortedRefusal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.refusal.stream/client",
        "${net}/openai.responses.sorted.refusal.stream/server"})
    public void shouldForwardOpenaiResponsesSortedRefusalStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.incomplete.max.output.tokens/client",
        "${net}/openai.responses.sorted.incomplete.max.output.tokens/server"})
    public void shouldForwardOpenaiResponsesSortedIncompleteMaxOutputTokens() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.incomplete.max.output.tokens.stream/client",
        "${net}/openai.responses.sorted.incomplete.max.output.tokens.stream/server"})
    public void shouldForwardOpenaiResponsesSortedIncompleteMaxOutputTokensStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.custom.tool.call/client",
        "${net}/openai.responses.sorted.custom.tool.call/server"})
    public void shouldForwardOpenaiResponsesSortedCustomToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.custom.tool.call.stream/client",
        "${net}/openai.responses.sorted.custom.tool.call.stream/server"})
    public void shouldForwardOpenaiResponsesSortedCustomToolCallStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.sorted.mcp/client",
        "${net}/openai.responses.sorted.mcp/server"})
    public void shouldForwardOpenaiResponsesSortedMcp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.sorted.code.interpreter/client",
        "${net}/openai.responses.sorted.code.interpreter/server"})
    public void shouldForwardOpenaiResponsesSortedCodeInterpreter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.sorted.code.interpreter.stream/client",
        "${net}/openai.responses.sorted.code.interpreter.stream/server"})
    public void shouldForwardOpenaiResponsesSortedCodeInterpreterStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.sorted.web.search/client",
        "${net}/openai.responses.sorted.web.search/server"})
    public void shouldForwardOpenaiResponsesSortedWebSearch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.sorted.web.search.stream/client",
        "${net}/openai.responses.sorted.web.search.stream/server"})
    public void shouldForwardOpenaiResponsesSortedWebSearchStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.usage.cache/client",
        "${net}/openai.responses.sorted.usage.cache/server"})
    public void shouldForwardOpenaiResponsesSortedUsageCache() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.usage.cache.stream/client",
        "${net}/openai.responses.sorted.usage.cache.stream/server"})
    public void shouldForwardOpenaiResponsesSortedUsageCacheStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.sorted.response.controls/client",
        "${net}/openai.responses.sorted.response.controls/server"})
    public void shouldRestoreOpenaiResponsesResponseControls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.response.controls.stream/client",
        "${net}/openai.responses.sorted.response.controls.stream/server"})
    public void shouldRestoreOpenaiResponsesResponseControlsStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.rejected.output.item.type/client",
        "${net}/openai.responses.sorted.rejected.output.item.type/server"})
    public void shouldRejectOpenaiResponsesUnsupportedOutputItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.responses.rejected.output.item.type.stream/client",
        "${net}/openai.responses.sorted.rejected.output.item.type.stream/server"})
    public void shouldRejectOpenaiResponsesUnsupportedOutputItemStream() throws Exception
    {
        k3po.finish();
    }
}
