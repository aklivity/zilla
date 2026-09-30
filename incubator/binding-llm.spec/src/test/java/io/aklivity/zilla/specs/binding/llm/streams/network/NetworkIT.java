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
        "${net}/request.valid/client",
        "${net}/request.valid/server"})
    public void shouldForwardValidRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.valid.10k/client",
        "${net}/request.valid.10k/server"})
    public void shouldForwardValidRequest10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.valid.100k/client",
        "${net}/request.valid.100k/server"})
    public void shouldForwardValidRequest100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.valid.10k/client",
        "${net}/response.valid.10k/server"})
    public void shouldForwardValidResponse10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.valid.100k/client",
        "${net}/response.valid.100k/server"})
    public void shouldForwardValidResponse100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.rejected.schema/client",
        "${net}/request.rejected.schema/server"})
    public void shouldRejectRequestFailingSchema() throws Exception
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
        "${net}/request.aborted/client",
        "${net}/request.aborted/server"})
    public void shouldRequestAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/same.dialect/client",
        "${net}/same.dialect/server"})
    public void shouldEncodeSameDialect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/cross.dialect/client",
        "${net}/cross.dialect/server"})
    public void shouldEncodeCrossDialect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.opaque.fallback/client",
        "${net}/client.opaque.fallback/server"})
    public void shouldForwardClientOpaqueFallback() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.abort/client",
        "${net}/client.abort/server"})
    public void shouldAbortClientRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.request/client",
        "${net}/openai.request/server"})
    public void shouldEncodeOpenaiRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.request/client",
        "${net}/anthropic.request/server"})
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
        "${net}/anthropic.response.invalid/client",
        "${net}/anthropic.response.invalid/server"})
    public void shouldEncodeAnthropicResponseWithMismatchedEventType() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.response.valid/client",
        "${net}/anthropic.response.valid/server"})
    public void shouldEncodeAnthropicResponseWithMatchingEventType() throws Exception
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
}
