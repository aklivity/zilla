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
package io.aklivity.zilla.specs.cog.http.streams.network.rfc7230;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

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
        .addScriptRoot("net", "io/aklivity/zilla/specs/cog/http/streams/network/rfc7230/connection.management");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/request.with.connection.close/client",
        "${net}/request.with.connection.close/server" })
    public void shouldCloseConnectionAfterRequestWithConnectionClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.with.header.override/client",
        "${net}/request.with.header.override/server" })
    public void shouldSendRequestWithHeaderOverride() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}response.with.connection.close/client",
        "${net}response.with.connection.close/server" })
    public void shouldCloseConnectionAfterSendingResponseWithConnectionClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/concurrent.requests.different.connections/client",
        "${net}/concurrent.requests.different.connections/server" })
    public void shouldProcessConcurrentRequestsOnDifferentConnections() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/multiple.requests.same.connection/client",
        "${net}/multiple.requests.same.connection/server" })
    public void shouldProcessMultipleRequestsOnSameConnection() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("WRITE_RESPONSE_ONE");
        k3po.notifyBarrier("WRITE_RESPONSE_TWO");
        k3po.notifyBarrier("WRITE_RESPONSE_THREE");
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/multiple.requests.pipelined/client",
        "${net}/multiple.requests.pipelined/server" })
    public void shouldProcessMultipleRequestsPipelined() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/multiple.requests.pipelined.with.retry/client",
        "${net}/multiple.requests.pipelined.with.retry/server" })
    public void shouldNotRetryPipeliningImmediatelyAfterFailure() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/first.pipelined.response.has.connection.close/client",
        "${net}/first.pipelined.response.has.connection.close/server" })
    public void shouldNotReuseConnectionAfterResponseWithConnectionClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.request.and.response/client",
        "${net}/upgrade.request.and.response/server" })
    public void shouldRespondWithUpgradeHeaderWhenActingAsServer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.and.upgrade.required.response/client",
        "${net}/request.and.upgrade.required.response/server" })
    public void shouldIncludeUpgradeHeaderWithUpgradeRequiredResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.request.and.response.with.data/client",
        "${net}/upgrade.request.and.response.with.data/server" })
    public void shouldSendA101ResponseBeforeData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.incomplete.response.headers.and.abort/client",
        "${net}/request.incomplete.response.headers.and.abort/server" })
    public void shouldReportResponseAbortedBeforeResponseHeadersComplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.incomplete.response.headers.and.end/client",
        "${net}/request.incomplete.response.headers.and.end/server" })
    public void shouldReportResponseEndedBeforeResponseHeadersComplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.incomplete.response.headers.and.reset/client",
        "${net}/request.incomplete.response.headers.and.reset/server" })
    public void shouldReportConnectStreamResetBeforeResponseHeadersComplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.no.response.and.end/client",
        "${net}/request.no.response.and.end/server" })
    public void shouldHandleConnectReplyStreamEndedWithNoResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.no.response.and.reset/client",
        "${net}/request.no.response.and.reset/server" })
    public void shouldHandleConnectStreamResetWithNoResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.response.headers.incomplete.data.and.abort/client",
        "${net}/request.response.headers.incomplete.data.and.abort/server" })
    public void shouldReportResponseAbortedWithIncompleteData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.response.headers.incomplete.data.and.end/client",
        "${net}/request.response.headers.incomplete.data.and.end/server" })
    public void shouldReportResponseEndedWithIncompleteData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.response.headers.incomplete.data.and.reset/client",
        "${net}/request.response.headers.incomplete.data.and.reset/server" })
    public void shouldReportConnectStreamResetWhenResponseHasIncompleteData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.and.abort/client",
        "${net}/request.and.abort/server" })
    public void shouldAbortRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/pending.request.second.request.and.abort/client",
        "${net}/pending.request.second.request.and.abort/server" })
    public void shouldAbortEnqueuedRequest() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("REQUEST_TWO_ABORTED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/partial.request.receive.reset/client",
        "${net}/partial.request.receive.reset/server" })
    public void shouldReportRequestReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.response.and.end/client",
        "${net}/request.response.and.end/server" })
    public void shouldProcessRequestResponseAndEnd() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.response.and.reset/client",
        "${net}/request.response.and.reset/server" })
    public void shouldProcessRequestResponseAndReset() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${net}/send.end.after.upgrade.request.completed/client",
        "${net}/send.end.after.upgrade.request.completed/server" })
    public void  shouldSendEndWhenEndReceivedAfterUpgradeRequestCompleted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.response.and.abort/client",
        "${net}/request.response.and.abort/server" })
    public void shouldFreeConnectionWhenAbortReceivedAfterCompleteResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.authority.with.no.port/client",
        "${net}/request.authority.with.no.port/server" })
    public void shouldHandleRequestAuthorityWithNoPort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.authority.with.port/client",
        "${net}/request.authority.with.port/server" })
    public void shouldHandleRequestAuthorityWithExplicitPort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.authority.mismatch/client",
        "${net}/request.authority.mismatch/server" })
    public void shouldRejectRequestAuthorityMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.send.abort.after.response.received/client",
        "${net}/request.send.abort.after.response.received/server" })
    public void shouldSendAbortAndResetOnAbortedRequestAfterResponseHeaderReceived() throws Exception
    {
        k3po.finish();
    }
}
