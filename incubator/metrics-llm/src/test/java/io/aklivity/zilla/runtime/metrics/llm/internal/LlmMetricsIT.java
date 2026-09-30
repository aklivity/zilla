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
package io.aklivity.zilla.runtime.metrics.llm.internal;

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

public class LlmMetricsIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/llm/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/metrics/llm/config")
        .external("app1")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("openai.usage.yaml")
    @Specification({
        "${app}/openai.usage/client",
        "${app}/openai.usage/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordOpenaiUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("openai.streaming.usage.yaml")
    @Specification({
        "${app}/openai.streaming.usage/client",
        "${app}/openai.streaming.usage/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordOpenaiStreamingUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("anthropic.usage.yaml")
    @Specification({
        "${app}/anthropic.usage/client",
        "${app}/anthropic.usage/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordAnthropicUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("anthropic.streaming.usage.yaml")
    @Specification({
        "${app}/anthropic.streaming.usage/client",
        "${app}/anthropic.streaming.usage/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordAnthropicStreamingUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("anthropic.streaming.abort.yaml")
    @Specification({
        "${app}/anthropic.streaming.abort/client",
        "${app}/anthropic.streaming.abort/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordAnthropicStreamingAbortUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("openai.rejected.rate.limit.yaml")
    @Specification({
        "${app}/openai.rejected.rate.limit/client",
        "${app}/openai.rejected.rate.limit/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordOpenaiRejectedRateLimit() throws Exception
    {
        k3po.finish();
    }
}
