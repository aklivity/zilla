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
    @Configuration("client.yaml")
    @Specification({
        "${app}/same.dialect/client",
        "${net}/same.dialect/server"})
    public void shouldForwardSameDialect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/cross.dialect/client",
        "${net}/cross.dialect/server"})
    public void shouldForwardCrossDialect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.opaque.yaml")
    @Specification({
        "${app}/client.opaque.fallback/client",
        "${net}/client.opaque.fallback/server"})
    public void shouldForwardClientOpaqueFallback() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/client.abort/client",
        "${net}/client.abort/server"})
    public void shouldAbortClientRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.conditional.yaml")
    @Specification({
        "${app}/client.conditional.streaming/client",
        "${net}/client.conditional.streaming/server"})
    public void shouldSelectStreamingDecoderFromRequestBody() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.conditional.yaml")
    @Specification({
        "${app}/client.conditional.nonstreaming/client",
        "${net}/client.conditional.nonstreaming/server"})
    public void shouldSelectNonstreamingDecoderFromRequestBody() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.streaming/client",
        "${net}/openai.streaming/server"})
    public void shouldForwardOpenAiStreamingSameDialect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.openai.yaml")
    @Specification({
        "${app}/openai.nonstreaming/client",
        "${net}/openai.nonstreaming/server"})
    public void shouldForwardOpenAiNonstreamingSameDialect() throws Exception
    {
        k3po.finish();
    }
}
