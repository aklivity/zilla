/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.ws.internal.streams.client;

import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_BUFFER_SLOT_CAPACITY_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Ignore;
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

/**
 * RFC-6455, section 5.2 "Base Framing Protocol"
 */
public class BaseFramingIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/ws/streams/application/framing")
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/ws/streams/network/framing");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/binding/ws/config")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Ignore("Need capability to read or write 0 length data frame at high level")
    @Configuration("client.yaml")
    @Specification({
        "${app}/echo.binary.payload.length.0/handshake.request.and.frame",
        "${net}/echo.binary.payload.length.0/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/echo.binary.payload.length.125/handshake.request.and.frame",
        "${net}/echo.binary.payload.length.125/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength125() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/echo.binary.payload.length.126/handshake.request.and.frame",
        "${net}/echo.binary.payload.length.126/handshake.response.and.frame" })
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
    @Configuration("client.yaml")
    @Specification({
        "${app}/echo.binary.payload.length.65535/handshake.request.and.frame",
        "${net}/echo.binary.payload.length.65535/handshake.response.and.frame" })
    @Configure(name = ENGINE_BUFFER_SLOT_CAPACITY_NAME, value = "65536")
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
