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
        "${net}/sse.passthrough/client",
        "${net}/sse.passthrough/server"})
    public void shouldPassthroughSse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/opaque.fallback/client",
        "${net}/opaque.fallback/server"})
    public void shouldForwardOpaqueFallback() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/json.fragmented/client",
        "${net}/json.fragmented/server"})
    public void shouldForwardJsonFragmentedWithSingleTerminalFlush() throws Exception
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
        "${net}/client.conditional.streaming/client",
        "${net}/client.conditional.streaming/server"})
    public void shouldEncodeClientConditionalStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.conditional.nonstreaming/client",
        "${net}/client.conditional.nonstreaming/server"})
    public void shouldEncodeClientConditionalNonstreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.request/client",
        "${net}/openai.request/server"})
    public void shouldEncodeOpenAiRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.streaming/client",
        "${net}/openai.streaming/server"})
    public void shouldEncodeOpenAiStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.nonstreaming/client",
        "${net}/openai.nonstreaming/server"})
    public void shouldEncodeOpenAiNonstreaming() throws Exception
    {
        k3po.finish();
    }
}
