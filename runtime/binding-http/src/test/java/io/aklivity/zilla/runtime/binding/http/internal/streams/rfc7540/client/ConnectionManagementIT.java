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

import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfiguration.HTTP_CONCURRENT_STREAMS;
import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfigurationTest.HTTP_STREAM_INITIAL_WINDOW_NAME;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.ScriptProperty;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class ConnectionManagementIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/http/streams/application/rfc7540/connection.management")
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7540/connection.management");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(HTTP_CONCURRENT_STREAMS, 100)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/binding/http/config/v2")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/http.get.exchange/client",
        "${net}/http.get.exchange/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void httpGetExchange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.override.yaml")
    @Specification({
        "${app}/http.get.exchange.with.header.override/client",
        "${net}/http.get.exchange.with.header.override/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void httpGetExchangeWithHeaderOverride() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/http.post.exchange/client",
        "${net}/http.post.exchange/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void httpPostExchange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/http.post.exchange.streaming/client",
        "${net}/http.post.exchange.streaming/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void httpPostExchangeWhenStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connection.has.two.streams/client",
        "${net}/connection.has.two.streams/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void connectionHasTwoStreams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/multiple.data.frames/client",
        "${net}/multiple.data.frames/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void multipleDataFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/client.sent.write.abort.on.closed.request/client",
        "${net}/client.sent.rst.stream.on.closed.request/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void clientSentRstStreamOnClosedRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/ignore.server.rst.stream/client",
        "${net}/ignore.server.rst.stream/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void ignoreServerRstStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/client.sent.read.abort.on.closed.request/client",
        "${net}/client.sent.rst.stream.on.closed.request/server"
    })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void clientSentReadAbortOnClosedRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/client.sent.write.abort.on.open.request/client",
        "${net}/client.sent.rst.stream.on.open.request.response/server"
    })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void clientSentWriteAbortOnOpenRequestResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/client.sent.write.abort.on.closed.request/client",
        "${net}/client.sent.rst.stream.on.closed.request/server"
    })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void clientSentWriteAbortOnClosedRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/client.sent.write.close/client",
        "${net}/client.sent.rst.stream.on.open.request.response/server"
    })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void clientSentWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/server.sent.read.abort.on.open.request/client",
        "${net}/server.sent.read.abort.on.open.request/server"
    })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void serverSentReadAbortOnOpenRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/server.sent.read.abort.before.response/client",
        "${net}/client.sent.read.abort.before.response/server"
    })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void serverSentReadAbortBeforeResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/server.sent.write.rst.frame.on.open.request.response/client",
        "${net}/server.sent.write.abort.on.open.request/server"
    })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void serverSentWriteAbortOnOpenRequestResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/server.sent.write.abort.on.closed.request/client",
        "${net}/server.sent.write.abort.on.closed.request/server"
    })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void serverSentWriteAbortOnClosedRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/server.sent.end.stream.before.payload/client",
        "${net}/server.sent.end.stream.before.payload/server"
    })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void serverSentEndStreamBeforePayload() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.authority.yaml")
    @Specification({
        "${app}/http.authority.default.port/client",
        "${net}/http.authority.default.port/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void defaultPortToAuthority() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.path.prefix.yaml")
    @Specification({
        "${app}/http.path.prefix/client",
        "${net}/http.path.prefix/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void pathPrefix() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.path.yaml")
    @Specification({
        "${app}/http.path.with.query/client",
        "${net}/http.path.with.query/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void pathWithQuery() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/http.unknown.authority/client" })
    public void httpUnknownAuthority() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.path.prefix.yaml")
    @Specification({
        "${app}/http.unknown.path/client" })
    public void httpUnknownPath() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/http.response.trailer/client",
        "${net}/http.response.trailer/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void shouldProxyResponseTrailer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/server.sent.close.before.response.headers/client",
        "${net}/server.sent.close.before.response.headers/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void shouldSendResetOnIncompleteResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/http.push.promise/client",
        "${net}/http.push.promise/server" })
    @ScriptProperty({
        "promiseId1 0x3f00_0000_8000_0003L",
        "promiseId2 0x3f00_0000_8000_0005L",
    })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void pushResources() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/http.get.exchange/client",
        "${net}/http.push.promise.none.cacheable.request/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void shouldRejectNotCacheablePromiseRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/streams.on.same.connection/client",
        "${net}/streams.on.same.connection/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void shouldHandleStreamsOnSameConnection() throws Exception
    {
        k3po.finish();
    }
}
