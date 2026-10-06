/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.http.internal.streams.eventstream.server;

import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfigurationTest.HTTP_SSE_INITIAL_COMMENT_ENABLED_NAME;
import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfigurationTest.HTTP_SSE_MAXIMUM_IDLE_TIME_NAME;
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
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class EventStreamIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/event.stream")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/http/streams/application/event.stream");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/http/config/v1.1")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.sse.event/client",
        "${app}/response.sse.event/server" })
    public void shouldReceiveResponseEvent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.sse.data.non.empty/client",
        "${app}/response.sse.data.non.empty/server" })
    public void shouldReceiveResponseDataNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.sse.data.empty/client",
        "${app}/response.sse.data.empty/server" })
    public void shouldReceiveResponseDataEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.sse.data.multi.line/client",
        "${app}/response.sse.data.multi.line/server" })
    public void shouldReceiveResponseDataMultiLine() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.sse.data.multiple/client",
        "${app}/response.sse.data.multiple/server" })
    public void shouldReceiveResponseDataMultiple() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.sse.data.fragmented.10k/client",
        "${app}/response.sse.data.fragmented.10k/server" })
    public void shouldReceiveResponseDataFragmented10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.sse.data.fragmented.100k/client",
        "${app}/response.sse.data.fragmented.100k/server" })
    public void shouldReceiveResponseDataFragmented100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.sse.id.non.empty/client",
        "${app}/response.sse.id.non.empty/server" })
    public void shouldReceiveResponseIdNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.sse.id.empty/client",
        "${app}/response.sse.id.empty/server" })
    public void shouldReceiveResponseIdEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.sse.type.non.empty/client",
        "${app}/response.sse.type.non.empty/server" })
    public void shouldReceiveResponseTypeNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.sse.type.empty/client",
        "${app}/response.sse.type.empty/server" })
    public void shouldReceiveResponseTypeEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.sse.type.fragmented/client",
        "${app}/response.sse.type.fragmented/server" })
    public void shouldReceiveResponseTypeFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.sse.no.framing/client",
        "${app}/response.sse.no.framing/server" })
    public void shouldReceiveSseResponseWithoutFraming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.sse.id.only/client",
        "${app}/response.sse.id.only/server" })
    public void shouldReceiveResponseIdOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.sse.retry.numeric/client",
        "${app}/response.sse.retry.numeric/server" })
    public void shouldReceiveResponseRetryNumeric() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.sse.retry.non.empty/client",
        "${app}/response.sse.retry.non.empty/server" })
    public void shouldReceiveResponseRetryNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.sse.comment.initial/client",
        "${app}/response.sse.comment.initial/server" })
    @Configure(name = HTTP_SSE_INITIAL_COMMENT_ENABLED_NAME, value = "true")
    public void shouldReceiveResponseCommentInitial() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.sse.comment.idle/client",
        "${app}/response.sse.comment.idle/server" })
    @Configure(name = HTTP_SSE_MAXIMUM_IDLE_TIME_NAME, value = "1")
    public void shouldReceiveResponseCommentIdle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.authorization.credentials.yaml")
    @Specification({
        "${net}/response.sse.challenge/client",
        "${app}/response.sse.challenge/server" })
    public void shouldChallengeResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.sse.model.yaml")
    @Specification({
        "${net}/response.sse.data.valid/client",
        "${app}/response.sse.data.valid/server" })
    public void shouldReceiveResponseDataValid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.sse.model.yaml")
    @Specification({
        "${net}/response.sse.data.invalid.reset/client",
        "${app}/response.sse.data.invalid.reset/server" })
    public void shouldResetInvalidEventData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Configure(name = HTTP_SSE_INITIAL_COMMENT_ENABLED_NAME, value = "true")
    @Specification({
        "${net}/response.sse.comment.initial.budget/client",
        "${app}/response.sse.comment.initial.budget/server" })
    public void shouldReceiveResponseCommentInitialBudget() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.sse.data.null.budget/client",
        "${app}/response.sse.data.null.budget/server" })
    public void shouldReceiveResponseDataNullBudget() throws Exception
    {
        k3po.finish();
    }
}
