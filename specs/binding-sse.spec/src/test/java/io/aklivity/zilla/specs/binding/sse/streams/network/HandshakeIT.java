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
package io.aklivity.zilla.specs.binding.sse.streams.network;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class HandshakeIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/sse/streams/network/handshake");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/connection.succeeded/request",
        "${net}/connection.succeeded/response" })
    public void shouldHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connection.succeeded.with.request.parameter/request",
        "${net}/connection.succeeded.with.request.parameter/response" })
    public void shouldHandshakeWithRequestParameter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connection.closed/request",
        "${net}/connection.closed/response" })
    public void shouldHandshakeThenClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connection.closed.deferred/request",
        "${net}/connection.closed.deferred/response" })
    public void shouldHandshakeThenCloseDeferred() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.method.unsupported/request",
        "${net}/request.method.unsupported/response" })
    public void shouldFailHandshakeWhenRequestMethodUnsupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.header.last.event.id/request",
        "${net}/request.header.last.event.id/response" })
    public void shouldHandshakeWithRequestHeaderLastEventId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.header.last.event.id.empty/request",
        "${net}/request.header.last.event.id.empty/response" })
    public void shouldHandshakeWithRequestHeaderLastEventIdEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.header.last.event.id.overflow/request",
        "${net}/request.header.last.event.id.overflow/response" })
    public void shouldFailHandshakeWithRequestHeaderLastEventIdOverflow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.header.last.event.id.overflow.multibyte/request",
        "${net}/request.header.last.event.id.overflow.multibyte/response" })
    public void shouldFailHandshakeWithRequestHeaderLastEventIdOverflowMultibyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/initial.comment/request",
        "${net}/initial.comment/response" })
    public void shouldSendInitialComment() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.parameter.last.event.id/request",
        "${net}/request.parameter.last.event.id/response" })
    public void shouldHandshakeWithRequestParameterLastEventId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.parameter.last.event.id.empty/request",
        "${net}/request.parameter.last.event.id.empty/response" })
    public void shouldHandshakeWithRequestParameterLastEventIdEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.parameter.last.event.id.overflow/request",
        "${net}/request.parameter.last.event.id.overflow/response" })
    public void shouldFailHandshakeWithRequestParameterLastEventIdOverflow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.parameter.last.event.id.url.encoded/request",
        "${net}/request.parameter.last.event.id.url.encoded/response" })
    public void shouldHandshakeWithURLEncodedRequestParameterLastEventId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.header.content.type.missing/request",
        "${net}/response.header.content.type.missing/response" })
    public void shouldFailHandshakeWhenResponseHeaderContentTypeMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.header.content.type.unsupported/request",
        "${net}/response.header.content.type.unsupported/response" })
    public void shouldFailHandshakeWhenResponseHeaderContentTypeUnsupported() throws Exception
    {
        k3po.finish();
    }
}
