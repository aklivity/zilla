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
package io.aklivity.zilla.specs.binding.mqtt.streams.network.v4;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class PublishIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network/v4");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/publish.multiple.messages/client",
        "${net}/publish.multiple.messages/server"})
    public void shouldSendMultipleMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.multiple.messages.unfragmented/client",
        "${net}/publish.multiple.messages.unfragmented/server"})
    public void shouldSendMultipleMessagesUnfragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.multiple.messages.with.delay/client",
        "${net}/publish.multiple.messages.with.delay/server"})
    public void shouldSendMultipleMessagesWithDelay() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("PUBLISHED_MESSAGE_TWO");
        k3po.notifyBarrier("PUBLISH_MESSAGE_THREE");
        k3po.finish();
    }

    // [MQTT-2.2.1-2]
    @Test
    @Specification({
        "${net}/publish.reject.qos0.with.packet.id/client",
        "${net}/publish.reject.qos0.with.packet.id/server"})
    public void shouldRejectQos0WithPackedId() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-2.2.1-3]
    @Test
    @Specification({
        "${net}/publish.reject.qos1.without.packet.id/client",
        "${net}/publish.reject.qos1.without.packet.id/server"})
    public void shouldRejectQos1WithoutPackedId() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-2.2.1-3]
    @Test
    @Specification({
        "${net}/publish.reject.qos2.without.packet.id/client",
        "${net}/publish.reject.qos2.without.packet.id/server"})
    public void shouldRejectQos2WithoutPackedId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.topic.not.routed/client",
        "${net}/publish.topic.not.routed/server"})
    public void shouldRejectTopicNotRouted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.retained/client",
        "${net}/publish.retained/server"})
    public void shouldPublishRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.empty.retained.message/client",
        "${net}/publish.empty.retained.message/server"})
    public void shouldSendEmptyRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.empty.message/client",
        "${net}/publish.empty.message/server"})
    public void shouldSendEmptyMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.subscribe.batched/client",
        "${net}/publish.subscribe.batched/server"})
    public void shouldPublishSubscribeBatched() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.invalid.message/client",
        "${net}/publish.invalid.message/server"})
    public void shouldPublishInvalidMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.valid.message/client",
        "${net}/publish.valid.message/server"})
    public void shouldPublishValidMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.reject.packet.too.large/client",
        "${net}/publish.reject.packet.too.large/server"})
    public void shouldRejectPacketTooLarge() throws Exception
    {
        k3po.finish();
    }
}
