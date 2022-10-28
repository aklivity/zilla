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
package io.aklivity.zilla.runtime.binding.http.internal.streams.rfc7230.client;

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
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Ignore
    @Configuration("client.override.json")
    @Specification({
        "${app}/request.with.header.override/client",
        "${net}/request.with.header.override/server" })
    public void shouldSendRequestWithHeaderOverride() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/request.with.connection.close/client",
        "${net}/request.with.connection.close/server" })
    @Ignore("TODO: SourceOutputStream should propagate high-level END after connection:close")
    public void clientAndServerMustCloseConnectionAfterRequestWithConnectionClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/response.with.connection.close/client",
        "${net}/response.with.connection.close/server" })
    public void responseWithConnectionClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/multiple.requests.serialized/client",
        "${net}/multiple.requests.same.connection/server" })
    public void multipleRequestsSameConnection() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("WRITE_RESPONSE_ONE");
        k3po.notifyBarrier("WRITE_RESPONSE_TWO");
        k3po.notifyBarrier("WRITE_RESPONSE_THREE");
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/concurrent.requests/client",
        "${net}/concurrent.requests.different.connections/server" })
    public void concurrentRequestsDifferentConnections() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/multiple.requests.pipelined/client",
        "${net}/multiple.requests.pipelined/server" })
    @Ignore("Issuing pipelined requests is not yet implemented")
    public void shouldSupporttHttpPipelining() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/multiple.requests.pipelined.with.retry/client",
        "${net}/multiple.requests.pipelined.with.retry/server" })
    @Ignore("Issuing pipelined requests is not yet implemented")
    public void clientWithPipeliningMustNotRetryPipeliningImmediatelyAfterFailure() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/first.pipelined.response.has.connection.close/client",
        "${net}/first.pipelined.response.has.connection.close/server" })
    @Ignore("Issuing pipelined requests is not yet implemented")
    public void clientMustNotReuseConnectionWhenReceivesConnectionClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/upgrade.request.and.response/client",
        "${net}/upgrade.request.and.response/server" })
    public void upgradeRequestandResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/request.and.upgrade.required.response/client",
        "${net}/request.and.upgrade.required.response/server" })
    public void requestAndUpgradeRequiredResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/upgrade.request.and.response.with.data/client",
        "${net}/upgrade.request.and.response.with.data/server" })
    public void upgradeRequestAndResponseWithData() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Configuration("client.json")
    @Specification({
        "${net}/proxy.must.not.forward.connection.header/client",
        "${net}/proxy.must.not.forward.connection.header/proxy",
        "${net}/proxy.must.not.forward.connection.header/backend" })
    @Ignore("http proxy not yet implemented")
    public void intermediaryMustRemoveConnectionHeaderOnForwardRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${net}/reverse.proxy.connection.established/client",
        "${net}/reverse.proxy.connection.established/proxy",
        "${net}/reverse.proxy.connection.established/backend" })
    @Ignore("http proxy not yet implemented")
    public void reverseProxyConnectionEstablished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${net}/proxy.must.not.retry.non.idempotent.requests/client",
        "${net}/proxy.must.not.retry.non.idempotent.requests/proxy",
        "${net}/proxy.must.not.retry.non.idempotent.requests/backend" })
    @Ignore("http proxy not yet implemented")
    public void proxyMustNotRetryNonIdempotentRequests() throws Exception
    {
        k3po.finish();
    }
}
