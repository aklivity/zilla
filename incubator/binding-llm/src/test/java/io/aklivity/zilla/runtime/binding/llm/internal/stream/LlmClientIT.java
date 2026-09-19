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
    @Configuration("client.opaque.yaml")
    @Specification({
        "${app}/opaque.fallback/client",
        "${net}/opaque.fallback/server"})
    public void shouldForwardClientOpaqueFallback() throws Exception
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
        "${app}/openai.nonstreaming/client",
        "${net}/openai.nonstreaming/server"})
    public void shouldForwardOpenaiNonstreamingSameDialect() throws Exception
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
        "${app}/anthropic.nonstreaming/client",
        "${net}/anthropic.nonstreaming/server"})
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
        "${app}/openai.nonstreaming.transformed/client",
        "${net}/anthropic.nonstreaming.transformed/server"})
    @ScriptProperty({ "model \"gpt-4\"", "id \"chatcmpl_2\"" })
    public void shouldTransformOpenaiToAnthropicNonstreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/anthropic.nonstreaming.transformed/client",
        "${net}/openai.nonstreaming.transformed/server"})
    @ScriptProperty({ "model \"claude-3-opus-20240229\"", "id \"msg_01\"" })
    public void shouldTransformAnthropicToOpenaiNonstreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.guarded.yaml")
    @Specification({
        "${app}/openai.request.guarded/client",
        "${net}/openai.request.guarded.forwarded/server"})
    public void shouldForwardOpenaiRequestWithForwardedCredentials() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.anthropic.guarded.yaml")
    @Specification({
        "${app}/anthropic.request.guarded/client",
        "${net}/anthropic.request.guarded.forwarded/server"})
    public void shouldForwardAnthropicRequestWithForwardedCredentials() throws Exception
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

    // openai.100k/anthropic.100k hang on the response-decode path: LlmClientFactory.decodeJsonContent() calls
    // responsePipeline.transform() exactly once and never retries on Status.SUSPENDED, unlike the working
    // retry loop in this same file's transformNativeStreamEvent(). Tracked for a fix; the two tests above
    // remain red until then.

    // Cross-dialect request transformation for content this large hits a separate, tracked bug
    // (aklivity/zilla#2597): the request forwards verbatim, untransformed, once content spans multiple
    // incremental transform() calls. Add shouldTransformAnthropicToOpenai10k/shouldTransformOpenaiToAnthropic10k
    // back once that is fixed.
}
