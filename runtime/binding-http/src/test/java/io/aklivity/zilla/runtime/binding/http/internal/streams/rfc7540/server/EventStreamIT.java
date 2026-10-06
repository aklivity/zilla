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
package io.aklivity.zilla.runtime.binding.http.internal.streams.rfc7540.server;

import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfiguration.HTTP_CONCURRENT_STREAMS;
import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfigurationTest.HTTP_SSE_INITIAL_COMMENT_ENABLED_NAME;
import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfigurationTest.HTTP_SSE_MAXIMUM_IDLE_TIME_NAME;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_BUFFER_SLOT_CAPACITY_NAME;
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
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7540/event.stream")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/http/streams/application/event.stream");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(HTTP_CONCURRENT_STREAMS, 100)
        .configurationRoot("io/aklivity/zilla/specs/binding/http/config/v2")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.sse.framing.event/client",
        "${app}/response.sse.framing.event/server" })
    public void shouldReceiveResponseEvent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.sse.framing.data.non.empty/client",
        "${app}/response.sse.framing.data.non.empty/server" })
    public void shouldReceiveResponseDataNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.sse.framing.data.multi.line/client",
        "${app}/response.sse.framing.data.multi.line/server" })
    public void shouldReceiveResponseDataMultiLine() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.sse.framing.id.only/client",
        "${app}/response.sse.framing.id.only/server" })
    public void shouldReceiveResponseIdOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Configure(name = HTTP_SSE_INITIAL_COMMENT_ENABLED_NAME, value = "true")
    @Specification({
        "${net}/response.sse.framing.comment.initial/client",
        "${app}/response.sse.framing.comment.initial/server" })
    public void shouldReceiveResponseCommentInitial() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Configure(name = HTTP_SSE_MAXIMUM_IDLE_TIME_NAME, value = "1")
    @Specification({
        "${net}/response.sse.framing.comment.idle/client",
        "${app}/response.sse.framing.comment.idle/server" })
    public void shouldReceiveResponseCommentIdle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.sse.model.yaml")
    @Specification({
        "${net}/response.sse.framing.data.invalid.reset/client",
        "${app}/response.sse.framing.data.invalid.reset/server" })
    public void shouldResetInvalidEventData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Configure(name = HTTP_SSE_INITIAL_COMMENT_ENABLED_NAME, value = "true")
    @Specification({
        "${net}/response.sse.framing.comment.initial.budget/client",
        "${app}/response.sse.framing.comment.initial/server" })
    public void shouldReceiveResponseCommentInitialBudget() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Configure(name = ENGINE_BUFFER_SLOT_CAPACITY_NAME, value = "65536")
    @Specification({
        "${net}/response.sse.framing.data.large.frames/client",
        "${app}/response.sse.framing.data.large.frames/server" })
    public void shouldReceiveResponseDataLargeFrames() throws Exception
    {
        k3po.finish();
    }
}
