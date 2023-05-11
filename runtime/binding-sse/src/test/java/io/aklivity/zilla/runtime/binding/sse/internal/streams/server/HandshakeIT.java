/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.sse.internal.streams.server;

import static io.aklivity.zilla.runtime.binding.sse.internal.SseConfigurationTest.SSE_INITIAL_COMMENT_ENABLED_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class HandshakeIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/sse/streams/network/handshake")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/sse/streams/application/handshake");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/binding/sse/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.when.yaml")
    @Specification({
        "${net}/connection.succeeded/request",
        "${app}/connection.succeeded/server" })
    public void shouldHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.yaml")
    @Specification({
        "${net}/connection.succeeded.with.request.parameter/request",
        "${app}/connection.succeeded/server" })
    public void shouldHandshakeWithRequestParameter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.yaml")
    @Specification({
        "${net}/connection.closed/request",
        "${app}/connection.closed/server" })
    public void shouldHandshakeThenClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.yaml")
    @Specification({
        "${net}/connection.closed.deferred/request",
        "${app}/connection.closed.deferred/server" })
    public void shouldHandshakeThenCloseDeferred() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.yaml")
    @Specification({
        "${net}/request.method.unsupported/request" })
    public void shouldFailHandshakeWhenRequestMethodUnsupported() throws Exception
    {
        k3po.finish();
    }

    @Configure(name = SSE_INITIAL_COMMENT_ENABLED_NAME, value = "true")
    @Test
    @Configuration("server.when.yaml")
    @Specification({
        "${net}/initial.comment/request",
        "${app}/last.event.id/server" })
    public void shouldSendInitialComment() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.yaml")
    @Specification({
        "${net}/request.header.last.event.id/request",
        "${app}/last.event.id/server" })
    public void shouldHandshakeWithRequestHeaderLastEventId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.yaml")
    @Specification({
        "${net}/request.header.last.event.id.empty/request",
        "${app}/last.event.id.empty/server" })
    public void shouldHandshakeWithRequestHeaderLastEventIdEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.yaml")
    @Specification({
        "${net}/request.header.last.event.id.overflow/request" })
    public void shouldFailHandshakeWithRequestHeaderLastEventIdOverflow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.yaml")
    @Specification({
        "${net}/request.header.last.event.id.overflow.multibyte/request" })
    public void shouldFailHandshakeWithRequestHeaderLastEventIdOverflowMultibyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.yaml")
    @Specification({
        "${net}/request.parameter.last.event.id.empty/request",
        "${app}/last.event.id.empty/server" })
    public void shouldHandshakeWithRequestParameterLastEventIdEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.yaml")
    @Specification({
        "${net}/request.parameter.last.event.id/request",
        "${app}/last.event.id/server" })
    public void shouldHandshakeWithRequestParameterLastEventId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.yaml")
    @Specification({
        "${net}/request.parameter.last.event.id.overflow/request" })
    public void shouldFailHandshakeWithRequestParameterLastEventIdOverflow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.yaml")
    @Specification({
        "${net}/request.parameter.last.event.id.url.encoded/request",
        "${app}/last.event.id/server" })
    public void shouldHandshakeWithURLEncodedRequestParameterLastEventId() throws Exception
    {
        k3po.finish();
    }
}
