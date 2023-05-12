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
package io.aklivity.zilla.specs.binding.http.streams.network.rfc7540;

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

public class ConnectionManagementIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7540/connection.management");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/connection.established/client",
        "${net}/connection.established/server",
    })
    public void connectionEstablished() throws Exception
    {
        k3po.finish();
    }

    @Ignore
    @Test
    @Specification({
        "${net}/http.get.exchange/client",
        "${net}/http.get.exchange/server",
    })
    public void httpGetExchange() throws Exception
    {
        k3po.finish();
    }

    @Ignore
    @Test
    @Specification({
        "${net}/http.get.exchange.with.header.override/client",
        "${net}/http.get.exchange.with.header.override/server"
    })
    public void shouldSendRequestWithHeaderOverride() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/http.unknown.authority/client",
        "${net}/http.unknown.authority/server",
    })
    public void httpUnknownAuthority() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/http.unknown.path/client",
        "${net}/http.unknown.path/server",
    })
    public void httpUnknownPath() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/http.post.exchange/client",
        "${net}/http.post.exchange/server",
    })
    public void httpPostExchange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/http.post.exchange.before.settings.exchange/client",
        "${net}/http.post.exchange.before.settings.exchange/server"
    })
    public void httpPostExchangeBeforeSettingsExchange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/http.post.exchange.streaming/client",
        "${net}/http.post.exchange.streaming/server"
    })
    public void httpPostExchangeWhenStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/multiple.data.frames/client",
        "${net}/multiple.data.frames/server",
    })
    public void multipleDataFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connection.has.two.streams/client",
        "${net}/connection.has.two.streams/server",
    })
    public void connectionHasTwoStreams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/http.push.promise/client",
        "${net}/http.push.promise/server",
    })
    public void pushResources() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/push.promise.on.different.stream/client",
        "${net}/push.promise.on.different.stream/server",
    })
    public void pushPromiseOnDifferentStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/http.push.promise.none.cacheable.request/client",
        "${net}/http.push.promise.none.cacheable.request/server",
    })
    public void shouldRejectNotCacheablePromiseRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/ignore.client.rst.stream/client",
        "${net}/ignore.client.rst.stream/server",
    })
    public void ignoreClientRstStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.read.abort.on.open.request/client",
        "${net}/client.sent.read.abort.on.open.request/server"
    })
    public void clientSentReadAbortOnOpenRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.read.abort.on.closed.request/client",
        "${net}/client.sent.read.abort.on.closed.request/server"
    })
    public void clientSentReadAbortOnClosedRequest() throws Exception
    {
        k3po.finish();
    }

    @Ignore
    @Test
    @Specification({
        "${net}/client.sent.write.abort.on.open.request/client",
        "${net}/client.sent.write.abort.on.open.request/server"
    })
    public void clientSentWriteAbortOnOpenRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.write.abort.on.closed.request/client",
        "${net}/client.sent.write.abort.on.closed.request/server"
    })
    public void clientSentWriteAbortOnClosedRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.write.close/client",
        "${net}/client.sent.write.close/server"
    })
    public void clientSentWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.read.abort.on.open.request/client",
        "${net}/server.sent.read.abort.on.open.request/server"
    })
    public void serverSentReadAbortOnOpenRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.read.abort.before.response/client",
        "${net}/server.sent.read.abort.before.response/server"
    })
    public void serverSentReadAbortBeforeResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.write.abort.on.open.request/client",
        "${net}/server.sent.write.abort.on.open.request/server"
    })
    public void serverSentWriteAbortOnOpenRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.write.abort.on.closed.request/client",
        "${net}/server.sent.write.abort.on.closed.request/server"
    })
    public void serverSentWriteAbortOnClosedRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.write.close/client",
        "${net}/server.sent.write.close/server"
    })
    public void serverSentWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/http.authority.default.port/client",
        "${net}/http.authority.default.port/server",
    })
    public void authorityWithoutPort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/http.path.prefix/client",
        "${net}/http.path.prefix/server",
    })
    public void pathPrefix() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/http.path.with.query/client",
        "${net}/http.path.with.query/server"
    })
    public void pathWithQuery() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/http.response.trailer/client",
        "${net}/http.response.trailer/server",
    })
    public void shouldProxyResponseTrailer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.close.before.response.headers/client",
        "${net}/server.sent.close.before.response.headers/server",
    })
    public void shouldSendResetOnIncompleteResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/http.push.promise.header.override/client",
        "${net}/http.push.promise.header.override/server",
    })
    public void pushResourcesWithOverrideHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.write.abort.then.read.abort.on.open.request/client",
        "${net}/client.sent.write.abort.then.read.abort.on.open.request/server",
    })
    public void clientSentWriteAbortThenReadAbortOnOpenRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.read.abort.before.response/client",
        "${net}/client.sent.read.abort.before.response/server",
    })
    public void clientSentReadAbortBeforeResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.rst.stream.on.closed.request/client",
        "${net}/client.sent.rst.stream.on.closed.request/server",
    })
    public void clientSentResetStreamOnClosedRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/streams.on.same.connection/client",
        "${net}/streams.on.same.connection/server" })
    public void shouldHandleStreamsOnSameConnection() throws Exception
    {
        k3po.finish();
    }
}
