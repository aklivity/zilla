/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.metrics.http.internal;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.ScriptProperty;
import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class HttpMetricsIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/http/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/metrics/http/config")
        .external("app1")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("request.with.content.length.yaml")
    @Specification({
        "${app}/rfc7230/message.format/request.with.content.length/client",
        "${app}/rfc7230/message.format/request.with.content.length/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordRequestWithContentLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("request.with.content.length.attributes.yaml")
    @Specification({
        "${app}/rfc7230/message.format/request.with.content.length/client",
        "${app}/rfc7230/message.format/request.with.content.length/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordRequestWithContentLengthAttributes() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("response.with.content.length.yaml")
    @Specification({
        "${app}/rfc7230/message.format/response.with.content.length/client",
        "${app}/rfc7230/message.format/response.with.content.length/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordResponseWithContentLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("request.transfer.encoding.chunked.yaml")
    @Specification({
        "${app}/rfc7230/transfer.codings/request.transfer.encoding.chunked/client",
        "${app}/rfc7230/transfer.codings/request.transfer.encoding.chunked/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordRequestTransferEncodingChunked() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.sent.write.abort.on.open.request.yaml")
    @Specification({
        "${app}/rfc7540/connection.abort/client.sent.write.abort.on.open.request/client",
        "${app}/rfc7540/connection.abort/client.sent.write.abort.on.open.request/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordClientSentWriteAbortOnOpenRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.sent.write.abort.on.open.response.yaml")
    @Specification({
        "${app}/rfc7540/connection.abort/server.sent.write.abort.on.open.response/client",
        "${app}/rfc7540/connection.abort/server.sent.write.abort.on.open.response/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordServerSentWriteAbortOnOpenResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.sent.read.abort.on.open.response.yaml")
    @Specification({
        "${app}/rfc7540/connection.abort/client.sent.read.abort.on.open.response/client",
        "${app}/rfc7540/connection.abort/client.sent.read.abort.on.open.response/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordClientSentReadAbortOnOpenResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.sent.read.abort.before.response.yaml")
    @Specification({
        "${app}/rfc7540/connection.management/server.sent.read.abort.before.response/client",
        "${app}/rfc7540/connection.management/server.sent.read.abort.before.response/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordServerSentReadAbortBeforeResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("invalid.chunked.request.no.crlf.at.end.of.chunk.yaml")
    @Specification({
        "${app}/rfc7230/transfer.codings/invalid.chunked.request.no.crlf.at.end.of.chunk/client",
        "${app}/rfc7230/transfer.codings/invalid.chunked.request.no.crlf.at.end.of.chunk/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordInvalidChunkedRequestNoCrlfAtEndOfChunk() throws Exception
    {
        k3po.finish();
    }
}
