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

import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_SEGMENT_BYTES;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_SEGMENT_INDEX_BYTES;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_SERVER_BOOTSTRAP;
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

public class CacheMergedIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/kafka/streams/application/merged");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(16384)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .configure(KAFKA_CACHE_SERVER_BOOTSTRAP, false)
        .configure(KAFKA_CACHE_SEGMENT_BYTES, 1 * 1024 * 1024)
        .configure(KAFKA_CACHE_SEGMENT_INDEX_BYTES, 256 * 1024)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/binding/kafka/config")
        .external("app1")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Ignore("requires k3po parallel reads")
    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.filter.header/client",
        "${app}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.filter.header.with.compaction/client",
        "${app}/unmerged.fetch.filter.none.with.compaction/server"})
    public void shouldFetchMergedMessagesWithHeaderFilterAfterCompaction() throws Exception
    {
        k3po.finish();
    }

    @Ignore("requires k3po parallel reads")
    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.filter.not.key/client",
        "${app}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithNotKeyFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("requires k3po parallel reads")
    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.filter.not.header/client",
        "${app}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithNotHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("requires k3po parallel reads")
    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.filter.key.and.not.header/client",
        "${app}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithKeyAndNotHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("requires k3po parallel reads")
    @Configuration("cache.options.merged.json")
    @Test
    @Specification({
        "${app}/merged.fetch.filter.header.and.header/client",
        "${app}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithHeaderAndHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("requires k3po parallel reads")
    @Configuration("cache.options.merged.json")
    @Test
    @Specification({
        "${app}/merged.fetch.filter.header.or.header/client",
        "${app}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithHeaderOrHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("requires k3po parallel reads")
    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.filter.key/client",
        "${app}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithKeyFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("requires k3po parallel reads")
    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.filter.key.and.header/client",
        "${app}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithKeyAndHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("requires k3po parallel reads")
    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.filter.key.or.header/client",
        "${app}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithKeyOrHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("GitHub Actions")
    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.filter.none/client",
        "${app}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithNoFilter() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_MESSAGE_B2");
        k3po.notifyBarrier("SEND_MESSAGE_A3");
        k3po.notifyBarrier("SEND_MESSAGE_B3");
        k3po.finish();
    }

    @Ignore("GitHub Actions")
    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/merged.fetch.message.values/client",
        "${app}/unmerged.fetch.message.values/server"})
    public void shouldFetchMergedMessageValuesByDefault() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("CHANGING_PARTITION_COUNT");
        Thread.sleep(400); // allow A1, B1, A2, B2 to be merged
        k3po.notifyBarrier("CHANGED_PARTITION_COUNT");
        k3po.finish();
    }

    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.message.values/client",
        "${app}/unmerged.fetch.message.values/server"})
    public void shouldFetchMergedMessageValues() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("CHANGING_PARTITION_COUNT");
        Thread.sleep(400); // allow A1, B1, A2, B2 to be merged
        k3po.notifyBarrier("CHANGED_PARTITION_COUNT");
        k3po.finish();
    }

    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.partition.leader.changed/client",
        "${app}/unmerged.fetch.partition.leader.changed/server"})
    public void shouldFetchMergedPartitionLeaderChanged() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("CHANGING_PARTITION_LEADER");
        Thread.sleep(400);
        k3po.notifyBarrier("CHANGED_PARTITION_LEADER");
        k3po.finish();
    }

    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.partition.leader.aborted/client",
        "${app}/unmerged.fetch.partition.leader.aborted/server"})
    public void shouldFetchMergedPartitionLeaderAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.partition.offsets.earliest/client",
        "${app}/unmerged.fetch.partition.offsets.earliest/server"})
    public void shouldFetchMergedPartitionOffsetsEarliest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.partition.offsets.earliest.overflow/client",
        "${app}/unmerged.fetch.partition.offsets.earliest/server"})
    public void shouldFetchMergedPartitionOffsetsEarliestOverflow() throws Exception
    {
        k3po.finish();
    }

    @Ignore("GitHub Actions")
    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.isolation.read.committed/client",
        "${app}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithIsolationReadCommitted() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_MESSAGE_B2");
        k3po.notifyBarrier("SEND_MESSAGE_A3");
        k3po.notifyBarrier("SEND_MESSAGE_B3");
        k3po.finish();
    }

    @Test
    @Configuration("cache.json")
    @Specification({
        "${app}/merged.produce.message.values/client",
        "${app}/unmerged.produce.message.values/server"})
    public void shouldProduceMergedMessageValuesByDefault() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.produce.message.values.null/client",
        "${app}/unmerged.produce.message.values.null/server"})
    public void shouldProduceMergedMessageValuesNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.produce.message.values/client",
        "${app}/unmerged.produce.message.values/server"})
    public void shouldProduceMergedMessageValues() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.produce.message.value.10k/client",
        "${app}/unmerged.produce.message.value.10k/server"})
    public void shouldProduceMergedMessageValue10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.produce.message.value.100k/client",
        "${app}/unmerged.produce.message.value.100k/server"})
    public void shouldProduceMergedMessageValue100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.produce.message.values.dynamic/client",
        "${app}/unmerged.produce.message.values.dynamic/server"})
    public void shouldProduceMergedMessageValuesDynamic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.produce.message.values.dynamic.hashed/client",
        "${app}/unmerged.produce.message.values.dynamic.hashed/server"})
    public void shouldProduceMergedMessageValuesDynamicHashed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.produce.message.flags.incomplete/client",
        "${app}/unmerged.produce.message.flags.incomplete/server"})
    public void shouldProduceMergedMessageFlagsIncomplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.server.sent.close/client",
        "${app}/unmerged.fetch.server.sent.close/server"})
    @Configure(name = KafkaConfigurationTest.KAFKA_CACHE_SERVER_RECONNECT_DELAY_NAME, value = "0")
    public void shouldCloseMergedOnUnmergedFetchClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.server.sent.close.with.message/client",
        "${app}/unmerged.fetch.server.sent.close.with.message/server"})
    @Configure(name = KafkaConfigurationTest.KAFKA_CACHE_SERVER_RECONNECT_DELAY_NAME, value = "0")
    public void shouldCloseMergedOnUnmergedFetchCloseWithMessage() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_MESSAGE");
        k3po.notifyBarrier("CLOSE_UNMERGED_FETCH_REPLY");
        k3po.finish();
    }

    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.server.sent.abort/client",
        "${app}/unmerged.fetch.server.sent.abort/server"})
    @Configure(name = KafkaConfigurationTest.KAFKA_CACHE_SERVER_RECONNECT_DELAY_NAME, value = "0")
    public void shouldCloseMergedOnUnmergedFetchAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.server.sent.abort.with.message/client",
        "${app}/unmerged.fetch.server.sent.abort.with.message/server"})
    @Configure(name = KafkaConfigurationTest.KAFKA_CACHE_SERVER_RECONNECT_DELAY_NAME, value = "0")
    public void shouldCloseMergedOnUnmergedFetchAbortWithMessage() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_MESSAGE");
        k3po.notifyBarrier("ABORT_UNMERGED_FETCH_REPLY");
        k3po.finish();
    }

    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.server.sent.abort/client",
        "${app}/unmerged.fetch.server.sent.reset/server"})
    @Configure(name = KafkaConfigurationTest.KAFKA_CACHE_SERVER_RECONNECT_DELAY_NAME, value = "0")
    public void shouldCloseMergedOnUnmergedFetchReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.server.sent.abort.with.message/client",
        "${app}/unmerged.fetch.server.sent.reset.and.abort.with.message/server"})
    @Configure(name = KafkaConfigurationTest.KAFKA_CACHE_SERVER_RECONNECT_DELAY_NAME, value = "0")
    public void shouldCloseMergedOnUnmergedFetchResetWithMessage() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_MESSAGE");
        k3po.notifyBarrier("RESET_UNMERGED_FETCH_INITIAL");
        k3po.finish();
    }

    @Ignore("requires k3po parallel reads")
    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.filter.headers.one/client",
        "${app}/unmerged.fetch.filter.none/server"})
    public void shouldReceiveMessagesWithHeadersOneFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("requires k3po parallel reads")
    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.filter.headers.one.empty/client",
        "${app}/unmerged.fetch.filter.none/server"})
    public void shouldReceiveMessagesWithHeadersOneEmptyFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("requires k3po parallel reads")
    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.filter.headers.many/client",
        "${app}/unmerged.fetch.filter.none/server"})
    public void shouldReceiveMessagesWithHeadersManyFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("requires k3po parallel reads")
    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.filter.headers.many.empty/client",
        "${app}/unmerged.fetch.filter.none/server"})
    public void shouldReceiveMessagesWithHeadersManyEmptyFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("requires k3po parallel reads")
    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.filter.headers.skip.one/client",
        "${app}/unmerged.fetch.filter.none/server"})
    public void shouldReceiveMessagesWithHeadersSkipOneFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("requires k3po parallel reads")
    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.filter.headers.skip.two/client",
        "${app}/unmerged.fetch.filter.none/server"})
    public void shouldReceiveMessagesWithHeadersSkipTwoFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("requires k3po parallel reads")
    @Test
    @Configuration("cache.options.merged.json")
    @Specification({
        "${app}/merged.fetch.filter.headers.skip.many/client",
        "${app}/unmerged.fetch.filter.none/server"})
    public void shouldReceiveMessagesWithHeadersSkipManyFilter() throws Exception
    {
        k3po.finish();
    }
}
