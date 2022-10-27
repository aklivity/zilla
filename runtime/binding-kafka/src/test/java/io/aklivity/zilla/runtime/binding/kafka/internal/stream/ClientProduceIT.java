/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
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

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfigurationTest;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class ClientProduceIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/kafka/streams/network/produce.v3")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/kafka/streams/application/produce");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/kafka/config")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/topic.missing/client"})
    public void shouldRejectWhenTopicMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/topic.not.routed/client"})
    public void shouldRejectWhenTopicNotRouted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/partition.unknown/client",
        "${net}/partition.unknown/server"})
    public void shouldRejectWhenPartitionUnknown() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/partition.not.leader/client",
        "${net}/partition.not.leader/server"})
    public void shouldRejectPartitionNotLeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.key/client",
        "${net}/message.key/server"})
    public void shouldSendMessageKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.key.null/client",
        "${net}/message.key.null/server"})
    public void shouldSendMessageKeyNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.key.with.value.null/client",
        "${net}/message.key.with.value.null/server"})
    public void shouldSendMessageKeyWithValueNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.key.with.value.distinct/client",
        "${net}/message.key.with.value.distinct/server"})
    public void shouldSendMessageKeyWithValueDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.key.with.header/client",
        "${net}/message.key.with.header/server"})
    public void shouldSendMessageKeyWithHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.key.distinct/client",
        "${net}/message.key.distinct/server"})
    public void shouldSendMessageKeyDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.value/client",
        "${net}/message.value/server"})
    public void shouldSendMessageValue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.value.null/client",
        "${net}/message.value.null/server"})
    public void shouldSendMessageValueNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.value.10k/client",
        "${net}/message.value.10k/server"})
    public void shouldSendMessageValue10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.value.100k/client",
        "${net}/message.value.100k/server"})
    public void shouldSendMessageValue100k() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.value.gzip/client",
        "${net}/message.value.gzip/server"})
    public void shouldSendMessageValueGzip() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.value.snappy/client",
        "${net}/message.value.snappy/server"})
    public void shouldSendMessageValueSnappy() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.value.lz4/client",
        "${net}/message.value.lz4/server"})
    public void shouldSendMessageValueLz4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.value.distinct/client",
        "${net}/message.value.distinct/server"})
    public void shouldSendMessageValueDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.header/client",
        "${net}/message.header/server"})
    public void shouldSendMessageHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.header.null/client",
        "${net}/message.header.null/server"})
    public void shouldSendMessageHeaderNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.headers.distinct/client",
        "${net}/message.headers.distinct/server"})
    public void shouldSendMessageHeadersDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.headers.repeated/client",
        "${net}/message.headers.repeated/server"})
    public void shouldSendMessageHeadersRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.value.repeated/client",
        "${net}/message.value.repeated/server"})
    @Configure(name = KafkaConfigurationTest.KAFKA_CLIENT_PRODUCE_MAX_REQUEST_MILLIS_NAME, value = "200")
    public void shouldSendMessageValueRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.values.sequential/client",
        "${net}/message.values.sequential/server"})
    public void shouldSendMessageValueSequential() throws Exception
    {
        k3po.finish();
    }
}
