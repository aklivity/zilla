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
package io.aklivity.zilla.specs.binding.http.streams.network.rfc7230;

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

public class MessageFormatIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7230/message.format");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/request.with.headers/client",
        "${net}/request.with.headers/server" })
    public void requestWithHeaders() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.with.content.length/client",
        "${net}/request.with.content.length/server" })
    public void requestWithContentLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.with.headers/client",
        "${net}/response.with.headers/server" })
    public void responseWithHeaders() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.with.content.length/client",
        "${net}/response.with.content.length/server" })
    public void responseWithContentLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/invalid.request.whitespace.after.start.line/client",
        "${net}/invalid.request.whitespace.after.start.line/server" })
    public void invalidRequestWhitespaceAfterStartLine() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/invalid.request.missing.target/client",
        "${net}/invalid.request.missing.target/server" })
    public void invalidRequestMissingTarget() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/invalid.request.not.http/client",
        "${net}/invalid.request.not.http/server" })
    public void invalidRequestNotHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.with.unimplemented.method/client",
        "${net}/request.with.unimplemented.method/server" })
    public void requestWithUnimplementedMethod() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/incomplete.request.with.unimplemented.method/client",
        "${net}/incomplete.request.with.unimplemented.method/server" })
    public void incompleteRequestWithUnimplementedMethod() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.with.extra.CRLF.before.request.line/client",
        "${net}/request.with.extra.CRLF.before.request.line/server" })
    public void robustServerShouldAllowExtraCRLFBeforeRequestLine() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.with.start.line.too.long/client",
        "${net}/request.with.start.line.too.long/server" })
    public void requestWithStartLineTooLong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/invalid.request.space.before.colon.in.header/client",
        "${net}/invalid.request.space.before.colon.in.header/server" })
    public void invalidRequestSpaceBeforeColonInHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.with.obsolete.line.folding/client",
        "${net}/request.with.obsolete.line.folding/server" })
    public void requestWithObsoleteLineFolding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.with.header.value.too.long/client",
        "${net}/request.with.header.value.too.long/server" })
    public void requestWithHeaderValueTooLong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.with.unknown.transfer.encoding/client",
        "${net}/request.with.unknown.transfer.encoding/server" })
    public void requestWithUnknownTransferEncoding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/post.request.with.no.content/client",
        "${net}/post.request.with.no.content/server" })
    public void postRequestWithNoContent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/head.request.and.response/client",
        "${net}/head.request.and.response/server" })
    public void headRequestAndResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/head.request.and.response.with.content.length/client",
        "${net}/head.request.and.response.with.content.length/server" })
    public void headRequestAndResponseWithContentLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/invalid.request.multiple.content.lengths/client",
        "${net}/invalid.request.multiple.content.lengths/server" })
    public void invalidRequestMultipleContentLengths() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/gateway.must.reject.request.with.multiple.different.content.length/client",
        "${net}/gateway.must.reject.request.with.multiple.different.content.length/gateway",
        "${net}/gateway.must.reject.request.with.multiple.different.content.length/server" })
    @Ignore("proxy tests not tests implemented")
    public void gatewayMustRejectResponseWithMultipleDifferentContentLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/on.response.proxy.must.remove.space.in.header.with.space.between.header.name.and.colon/client",
        "${net}/on.response.proxy.must.remove.space.in.header.with.space.between.header.name.and.colon/server",
        "${net}/on.response.proxy.must.remove.space.in.header.with.space.between.header.name.and.colon/proxy" })
    @Ignore("proxy tests not tests implemented")
    public void onResponseProxyMustRemoveSpaceInHeaderWithSpaceBetweenHeaderNameAndColon() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/proxy.or.gateway.must.reject.obs.in.header.value/client",
        "${net}/proxy.or.gateway.must.reject.obs.in.header.value/server" })
    @Ignore("proxy tests not tests implemented")
    public void proxyOrGatewayMustRejectOBSInHeaderValue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/proxy.should.preserve.unrecognized.headers/client",
        "${net}/proxy.should.preserve.unrecognized.headers/server",
        "${net}/proxy.should.preserve.unrecognized.headers/proxy" })
    @Ignore("proxy tests not tests implemented")
    public void proxyShouldPreserveUnrecognizedHeaders() throws Exception
    {
        k3po.finish();
    }
}
