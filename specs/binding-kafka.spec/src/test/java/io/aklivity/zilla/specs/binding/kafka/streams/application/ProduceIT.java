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
package io.aklivity.zilla.specs.binding.kafka.streams.application;

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

public class ProduceIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/kafka/streams/application/produce");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/topic.missing/client",
        "${app}/topic.missing/server"})
    public void shouldRejectWhenTopicMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/topic.not.routed/client",
        "${app}/topic.not.routed/server"})
    public void shouldRejectWhenTopicNotRouted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/partition.unknown/client",
        "${app}/partition.unknown/server"})
    public void shouldRejectWhenPartitionUnknown() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/partition.not.leader/client",
        "${app}/partition.not.leader/server"})
    public void shouldRejectPartitionNotLeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/partition.not.leader.reconnect.parallel/client",
        "${app}/partition.not.leader.reconnect.parallel/server"})
    public void shouldReconnectPartitionNotLeaderParallel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.key/client",
        "${app}/message.key/server"})
    public void shouldSendMessageKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.key.null/client",
        "${app}/message.key.null/server"})
    public void shouldSendMessageKeyNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.key.with.value.null/client",
        "${app}/message.key.with.value.null/server"})
    public void shouldSendMessageKeyWithValueNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.key.with.value.distinct/client",
        "${app}/message.key.with.value.distinct/server"})
    public void shouldSendMessageKeyWithValueDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.key.with.header/client",
        "${app}/message.key.with.header/server"})
    public void shouldSendMessageKeyWithHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.key.distinct/client",
        "${app}/message.key.distinct/server"})
    public void shouldSendMessageKeyDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.value/client",
        "${app}/message.value/server"})
    public void shouldSendMessageValue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.producer.id/client",
        "${app}/message.producer.id/server"})
    public void shouldSendMessageValueWithProducerId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.values.producer.id/client",
        "${app}/message.values.producer.id/server"})
    public void shouldSendMessageValuesWithProducerId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.values.producer.id.changes/client",
        "${app}/message.values.producer.id.changes/server"})
    public void shouldSendMessageValuesWithProducerIdThatChanges() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.values.producer.id.replay/client",
        "${app}/message.values.producer.id.replay/server"})
    public void shouldReplyMessageValuesWithProducerId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.value.null/client",
        "${app}/message.value.null/server"})
    public void shouldSendMessageValueNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.value.10k/client",
        "${app}/message.value.10k/server"})
    public void shouldSendMessageValue10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.value.100k/client",
        "${app}/message.value.100k/server"})
    public void shouldSendMessageValue100k() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Specification({
        "${app}/message.value.gzip/client",
        "${app}/message.value.gzip/server"})
    public void shouldSendMessageValueGzip() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Specification({
        "${app}/message.value.snappy/client",
        "${app}/message.value.snappy/server"})
    public void shouldSendMessageValueSnappy() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Specification({
        "${app}/message.value.lz4/client",
        "${app}/message.value.lz4/server"})
    public void shouldSendMessageValueLz4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.value.distinct/client",
        "${app}/message.value.distinct/server"})
    public void shouldSendMessageValueDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.value.repeated/client",
        "${app}/message.value.repeated/server"})
    public void shouldSendMessageValueRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.values.sequential/client",
        "${app}/message.values.sequential/server"})
    public void shouldSendMessageValueSequential() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.header/client",
        "${app}/message.header/server"})
    public void shouldSendMessageHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.header.null/client",
        "${app}/message.header.null/server"})
    public void shouldSendMessageHeaderNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.headers.distinct/client",
        "${app}/message.headers.distinct/server"})
    public void shouldSendMessageHeadersDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.headers.repeated/client",
        "${app}/message.headers.repeated/server"})
    public void shouldSendMessageHeadersRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.trailer/client",
        "${app}/message.trailer/server"})
    public void shouldSendMessageTrailer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.trailers.overlap/client",
        "${app}/message.trailers.overlap/server"})
    public void shouldSendMessageTrailersOverlap() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.value.rejected/client",
        "${app}/message.value.rejected/server"})
    public void shouldRejectMessageValue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.values.rejected/client",
        "${app}/message.values.rejected/server"})
    public void shouldRejectMessageValues() throws Exception
    {
        k3po.finish();
    }
}
