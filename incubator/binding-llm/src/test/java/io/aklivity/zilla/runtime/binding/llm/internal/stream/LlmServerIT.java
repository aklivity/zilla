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

public class LlmServerIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/llm/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/llm/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/llm/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/request.valid/client",
        "${app}/request.valid/server"})
    public void shouldForwardValidRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/request.valid.10k/client",
        "${app}/request.valid.10k/server"})
    public void shouldForwardValidRequest10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/request.valid.100k/client",
        "${app}/request.valid.100k/server"})
    public void shouldForwardValidRequest100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/request.rejected.schema/client"})
    public void shouldRejectRequestFailingSchema() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/request.rejected.dialect/client"})
    public void shouldRejectRequestWithUnresolvedDialect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.valid.10k/client",
        "${app}/response.valid.10k/server"})
    public void shouldForwardValidResponse10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.valid.100k/client",
        "${app}/response.valid.100k/server"})
    public void shouldForwardValidResponse100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/request.aborted/client",
        "${app}/request.aborted/server"})
    public void shouldRequestAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.request/client",
        "${app}/openai.request/server"})
    public void shouldDetectOpenaiDialectFromPath() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.request/client",
        "${app}/anthropic.request/server"})
    public void shouldDetectAnthropicDialectFromPath() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.request.invalid/client"})
    public void shouldRejectInvalidOpenaiRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.streaming/client",
        "${app}/openai.streaming/server"})
    public void shouldForwardOpenaiStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/openai.nonstreaming/client",
        "${app}/openai.nonstreaming/server"})
    public void shouldForwardOpenaiNonstreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.request/client",
        "${app}/anthropic.request/server"})
    public void shouldDetectAnthropicDialectFromPath() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.streaming/client",
        "${app}/anthropic.streaming/server"})
    public void shouldForwardAnthropicStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/anthropic.nonstreaming/client",
        "${app}/anthropic.nonstreaming/server"})
    public void shouldForwardAnthropicNonstreaming() throws Exception
    {
        k3po.finish();
    }
}
