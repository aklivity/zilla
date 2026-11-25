/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream;

import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.INSTANCE_ID_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.LIFETIME_ID_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.PUBLISH_MAX_QOS_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.SESSION_ID_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.SESSION_STATE_STORE_ENABLED_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.SHARED_SUBSCRIPTION_AVAILABLE_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.WILL_AVAILABLE_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.WILL_ID_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.WILL_STREAM_RECONNECT_DELAY_NAME;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_CLOCK_NAME;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_TIMER_WHEEL_TICK_MILLIS_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.TestClock;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class MqttKafkaSessionProxyIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("mqtt", "io/aklivity/zilla/specs/binding/mqtt/kafka/streams/mqtt")
        .addScriptRoot("kafka", "io/aklivity/zilla/specs/binding/mqtt/kafka/streams/kafka");

    private static final String ENGINE_STREAMS_BUFFER_CAPACITY_NAME = "zilla.engine.streams.buffer.capacity";

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final TestClock clock = TestClock.of(999L);

    public final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configure(SHARED_SUBSCRIPTION_AVAILABLE_NAME, "false")
        .configure(SESSION_ID_NAME,
            "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplySessionId")
        .configure(INSTANCE_ID_NAME,
            "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyInstanceId")
        .configure(ENGINE_TIMER_WHEEL_TICK_MILLIS_NAME, "1")
        .clock(clock)
        .configure(LIFETIME_ID_NAME,
            "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyLifetimeId")
        .configure(WILL_ID_NAME,
            "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyWillId")
        .configurationRoot("io/aklivity/zilla/specs/binding/mqtt/kafka/config")
        .external("kafka0")
        .aroundStart(k3po::deferStartable)
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.abort.reconnect.non.clean.start/client",
        "${kafka}/session.abort.reconnect.non.clean.start/server"})
    public void shouldReconnectNonCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.subscribe/client",
        "${kafka}/session.subscribe/server"})
    public void shouldSubscribeSaveSubscriptionsInSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.subscribe.hash.key/client",
        "${kafka}/session.subscribe.hash.key/server"})
    public void shouldSaveSessionStateWithHashKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.log.event.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.reject.non.compacted.sessions.topic/client",
        "${kafka}/session.reject.non.compacted.sessions.topic/server"})
    public void shouldRejectSessionNonCompactedSessionsTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.log.event.retained.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.retained.topic.not.compacted/client",
        "${kafka}/session.retained.topic.not.compacted/server"})
    public void shouldAdvertiseRetainedUnavailableWhenRetainedTopicNotCompacted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.subscribe.via.session.state/client",
        "${kafka}/session.subscribe.via.session.state/server"})
    public void shouldReceiveMessageSubscribedViaSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.subscribe.via.session.state.multiple.partitions/client",
        "${kafka}/session.subscribe.via.session.state.multiple.partitions/server"})
    public void shouldRestoreSessionStateFromMultiplePartitions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.subscribe.shared.via.session.state/client",
        "${kafka}/session.subscribe.shared.via.session.state/server"})
    public void shouldReceiveSharedSubscriptionViaSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.unsubscribe.after.subscribe/client",
        "${kafka}/session.unsubscribe.after.subscribe/server"})
    public void shouldUnsubscribeAndUpdateSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.unsubscribe.via.session.state/client",
        "${kafka}/session.unsubscribe.via.session.state/server"})
    public void shouldUnsubscribeViaSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.client.sent.reset/client",
        "${kafka}/session.client.sent.reset/server"})
    public void shouldSessionStreamReceiveClientSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.flush.unknown.packet.id/client",
        "${kafka}/session.flush.unknown.packet.id/server"})
    public void shouldAnswerSessionFlushWithUnknownPacketId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.server.sent.reset/client",
        "${kafka}/session.server.sent.reset/server"})
    public void shouldSessionStreamReceiveServerSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.close.reconnect.non.expiring/client",
        "${kafka}/session.close.reconnect.non.expiring/server"})
    public void shouldReconnectNonExpiringSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.close.expire.session.state.large.interval/client",
        "${kafka}/session.close.expire.session.state.large.interval/server"})
    public void shouldExpireSessionOnCloseAfterLargeInterval() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.close.expire.session.state.immediately/client",
        "${kafka}/session.close.expire.session.state.immediately/server"})
    public void shouldExpireSessionOnCloseImmediately() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.close.reconnect.clean.start/client",
        "${kafka}/session.close.reconnect.clean.start/server"})
    public void shouldReconnectCleanStartAfterNonExpiringSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/session.clean.start.discard.offsets/client",
        "${kafka}/session.clean.start.discard.offsets/server"})
    public void shouldDiscardOffsetsOnCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = SESSION_STATE_STORE_ENABLED_NAME, value = "true")
    @Specification({
        "${mqtt}/session.store.clean.start/client",
        "${kafka}/session.store.clean.start/server"})
    public void shouldKeepNoKafkaSessionStateOnCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = SESSION_STATE_STORE_ENABLED_NAME, value = "true")
    @Specification({
        "${mqtt}/session.store.clean.start.abort/client",
        "${kafka}/session.store.clean.start.abort/server"})
    public void shouldKeepNoKafkaSessionStateOnCleanStartAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = SESSION_STATE_STORE_ENABLED_NAME, value = "true")
    @Specification({
        "${mqtt}/session.store.clean.start.qos.max.1/client",
        "${kafka}/session.store.clean.start.qos.max.1/server"})
    public void shouldKeepNoKafkaSessionStateOnCleanStartWhenPublishQosMax1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = SESSION_STATE_STORE_ENABLED_NAME, value = "true")
    @Specification({
        "${mqtt}/session.store.subscribe.qos.max.1/client",
        "${kafka}/session.store.subscribe.qos.max.1/server"})
    public void shouldEchoSubscribeStateWithoutKafkaSessionStateWhenPublishQosMax1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = SESSION_STATE_STORE_ENABLED_NAME, value = "true")
    @Specification({
        "${mqtt}/session.store.unsubscribe.after.subscribe.qos.max.1/client",
        "${kafka}/session.store.unsubscribe.after.subscribe.qos.max.1/server"})
    public void shouldEchoUnsubscribeStateWithoutKafkaSessionStateWhenPublishQosMax1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = SESSION_STATE_STORE_ENABLED_NAME, value = "true")
    @Specification({
        "${mqtt}/session.store.persistent.subscribe.qos.max.1/client",
        "${kafka}/session.store.clean.start.qos.max.1/server"})
    public void shouldKeepNoKafkaSessionStateOnPersistentSessionWhenPublishQosMax1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = SESSION_STATE_STORE_ENABLED_NAME, value = "true")
    @Specification({
        "${mqtt}/session.clean.start.discard.offsets/client",
        "${kafka}/session.store.persistent.clean.start.no.offsets.topic/server"})
    public void shouldDiscardOffsetsOnCleanStartWithSessionExpiryWhenSessionStateStore() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = SESSION_STATE_STORE_ENABLED_NAME, value = "true")
    @Specification({
        "${mqtt}/session.clean.start.qos.max.1/client",
        "${kafka}/session.store.persistent.clean.start.reset.group.offsets.coordinator.not.available.qos.max.1/server"})
    public void shouldResetGroupOffsetsOnCleanStartWithSessionExpiryWhenSessionStateStoreAndPublishQosMax1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = SESSION_STATE_STORE_ENABLED_NAME, value = "true")
    @Specification({
        "${mqtt}/session.will.message.clean.start/client",
        "${kafka}/session.store.will.message.clean.start/server"})
    public void shouldGenerateLifeTimeIdOnCleanStartWithWillWhenSessionStateStore() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = SESSION_STATE_STORE_ENABLED_NAME, value = "true")
    @Specification({
        "${mqtt}/session.store.persistent.close.reconnect.non.expiring/client",
        "${kafka}/session.store.persistent.close.reconnect.non.expiring/server"})
    public void shouldReconnectNonExpiringSessionWhenSessionStateStore() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/session.clean.start.discard.offsets/client",
        "${kafka}/session.clean.start.reset.group.offsets.negotiated/server"})
    public void shouldResetGroupOffsetsOnCleanStartNegotiated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/session.clean.start.discard.offsets/client",
        "${kafka}/session.clean.start.reset.group.offsets.partition.error/server"})
    public void shouldResetGroupOffsetsOnCleanStartPartitionError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/session.clean.start.discard.offsets/client",
        "${kafka}/session.clean.start.reset.group.offsets.group.not.found/server"})
    public void shouldResetGroupOffsetsOnCleanStartGroupNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/session.clean.start.discard.offsets/client",
        "${kafka}/session.clean.start.reset.group.offsets.coordinator.not.available/server"})
    public void shouldResetGroupOffsetsOnCleanStartCoordinatorNotAvailable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/session.clean.start.discard.offsets/client",
        "${kafka}/session.clean.start.reset.group.offsets.broker.zero/server"})
    public void shouldResetGroupOffsetsOnCleanStartBrokerZero() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.clean.start.qos.max.1/client",
        "${kafka}/session.clean.start.reset.group.offsets.coordinator.not.available.qos.max.1/server"})
    public void shouldResetGroupOffsetsOnCleanStartCoordinatorNotAvailableWhenPublishQosMax1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.close.expire.session.offsets.immediately/client",
        "${kafka}/session.close.expire.session.offsets.immediately/server"})
    public void shouldExpireSessionAndOffsetsImmediately() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_EXPIRE_AT_SIGNAL");
        clock.advanceTo(2001L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1L))
        {
            k3po.finish();
        }
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.close.expire.session.offsets.immediately/client",
        "${kafka}/session.close.expire.session.reset.group.offsets/server"})
    public void shouldResetGroupOffsetsOnSessionExpiry() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_EXPIRE_AT_SIGNAL");
        clock.advanceTo(2001L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1L))
        {
            k3po.finish();
        }
    }

    @Test
    @Configuration("proxy.when.publish.topic.with.messages.params.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.close.expire.session.offsets.immediately/client",
        "${kafka}/session.close.expire.session.reset.group.offsets/server"})
    public void shouldResetGroupOffsetsOnSessionExpiryWhenMessagesWithParams() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_EXPIRE_AT_SIGNAL");
        clock.advanceTo(2001L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1L))
        {
            k3po.finish();
        }
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.close.expire.session.offsets.immediately/client",
        "${kafka}/session.close.expire.session.reset.group.offsets.small.window/server"})
    public void shouldResetGroupOffsetsOnSessionExpiryAfterSmallWindow() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_EXPIRE_AT_SIGNAL");
        clock.advanceTo(2001L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1L))
        {
            k3po.finish();
        }
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.reconnect.during.expire.reset.group.offsets/client",
        "${kafka}/session.reconnect.during.expire.reset.group.offsets/server"})
    public void shouldReconnectDuringSessionExpiryGroupOffsetsReset() throws Exception
    {
        k3po.start();
        clock.advanceTo(1001L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1L))
        {
            k3po.awaitBarrier("OFFSET_DELETE_RECEIVED");
        }
        clock.advanceTo(1000L);
        k3po.notifyBarrier("CLOCK_RESET");
        k3po.awaitBarrier("RECEIVED_EXPIRE_AT_SIGNAL");
        clock.advanceTo(2001L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1L))
        {
            k3po.finish();
        }
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = ENGINE_TIMER_WHEEL_TICK_MILLIS_NAME, value = "1024")
    @Specification({
        "${mqtt}/session.close.expire.session.offsets.large.interval/client",
        "${kafka}/session.close.expire.session.offsets.large.interval/server"})
    public void shouldExpireSessionAndOffsetsAfterLargeInterval() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_EXPIRE_LATER_SIGNAL");
        clock.advanceTo(1000L);
        k3po.notifyBarrier("CLOCK_RESET");
        k3po.awaitBarrier("RECEIVED_EXPIRE_AT_SIGNAL");
        clock.advanceTo(3000001000L + 3L * 1024L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1024L))
        {
            k3po.finish();
        }
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.close.expire.session.state/client",
        "${kafka}/session.close.expire.session.state/server"})
    public void shouldExpireSessionOnClose() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_EXPIRE_AT_SIGNAL");
        clock.advanceTo(2001L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1L))
        {
            k3po.finish();
        }
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.abort.expire.session.state/client",
        "${kafka}/session.abort.expire.session.state/server"})
    public void shouldExpireSessionOnAbort() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_EXPIRE_AT_SIGNAL");
        clock.advanceTo(2001L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1L))
        {
            k3po.finish();
        }
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${kafka}/session.cancel.session.expiry/server"})
    public void shouldCancelSessionExpiry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${kafka}/session.session.expiry.fragmented/server"})
    public void shouldDecodeSessionExpirySignalFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${kafka}/session.signal.stream.credit/server"})
    public void shouldSessionSignalStreamCredit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = ENGINE_CLOCK_NAME, value = "java.time.Clock::systemUTC")
    @Specification({
        "${kafka}/session.expiry.signal.fragmented.local.ignored/server"})
    public void shouldDecodeSessionSignalAfterFragmentedLocalSignalIgnored() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = ENGINE_CLOCK_NAME, value = "java.time.Clock::systemUTC")
    @Specification({
        "${kafka}/session.expiry.signal.malformed.dropped/server"})
    public void shouldDropMalformedSessionSignal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = ENGINE_TIMER_WHEEL_TICK_MILLIS_NAME, value = "1073741824")
    @Specification({
        "${kafka}/session.expiry.after.signal.stream.restart/server"})
    public void shouldExpireSessionAfterSignalStreamRestart() throws Exception
    {
        k3po.start();
        clock.advanceTo(System.currentTimeMillis() + 1000L + 3L * 1073741824L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1073741824L))
        {
            k3po.finish();
        }
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = ENGINE_TIMER_WHEEL_TICK_MILLIS_NAME, value = "1073741824")
    @Specification({
        "${kafka}/session.expiry.after.signal.stream.restart.multiple.partitions/server"})
    public void shouldExpireSessionAfterSignalStreamRestartFromMultiplePartitions() throws Exception
    {
        k3po.start();
        clock.advanceTo(System.currentTimeMillis() + 1000L + 3L * 1073741824L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1073741824L))
        {
            k3po.finish();
        }
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = ENGINE_TIMER_WHEEL_TICK_MILLIS_NAME, value = "1073741824")
    @Specification({
        "${kafka}/session.ignore.non.fetch.signal.stream.flush/server"})
    public void shouldIgnoreNonFetchSignalStreamFlush() throws Exception
    {
        k3po.start();
        clock.advanceTo(System.currentTimeMillis() + 1000L + 3L * 1073741824L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1073741824L))
        {
            k3po.finish();
        }
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.will.message.normal.disconnect/client",
        "${kafka}/session.will.message.normal.disconnect/server"})
    public void shouldNotSendWillMessageOnNormalDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = SESSION_STATE_STORE_ENABLED_NAME, value = "true")
    @Specification({
        "${mqtt}/session.will.message.normal.disconnect/client",
        "${kafka}/session.store.will.message.normal.disconnect/server"})
    public void shouldKeepNoKafkaSessionStateOrExpirySignalOnWillSessionWhenPublishQosMax1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = SESSION_STATE_STORE_ENABLED_NAME, value = "true")
    @Specification({
        "${mqtt}/session.store.will.message.subscribe/client",
        "${kafka}/session.store.will.message.normal.disconnect/server"})
    public void shouldEchoSubscribeStateOnWillSessionWhenPublishQosMax1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.will.message.clean.start/client",
        "${kafka}/session.will.message.clean.start/server"})
    public void shouldGenerateLifeTimeIdOnCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.will.message.abort.deliver.will/client",
        "${kafka}/session.will.message.abort.deliver.will/server"})
    public void shouldSendWillMessageOnAbort() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_SESSION_EXPIRE_AT_SIGNAL");
        clock.advanceTo(2001L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1L))
        {
            k3po.finish();
        }
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.will.message.topic.trailing.empty.level/client",
        "${kafka}/session.will.message.topic.trailing.empty.level/server"})
    public void shouldSendWillMessageWithTrailingEmptyLevelTopic() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_SESSION_EXPIRE_AT_SIGNAL");
        clock.advanceTo(2001L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1L))
        {
            k3po.finish();
        }
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.will.message.abort.deliver.will.non.expiring/client",
        "${kafka}/session.will.message.abort.deliver.will.non.expiring/server"})
    public void shouldSendWillMessageOnAbortNonExpiring() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_WILL_DELIVER_AT_SIGNAL");
        clock.advanceTo(2001L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1L))
        {
            k3po.finish();
        }
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.will.message.max.delay/client",
        "${kafka}/session.will.message.max.delay/server"})
    public void shouldSendWillMessageWithMaximumDelay() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.will.message.10k.abort.deliver.will/client",
        "${kafka}/session.will.message.10k.abort.deliver.will/server"})
    public void shouldSendWillMessage10kOnAbort() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_SESSION_EXPIRE_AT_SIGNAL");
        clock.advanceTo(2001L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1L))
        {
            k3po.finish();
        }
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = SESSION_STATE_STORE_ENABLED_NAME, value = "true")
    @Specification({
        "${mqtt}/session.will.message.10k.abort.deliver.will/client",
        "${kafka}/session.store.will.message.10k.abort.deliver.will/server"})
    public void shouldSendWillMessage10kOnAbortWithoutExpirySignalWhenSessionStateStore() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_WILL_DELIVER_AT_SIGNAL");
        clock.advanceTo(2001L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1L))
        {
            k3po.finish();
        }
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = SESSION_STATE_STORE_ENABLED_NAME, value = "true")
    @Specification({
        "${mqtt}/session.store.will.message.qos2.abort.deliver.will/client",
        "${kafka}/session.store.will.message.qos2.abort.deliver.will/server"})
    public void shouldSendWillMessageQos2OnAbortWithoutExpirySignalWhenSessionStateStore() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_WILL_DELIVER_AT_SIGNAL");
        clock.advanceTo(2001L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1L))
        {
            k3po.finish();
        }
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mqtt}/session.will.message.qos2.abort.deliver.will/client",
        "${kafka}/session.will.message.qos2.abort.deliver.will/server"})
    public void shouldSendWillMessageQos2OnAbort() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_WILL_DELIVER_AT_SIGNAL");
        clock.advanceTo(2001L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1L))
        {
            k3po.finish();
        }
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = ENGINE_STREAMS_BUFFER_CAPACITY_NAME, value = "8192")
    @Specification({
        "${mqtt}/session.will.message.abort.deliver.will.signal.buffer.reuse/client",
        "${kafka}/session.will.message.abort.deliver.will.signal.buffer.reuse/server"})
    public void shouldSendWillMessageOnAbortAfterSignalBufferReuse() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_SESSION_EXPIRE_AT_SIGNAL");
        k3po.awaitBarrier("RECEIVED_WILL_SIGNAL_TOMBSTONES");
        clock.advanceTo(2001L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1L))
        {
            k3po.finish();
        }
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.will.message.abort.deliver.will/client",
        "${kafka}/session.will.message.will.id.mismatch.skip.delivery/server"})
    public void shouldNotSendWillMessageOnWillIdMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/session.will.message.qos2.abort.before.session.established/client",
        "${kafka}/session.will.message.qos2.abort.before.session.established/server"})
    public void shouldSendWillSignalOnQos2AbortBeforeSessionEstablished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/session.qos2.end.before.session.established/client",
        "${kafka}/session.qos2.end.before.session.established/server"})
    public void shouldEndQos2SessionBeforeSessionStreamEstablished() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/session.qos2.meta.reset/client",
        "${kafka}/session.qos2.meta.reset/server"})
    public void shouldResetMqttSessionWhenMetaStreamReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/session.qos2.init.producer.reset/client",
        "${kafka}/session.qos2.init.producer.reset/server"})
    public void shouldResetMqttSessionWhenInitProducerStreamReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/session.qos2.offset.fetch.reset/client",
        "${kafka}/session.qos2.offset.fetch.reset/server"})
    public void shouldResetMqttSessionWhenOffsetFetchStreamReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/session.qos2.offset.fetch.data.then.caught.up/client",
        "${kafka}/session.qos2.offset.fetch.data.then.caught.up/server"})
    public void shouldSessionQos2OffsetFetchDataThenCaughtUp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.will.message.clean.start.abort.zero.window/client",
        "${kafka}/session.will.message.clean.start.abort.zero.window/server"})
    public void shouldSkipWillSignalOnAbortBeforeKafkaSessionStreamWindowGranted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.will.message.abort.deliver.will.retain/client",
        "${kafka}/session.will.message.abort.deliver.will.retain/server"})
    public void shouldSaveWillMessageAsRetain() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_SESSION_EXPIRE_AT_SIGNAL");
        clock.advanceTo(2001L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1L))
        {
            k3po.finish();
        }
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${kafka}/session.will.message.cancel.delivery/server"})
    public void shouldCancelWillDelivery() throws Exception
    {
        k3po.start();
        Thread.sleep(1000);
        k3po.notifyBarrier("ONE_SECOND_ELAPSED");
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = WILL_STREAM_RECONNECT_DELAY_NAME, value = "1")
    @Specification({
        "${kafka}/session.will.stream.end.reconnect/server"})
    public void shouldReconnectWillStreamOnKafkaEnd() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("WILL_STREAM_CLOSED");
        clock.advanceBy(1001L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1L))
        {
            k3po.finish();
        }
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = WILL_STREAM_RECONNECT_DELAY_NAME, value = "1")
    @Specification({
        "${kafka}/session.will.stream.abort.reconnect/server"})
    public void shouldReconnectWillStreamOnKafkaAbort() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("WILL_STREAM_CLOSED");
        clock.advanceBy(1001L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1L))
        {
            k3po.finish();
        }
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = WILL_STREAM_RECONNECT_DELAY_NAME, value = "1")
    @Specification({
        "${kafka}/session.will.stream.reset.reconnect/server"})
    public void shouldReconnectWillStreamOnKafkaReset() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("WILL_STREAM_CLOSED");
        clock.advanceBy(1001L);
        try (TestClock.Ticker ticker = clock.ticker(1L, 1L))
        {
            k3po.finish();
        }
    }

    public static String supplySessionId()
    {
        return "sender-1";
    }

    public static String supplyWillId()
    {
        return "d252a6bd-abb5-446a-b0f7-d0a3d8c012e2";
    }

    public static String supplyLifetimeId()
    {
        return "1e6a1eb5-810a-459d-a12c-a6fa08f228d1";
    }

    public static String supplyInstanceId()
    {
        return "zilla-1";
    }


    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/session.will.message.qos2.reply.window.before.begin/client",
        "${kafka}/session.will.message.qos2.abort.before.session.established/server"})
    public void shouldSendWillSignalOnQos2AbortWhenReplyWindowBeforeReplyBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.publish.qos.max.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/session.publish.qos.max.1/client",
        "${kafka}/session.publish.qos.max.1/server"})
    public void shouldAdvertisePublishQosMax1WhenOptionsPublishQosMax1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.publish.qos.max.0.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/session.publish.qos.max.0/client",
        "${kafka}/session.publish.qos.max.0/server"})
    public void shouldAdvertisePublishQosMax0WhenOptionsPublishQosMax0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.when.publish.topic.with.messages.params.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/session.publish.qos.max.1/client",
        "${kafka}/session.publish.qos.max.1/server"})
    public void shouldAdvertisePublishQosMax1WhenMessagesWithParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.when.subscribe.topic.with.messages.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.subscribe.unmatched.filter/client",
        "${kafka}/session.subscribe.unmatched.filter/server"})
    public void shouldSessionSubscribeUnmatchedFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.close.expiry.zero/client",
        "${kafka}/session.close.expiry.zero/server"})
    public void shouldCloseSessionWithoutExpiry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Specification({
        "${mqtt}/session.capabilities.shared.available/client",
        "${kafka}/session.capabilities/server"})
    public void shouldReceiveSessionCapabilitiesSharedAvailable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.when.subscribe.topic.with.messages.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.subscribe.unmatched.all/client",
        "${kafka}/session.subscribe.unmatched.all/server"})
    public void shouldSessionSubscribeUnmatchedAll() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.when.subscribe.topic.with.messages.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.subscribe.unmatched.resume/client",
        "${kafka}/session.subscribe.unmatched.resume/server"})
    public void shouldSessionSubscribeUnmatchedResume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/session.capabilities.shared.unavailable.disabled/client",
        "${kafka}/session.capabilities/server"})
    public void shouldReceiveSessionCapabilitiesSharedUnavailableDisabled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Specification({
        "${mqtt}/session.subscribe.shared.granted.qos2/client",
        "${kafka}/session.subscribe.shared.granted.qos2/server"})
    public void shouldSessionSubscribeSharedGrantedQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Specification({
        "${mqtt}/session.reconnect.shared.subscription/client",
        "${kafka}/session.reconnect.shared.subscription/server"})
    public void shouldSessionReconnectSharedSubscription() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Specification({
        "${mqtt}/session.unsubscribe.shared/client",
        "${kafka}/session.unsubscribe.shared/server"})
    public void shouldLeaveSharedSubscriptionOnUnsubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Specification({
        "${mqtt}/session.resubscribe.shared/client",
        "${kafka}/session.resubscribe.shared/server"})
    public void shouldBeginNewLifeOfSharedSubscriptionOnResubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Specification({
        "${mqtt}/session.close.end.expiry.zero/client",
        "${kafka}/session.close.end.expiry.zero/server"})
    public void shouldExpireSessionOnCloseWithEndExpiryZero() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = PUBLISH_MAX_QOS_NAME, value = "1")
    @Configure(name = ENGINE_CLOCK_NAME, value = "java.time.Clock::systemUTC")
    @Specification({
        "${kafka}/session.expiry.signal.replaced/server"})
    public void shouldReplaceSessionExpiryTimerForSameClient() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.qos2.abort.after.publish/client",
        "${kafka}/publish.qos2.abort.after.publish/server"})
    public void shouldRecoverMessageQos2AbortedAfterPublish() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Specification({
        "${mqtt}/publish.qos2.abort.after.pubrel/client",
        "${kafka}/publish.qos2.abort.after.pubrel/server"})
    public void shouldRecoverMessageQos2AbortedAfterPubrel() throws Exception
    {
        k3po.finish();
    }
}
