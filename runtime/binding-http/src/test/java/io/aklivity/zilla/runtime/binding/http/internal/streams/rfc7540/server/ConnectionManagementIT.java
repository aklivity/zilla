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
package io.aklivity.zilla.runtime.binding.http.internal.streams.rfc7540.server;

import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfiguration.HTTP_SERVER_CONCURRENT_STREAMS;
import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfigurationTest.HTTP_MAX_CONCURRENT_STREAMS_CLEANUP_NAME;
import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfigurationTest.HTTP_STREAMS_CLEANUP_DELAY_NAME;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_BUFFER_SLOT_CAPACITY_NAME;
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

public class ConnectionManagementIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7540/connection.management")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/http/streams/application/rfc7540/connection.management");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(HTTP_SERVER_CONCURRENT_STREAMS, 100)
        .configurationRoot("io/aklivity/zilla/specs/binding/http/config/v2")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/connection.established/client" })
    public void connectionEstablished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/http.get.exchange/client",
        "${app}/http.get.exchange/server" })
    public void httpGetExchange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.override.json")
    @Specification({
        "${net}/http.get.exchange.with.header.override/client",
        "${app}/http.get.exchange.with.header.override/server" })
    public void httpGetExchangeWithHeaderOverride() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/http.unknown.authority/client" })
    public void httpUnknownAuthority() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.path.prefix.json")
    @Specification({
        "${net}/http.unknown.path/client" })
    public void httpUnknownPath() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/http.post.exchange/client",
        "${app}/http.post.exchange/server" })
    public void httpPostExchange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/http.post.exchange.before.settings.exchange/client",
        "${app}/http.post.exchange.before.settings.exchange/server" })
    @Configure(name = ENGINE_BUFFER_SLOT_CAPACITY_NAME, value = "65536")
    public void httpPostExchangeBeforeSettingsExchange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/http.post.exchange.streaming/client",
        "${app}/http.post.exchange.streaming/server" })
    public void httpPostExchangeWhenStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/connection.has.two.streams/client",
        "${app}/connection.has.two.streams/server" })
    public void connectionHasTwoStreams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/http.push.promise/client",
        "${app}/http.push.promise/server" })
    public void pushResources() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/push.promise.on.different.stream/client",
        "${app}/push.promise.on.different.stream/server" })
    public void pushPromiseOnDifferentStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/multiple.data.frames/client",
        "${app}/multiple.data.frames/server" })
    public void multipleDataFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/client.sent.rst.stream.on.closed.request/client",
        "${app}/client.sent.write.abort.on.closed.request/server" })
    public void clientSentRstStreamOnClosedRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/ignore.client.rst.stream/client",
        "${app}/ignore.client.rst.stream/server" })
    public void ignoreClientRstStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/client.sent.read.abort.on.open.request/client",
        "${app}/client.sent.read.abort.on.open.request/server"
    })
    public void clientSentReadAbortOnOpenRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/client.sent.read.abort.on.closed.request/client",
        "${app}/client.sent.read.abort.on.closed.request/server"
    })
    public void clientSentReadAbortOnClosedRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/client.sent.write.abort.on.open.request/client",
        "${app}/client.sent.write.abort.on.open.request/server"
    })
    public void clientSentWriteAbortOnOpenRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/client.sent.write.abort.on.closed.request/client",
        "${app}/client.sent.write.abort.on.closed.request/server"
    })
    public void clientSentWriteAbortOnClosedRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/client.sent.write.close/client",
        "${app}/client.sent.write.close/server"
    })
    public void clientSentWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/server.sent.read.abort.on.open.request/client",
        "${app}/server.sent.read.abort.on.open.request/server"
    })
    public void serverSentReadAbortOnOpenRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/server.sent.read.abort.before.response/client",
        "${app}/server.sent.read.abort.before.response/server"
    })
    public void serverSentReadAbortBeforeResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/server.sent.write.abort.on.open.request/client",
        "${app}/server.sent.write.abort.on.open.request/server"
    })
    public void serverSentWriteAbortOnOpenRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/server.sent.write.abort.on.closed.request/client",
        "${app}/server.sent.write.abort.on.closed.request/server"
    })
    public void serverSentWriteAbortOnClosedRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/server.sent.write.close/client",
        "${app}/server.sent.write.close/server"
    })
    public void serverSentWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.authority.json")
    @Specification({
        "${net}/http.authority.default.port/client",
        "${app}/http.authority.default.port/server" })
    public void defaultPortToAuthority() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.path.prefix.json")
    @Specification({
        "${net}/http.path.prefix/client",
        "${app}/http.path.prefix/server" })
    public void pathPrefix() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.path.json")
    @Specification({
        "${net}/http.path.with.query/client",
        "${app}/http.path.with.query/server" })
    public void pathWithQuery() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/http.response.trailer/client",
        "${app}/http.response.trailer/server" })
    public void shouldProxyResponseTrailer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/client.sent.end.before.response.received/client",
        "${app}/client.sent.end.before.response.received/server" })
    public void shouldSendResetOnIncompleteResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.override.json")
    @Specification({
        "${net}/http.push.promise.header.override/client",
        "${app}/http.push.promise.header.override/server" })
    public void pushResourcesWithOverrideHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configure(name = HTTP_MAX_CONCURRENT_STREAMS_CLEANUP_NAME, value = "1")
    @Configure(name = HTTP_STREAMS_CLEANUP_DELAY_NAME, value = "10")
    @Configuration("server.json")
    @Specification({
        "${net}/client.sent.write.abort.then.read.abort.on.open.request/client",
        "${app}/client.sent.write.abort.then.read.abort.on.open.request/server"
    })
    public void clientSentWriteAbortThenReadAbortOnOpenRequest() throws Exception
    {
        k3po.finish();
    }
}
