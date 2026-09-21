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
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.streaming/client",
        "${net}/anthropic.streaming/server"})
    public void shouldForwardAnthropicStreamingSameDialect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.streaming.usage/client",
        "${net}/anthropic.streaming.usage/server"})
    public void shouldForwardAnthropicStreamingUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.streaming.abort/client",
        "${net}/anthropic.streaming.abort/server"})
    public void shouldAbortAnthropicStreamingWithPartialUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.usage/client",
        "${net}/anthropic.usage/server"})
    public void shouldForwardAnthropicNonstreamingUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic/client",
        "${net}/anthropic/server"})
    public void shouldForwardAnthropicNonstreamingSameDialect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.streaming.transformed/client",
        "${net}/anthropic.streaming.transformed/server"})
    @ScriptProperty({ "model \"gpt-4\"", "id \"chatcmpl_1\"" })
    public void shouldTransformOpenaiToAnthropicStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.streaming.transformed.100k/client",
        "${net}/anthropic.streaming.transformed.100k/server"})
    @ScriptProperty({ "model \"claude-3-opus-20240229\"", "id \"msg_01\"" })
    public void shouldTransformOpenaiToAnthropicStreaming100k() throws Exception
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
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.transformed/client",
        "${net}/anthropic.transformed/server"})
    @ScriptProperty({ "model \"gpt-4\"", "id \"chatcmpl_2\"" })
    public void shouldTransformOpenaiToAnthropicNonstreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.transformed.100k/client",
        "${net}/anthropic.transformed.100k/server"})
    @ScriptProperty({ "model \"gpt-4\"", "id \"chatcmpl_2\"" })
    public void shouldTransformOpenaiToAnthropic100k() throws Exception
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
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.transformed.tool.only/client",
        "${net}/anthropic.transformed.tool.only/server"})
    @ScriptProperty({ "model \"gpt-4\"", "id \"chatcmpl_2\"" })
    public void shouldTransformOpenaiToAnthropicToolOnly() throws Exception
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
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/openai.transformed.multi.tool/client",
        "${net}/anthropic.transformed.multi.tool/server"})
    @ScriptProperty({ "model \"gpt-4\"", "id \"chatcmpl_2\"" })
    public void shouldTransformOpenaiToAnthropicMultiTool() throws Exception
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
    @Configuration("client.anthropic.guarded.yaml")
    @Specification({
        "${app}/anthropic.authorized/client",
        "${net}/anthropic.authorized/server"})
    public void shouldForwardAnthropicRequestWithAuthorizedCredentials() throws Exception
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
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.10k/client",
        "${net}/anthropic.10k/server"})
    public void shouldForwardAnthropic10k() throws Exception
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
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.100k/client",
        "${net}/anthropic.100k/server"})
    public void shouldForwardAnthropic100k() throws Exception
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
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.streaming.10k/client",
        "${net}/anthropic.streaming.10k/server"})
    public void shouldForwardAnthropicStreaming10k() throws Exception
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
    @Configuration("client.anthropic.yaml")
    @Specification({
        "${app}/anthropic.streaming.100k/client",
        "${net}/anthropic.streaming.100k/server"})
    public void shouldForwardAnthropicStreaming100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.signed.yaml")
    @Specification({
        "${app}/buffer.and.sign/client",
        "${net}/buffer.and.sign/server"})
    public void shouldBufferAndSignRequestBeforeSending() throws Exception
    {
        k3po.finish();
    }
}
