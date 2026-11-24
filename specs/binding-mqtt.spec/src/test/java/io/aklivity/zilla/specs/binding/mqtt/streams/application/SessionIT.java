/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.specs.binding.mqtt.streams.application;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class SessionIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mqtt/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);


    @Test
    @Specification({
        "${app}/session.connect/client",
        "${app}/session.connect/server"})
    public void shouldConnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.connect.non.clean.start/client",
        "${app}/session.connect.non.clean.start/server"})
    public void shouldConnectNonCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.connect.clean.start/client",
        "${app}/session.connect.clean.start/server"})
    public void shouldConnectCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.connect.with.session.expiry.never/client",
        "${app}/session.connect.with.session.expiry.never/server"})
    public void shouldConnectWithSessionExpiryNever() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.connect.redirect.support/client",
        "${app}/session.connect.redirect.support/server"})
    public void shouldConnectSupportSharding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.connect.authorization/client",
        "${app}/session.connect.authorization/server"})
    public void shouldConnectAndAuthorize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.connect.with.session.expiry/client",
        "${app}/session.connect.with.session.expiry/server"})
    public void shouldConnectWithSessionExpiry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.connect.override.session.expiry/client",
        "${app}/session.connect.override.session.expiry/server"})
    public void shouldConnectServerOverridesSessionExpiry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.exists.clean.start/client",
        "${app}/session.exists.clean.start/server"})
    public void shouldRemoveSessionAtCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.will.message.abort/client",
        "${app}/session.will.message.abort/server"})
    public void shouldAbortSessionStreamWhenWillDelivery() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.will.message.normal.disconnect/client",
        "${app}/session.will.message.normal.disconnect/server"})
    public void shouldSendReasonForEndAfterNormalClientDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.will.message.max.delay/client",
        "${app}/session.will.message.max.delay/server"})
    public void shouldSendWillMessageWithMaximumDelay() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.will.message.10k/client",
        "${app}/session.will.message.10k/server"})
    public void shouldSendWillMessage10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.will.message.32k/client",
        "${app}/session.will.message.32k/server"})
    public void shouldSendWillMessage32k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.will.message.end.without.disconnect/client",
        "${app}/session.will.message.end.without.disconnect/server"})
    public void shouldEndWithoutDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.will.message.abort.while.deferred/client",
        "${app}/session.will.message.abort.while.deferred/server"})
    public void shouldAbortWhileDeferred() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.will.message.disconnect.while.deferred/client",
        "${app}/session.will.message.disconnect.while.deferred/server"})
    public void shouldDisconnectWhileDeferred() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.will.message.zero.window.on.connect/client",
        "${app}/session.will.message.zero.window.on.connect/server"})
    public void shouldSendWillMessageWhenSessionWindowStartsAtZero() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.will.message.with.user.properties/client",
        "${app}/session.will.message.with.user.properties/server"})
    public void shouldSendWillMessageWithUserProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.subscribe/client",
        "${app}/session.subscribe/server"})
    public void shouldSubscribeSaveSubscriptionsInSession() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.2-15]
    @Test
    @Specification({
        "${app}/session.will.message.retain/client",
        "${app}/session.will.message.retain/server"})
    public void shouldConnectWithWillMessageWithRetain() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.abort.reconnect.non.clean.start/client",
        "${app}/session.abort.reconnect.non.clean.start/server"})
    public void shouldReconnectNonCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.client.takeover/client",
        "${app}/session.client.takeover/server"})
    public void shouldTakeOverSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.client.takeover.persistent/client",
        "${app}/session.client.takeover.persistent/server"})
    public void shouldTakeOverPersistentSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.client.takeover.persistent.stored/client",
        "${app}/session.client.takeover.persistent.stored/server"})
    public void shouldTakeOverPersistentSessionStored() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.client.takeover.persistent.pipelined.subscribe/client",
        "${app}/session.client.takeover.persistent.pipelined.subscribe/server"})
    public void shouldTakeOverPersistentSessionPipelinedSubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.unsubscribe.after.subscribe/client",
        "${app}/session.unsubscribe.after.subscribe/server"})
    public void shouldUnsubscribeAndUpdateSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.subscribe.via.session.state/client",
        "${app}/session.subscribe.via.session.state/server"})
    public void shouldReceiveMessageSubscribedViaSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.unsubscribe.via.session.state/client",
        "${app}/session.unsubscribe.via.session.state/server"})
    public void shouldUnsubscribeViaSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.unsubscribe.after.subscribe.deferred/client",
        "${app}/session.unsubscribe.after.subscribe.deferred/server"})
    public void shouldUnsubscribeAfterSubscribeDeferred() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.server.redirect.before.connack/client",
        "${app}/session.server.redirect.before.connack/server"})
    public void shouldRedirectBeforeConnack() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.server.sent.reset.before.connack/client",
        "${app}/session.server.sent.reset.before.connack/server"})
    public void shouldResetBeforeConnack() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.server.sent.reset.buffered.disconnect/client",
        "${app}/session.server.sent.reset.buffered.disconnect/server"})
    public void shouldDisconnectWhenServerSentResetWithBufferedDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.server.sent.reset.after.connack/client",
        "${app}/session.server.sent.reset.after.connack/server"})
    public void shouldResetAfterConnack() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.server.redirect.after.connack.with.reason.and.user.property/client",
        "${app}/session.server.redirect.after.connack.with.reason.and.user.property/server"})
    public void shouldRedirectAfterConnackWithReasonAndUserProperty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.server.redirect.after.connack/client",
        "${app}/session.server.redirect.after.connack/server"})
    public void shouldRedirectAfterConnack() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.subscribe.invalid.state/client",
        "${app}/session.subscribe.invalid.state/server"})
    public void shouldSubscribeInvalidSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.subscribe.multiple.isolated/client",
        "${app}/session.subscribe.multiple.isolated/server"})
    public void shouldSubscribeMultipleSaveSubscriptionsInSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.subscribe.publish.routing/client",
        "${app}/session.subscribe.publish.routing/server"})
    public void shouldSubscribeAndPublishToNonDefaultRoute() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.reject.non.compacted.sessions.topic/client",
        "${app}/session.reject.non.compacted.sessions.topic/server"})
    public void shouldRejectSessionNonCompactedSessionsTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.invalid.session.timeout.after.connack/client",
        "${app}/session.invalid.session.timeout.after.connack/server"})
    public void shouldPropagateMqttReasonCodeAndStringAfterConnack() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.invalid.session.timeout.before.connack/client",
        "${app}/session.invalid.session.timeout.before.connack/server"})
    public void shouldPropagateMqttReasonCodeAndStringBeforeConnack() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.publish.qos.max.per.connection/client",
        "${app}/session.publish.qos.max.per.connection/server"})
    public void shouldRejectPublishQosAboveSessionMaximumPerConnection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.subscribe.unroutable.resume/client",
        "${app}/session.subscribe.unroutable.resume/server"})
    public void shouldSessionSubscribeUnroutableResume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.subscribe.unroutable.resume.session.expiry.never/client",
        "${app}/session.subscribe.unroutable.resume.session.expiry.never/server"})
    public void shouldSessionSubscribeUnroutableResumeSessionExpiryNever() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.subscribe.non.successful.resume/client",
        "${app}/session.subscribe.non.successful.resume/server"})
    public void shouldSessionSubscribeNonSuccessfulResume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.subscribe.non.successful.resume.session.expiry.never/client",
        "${app}/session.subscribe.non.successful.resume.session.expiry.never/server"})
    public void shouldSessionSubscribeNonSuccessfulResumeSessionExpiryNever() throws Exception
    {
        k3po.finish();
    }
    @Test
    @Specification({
        "${app}/session.resume.subscription.id.127/client",
        "${app}/session.resume.subscription.id.127/server"})
    public void shouldSessionResumeSubscriptionId127() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.resume.session.state.version.1.generated.subscription.id/client",
        "${app}/session.resume.session.state.version.1.generated.subscription.id/server"})
    public void shouldSessionResumeSessionStateVersion1GeneratedSubscriptionId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.abort.reconnect.non.clean.start.v4/client",
        "${app}/session.abort.reconnect.non.clean.start.v4/server"})
    public void shouldSessionAbortReconnectNonCleanStartV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.client.takeover.v4/client",
        "${app}/session.client.takeover.v4/server"})
    public void shouldSessionClientTakeoverV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.exists.clean.start.v4/client",
        "${app}/session.exists.clean.start.v4/server"})
    public void shouldSessionExistsCleanStartV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.subscribe.multiple.isolated.v4/client",
        "${app}/session.subscribe.multiple.isolated.v4/server"})
    public void shouldSessionSubscribeMultipleIsolatedV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.subscribe.publish.routing.v4/client",
        "${app}/session.subscribe.publish.routing.v4/server"})
    public void shouldSessionSubscribePublishRoutingV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.subscribe.v4/client",
        "${app}/session.subscribe.v4/server"})
    public void shouldSessionSubscribeV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.unsubscribe.after.subscribe.deferred.v4/client",
        "${app}/session.unsubscribe.after.subscribe.deferred.v4/server"})
    public void shouldSessionUnsubscribeAfterSubscribeDeferredV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.unsubscribe.after.subscribe.v4/client",
        "${app}/session.unsubscribe.after.subscribe.v4/server"})
    public void shouldSessionUnsubscribeAfterSubscribeV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.unsubscribe.via.session.state.v4/client",
        "${app}/session.unsubscribe.via.session.state.v4/server"})
    public void shouldSessionUnsubscribeViaSessionStateV4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.abort.on.network.error/client",
        "${app}/session.abort.on.network.error/server"})
    public void shouldAbortOnNetworkError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.connect.shared.subscriptions.not.supported/client",
        "${app}/session.connect.shared.subscriptions.not.supported/server"})
    public void shouldSessionConnectSharedSubscriptionsNotSupported() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.2.2-5]
    @Test
    @Specification({
        "${app}/session.will.reject.will.qos.exceeds.publish.qos.max/client",
        "${app}/session.will.reject.will.qos.exceeds.publish.qos.max/server"})
    public void shouldRejectWillQosExceedsPublishQosMax() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.0-2]
    @Test
    @Specification({
        "${app}/session.reject.second.connect.before.connack/client",
        "${app}/session.reject.second.connect.before.connack/server"})
    public void shouldRejectSecondConnectBeforeConnack() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.2.0-1]
    @Test
    @Specification({
        "${app}/session.ping.before.connack/client",
        "${app}/session.ping.before.connack/server"})
    public void shouldPingBeforeConnack() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.2.0-1]
    @Test
    @Specification({
        "${app}/session.client.takeover.before.connack/client",
        "${app}/session.client.takeover.before.connack/server"})
    public void shouldClientTakeOverBeforeConnack() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.2.2-7]
    @Test
    @Specification({
        "${app}/session.keep.alive.timeout.before.connack/client",
        "${app}/session.keep.alive.timeout.before.connack/server"})
    public void shouldAbortSessionAfterKeepAliveTimeoutBeforeConnack() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.14.2.2]
    @Test
    @Specification({
        "${app}/session.server.redirect.with.reason.after.connack/client",
        "${app}/session.server.redirect.with.reason.after.connack/server"})
    public void shouldRedirectWithReasonAfterConnack() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.14.2.2.2]
    @Test
    @Specification({
        "${app}/session.disconnect.update.session.expiry/client",
        "${app}/session.disconnect.update.session.expiry/server"})
    public void shouldEndSessionWithUpdatedSessionExpiry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.connect.non.clean.start.store.empty.state/client",
        "${app}/session.connect.non.clean.start.store.empty.state/server"})
    public void shouldConnectNonCleanStartStoreEmptyState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.connect.non.clean.start.store.held.state/client",
        "${app}/session.connect.non.clean.start.store.held.state/server"})
    public void shouldConnectNonCleanStartStoreHeldState() throws Exception
    {
        k3po.finish();
    }
}
