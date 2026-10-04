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
        "${net}/reject.unknown.dialect/client"})
    public void shouldRejectRequestWithUnresolvedDialect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.text/client",
        "${app}/openai.response.encode.text/server"})
    public void shouldEncodeOpenaiResponseText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.text.and.tool.call/client",
        "${app}/openai.response.encode.text.and.tool.call/server"})
    public void shouldEncodeOpenaiResponseTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.refusal/client",
        "${app}/openai.response.encode.refusal/server"})
    public void shouldEncodeOpenaiResponseRefusal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.streaming/client",
        "${app}/openai.response.encode.streaming/server"})
    public void shouldEncodeOpenaiResponseStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.streaming.tool.call/client",
        "${app}/openai.response.encode.streaming.tool.call/server"})
    public void shouldEncodeOpenaiResponseStreamingToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.streaming.error/client",
        "${app}/openai.response.encode.streaming.error/server"})
    public void shouldEncodeOpenaiResponseStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.text.100k/client",
        "${app}/openai.response.encode.text.100k/server"})
    public void shouldEncodeOpenaiResponseText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.reasoning/client",
        "${app}/openai.response.encode.reasoning/server"})
    public void shouldEncodeOpenaiResponseReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.text.escaped/client",
        "${app}/openai.response.encode.text.escaped/server"})
    public void shouldEncodeOpenaiResponseTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.response.encode.streaming.multibyte/client",
        "${app}/openai.response.encode.streaming.multibyte/server"})
    public void shouldEncodeOpenaiResponseStreamingMultibyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.response.encode.reasoning/client",
        "${app}/anthropic.response.encode.reasoning/server"})
    public void shouldEncodeAnthropicResponseReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.response.encode.refusal/client",
        "${app}/anthropic.response.encode.refusal/server"})
    public void shouldEncodeAnthropicResponseRefusal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.response.encode.streaming/client",
        "${app}/anthropic.response.encode.streaming/server"})
    public void shouldEncodeAnthropicResponseStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.response.encode.streaming.error/client",
        "${app}/anthropic.response.encode.streaming.error/server"})
    public void shouldEncodeAnthropicResponseStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.response.encode.streaming.multibyte/client",
        "${app}/anthropic.response.encode.streaming.multibyte/server"})
    public void shouldEncodeAnthropicResponseStreamingMultibyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.response.encode.streaming.tool.call/client",
        "${app}/anthropic.response.encode.streaming.tool.call/server"})
    public void shouldEncodeAnthropicResponseStreamingToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.response.encode.text/client",
        "${app}/anthropic.response.encode.text/server"})
    public void shouldEncodeAnthropicResponseText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.response.encode.text.100k/client",
        "${app}/anthropic.response.encode.text.100k/server"})
    public void shouldEncodeAnthropicResponseText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.response.encode.text.and.tool.call/client",
        "${app}/anthropic.response.encode.text.and.tool.call/server"})
    public void shouldEncodeAnthropicResponseTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.response.encode.text.escaped/client",
        "${app}/anthropic.response.encode.text.escaped/server"})
    public void shouldEncodeAnthropicResponseTextEscaped() throws Exception
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
        "${net}/anthropic.section.rejected.model.last.oversize/client"})
    public void shouldDecodeAnthropicSectionRejectedModelLastOversize() throws Exception
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
}
