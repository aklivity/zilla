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
package io.aklivity.zilla.runtime.binding.http.internal.streams.rfc7230.client;

import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfiguration.HTTP_MAXIMUM_CONNECTIONS;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
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

public class ConnectionManagementPoolSize1IT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/http/streams/application/rfc7230/connection.management")
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7230/connection.management");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(HTTP_MAXIMUM_CONNECTIONS, 1)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/binding/http/config/v1.1")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Ignore("GitHub Actions")
    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/concurrent.requests/client",
        "${net}/multiple.requests.same.connection/server" })
    public void concurrentRequestsSameConnection() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("WRITE_RESPONSE_ONE");
        k3po.notifyBarrier("WRITE_RESPONSE_TWO");
        k3po.notifyBarrier("WRITE_RESPONSE_THREE");
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/concurrent.upgrade.requests.and.responses.with.data/client",
        "${net}/concurrent.upgrade.requests.and.responses.with.data/server" })
    public void connectionsLimitShouldNotApplyToUpgradedConnections() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("REQUEST_ONE_RECEIVED");
        k3po.notifyBarrier("WRITE_DATA_REQUEST_ONE");
        k3po.awaitBarrier("REQUEST_TWO_RECEIVED");
        k3po.notifyBarrier("WRITE_DATA_REQUEST_TWO");
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/request.and.503.response.with.retry/client",
        "${net}/request.incomplete.response.headers.and.abort/server" })
    public void shouldGive503ResponseAndFreeConnectionWhenConnectReplyStreamIsAbortedBeforeResponseHeadersComplete()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/request.and.503.response.with.retry/client",
        "${net}/request.incomplete.response.headers.and.end/server" })
    public void shouldGive503ResponseAndFreeConnectionWhenConnectReplyStreamEndsBeforeResponseHeadersComplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/request.and.503.response.with.retry/client",
        "${net}/request.incomplete.response.headers.and.reset/server" })
    public void shouldGive503ResponseAndFreeConnectionWhenConnectStreamIsResetBeforeResponseHeadersComplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/request.and.503.response.with.retry/client",
        "${net}/request.no.response.and.end/server" })
    public void shouldGive503ResponseAndFreeConnectionWhenConnectReplyStreamEndsBeforeResponseReceived() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/request.and.503.response.with.retry/client",
        "${net}/request.no.response.and.reset/server" })
    public void shouldGive503ResponseAndFreeConnectionWhenConnectStreamIsResetBeforeResponseReceived() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/request.and.abort/client",
        "${net}/request.and.abort/server"})
    public void shouldAbortTransportAndFreeConnectionWhenRequestIsAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/pending.request.second.request.and.abort/client",
        "${net}/pending.request.second.request.and.abort/server"})
    public void shouldLeaveTransportUntouchedWhenEnqueuedRequestIsAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/request.receive.reset/client",
        "${net}/partial.request.receive.reset/server"})
    public void shouldResetRequestAndFreeConnectionWhenLowLevelIsReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/request.and.response.twice/client",
        "${net}/request.response.and.end/server"})
    public void shouldEndOutputAndFreeConnectionWhenEndReceivedAfterCompleteResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/request.response/client",
        "${net}/request.response.and.abort/server"})
    public void shouldFreeConnectionWhenAbortReceivedAfterCompleteResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/request.and.response.twice.awaiting.barrier/client",
        "${net}/request.response.and.reset/server"})
    public void shouldEndOutputAndFreeConnectionWhenResetReceivedAfterCompleteResponse() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("CONNECTION_RESET");
        k3po.notifyBarrier("ISSUE_SECOND_REQUEST");
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/request.and.response.with.incomplete.data.and.abort/client",
        "${net}/request.response.headers.incomplete.data.and.end/server"})
    public void shouldSendAbortAndFreeConnectionWhenConnectReplyStreamEndsBeforeResponseDataComplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/request.and.response.with.incomplete.data.and.abort/client",
        "${net}/request.response.headers.incomplete.data.and.abort/server"})
    public void shouldSendAbortAndFreeConnectionWhenConnectReplyStreamIsAbortedBeforeResponseDataComplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.with.content.length.is.reset/client",
        "${net}/response.with.content.length.is.reset/server" })
    public void shouldResetRequestAndFreeConnectionWhenRequestWithContentLengthIsReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/request.send.abort.after.response.received/client",
        "${net}/request.send.abort.after.response.received/server"})
    public void shouldSendAbortAndResetOnAbortedRequestAfterResponseHeaderReceived() throws Exception
    {
        k3po.finish();
    }
}
