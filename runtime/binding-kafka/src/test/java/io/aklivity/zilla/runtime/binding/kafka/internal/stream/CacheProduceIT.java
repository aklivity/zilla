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

import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_SERVER_BOOTSTRAP;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_SERVER_RECONNECT_DELAY;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfigurationTest.KAFKA_CACHE_CLIENT_CLEANUP_DELAY_NAME;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfigurationTest.KAFKA_CACHE_SERVER_RECONNECT_DELAY_NAME;
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
import org.kaazing.k3po.junit.annotation.ScriptProperty;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class CacheProduceIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/kafka/streams/application/produce");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .configure(KAFKA_CACHE_SERVER_BOOTSTRAP, false)
        .configure(KAFKA_CACHE_SERVER_RECONNECT_DELAY, 0)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/binding/kafka/config")
        .external("app1")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/topic.missing/client"})
    public void shouldRejectWhenTopicMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.when.topic.json")
    @Specification({
        "${app}/topic.not.routed/client"})
    public void shouldRejectWhenTopicNotRouted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/partition.unknown/client",
        "${app}/partition.unknown/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = KAFKA_CACHE_CLIENT_CLEANUP_DELAY_NAME, value = "0")
    public void shouldRejectWhenPartitionUnknown() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.value/client",
        "${app}/partition.not.leader.reconnect/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = KAFKA_CACHE_SERVER_RECONNECT_DELAY_NAME, value = "1")
    public void shouldRecnnectPartitionNotLeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.values.parallel/client",
        "${app}/partition.not.leader.reconnect.parallel/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = KAFKA_CACHE_SERVER_RECONNECT_DELAY_NAME, value = "1")
    public void shouldRetryPartitionNotLeaderMessageValues() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.key/client",
        "${app}/message.key/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessageKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.key.null/client",
        "${app}/message.key.null/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessageKeyNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.key.with.value.null/client",
        "${app}/message.key.with.value.null/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessageKeyWithValueNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.key.with.value.distinct/client",
        "${app}/message.key.with.value.distinct/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessageKeyWithValueDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.key.with.header/client",
        "${app}/message.key.with.header/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessageKeyWithHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.key.distinct/client",
        "${app}/message.key.distinct/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessageKeyDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.value/client",
        "${app}/message.value/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessageValue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.value.null/client",
        "${app}/message.value.null/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessageValueNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.value.10k/client",
        "${app}/message.value.10k/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessageValue10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.value.100k/client",
        "${app}/message.value.100k/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessageValue100k() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.value.gzip/client",
        "${app}/message.value.gzip/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessageValueGzip() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.value.snappy/client",
        "${app}/message.value.snappy/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessageValueSnappy() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.value.lz4/client",
        "${app}/message.value.lz4/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessageValueLz4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.value.distinct/client",
        "${app}/message.value.distinct/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessageValueDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.header/client",
        "${app}/message.header/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessageHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.header.null/client",
        "${app}/message.header.null/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessageHeaderNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.headers.distinct/client",
        "${app}/message.headers.distinct/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessageHeadersDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.headers.repeated/client",
        "${app}/message.headers.repeated/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessageHeadersRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.trailer/client",
        "${app}/message.header/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessageTrailer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.trailers.overlap/client",
        "${app}/message.value.repeated/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessageTrailersOverlap() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.value.repeated/client",
        "${app}/message.value.repeated/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessageValueRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.value.rejected/client",
        "${app}/message.value.rejected/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = KAFKA_CACHE_CLIENT_CLEANUP_DELAY_NAME, value = "0")
    @Configure(name = KAFKA_CACHE_SERVER_RECONNECT_DELAY_NAME, value = "0")
    public void shouldRejectMessageValue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/message.values.rejected/client",
        "${app}/message.values.rejected/server"})
    @ScriptProperty({"serverAddress \"zilla://streams/app1\""})
    @Configure(name = KAFKA_CACHE_CLIENT_CLEANUP_DELAY_NAME, value = "0")
    @Configure(name = KAFKA_CACHE_SERVER_RECONNECT_DELAY_NAME, value = "1")
    public void shouldRejectMessageValues() throws Exception
    {
        k3po.finish();
    }
}
