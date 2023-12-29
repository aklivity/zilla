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
package io.aklivity.zilla.specs.binding.mqtt.streams.application;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class ConnectionIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mqtt/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/client.sent.abort/client",
        "${app}/client.sent.abort/server"})
    public void shouldReceiveClientSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/disconnect.after.subscribe.and.publish/client",
        "${app}/disconnect.after.subscribe.and.publish/server"})
    public void shouldDisconnectAfterSubscribeAndPublish() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connect.non.successful.connack/client",
        "${app}/connect.non.successful.connack/server"})
    public void shouldResetWithReasonCodeOnNonSuccessfulConnack() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connect.non.successful.disconnect/client",
        "${app}/connect.non.successful.disconnect/server"})
    public void shouldResetWithReasonCodeOnNonSuccessfulDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connect.delegate.connack.properties/client",
        "${app}/connect.delegate.connack.properties/server"})
    public void shouldDelegateConnackProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connect.retain.not.supported/client",
        "${app}/connect.retain.not.supported/server"})
    public void shouldConnectWithRetainNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connect.reject.will.retain.not.supported/client",
        "${app}/connect.reject.will.retain.not.supported/server"})
    public void shouldRejectConnectWillRetainNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connect.maximum.qos.0/client",
        "${app}/connect.maximum.qos.0/server"})
    public void shouldConnectWithMaximumQos0() throws Exception
    {
        k3po.finish();
    }
}
