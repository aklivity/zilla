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
package io.aklivity.zilla.specs.binding.llm.streams.application;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class ApplicationIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/llm/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/openai.response.invalid.content.type/client",
        "${app}/openai.response.invalid.content.type/server"})
    public void shouldAbortResponseWithInvalidContentType() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/abort/client",
        "${app}/abort/server"})
    public void shouldAbortClientRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.echo/client",
        "${app}/openai.echo/server"})
    public void shouldForwardOpenaiRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.echo/client",
        "${app}/anthropic.echo/server"})
    public void shouldForwardAnthropicRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.streaming/client",
        "${app}/openai.streaming/server"})
    public void shouldForwardOpenaiStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai/client",
        "${app}/openai/server"})
    public void shouldForwardOpenaiNonstreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.response.invalid/client",
        "${app}/openai.response.invalid/server"})
    public void shouldAbortInvalidOpenaiResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.streaming/client",
        "${app}/anthropic.streaming/server"})
    public void shouldForwardAnthropicStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic/client",
        "${app}/anthropic/server"})
    public void shouldForwardAnthropicNonstreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.authorized/client",
        "${app}/openai.authorized/server"})
    public void shouldForwardOpenaiRequestAuthorized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.authorized/client",
        "${app}/anthropic.authorized/server"})
    public void shouldForwardAnthropicRequestAuthorized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.10k/client",
        "${app}/openai.10k/server"})
    public void shouldForwardOpenai10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.10k/client",
        "${app}/anthropic.10k/server"})
    public void shouldForwardAnthropic10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.100k/client",
        "${app}/openai.100k/server"})
    public void shouldForwardOpenai100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.100k/client",
        "${app}/anthropic.100k/server"})
    public void shouldForwardAnthropic100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.streaming.10k/client",
        "${app}/openai.streaming.10k/server"})
    public void shouldForwardOpenaiStreaming10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.streaming.10k/client",
        "${app}/anthropic.streaming.10k/server"})
    public void shouldForwardAnthropicStreaming10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.streaming.100k/client",
        "${app}/openai.streaming.100k/server"})
    public void shouldForwardOpenaiStreaming100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.streaming.100k/client",
        "${app}/anthropic.streaming.100k/server"})
    public void shouldForwardAnthropicStreaming100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.streaming.transformed/client",
        "${app}/openai.streaming.transformed/server"})
    public void shouldForwardOpenaiStreamingTransformed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.streaming.transformed/client",
        "${app}/anthropic.streaming.transformed/server"})
    public void shouldForwardAnthropicStreamingTransformed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transformed/client",
        "${app}/openai.transformed/server"})
    public void shouldForwardOpenaiNonstreamingTransformed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transformed/client",
        "${app}/anthropic.transformed/server"})
    public void shouldForwardAnthropicNonstreamingTransformed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transformed.100k/client",
        "${app}/openai.transformed.100k/server"})
    public void shouldForwardOpenaiNonstreamingTransformed100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transformed.100k/client",
        "${app}/anthropic.transformed.100k/server"})
    public void shouldForwardAnthropicNonstreamingTransformed100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transformed.tool.only/client",
        "${app}/openai.transformed.tool.only/server"})
    public void shouldForwardOpenaiNonstreamingTransformedToolOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/anthropic.transformed.tool.only/client",
        "${app}/anthropic.transformed.tool.only/server"})
    public void shouldForwardAnthropicNonstreamingTransformedToolOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/openai.transformed.multi.tool/client",
        "${app}/openai.transformed.multi.tool/server"})
    public void shouldForwardOpenaiNonstreamingTransformedMultiTool() throws Exception
    {
        k3po.finish();
    }
}
