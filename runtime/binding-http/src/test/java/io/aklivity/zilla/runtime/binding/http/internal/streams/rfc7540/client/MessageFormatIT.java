/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.http.internal.streams.rfc7540.client;

import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfiguration.HTTP_SERVER_CONCURRENT_STREAMS;
import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfigurationTest.HTTP_CLIENT_MAX_FRAME_SIZE_NAME;
import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfigurationTest.HTTP_STREAM_INITIAL_WINDOW_NAME;
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
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class MessageFormatIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/http/streams/application/rfc7540/message.format")
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7540/message.format");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(HTTP_SERVER_CONCURRENT_STREAMS, 100)
        .configurationRoot("io/aklivity/zilla/specs/binding/http/config/v2")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/server.continuation.frames/client",
        "${net}/server.continuation.frames/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void continuationFrames() throws Exception
    {
        k3po.finish();
    }

    @Ignore("Implement dynamic header list")
    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/dynamic.table.requests/client",
        "${net}/dynamic.table.requests/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void dynamicTableRequests() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/max.frame.size/client",
        "${net}/max.frame.size/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "29000")
    @Configure(name = HTTP_CLIENT_MAX_FRAME_SIZE_NAME, value = "20000")
    public void maxFrameSize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/client.frame.error/client",
        "${net}/client.max.frame.size.error/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void maxFrameSizeError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/client.frame.error/client",
        "${net}/client.ping.frame.size.error/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void pingFrameSizeError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/client.frame.error/client",
        "${net}/client.connection.window.frame.size.error/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void connectionWindowFrameSizeError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/client.frame.error/client",
        "${net}/client.window.frame.size.error/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void windowFrameSizeError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/client.rst.stream.frame.size.error/client",
        "${net}/client.rst.stream.frame.size.error/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void rstStreamFrameSizeError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/client.priority.frame.size.error/client",
        "${net}/client.priority.frame.size.error/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void priorityFrameSizeError() throws Exception
    {
        k3po.finish();
    }

    @Ignore("Implement Connection header")
    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/connection.headers/client",
        "${net}/connection.headers/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void connectionHeaders() throws Exception
    {
        k3po.finish();
    }

    @Ignore("Implement push promise")
    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/stream.id.order/client",
        "${net}/stream.id.order/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void streamIdOrder() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/client.frame.error/client",
        "${net}/client.invalid.hpack.index/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void invalidHpackIndex() throws Exception
    {
        k3po.finish();
    }
}
