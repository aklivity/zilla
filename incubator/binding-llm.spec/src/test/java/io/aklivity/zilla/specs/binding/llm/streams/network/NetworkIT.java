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
        "${net}/anthropic.invalid/client",
        "${net}/anthropic.invalid/server"})
    public void shouldRejectInvalidAnthropicRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.unknown.dialect/client",
        "${net}/reject.unknown.dialect/server"})
    public void shouldRejectRequestWithUnresolvedDialect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.response.invalid.content.type/client",
        "${net}/openai.response.invalid.content.type/server"})
    public void shouldEncodeResponseWithInvalidContentType() throws Exception
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
        "${net}/openai.echo/client",
        "${net}/openai.echo/server"})
    public void shouldEncodeOpenaiRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.echo/client",
        "${net}/anthropic.echo/server"})
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
        "${net}/openai/client",
        "${net}/openai/server"})
    public void shouldEncodeOpenaiNonstreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.custom.base.path/client",
        "${net}/openai.custom.base.path/server"})
    public void shouldEncodeOpenaiUnderCustomBasePath() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.invalid/client",
        "${net}/openai.invalid/server"})
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
        "${net}/anthropic/client",
        "${net}/anthropic/server"})
    public void shouldEncodeAnthropicNonstreaming() throws Exception
    {
        k3po.finish();
    }

    // network/openai.authorized and network/anthropic.authorized are exercised as two
    // independent, cross-directory pairs -- client.rpt (an already-authenticated caller) by LlmServerIT,
    // server.rpt (asserts the guard-resolved zilla:authorization arrived) by LlmClientIT -- not self-paired
    // here, since a plain k3po connect never carries an authorization value the accept side would resolve.

    @Test
    @Specification({
        "${net}/openai.rejected.authorization/client",
        "${net}/openai.rejected.authorization/server"})
    public void shouldRejectOpenaiRequestFailingAuthorization() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.rejected.authorization/client",
        "${net}/anthropic.rejected.authorization/server"})
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
        "${net}/openai.streaming.10k/client",
        "${net}/openai.streaming.10k/server"})
    public void shouldEncodeOpenaiStreaming10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.streaming.10k/client",
        "${net}/anthropic.streaming.10k/server"})
    public void shouldEncodeAnthropicStreaming10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.streaming.100k/client",
        "${net}/openai.streaming.100k/server"})
    public void shouldEncodeOpenaiStreaming100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.streaming.100k/client",
        "${net}/anthropic.streaming.100k/server"})
    public void shouldEncodeAnthropicStreaming100k() throws Exception
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
        "${net}/openai.transformed/client",
        "${net}/openai.transformed/server"})
    public void shouldEncodeOpenaiNonstreamingTransformed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.transformed/client",
        "${net}/anthropic.transformed/server"})
    public void shouldEncodeAnthropicNonstreamingTransformed() throws Exception
    {
        k3po.finish();
    }
}
