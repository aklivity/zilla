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
package io.aklivity.zilla.runtime.binding.ws.internal.streams.server;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

/**
 * RFC-6455, section 5.2 "Base Framing Protocol"
 */
public class BaseFramingIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/ws/streams/network/framing")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/ws/streams/application/framing");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/binding/ws/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Ignore("Need capability to read or write 0 length data frame at high level")
    @Configuration("server.yaml")
    @Specification({
        "${net}/echo.binary.payload.length.0/handshake.request.and.frame",
        "${app}/echo.binary.payload.length.0/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/echo.binary.payload.length.125/handshake.request.and.frame",
        "${app}/echo.binary.payload.length.125/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength125() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/echo.binary.payload.length.126/handshake.request.and.frame",
        "${app}/echo.binary.payload.length.126/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength126() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/echo.binary.payload.length.127/handshake.request.and.frame",
        "${net}/echo.binary.payload.length.127/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength127() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/echo.binary.payload.length.128/handshake.request.and.frame",
        "${net}/echo.binary.payload.length.128/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength128() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/echo.binary.payload.length.65535/handshake.request.and.frame",
        "${app}/echo.binary.payload.length.65535/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength65535() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/echo.binary.payload.length.65536/handshake.request.and.frame",
        "${net}/echo.binary.payload.length.65536/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength65536() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/echo.text.payload.length.0/handshake.request.and.frame",
        "${net}/echo.text.payload.length.0/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/echo.text.payload.length.125/handshake.request.and.frame",
        "${net}/echo.text.payload.length.125/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength125() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/echo.text.payload.length.126/handshake.request.and.frame",
        "${net}/echo.text.payload.length.126/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength126() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/echo.text.payload.length.127/handshake.request.and.frame",
        "${net}/echo.text.payload.length.127/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength127() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/echo.text.payload.length.128/handshake.request.and.frame",
        "${net}/echo.text.payload.length.128/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength128() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/echo.text.payload.length.65535/handshake.request.and.frame",
        "${net}/echo.text.payload.length.65535/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength65535() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/echo.text.payload.length.65536/handshake.request.and.frame",
        "${net}/echo.text.payload.length.65536/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength65536() throws Exception
    {
        k3po.finish();
    }
}
