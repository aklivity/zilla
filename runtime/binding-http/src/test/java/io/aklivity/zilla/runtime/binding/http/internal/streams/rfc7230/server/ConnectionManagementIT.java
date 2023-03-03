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
package io.aklivity.zilla.runtime.binding.http.internal.streams.rfc7230.server;

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

public class ConnectionManagementIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7230/connection.management")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/http/streams/application/rfc7230/connection.management");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/http/config/v1.1")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/request.rejected/client",
        "${app}/request.rejected/server",
    })
    public void shouldRejectRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.override.yaml")
    @Specification({
        "${net}/request.with.header.override/client",
        "${app}/request.with.header.override/server" })
    public void shouldSendRequestWithHeaderOverride() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/request.with.connection.close/client",
        "${app}/request.with.connection.close/server" })
    @Ignore("TODO: SourceOutputEstablishedStream should propagate high-level END after connection:close")
    public void clientAndServerMustCloseConnectionAfterRequestWithConnectionClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${client}response.with.connection.close/client",
        "${server}response.with.connection.close/server" })
    @Ignore("TODO: SourceOutputEstablishedStream should propagate high-level END after connection:close")
    public void serverMustCloseConnectionAfterResponseWithConnectionClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/multiple.requests.same.connection/client",
        "${app}/multiple.requests.serialized/server" })
    public void shouldSupportMultipleRequestsOnSameConnection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/multiple.requests.pipelined/client",
        "${app}/concurrent.requests/server" })
    @Ignore("TODO: support pipelined requests, at a minimum by serializing them")
    public void shouldSupporttHttpPipelining() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/multiple.requests.pipelined.with.retry/client",
        "${app}/multiple.requests.pipelined.with.retry/server" })
    @Ignore("TODO: scripts")
    public void clientWithPipeliningMustNotRetryPipeliningImmediatelyAfterFailure() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/first.pipelined.response.has.connection.close/client",
        "${app}/first.pipelined.response.has.connection.close/server" })
    public void clientMustNotReuseConnectionWhenReceivesConnectionClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/upgrade.request.and.response/client",
        "${app}/upgrade.request.and.response/server" })
    public void serverGettingUpgradeRequestMustRespondWithUpgradeHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.authority.yaml")
    @Specification({
        "${net}/request.authority.with.no.port/client",
        "${app}/request.authority.with.no.port/server" })
    public void shouldHandleRequestAuthorityWithNoPort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.authority.yaml")
    @Specification({
        "${net}/request.authority.with.port/client",
        "${app}/request.authority.with.no.port/server" })
    public void shouldHandleRequestAuthorityWithExplicitPort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/request.authority.mismatch/client" })
    public void shouldRejectRequestAuthorityMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/send.end.after.upgrade.request.completed/client",
        "${app}/send.end.after.upgrade.request.completed/server" })
    public void shouldSendEndWhenEndReceivedAfterUpgradeRequestCompleted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/upgrade.request.and.abort/client",
        "${app}/upgrade.request.and.abort/server" })
    public void serverGettingAbortShouldPropagateAbortOnAllDirections() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/request.and.upgrade.required.response/client",
        "${app}/request.and.upgrade.required.response/server" })
    public void serverThatSendsUpgradeRequiredMustIncludeUpgradeHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/upgrade.request.and.response.with.data/client",
        "${app}/upgrade.request.and.response.with.data/server" })
    public void serverThatIsUpgradingMustSendA101ResponseBeforeData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/proxy.must.not.forward.connection.header/client",
        "${app}/proxy.must.not.forward.connection.header/proxy",
        "${app}/proxy.must.not.forward.connection.header/backend" })
    @Ignore("http proxy not yet implemented")
    public void intermediaryMustRemoveConnectionHeaderOnForwardRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/reverse.proxy.connection.established/client",
        "${app}/reverse.proxy.connection.established/proxy",
        "${app}/reverse.proxy.connection.established/backend" })
    @Ignore("http proxy not yet implemented")
    public void reverseProxyConnectionEstablished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/proxy.must.not.retry.non.idempotent.requests/client",
        "${app}/proxy.must.not.retry.non.idempotent.requests/proxy",
        "${app}/proxy.must.not.retry.non.idempotent.requests/backend" })
    @Ignore("http proxy not yet implemented")
    public void proxyMustNotRetryNonIdempotentRequests() throws Exception
    {
        k3po.finish();
    }
}
