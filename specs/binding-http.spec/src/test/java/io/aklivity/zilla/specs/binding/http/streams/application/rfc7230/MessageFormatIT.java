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
package io.aklivity.zilla.specs.binding.http.streams.application.rfc7230;

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
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/http/streams/application/rfc7230/message.format");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/request.with.headers/client",
        "${app}/request.with.headers/server" })
    public void requestWithHeaders() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/request.with.content.length/client",
        "${app}/request.with.content.length/server" })
    public void requestWithContentLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/response.with.headers/client",
        "${app}/response.with.headers/server" })
    public void responseWithHeaders() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/response.with.content.length/client",
        "${app}/response.with.content.length/server" })
    public void responseWithContentLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/request.with.extra.CRLF.before.request.line/client",
        "${app}/request.with.extra.CRLF.before.request.line/server" })
    public void robustServerShouldAllowExtraCRLFBeforeRequestLine() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/post.request.with.no.content/client",
        "${app}/post.request.with.no.content/server" })
    public void postRequestWithNoContent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/head.request.and.response/client",
        "${app}/head.request.and.response/server" })
    public void headRequestAndResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/head.request.and.response.with.content.length/client",
        "${app}/head.request.and.response.with.content.length/server" })
    public void headRequestAndResponseWithContentLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gateway.must.reject.request.with.multiple.different.content.length/client",
        "${app}/gateway.must.reject.request.with.multiple.different.content.length/gateway",
        "${app}/gateway.must.reject.request.with.multiple.different.content.length/server" })
    @Ignore("proxy tests not tests implemented")
    public void gatewayMustRejectResponseWithMultipleDifferentContentLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/on.response.proxy.must.remove.space.in.header.with.space.between.header.name.and.colon/client",
        "${app}/on.response.proxy.must.remove.space.in.header.with.space.between.header.name.and.colon/server",
        "${app}/on.response.proxy.must.remove.space.in.header.with.space.between.header.name.and.colon/proxy" })
    @Ignore("proxy tests not tests implemented")
    public void onResponseProxyMustRemoveSpaceInHeaderWithSpaceBetweenHeaderNameAndColon() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/proxy.gets.response.with.multiple.content.lengths/client",
        "${app}/proxy.gets.response.with.multiple.content.lengths/server" })
    @Ignore("proxy tests not tests implemented")
    public void proxyGetsResponseWithMultipleContentLengths() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/proxy.or.gateway.must.reject.obs.in.header.value/client",
        "${app}/proxy.or.gateway.must.reject.obs.in.header.value/server" })
    @Ignore("proxy tests not tests implemented")
    public void proxyOrGatewayMustRejectOBSInHeaderValue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/proxy.should.preserve.unrecognized.headers/client",
        "${app}/proxy.should.preserve.unrecognized.headers/server",
        "${app}/proxy.should.preserve.unrecognized.headers/proxy" })
    @Ignore("proxy tests not tests implemented")
    public void proxyShouldPreserveUnrecognizedHeaders() throws Exception
    {
        k3po.finish();
    }
}
