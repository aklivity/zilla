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
package io.aklivity.zilla.runtime.binding.mqtt.internal.stream;

import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfiguration.PUBLISH_TIMEOUT;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.KEEP_ALIVE_MINIMUM_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.MAXIMUM_QOS_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.SESSION_EXPIRY_INTERVAL_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.SHARED_SUBSCRIPTION_AVAILABLE_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.WILDCARD_SUBSCRIPTION_AVAILABLE_NAME;
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

public class SessionIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mqtt/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(PUBLISH_TIMEOUT, 1L)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/binding/mqtt/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.connect.with.session.expiry/client",
        "${app}/session.connect.with.session.expiry/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "10")
    public void shouldConnectWithSessionExpiry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.subscribe/client",
        "${app}/session.subscribe/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "10")
    public void shouldSubscribeSaveSubscriptionsInSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.subscribe.via.session.state/client",
        "${app}/session.subscribe.via.session.state/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "10")
    public void shouldSubscribeViaSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.unsubscribe.after.subscribe/client",
        "${app}/session.unsubscribe.after.subscribe/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "10")
    public void shouldUnsubscribeSaveNewSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.unsubscribe.after.subscribe.deferred/client",
        "${app}/session.unsubscribe.after.subscribe.deferred/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "10")
    public void shouldUnsubscribeAfterSubscribeDeferred() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.subscribe/client",
        "${app}/session.unsubscribe.via.session.state/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "10")
    public void shouldUnsubscribeViaSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.will.message.retain/client",
        "${app}/session.will.message.retain/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "10")
    public void shouldStoreWillMessageInSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.will.message.normal.disconnect/client",
        "${app}/session.will.message.normal.disconnect/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "10")
    public void shouldCloseSessionNormalDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.will.message.disconnect.with.will.message/client",
        "${app}/session.will.message.disconnect.with.will.message/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "10")
    public void shouldCloseSessionDisconnectWithWill() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.will.message.no.ping.within.keep.alive/client",
        "${app}/session.will.message.no.ping.within.keep.alive/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "10")
    @Configure(name = KEEP_ALIVE_MINIMUM_NAME, value = "1")
    public void shouldCloseSessionWithKeepAliveExpired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.exists.clean.start/client",
        "${app}/session.exists.clean.start/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "10")
    public void shouldCloseExistingConnectionCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.abort.reconnect.non.clean.start/client",
        "${app}/session.abort.reconnect.non.clean.start/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "10")
    public void shouldClientAbortAndReconnectWithNonCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.client.takeover/client",
        "${app}/session.client.takeover/server"})
    @Configure(name = WILDCARD_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = SHARED_SUBSCRIPTION_AVAILABLE_NAME, value = "true")
    @Configure(name = MAXIMUM_QOS_NAME, value = "2")
    @Configure(name = SESSION_EXPIRY_INTERVAL_NAME, value = "10")
    public void shouldClientTakeOverSession() throws Exception
    {
        k3po.finish();
    }
}
