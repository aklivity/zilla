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
package io.aklivity.zilla.runtime.binding.mqtt.internal.stream.server.v4;

import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfiguration.PUBLISH_TIMEOUT;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
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
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class UnsubscribeIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network/v4")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mqtt/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
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
        "${net}/unsubscribe.after.subscribe/client",
        "${app}/unsubscribe.after.subscribe.v4/server"})
    public void shouldAcknowledge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/unsubscribe.topic.filter.single/client",
        "${app}/unsubscribe.topic.filter.single.v4/server"})
    public void shouldAcknowledgeSingleTopicFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/unsubscribe.publish.unfragmented/client",
        "${app}/unsubscribe.publish.unfragmented.v4/server"})
    public void shouldAcknowledgeAndPublishUnfragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/unsubscribe.aggregated.topic.filters.both.exact/client",
        "${app}/unsubscribe.aggregated.topic.filters.both.exact.v4/server"})
    public void shouldAcknowledgeAggregatedTopicFiltersBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/unsubscribe.no.matching.subscription/client",
        "${app}/subscribe.topic.filter.single.exact.v4/server"})
    public void shouldAcknowledgeNoMatchingSubscription() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/unsubscribe.reject.invalid.fixed.header.flags/client",
        "${app}/subscribe.topic.filter.single.exact.v4/server"})
    public void shouldRejectMalformedPacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/unsubscribe.reject.missing.packet.id/client",
        "${app}/subscribe.topic.filter.single.exact.v4/server"})
    public void shouldRejectWithoutPacketId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/unsubscribe.reject.no.topic.filter/client",
        "${app}/subscribe.topic.filter.single.exact.v4/server"})
    public void shouldRejectNoTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.route.non.default.yaml")
    @Specification({
        "${net}/unsubscribe.topic.filter.unroutable/client",
        "${app}/unsubscribe.topic.filter.unroutable/server"})
    public void shouldUnsubscribeTopicFilterUnroutable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/unsubscribe.before.suback.after.stream.begin/client",
        "${app}/unsubscribe.before.suback.after.stream.begin.v4/server"})
    public void shouldUnsubscribeBeforeSubackAfterStreamBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/unsubscribe.one.filter.before.suback.after.stream.begin/client",
        "${app}/unsubscribe.one.filter.before.suback.after.stream.begin.v4/server"})
    public void shouldUnsubscribeOneFilterBeforeSubackAfterStreamBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.route.non.default.yaml")
    @Specification({
        "${net}/unsubscribe.topic.filters.mixed.subscribed.and.not/client",
        "${app}/unsubscribe.topic.filters.mixed.subscribed.and.not.v4/server"})
    public void shouldUnsubscribeTopicFiltersMixedSubscribedAndNot() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/unsubscribe.while.publish.fragmented/client",
        "${app}/unsubscribe.while.publish.fragmented.v4/server"})
    public void shouldCompleteFragmentedPublishBeforeUnsubscribeAcknowledged() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/unsubscribe.reject.topic.filter.empty/client",
        "${app}/session.connect/server"})
    public void shouldRejectTopicFilterEmpty() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.3-1]
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/unsubscribe.reject.topic.filter.ill.formed.utf8/client",
        "${app}/session.connect/server"})
    public void shouldRejectUnsubscribeTopicFilterIllFormedUtf8() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-1.5.3-2]
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/unsubscribe.reject.topic.filter.null.character/client",
        "${app}/session.connect/server"})
    public void shouldRejectUnsubscribeTopicFilterNullCharacter() throws Exception
    {
        k3po.finish();
    }
}
