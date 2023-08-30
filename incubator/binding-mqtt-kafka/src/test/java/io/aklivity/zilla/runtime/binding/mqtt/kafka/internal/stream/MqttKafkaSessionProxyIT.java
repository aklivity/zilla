/*
 * Copyright 2021-2023 Aklivity Inc
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
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.SESSION_ID_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.TIME_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.WILL_AVAILABLE_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.WILL_ID_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.WILL_STREAM_RECONNECT_DELAY_NAME;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class MqttKafkaSessionProxyIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("mqtt", "io/aklivity/zilla/specs/binding/mqtt/kafka/streams/mqtt")
        .addScriptRoot("kafka", "io/aklivity/zilla/specs/binding/mqtt/kafka/streams/kafka");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    public final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/binding/mqtt/kafka/config")
        .external("kafka0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = SESSION_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplySessionId")
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
    @Configure(name = SESSION_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplySessionId")
    @Configure(name = TIME_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyTime")
    @Configure(name = INSTANCE_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyInstanceId")
    @Specification({
        "${mqtt}/session.client.takeover/client",
        "${kafka}/session.client.takeover/server"})
    public void shouldTakeOverSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = SESSION_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplySessionId")
    @Configure(name = TIME_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyTime")
    @Configure(name = INSTANCE_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyInstanceId")
    @Specification({
        "${mqtt}/session.exists.clean.start/client",
        "${kafka}/session.exists.clean.start/server"})
    public void shouldRemoveSessionAtCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_AVAILABLE_NAME, value = "false")
    @Configure(name = SESSION_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplySessionId")
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
    @Configure(name = SESSION_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplySessionId")
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
    @Configure(name = SESSION_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplySessionId")
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
    @Configure(name = SESSION_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplySessionId")
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
    @Configure(name = SESSION_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplySessionId")
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
    @Configure(name = SESSION_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplySessionId")
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
    @Configure(name = SESSION_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplySessionId")
    @Specification({
        "${mqtt}/session.server.sent.reset/client",
        "${kafka}/session.group.server.sent.reset/server"})
    public void shouldGroupStreamReceiveServerSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = SESSION_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplySessionId")
    @Configure(name = LIFETIME_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyLifetimeId")
    @Configure(name = WILL_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyWillId")
    @Configure(name = INSTANCE_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyInstanceId")
    @Configure(name = TIME_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyTime")
    @Specification({
        "${mqtt}/session.will.message.normal.disconnect/client",
        "${kafka}/session.will.message.normal.disconnect/server"})
    public void shouldNotSendWillMessageOnNormalDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = SESSION_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplySessionId")
    @Configure(name = LIFETIME_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyLifetimeId")
    @Configure(name = WILL_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyWillId")
    @Configure(name = INSTANCE_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyInstanceId")
    @Configure(name = TIME_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyTime")
    @Specification({
        "${mqtt}/session.will.message.clean.start/client",
        "${kafka}/session.will.message.clean.start/server"})
    public void shouldGenerateLifeTimeIdOnCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = SESSION_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplySessionId")
    @Configure(name = LIFETIME_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyLifetimeId")
    @Configure(name = WILL_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyWillId")
    @Configure(name = INSTANCE_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyInstanceId")
    @Configure(name = TIME_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyTime")
    @Specification({
        "${mqtt}/session.will.message.abort.deliver.will/client",
        "${kafka}/session.will.message.abort.deliver.will/server"})
    public void shouldSendWillMessageOnAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = SESSION_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplySessionId")
    @Configure(name = LIFETIME_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyLifetimeId")
    @Configure(name = WILL_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyWillId")
    @Configure(name = INSTANCE_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyInstanceId")
    @Configure(name = TIME_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyTime")
    @Specification({
        "${mqtt}/session.will.message.abort.deliver.will/client",
        "${kafka}/session.will.message.will.id.mismatch.no.deliver/server"})
    public void shouldNotSendWillMessageOnWillIdMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = SESSION_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplySessionId")
    @Configure(name = LIFETIME_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyLifetimeId")
    @Configure(name = WILL_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyWillId")
    @Configure(name = INSTANCE_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyInstanceId")
    @Configure(name = TIME_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyTime")
    @Specification({
        "${mqtt}/session.will.message.abort.deliver.will.retain/client",
        "${kafka}/session.will.message.abort.deliver.will.retain/server"})
    public void shouldSaveWillMessageAsRetain() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = SESSION_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplySessionId")
    @Configure(name = LIFETIME_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyLifetimeId")
    @Configure(name = WILL_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyWillId")
    @Configure(name = INSTANCE_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyInstanceId")
    @Configure(name = TIME_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyTime")
    @Specification({
        "${mqtt}/session.will.message.client.takeover.deliver.will/client",
        "${kafka}/session.will.message.takeover.deliver.will/server"})
    public void shouldSendWillMessageOnAbortClientTakeover() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = SESSION_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplySessionId")
    @Configure(name = LIFETIME_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyLifetimeId")
    @Configure(name = WILL_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyWillId")
    @Configure(name = INSTANCE_ID_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyInstanceId")
    @Configure(name = TIME_NAME,
        value = "io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaSessionProxyIT::supplyTime")
    @Specification({
        "${kafka}/session.will.message.cancel.delivery/server"})
    public void shouldCancelWillDelivery() throws Exception
    {
        k3po.start();
        Thread.sleep(1000);
        k3po.notifyBarrier("WAIT_1_SECOND");
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_STREAM_RECONNECT_DELAY_NAME, value = "1")
    @Specification({
        "${kafka}/session.will.stream.end.reconnect/server"})
    public void shouldReconnectWillStreamOnKafkaEnd() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_STREAM_RECONNECT_DELAY_NAME, value = "1")
    @Specification({
        "${kafka}/session.will.stream.abort.reconnect/server"})
    public void shouldReconnectWillStreamOnKafkaAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Configure(name = WILL_STREAM_RECONNECT_DELAY_NAME, value = "1")
    @Specification({
        "${kafka}/session.will.stream.reset.reconnect/server"})
    public void shouldReconnectWillStreamOnKafkaReset() throws Exception
    {
        k3po.finish();
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

    public static long supplyTime()
    {
        return 1000L;
    }
}
