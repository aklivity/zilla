/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.http.internal.streams.rfc7230.server;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Ignore;
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

public class MessageFormatIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/cog/http/streams/network/rfc7230/message.format")
        .addScriptRoot("app", "io/aklivity/zilla/specs/cog/http/streams/application/rfc7230/message.format");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .configurationRoot("io/aklivity/zilla/specs/cog/http/config/v1.1")
        .external("app#0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/request.with.headers/client",
        "${app}/request.with.headers/server" })
    public void requestWithHeaders() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/request.with.content.length/client",
        "${app}/request.with.content.length/server" })
    public void requestWithContentLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/response.with.headers/client",
        "${app}/response.with.headers/server" })
    public void responseWithHeaders() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/response.with.content.length/client",
        "${app}/response.with.content.length/server" })
    public void responseWithContentLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/invalid.request.whitespace.after.start.line/client"})
    public void invalidRequestWhitespaceAfterStartLine() throws Exception
    {
        // As per RFC, alternatively could process everything before whitespace,
        // but the better choice is to reject
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/invalid.request.missing.target/client"})
    public void invalidRequestMissingTarget() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/invalid.request.not.http/client"})
    public void invalidRequestNotHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/incomplete.request.with.unimplemented.method/client"})
    public void incompleteRequestWithUnimplementedMethod() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/request.with.unimplemented.method/client"})
    public void requestWithUnimplementedMethod() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/request.with.extra.CRLF.before.request.line/client",
        "${app}/request.with.extra.CRLF.before.request.line/server" })
    public void robustServerShouldAllowExtraCRLFBeforeRequestLine() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/request.with.start.line.too.long/client"})
    public void requestWithStartLineTooLong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/invalid.request.space.before.colon.in.header/client"})
    public void invalidRequestSpaceBeforeColonInHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/request.with.obsolete.line.folding/client"})
    public void requestWithObsoleteLineFolding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/request.with.header.value.too.long/client"})
    @ScriptProperty("headerSize \"9001\"")
    public void requestWithHeaderValueTooLong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/request.with.unknown.transfer.encoding/client"})
    public void requestWithUnknownTransferEncoding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/post.request.with.no.content/client",
        "${app}/post.request.with.no.content/server" })
    public void postRequestWithNoContent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/head.request.and.response/client",
        "${app}/head.request.and.response/server" })
    public void headRequestAndResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/head.request.and.response.with.content.length/client",
        "${app}/head.request.and.response.with.content.length/server" })
    public void headRequestAndResponseWithContentLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/invalid.request.multiple.content.lengths/client"})
    public void invalidRequestMultipleContentLengths() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/gateway.must.reject.request.with.multiple.different.content.length/client",
        "${gateway}/gateway.must.reject.request.with.multiple.different.content.length/gateway",
        "${app}/gateway.must.reject.request.with.multiple.different.content.length/server" })
    @Ignore("proxy tests not implemented")
    public void gatewayMustRejectResponseWithMultipleDifferentContentLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/on.response.proxy.must.remove.space.in.header.with.space.between.header.name.and.colon/client",
        "${app}/on.response.proxy.must.remove.space.in.header.with.space.between.header.name.and.colon/server",
        "${proxy}/on.response.proxy.must.remove.space.in.header.with.space.between.header.name.and.colon/proxy" })
    @Ignore("proxy tests not implemented")
    public void onResponseProxyMustRemoveSpaceInHeaderWithSpaceBetweenHeaderNameAndColon() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/proxy.or.gateway.must.reject.obs.in.header.value/client",
        "${app}/proxy.or.gateway.must.reject.obs.in.header.value/server" })
    @Ignore("proxy tests not implemented")
    public void proxyOrGatewayMustRejectOBSInHeaderValue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/proxy.should.preserve.unrecongnized.headers/client",
        "${app}/proxy.should.preserve.unrecongnized.headers/server",
        "${proxy}/proxy.should.preserve.unrecongnized.headers/proxy" })
    @Ignore("proxy tests not implemented")
    public void proxyShouldPreserveUnrecognizedHeaders() throws Exception
    {
        k3po.finish();
    }
}
