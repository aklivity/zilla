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

public class LlmClientIT
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
    @Configuration("client.signed.yaml")
    @Specification({
        "${app}/buffer.and.sign/client",
        "${net}/buffer.and.sign/server"})
    public void shouldBufferAndSignRequestBeforeSending() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.signing.unavailable.yaml")
    @Specification({
        "${app}/signing.unavailable/client"})
    public void shouldResetRequestWhenSigningUnavailable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.response.decode.text/client",
        "${net}/openai.response.decode.text/server"})
    public void shouldDecodeOpenaiResponseText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.response.decode.text.and.tool.call/client",
        "${net}/openai.response.decode.text.and.tool.call/server"})
    public void shouldDecodeOpenaiResponseTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.response.decode.refusal/client",
        "${net}/openai.response.decode.refusal/server"})
    public void shouldDecodeOpenaiResponseRefusal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.response.decode.streaming/client",
        "${net}/openai.response.decode.streaming/server"})
    public void shouldDecodeOpenaiResponseStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.response.decode.streaming.tool.call/client",
        "${net}/openai.response.decode.streaming.tool.call/server"})
    public void shouldDecodeOpenaiResponseStreamingToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.response.decode.streaming.usage/client",
        "${net}/openai.response.decode.streaming.usage/server"})
    public void shouldDecodeOpenaiResponseStreamingUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.response.decode.streaming.error/client",
        "${net}/openai.response.decode.streaming.error/server"})
    public void shouldDecodeOpenaiResponseStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.response.decode.text.100k/client",
        "${net}/openai.response.decode.text.100k/server"})
    public void shouldDecodeOpenaiResponseText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.response.decode.text.escaped/client",
        "${net}/openai.response.decode.text.escaped/server"})
    public void shouldDecodeOpenaiResponseTextEscaped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.response.decode.reasoning/client",
        "${net}/anthropic.response.decode.reasoning/server"})
    public void shouldDecodeAnthropicResponseReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.response.decode.streaming/client",
        "${net}/anthropic.response.decode.streaming/server"})
    public void shouldDecodeAnthropicResponseStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.response.decode.streaming.error/client",
        "${net}/anthropic.response.decode.streaming.error/server"})
    public void shouldDecodeAnthropicResponseStreamingError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.response.decode.streaming.tool.call/client",
        "${net}/anthropic.response.decode.streaming.tool.call/server"})
    public void shouldDecodeAnthropicResponseStreamingToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.response.decode.text/client",
        "${net}/anthropic.response.decode.text/server"})
    public void shouldDecodeAnthropicResponseText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.response.decode.text.100k/client",
        "${net}/anthropic.response.decode.text.100k/server"})
    public void shouldDecodeAnthropicResponseText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.response.decode.text.and.tool.call/client",
        "${net}/anthropic.response.decode.text.and.tool.call/server"})
    public void shouldDecodeAnthropicResponseTextAndToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.response.decode.text.escaped/client",
        "${net}/anthropic.response.decode.text.escaped/server"})
    public void shouldDecodeAnthropicResponseTextEscaped() throws Exception
    {
        k3po.finish();
    }
}
