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
package io.aklivity.zilla.specs.binding.http.streams.application.rfc7540;

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
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/http/streams/application/rfc7540/connection.management");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/http.get.exchange/client",
        "${app}/http.get.exchange/server"
    })
    public void httpGetExchange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/http.get.exchange.with.header.override/client",
        "${app}/http.get.exchange.with.header.override/server"
    })
    public void shouldSendRequestWithHeaderOverride() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/http.post.exchange/client",
        "${app}/http.post.exchange/server"
    })
    public void httpPostExchange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/http.post.exchange.streaming/client",
        "${app}/http.post.exchange.streaming/server"
    })
    public void httpPostExchangeWhenStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/http.post.exchange.before.settings.exchange/client",
        "${app}/http.post.exchange.before.settings.exchange/server"
    })
    public void httpPostExchangeBeforeSettingsExchange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/multiple.data.frames/client",
        "${app}/multiple.data.frames/server"
    })
    public void multipleDataFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connection.has.two.streams/client",
        "${app}/connection.has.two.streams/server"
    })
    public void connectionHasTwoStreams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/http.push.promise/client",
        "${app}/http.push.promise/server"
    })
    public void pushResources() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.promise.on.different.stream/client",
        "${app}/push.promise.on.different.stream/server"
    })
    public void pushPromiseOnDifferentStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reset.http2.stream/client",
        "${app}/reset.http2.stream/server"
    })
    public void resetHttp2Stream() throws Exception
    {
        k3po.finish();
    }

    @Ignore
    @Test
    @Specification({
        "${app}/ignore.client.rst.stream/client",
        "${app}/ignore.client.rst.stream/server"
    })
    public void ignoreClientRstStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.read.abort.on.open.request/client",
        "${app}/client.sent.read.abort.on.open.request/server"
    })
    public void clientSentReadAbortOnOpenRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.rst.stream.last.frame/client",
        "${app}/server.rst.stream.last.frame/server"
    })
    public void serverRstStreamLastFrame() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.read.abort.on.closed.request/client",
        "${app}/client.sent.read.abort.on.closed.request/server"
    })
    public void clientSentReadAbortOnClosedRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.write.abort.on.open.request/client",
        "${app}/client.sent.write.abort.on.open.request/server"
    })
    public void clientSentWriteAbortOnOpenRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.write.abort.on.closed.request/client",
        "${app}/client.sent.write.abort.on.closed.request/server"
    })
    public void clientSentWriteAbortOnClosedRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.write.close/client",
        "${app}/client.sent.write.close/server"
    })
    public void clientSentWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.read.abort.on.open.request/client",
        "${app}/server.sent.read.abort.on.open.request/server"
    })
    public void serverSentReadAbortOnOpenRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.read.abort.before.correlated/client",
        "${app}/server.sent.read.abort.before.correlated/server"
    })
    public void serverSentReadAbortBeforeCorrelated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.write.abort.on.open.request/client",
        "${app}/server.sent.write.abort.on.open.request/server"
    })
    public void serverSentWriteAbortOnOpenRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.write.abort.on.closed.request/client",
        "${app}/server.sent.write.abort.on.closed.request/server"
    })
    public void serverSentWriteAbortOnClosedRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.write.close/client",
        "${app}/server.sent.write.close/server"
    })
    public void serverSentWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/http.authority.default.port/client",
        "${app}/http.authority.default.port/server"
    })
    public void authorityWithoutPort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/http.path.prefix/client",
        "${app}/http.path.prefix/server"
    })
    public void pathPrefix() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/http.path.with.query/client",
        "${app}/http.path.with.query/server"
    })
    public void pathWithQuery() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/http.response.trailer/client",
        "${app}/http.response.trailer/server"
    })
    public void shouldProxyResponseTrailer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/http.push.promise.header.override/client",
        "${app}/http.push.promise.header.override/server"
    })
    public void pushResourcesWithOverrideHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.write.abort.then.read.abort.on.open.request/client",
        "${app}/client.sent.write.abort.then.read.abort.on.open.request/server"
    })
    public void clientSentWriteAbortThenReadAbortOnOpenRequest() throws Exception
    {
        k3po.finish();
    }
}
