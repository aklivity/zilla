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
package io.aklivity.zilla.specs.binding.llm.streams.network;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class NetworkIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/llm/streams/network");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/anthropic.request.invalid/client",
        "${net}/anthropic.request.invalid/server"})
    public void shouldRejectInvalidAnthropicRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.rejected.dialect/client",
        "${net}/request.rejected.dialect/server"})
    public void shouldRejectRequestWithUnresolvedDialect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/opaque.fallback/client",
        "${net}/opaque.fallback/server"})
    public void shouldForwardClientOpaqueFallback() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/abort/client",
        "${net}/abort/server"})
    public void shouldAbortClientRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.request.echo/client",
        "${net}/openai.request.echo/server"})
    public void shouldEncodeOpenaiRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.request.echo/client",
        "${net}/anthropic.request.echo/server"})
    public void shouldEncodeAnthropicRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.streaming/client",
        "${net}/openai.streaming/server"})
    public void shouldEncodeOpenaiStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.nonstreaming/client",
        "${net}/openai.nonstreaming/server"})
    public void shouldEncodeOpenaiNonstreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.request.invalid/client",
        "${net}/openai.request.invalid/server"})
    public void shouldRejectInvalidOpenaiRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.response.invalid/client",
        "${net}/openai.response.invalid/server"})
    public void shouldEncodeInvalidOpenaiResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.streaming/client",
        "${net}/anthropic.streaming/server"})
    public void shouldEncodeAnthropicStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.nonstreaming/client",
        "${net}/anthropic.nonstreaming/server"})
    public void shouldEncodeAnthropicNonstreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.request.guarded/client",
        "${net}/openai.request.guarded/server"})
    public void shouldEncodeOpenaiRequestGuarded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.request.guarded/client",
        "${net}/anthropic.request.guarded/server"})
    public void shouldEncodeAnthropicRequestGuarded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.request.rejected.authorization/client",
        "${net}/openai.request.rejected.authorization/server"})
    public void shouldRejectOpenaiRequestFailingAuthorization() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.request.rejected.authorization/client",
        "${net}/anthropic.request.rejected.authorization/server"})
    public void shouldRejectAnthropicRequestFailingAuthorization() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.10k/client",
        "${net}/openai.10k/server"})
    public void shouldEncodeOpenai10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.10k/client",
        "${net}/anthropic.10k/server"})
    public void shouldEncodeAnthropic10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.100k/client",
        "${net}/openai.100k/server"})
    public void shouldEncodeOpenai100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.100k/client",
        "${net}/anthropic.100k/server"})
    public void shouldEncodeAnthropic100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.streaming.transformed/client",
        "${net}/openai.streaming.transformed/server"})
    public void shouldEncodeOpenaiStreamingTransformed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.streaming.transformed/client",
        "${net}/anthropic.streaming.transformed/server"})
    public void shouldEncodeAnthropicStreamingTransformed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.nonstreaming.transformed/client",
        "${net}/openai.nonstreaming.transformed/server"})
    public void shouldEncodeOpenaiNonstreamingTransformed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.nonstreaming.transformed/client",
        "${net}/anthropic.nonstreaming.transformed/server"})
    public void shouldEncodeAnthropicNonstreamingTransformed() throws Exception
    {
        k3po.finish();
    }
}
