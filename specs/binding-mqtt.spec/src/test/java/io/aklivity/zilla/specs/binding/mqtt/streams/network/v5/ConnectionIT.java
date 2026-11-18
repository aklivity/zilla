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
package io.aklivity.zilla.specs.binding.mqtt.streams.network.v5;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class ConnectionIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network/v5");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/connect.successful/client",
        "${net}/connect.successful/server"})
    public void shouldConnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.username.authentication.successful/client",
        "${net}/connect.username.authentication.successful/server"})
    public void shouldAuthenticateUsernameAndConnect() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.4], MQTT 5.0 3.1.4 [MQTT-3.1.4-2]
    @Test
    @Specification({
        "${net}/connect.route.not.authorized/client",
        "${net}/connect.route.not.authorized/server"})
    public void shouldConnectRouteNotAuthorized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.username.authentication.failed/client",
        "${net}/connect.username.authentication.failed/server"})
    public void shouldFailUsernameAuthentication() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.password.authentication.successful/client",
        "${net}/connect.password.authentication.successful/server"})
    public void shouldAuthenticatePasswordAndConnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.password.authentication.failed/client",
        "${net}/connect.password.authentication.failed/server"})
    public void shouldFailPasswordAuthentication() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.server.assigned.client.id/client",
        "${net}/connect.server.assigned.client.id/server"})
    public void shouldConnectWithServerAssignedClientId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.reject.missing.client.id/client",
        "${net}/connect.reject.missing.client.id/server"})
    public void shouldRejectMissingClientId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.reject.exceeding.max.client.id/client",
        "${net}/connect.reject.exceeding.max.client.id/server"})
    public void shouldRejectExceedingClientIdMax() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/disconnect/client",
        "${net}/disconnect/server"})
    public void shouldConnectThenDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.invalid.protocol.version/client",
        "${net}/connect.invalid.protocol.version/server"})
    public void shouldRejectInvalidProtocolVersion() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.unsupported.protocol.version/client",
        "${net}/connect.unsupported.protocol.version/server"})
    public void shouldRejectUnsupportedProtocolVersion() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.invalid.flags/client",
        "${net}/connect.invalid.flags/server"})
    public void shouldRejectMalformedConnectPacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.invalid.authentication.method/client",
        "${net}/connect.invalid.authentication.method/server"})
    public void shouldRejectBadAuthenticationMethod() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/disconnect.reject.invalid.fixed.header.flags/client",
        "${net}/disconnect.reject.invalid.fixed.header.flags/server"})
    public void shouldRejectMalformedDisconnectPacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.reject.second.connect/client",
        "${net}/connect.reject.second.connect/server"})
    public void shouldRejectSecondConnectPacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.reject.topic.alias.maximum.repeated/client",
        "${net}/connect.reject.topic.alias.maximum.repeated/server"})
    public void shouldRejectConnectWhenTopicAliasMaximumRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.successful.fragmented/client",
        "${net}/connect.successful.fragmented/server"})
    public void shouldConnectFragmented() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.0-1]
    @Test
    @Specification({
        "${net}/connect.reject.other.packet.before.connect/client",
        "${net}/connect.reject.other.packet.before.connect/server"})
    public void shouldRejectOtherPacketBeforeConnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.close/client",
        "${net}/client.sent.close/server"})
    public void shouldReceiveClientSentClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.abort/client",
        "${net}/client.sent.abort/server"})
    public void shouldReceiveClientSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.reset/client",
        "${net}/client.sent.reset/server"})
    public void shouldReceiveClientSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/disconnect.after.keep.alive.timeout/client",
        "${net}/disconnect.after.keep.alive.timeout/server"})
    public void shouldDisconnectClientAfterKeepAliveTimeout() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.2-21], [MQTT-3.2.2-21]
    @Test
    @Specification({
        "${net}/connect.server.defined.keep.alive/client",
        "${net}/connect.server.defined.keep.alive/server"})
    public void shouldConnectAndUseServerDefinedKeepAlive() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.2-21], [MQTT-3.2.2-21]
    @Test
    @Specification({
        "${net}/connect.server.defined.keep.alive.default.minimum/client",
        "${net}/connect.server.defined.keep.alive.default.minimum/server"})
    public void shouldConnectAndUseServerDefinedKeepAliveDefaultMinimum() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.timeout.before.connect/client",
        "${net}/connect.timeout.before.connect/server"})
    public void shouldTimeoutBeforeConnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.maximum.qos.0/client",
        "${net}/connect.maximum.qos.0/server"})
    public void shouldConnectWithMaximumQos0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.retain.not.supported/client",
        "${net}/connect.retain.not.supported/server"})
    public void shouldConnectWithRetainNotSupported() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.2.2-13]
    @Test
    @Specification({
        "${net}/connect.reject.will.retain.not.supported/client",
        "${net}/connect.reject.will.retain.not.supported/server"})
    public void shouldRejectConnectWillRetainNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Ignore
    @Test
    @Specification({
        "${net}/connect.reject.username.not.authorized/client",
        "${net}/connect.reject.username.not.authorized/server"})
    public void shouldRejectConnectWithUsernameNotAuthorized() throws Exception
    {
        k3po.finish();
    }

    @Ignore
    @Test
    @Specification({
        "${net}/connect.reject.password.not.authorized/client",
        "${net}/connect.reject.password.not.authorized/server"})
    public void shouldRejectConnectWithPasswordNotAuthorized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.will.invalid.will.qos/client",
        "${net}/connect.will.invalid.will.qos/server"})
    public void shouldRejectInvalidWillQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.will.reject.will.qos.1.without.will.flag/client",
        "${net}/connect.will.reject.will.qos.1.without.will.flag/server"})
    public void shouldRejectWillQos1WithoutWillFlag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.will.reject.will.qos.2.without.will.flag/client",
        "${net}/connect.will.reject.will.qos.2.without.will.flag/server"})
    public void shouldRejectWillQos2WithoutWillFlag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.will.reject.will.retain.without.will.flag/client",
        "${net}/connect.will.reject.will.retain.without.will.flag/server"})
    public void shouldRejectWillRetainWithoutWillFlag() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.2-9]
    @Test
    @Specification({
        "${net}/connect.reject.will.payload.missing/client",
        "${net}/connect.reject.will.payload.missing/server"})
    public void shouldRejectConnectWillPayloadMissing() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.2-9]
    @Test
    @Specification({
        "${net}/connect.reject.will.properties.missing/client",
        "${net}/connect.reject.will.properties.missing/server"})
    public void shouldRejectConnectWillPropertiesMissing() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.2-9]
    @Test
    @Specification({
        "${net}/connect.reject.will.topic.missing/client",
        "${net}/connect.reject.will.topic.missing/server"})
    public void shouldRejectConnectWillTopicNotMissing() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.2-16]
    @Test
    @Specification({
        "${net}/connect.reject.username.flag.missing/client",
        "${net}/connect.reject.username.flag.missing/server"})
    public void shouldRejectUsernameUserFlagMissing() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.2-18]
    @Test
    @Specification({
        "${net}/connect.reject.password.no.password.flag/client",
        "${net}/connect.reject.password.no.password.flag/server"})
    public void shouldRejectPasswordWhenMissingPasswordFlag() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.2-17]
    @Test
    @Specification({
        "${net}/connect.reject.username.flag.only/client",
        "${net}/connect.reject.username.flag.only/server"})
    public void shouldRejectConnectUsernameFlagOnly() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.2-19]
    @Test
    @Specification({
        "${net}/connect.reject.password.flag.no.password/client",
        "${net}/connect.reject.password.flag.no.password/server"})
    public void shouldRejectPasswordFlagWhenMissingPassword() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.subscribe.batched/client",
        "${net}/connect.subscribe.batched/server"})
    public void shouldConnectAndSubscribeFalseStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/disconnect.after.subscribe.and.publish/client",
        "${net}/disconnect.after.subscribe.and.publish/server"})
    public void shouldDisconnectAfterSubscribeAndPublish() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/disconnect.invalid.session.expiry/client",
        "${net}/disconnect.invalid.session.expiry/server"})
    public void shouldRejectInvalidSessionExpiryOnDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.non.successful.connack/client",
        "${net}/connect.non.successful.connack/server"})
    public void shouldResetWithReasonCodeOnNonSuccessfulConnack() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.non.successful.disconnect/client",
        "${net}/connect.non.successful.disconnect/server"})
    public void shouldResetWithReasonCodeOnNonSuccessfulDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.delegate.connack.properties/client",
        "${net}/connect.delegate.connack.properties/server"})
    public void shouldDelegateConnackProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/disconnect.no.reasoncode.no.properties/client",
        "${net}/disconnect.no.reasoncode.no.properties/server"})
    public void shouldConnectThenDisconnectWithNoReasonCodeNoProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.username.reauthorize.after.disconnect/client",
        "${net}/connect.username.reauthorize.after.disconnect/server"})
    public void shouldReauthorizeUsernameAfterDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.abort.before.begin/client",
        "${net}/server.sent.abort.before.begin/server"})
    public void shouldReceiveServerSentAbortBeforeBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.max.packet.size.other.connection.publish/client",
        "${net}/connect.max.packet.size.other.connection.publish/server"})
    public void shouldPublishLargerThanOtherConnectionMaximumPacketSize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.max.packet.size.other.connection.subscribe/client",
        "${net}/connect.max.packet.size.other.connection.subscribe/server"})
    public void shouldSubscribeLargerThanOtherConnectionMaximumPacketSize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.reject.maximum.packet.size.repeated/client",
        "${net}/connect.reject.maximum.packet.size.repeated/server"})
    public void shouldRejectRepeatedMaximumPacketSize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.max.packet.size.exceeded.by.packet.overhead/client",
        "${net}/connect.max.packet.size.exceeded.by.packet.overhead/server"})
    public void shouldDropPublishWhenPacketExceedsMaximumPacketSize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.reject.receive.maximum.zero/client",
        "${net}/connect.reject.receive.maximum.zero/server"})
    public void shouldRejectZeroReceiveMaximum() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.reject.receive.maximum.repeated/client",
        "${net}/connect.reject.receive.maximum.repeated/server"})
    public void shouldRejectRepeatedReceiveMaximum() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.publish.maximum.qos.1/client",
        "${net}/connect.publish.maximum.qos.1/server"})
    public void shouldConnectWithPublishMaximumQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.publish.maximum.qos.0/client",
        "${net}/connect.publish.maximum.qos.0/server"})
    public void shouldConnectWithPublishMaximumQos0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.will.reject.will.qos.2.publish.maximum.qos.1/client",
        "${net}/connect.will.reject.will.qos.2.publish.maximum.qos.1/server"})
    public void shouldRejectWillQos2WhenPublishMaximumQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.reject.invalid.property.identifier/client",
        "${net}/connect.reject.invalid.property.identifier/server"})
    public void shouldConnectRejectInvalidPropertyIdentifier() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.will.reject.invalid.property.identifier/client",
        "${net}/connect.will.reject.invalid.property.identifier/server"})
    public void shouldConnectWillRejectInvalidPropertyIdentifier() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/disconnect.reject.invalid.property.identifier/client",
        "${net}/disconnect.reject.invalid.property.identifier/server"})
    public void shouldDisconnectRejectInvalidPropertyIdentifier() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.connack.invalid.property.identifier/client",
        "${net}/server.sent.connack.invalid.property.identifier/server"})
    public void shouldServerSentConnackInvalidPropertyIdentifier() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.second.connack/client",
        "${net}/server.sent.second.connack/server"})
    public void shouldServerSentSecondConnack() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.disconnect.invalid.property.identifier/client",
        "${net}/server.sent.disconnect.invalid.property.identifier/server"})
    public void shouldServerSentDisconnectInvalidPropertyIdentifier() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.disconnect.property.not.valid/client",
        "${net}/server.sent.disconnect.property.not.valid/server"})
    public void shouldServerSentDisconnectPropertyNotValid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.disconnect.reason.string.repeated/client",
        "${net}/server.sent.disconnect.reason.string.repeated/server"})
    public void shouldServerSentDisconnectReasonStringRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.disconnect.server.reference.repeated/client",
        "${net}/server.sent.disconnect.server.reference.repeated/server"})
    public void shouldServerSentDisconnectServerReferenceRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.unknown.packet.type/client",
        "${net}/server.sent.unknown.packet.type/server"})
    public void shouldServerSentUnknownPacketType() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.packet.too.large/client",
        "${net}/server.sent.packet.too.large/server"})
    public void shouldServerSentPacketTooLarge() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-1]
    @Test
    @Specification({
        "${net}/connect.reject.client.id.ill.formed.utf8/client",
        "${net}/connect.reject.client.id.ill.formed.utf8/server"})
    public void shouldRejectConnectClientIdIllFormedUtf8() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-2]
    @Test
    @Specification({
        "${net}/connect.reject.client.id.null.character/client",
        "${net}/connect.reject.client.id.null.character/server"})
    public void shouldRejectConnectClientIdNullCharacter() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-1]
    @Test
    @Specification({
        "${net}/connect.reject.user.name.ill.formed.utf8/client",
        "${net}/connect.reject.user.name.ill.formed.utf8/server"})
    public void shouldRejectConnectUserNameIllFormedUtf8() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-2]
    @Test
    @Specification({
        "${net}/connect.reject.user.name.null.character/client",
        "${net}/connect.reject.user.name.null.character/server"})
    public void shouldRejectConnectUserNameNullCharacter() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-1]
    @Test
    @Specification({
        "${net}/connect.reject.will.topic.ill.formed.utf8/client",
        "${net}/connect.reject.will.topic.ill.formed.utf8/server"})
    public void shouldRejectConnectWillTopicIllFormedUtf8() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-2]
    @Test
    @Specification({
        "${net}/connect.reject.will.topic.null.character/client",
        "${net}/connect.reject.will.topic.null.character/server"})
    public void shouldRejectConnectWillTopicNullCharacter() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-1]
    @Test
    @Specification({
        "${net}/connect.reject.will.content.type.ill.formed.utf8/client",
        "${net}/connect.reject.will.content.type.ill.formed.utf8/server"})
    public void shouldRejectConnectWillContentTypeIllFormedUtf8() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-1]
    @Test
    @Specification({
        "${net}/connect.reject.will.response.topic.ill.formed.utf8/client",
        "${net}/connect.reject.will.response.topic.ill.formed.utf8/server"})
    public void shouldRejectConnectWillResponseTopicIllFormedUtf8() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-1]
    @Test
    @Specification({
        "${net}/connect.reject.will.user.property.key.ill.formed.utf8/client",
        "${net}/connect.reject.will.user.property.key.ill.formed.utf8/server"})
    public void shouldRejectConnectWillUserPropertyKeyIllFormedUtf8() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-1]
    @Test
    @Specification({
        "${net}/connect.reject.will.user.property.value.ill.formed.utf8/client",
        "${net}/connect.reject.will.user.property.value.ill.formed.utf8/server"})
    public void shouldRejectConnectWillUserPropertyValueIllFormedUtf8() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-1]
    @Test
    @Specification({
        "${net}/server.sent.connack.assigned.client.id.ill.formed.utf8/client",
        "${net}/server.sent.connack.assigned.client.id.ill.formed.utf8/server"})
    public void shouldServerSentConnackAssignedClientIdIllFormedUtf8() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.4-2]
    @Test
    @Specification({
        "${net}/server.sent.connack.assigned.client.id.null.character/client",
        "${net}/server.sent.connack.assigned.client.id.null.character/server"})
    public void shouldServerSentConnackAssignedClientIdNullCharacter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/disconnect.after.shared.subscription/client",
        "${net}/disconnect.after.shared.subscription/server"})
    public void shouldDisconnectAfterSharedSubscription() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.shared.subscriptions.not.supported/client",
        "${net}/connect.shared.subscriptions.not.supported/server"})
    public void shouldConnectSharedSubscriptionsNotSupported() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.4-1]
    @Test
    @Specification({
        "${net}/connect.reject.will.topic.no.will.flag/client",
        "${net}/connect.reject.will.topic.no.will.flag/server"})
    public void shouldRejectWillTopicWhenMissingWillFlag() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.0-2]
    @Test
    @Specification({
        "${net}/connect.reject.second.connect.before.connack/client",
        "${net}/connect.reject.second.connect.before.connack/server"})
    public void shouldRejectSecondConnectBeforeConnack() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.2.2-7]
    @Test
    @Specification({
        "${net}/connect.keep.alive.timeout.before.connack/client",
        "${net}/connect.keep.alive.timeout.before.connack/server"})
    public void shouldCloseWithoutConnackAfterKeepAliveTimeoutBeforeConnack() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.14.2.2.3]
    @Test
    @Specification({
        "${net}/disconnect.reason.string/client",
        "${net}/disconnect.reason.string/server"})
    public void shouldDisconnectWithReasonString() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.14.2.2.4]
    @Test
    @Specification({
        "${net}/disconnect.user.property/client",
        "${net}/disconnect.user.property/server"})
    public void shouldDisconnectWithUserProperty() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.14.2.2.2]
    @Test
    @Specification({
        "${net}/disconnect.session.expiry/client",
        "${net}/disconnect.session.expiry/server"})
    public void shouldDisconnectWithSessionExpiryAfterConnectWithSessionExpiry() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.14.2.2.5]
    @Test
    @Specification({
        "${net}/disconnect.reject.server.reference/client",
        "${net}/disconnect.reject.server.reference/server"})
    public void shouldRejectDisconnectWithServerReference() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-2.1.3-1]
    @Test
    @Specification({
        "${net}/disconnect.reject.invalid.fixed.header.flags.no.reason.code/client",
        "${net}/disconnect.reject.invalid.fixed.header.flags.no.reason.code/server"})
    public void shouldRejectDisconnectInvalidFixedHeaderFlagsNoReasonCode() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.14.2.1]
    @Test
    @Specification({
        "${net}/disconnect.followed.by.pingreq/client",
        "${net}/disconnect.followed.by.pingreq/server"})
    public void shouldDisconnectFollowedByPingreq() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-2.1.3-1]
    @Test
    @Specification({
        "${net}/connect.reject.packet.type.zero/client",
        "${net}/connect.reject.packet.type.zero/server"})
    public void shouldRejectPacketTypeZero() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.3.2-2]
    @Test
    @Specification({
        "${net}/connect.reject.will.topic.wildcard/client",
        "${net}/connect.reject.will.topic.wildcard/server"})
    public void shouldRejectWillTopicWildcard() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.3.2-14]
    @Test
    @Specification({
        "${net}/connect.reject.will.response.topic.wildcard/client",
        "${net}/connect.reject.will.response.topic.wildcard/server"})
    public void shouldRejectWillResponseTopicWildcard() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.14.2.2.2]
    @Test
    @Specification({
        "${net}/disconnect.update.session.expiry/client",
        "${net}/disconnect.update.session.expiry/server"})
    public void shouldDisconnectWithUpdatedSessionExpiry() throws Exception
    {
        k3po.finish();
    }
}
