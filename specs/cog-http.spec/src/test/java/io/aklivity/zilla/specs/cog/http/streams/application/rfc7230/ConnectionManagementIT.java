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
package io.aklivity.zilla.specs.cog.http.streams.application.rfc7230;

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
        .addScriptRoot("app", "io/aklivity/zilla/specs/cog/http/streams/application/rfc7230/connection.management");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/request.with.connection.close/client",
        "${app}/request.with.connection.close/server" })
    public void clientAndServerMustCloseConnectionAfterRequestWithConnectionClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}response.with.connection.close/client",
        "${app}response.with.connection.close/server" })
    public void serverMustCloseConnectionAfterResponseWithConnectionClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/concurrent.requests/client",
        "${app}/concurrent.requests/server" })
    public void shouldProcessConcurrentRequests() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("REQUEST_ONE_RECEIVED");
        k3po.notifyBarrier("REQUEST_TWO_RECEIVED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/concurrent.requests.with.content/client",
        "${app}/concurrent.requests.with.content/server" })
    public void concurrentRequestsWithContent() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("REQUEST_ONE_RECEIVED");
        k3po.notifyBarrier("REQUEST_TWO_RECEIVED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/multiple.requests.serialized/client",
        "${app}/multiple.requests.serialized/server" })
    public void multipleRequestsSerialized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/first.pipelined.response.has.connection.close/client",
        "${app}/first.pipelined.response.has.connection.close/server" })
    public void clientMustNotReuseConnectionWhenReceivesConnectionClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/upgrade.request.and.response/client",
        "${app}/upgrade.request.and.response/server" })
    public void serverGettingUpgradeRequestMustRespondWithUpgradeHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/request.and.503.response/client",
        "${app}/request.and.503.response/server" })
    public void requestAnd503Response() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/request.and.response.twice/client",
        "${app}/request.and.response.twice/server" })
    public void requestAndResponseTwice() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/request.and.response.twice.awaiting.barrier/client",
        "${app}/request.and.response.twice.awaiting.barrier/server" })
    public void requestAndResponseTwiceAwaitingBarrier() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("ISSUE_SECOND_REQUEST");
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/request.and.response.with.incomplete.data/client",
        "${app}/request.and.response.with.incomplete.data/server" })
    public void responseWithContentLengthAndIncompleteData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/request.and.response.with.incomplete.data.and.abort/client",
        "${app}/request.and.response.with.incomplete.data.and.abort/server" })
    public void responseWithContentLengthAndIncompleteDataAndAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/request.and.response.with.incomplete.data.and.end/client",
        "${app}/request.and.response.with.incomplete.data.and.end/server" })
    public void responseWithContentLengthAndIncompleteDataAndEnd() throws Exception
    {
        k3po.finish();
    }

    @Ignore("Requires connect aborted, see k3po/k3po#454")
    @Test
    @Specification({
        "${app}/request.and.abort/client",
        "${app}/request.and.abort/server" })
    public void shouldProcessAbortFromClient() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/pending.request.second.request.and.abort/client",
        "${app}/pending.request.second.request.and.abort/server" })
    public void shouldProcessAbortFromClientWithPendingRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/request.receive.reset/client",
        "${app}/request.receive.reset/server" })
    public void requestIsReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/response.with.content.length.is.reset/client",
        "${app}/response.with.content.length.is.reset/server" })
    public void responseWithContentLengthIsReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/request.and.upgrade.required.response/client",
        "${app}/request.and.upgrade.required.response/server" })
    public void serverThatSendsUpgradeRequiredMustIncludeUpgradeHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/upgrade.request.and.response.with.data/client",
        "${app}/upgrade.request.and.response.with.data/server" })
    public void serverThatIsUpgradingMustSendA101ResponseBeforeData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/concurrent.upgrade.requests.and.responses.with.data/client",
        "${app}/concurrent.upgrade.requests.and.responses.with.data/server" })
    public void concurrentUpgradeRequestsandResponsesWithData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/proxy.must.not.forward.connection.header/client",
        "${app}/proxy.must.not.forward.connection.header/proxy",
        "${app}/proxy.must.not.forward.connection.header/backend" })
    public void intermediaryMustRemoveConnectionHeaderOnForwardRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reverse.proxy.connection.established/client",
        "${app}/reverse.proxy.connection.established/proxy",
        "${app}/reverse.proxy.connection.established/backend" })
    public void reverseProxyConnectionEstablished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/proxy.must.not.retry.non.idempotent.requests/client",
        "${app}/proxy.must.not.retry.non.idempotent.requests/proxy",
        "${app}/proxy.must.not.retry.non.idempotent.requests/backend" })
    public void proxyMustNotRetryNonIdempotentRequests() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/send.end.after.upgrade.request.completed/client",
        "${app}/send.end.after.upgrade.request.completed/server" })
    public void shouldSendEndWhenEndReceivedAfterUpgradeRequestCompleted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/upgrade.request.and.abort/client",
        "${app}/upgrade.request.and.abort/server" })
    public void serverGettingAbortShouldPropagateAbortOnAllDirections() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/request.response/client",
        "${app}/request.response/server" })
    public void requestAndResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/request.with.header.override/client",
        "${app}/request.with.header.override/server" })
    public void shouldProxyRequestWithHeaderOverride() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/request.authority.with.no.port/client",
        "${app}/request.authority.with.no.port/server" })
    public void requestAuthorityWithNoPort() throws Exception
    {
        k3po.finish();
    }
}
