/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.kafka.internal.stream;

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

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class ClientFetchIT
{
    private static final String ENGINE_BUFFER_SLOT_CAPACITY_NAME = "zilla.engine.buffer.slot.capacity";
    static
    {
        assert ENGINE_BUFFER_SLOT_CAPACITY_NAME.equals(ENGINE_BUFFER_SLOT_CAPACITY.name());
    }

    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/cog/kafka/streams/network/fetch.v5")
        .addScriptRoot("app", "io/aklivity/zilla/specs/cog/kafka/streams/application/fetch");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/cog/kafka/config")
        .external("net#0")
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
        "${app}/partition.offset/client",
        "${net}/partition.offset/server"})
    public void shouldRequestPartitionOffset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/partition.offset.earliest/client",
        "${net}/partition.offset.earliest/server"})
    public void shouldRequestPartitionOffsetEarliest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/partition.offset.latest/client",
        "${net}/partition.offset.latest/server"})
    public void shouldRequestPartitionOffsetLatest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.key/client",
        "${net}/message.key/server"})
    public void shouldReceiveMessageKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.key.null/client",
        "${net}/message.key.null/server"})
    public void shouldReceiveMessageKeyNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.key.with.value.null/client",
        "${net}/message.key.with.value.null/server"})
    public void shouldReceiveMessageKeyWithValueNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.key.with.value.distinct/client",
        "${net}/message.key.with.value.distinct/server"})
    public void shouldReceiveMessageKeyWithValueDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.key.with.header/client",
        "${net}/message.key.with.header/server"})
    public void shouldReceiveMessageKeyWithHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.key.distinct/client",
        "${net}/message.key.distinct/server"})
    public void shouldReceiveMessageKeyDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.value/client",
        "${net}/message.value/server"})
    public void shouldReceiveMessageValue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.value.null/client",
        "${net}/message.value.null/server"})
    public void shouldReceiveMessageValueNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.value.10k/client",
        "${net}/message.value.10k/server"})
    public void shouldReceiveMessageValue10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.value.100k/client",
        "${net}/message.value.100k/server"})
    @Configure(name = ENGINE_BUFFER_SLOT_CAPACITY_NAME, value = "65536")
    public void shouldReceiveMessageValue100k() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.value.gzip/client",
        "${net}/message.value.gzip/server"})
    public void shouldReceiveMessageValueGzip() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.value.snappy/client",
        "${net}/message.value.snappy/server"})
    public void shouldReceiveMessageValueSnappy() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.value.lz4/client",
        "${net}/message.value.lz4/server"})
    public void shouldReceiveMessageValueLz4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.value.distinct/client",
        "${net}/message.value.distinct/server"})
    public void shouldReceiveMessageValueDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.header/client",
        "${net}/message.header/server"})
    public void shouldReceiveMessageHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.header.null/client",
        "${net}/message.header.null/server"})
    public void shouldReceiveMessageHeaderNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.headers.distinct/client",
        "${net}/message.headers.distinct/server"})
    public void shouldReceiveMessageHeadersDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/message.headers.repeated/client",
        "${net}/message.headers.repeated/server"})
    public void shouldReceiveMessageHeadersRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/filter.none/client",
        "${net}/filter.none/server"})
    public void shouldReceiveMessagesWithNoFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/filter.key/client",
        "${net}/filter.key/server"})
    public void shouldReceiveMessagesWithKeyFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/filter.key.and.header/client",
        "${net}/filter.key.and.header/server"})
    public void shouldReceiveMessagesWithKeyAndHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/filter.key.or.header/client",
        "${net}/filter.key.or.header/server"})
    public void shouldReceiveMessagesWithKeyOrHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/filter.header/client",
        "${net}/filter.header/server"})
    public void shouldReceiveMessagesWithHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/filter.header.and.header/client",
        "${net}/filter.header.and.header/server"})
    public void shouldReceiveMessagesWithHeaderAndHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/filter.header.or.header/client",
        "${net}/filter.header.or.header/server"})
    public void shouldReceiveMessagesWithHeaderOrHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/filter.not.header/client",
        "${net}/filter.not.header/server"})
    public void shouldReceiveMessagesWithNotHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/filter.not.key/client",
        "${net}/filter.not.key/server"})
    public void shouldReceiveMessagesWithNotKeyFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/filter.key.and.not.header/client",
        "${net}/filter.key.and.not.header/server"})
    public void shouldReceiveMessagesWithKeyAndNotHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/filter.headers.one/client",
        "${net}/filter.headers.one/server"})
    public void shouldReceiveMessagesWithHeadersOneFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/filter.headers.one.empty/client",
        "${net}/filter.headers.one.empty/server"})
    public void shouldReceiveMessagesWithHeadersOneEmptyFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/filter.headers.many/client",
        "${net}/filter.headers.many/server"})
    public void shouldReceiveMessagesWithHeadersManyFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/filter.headers.many.empty/client",
        "${net}/filter.headers.many.empty/server"})
    public void shouldReceiveMessagesWithHeadersManyEmptyFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/filter.headers.skip.one/client",
        "${net}/filter.headers.skip.one/server"})
    public void shouldReceiveMessagesWithHeadersSkipOneFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/filter.headers.skip.two/client",
        "${net}/filter.headers.skip.two/server"})
    public void shouldReceiveMessagesWithHeadersSkipTwoFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.when.topic.json")
    @Specification({
        "${app}/filter.headers.skip.many/client",
        "${net}/filter.headers.skip.many/server"})
    public void shouldReceiveMessagesWithHeadersSkipManyFilter() throws Exception
    {
        k3po.finish();
    }
}
