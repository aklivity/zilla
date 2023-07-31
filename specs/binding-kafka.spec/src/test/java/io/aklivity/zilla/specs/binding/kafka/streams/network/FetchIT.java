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
package io.aklivity.zilla.specs.binding.kafka.streams.network;

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

public class FetchIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/kafka/streams/network/fetch.v5");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/partition.unknown/client",
        "${net}/partition.unknown/server" })
    public void shouldErrorWhenPartitionUnknown() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/partition.not.leader/client",
        "${net}/partition.not.leader/server"})
    public void shouldErrorWhenPartitionNotLeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/partition.incomplete/client",
        "${net}/partition.incomplete/server" })
    public void shouldReceivePartitionIncomplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/partition.offset/client",
        "${net}/partition.offset/server"})
    public void shouldRequestPartitionOffset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/partition.offset.earliest/client",
        "${net}/partition.offset.earliest/server"})
    public void shouldRequestPartitionOffsetEarliest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/partition.offset.latest/client",
        "${net}/partition.offset.latest/server"})
    public void shouldRequestPartitionOffsetLatest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/partition.offset.zero/client",
        "${net}/partition.offset.zero/server"})
    public void shouldRequestPartitionOffsetZero() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/message.key/client",
        "${net}/message.key/server"})
    public void shouldReceiveMessageKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/message.key.null/client",
        "${net}/message.key.null/server"})
    public void shouldReceiveMessageKeyNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/message.key.with.value.null/client",
        "${net}/message.key.with.value.null/server"})
    public void shouldReceiveMessageKeyWithValueNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/message.key.with.value.distinct/client",
        "${net}/message.key.with.value.distinct/server"})
    public void shouldReceiveMessageKeyWithValueDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/message.key.with.header/client",
        "${net}/message.key.with.header/server"})
    public void shouldReceiveMessageKeyWithHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/message.key.distinct/client",
        "${net}/message.key.distinct/server"})
    public void shouldReceiveMessageKeyDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/message.value/client",
        "${net}/message.value/server"})
    public void shouldReceiveMessageValue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/message.value.null/client",
        "${net}/message.value.null/server"})
    public void shouldReceiveMessageValueNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/message.value.10k/client",
        "${net}/message.value.10k/server"})
    public void shouldReceiveMessageValue10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/message.value.100k/client",
        "${net}/message.value.100k/server"})
    public void shouldReceiveMessageValue100k() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Specification({
        "${net}/message.value.gzip/client",
        "${net}/message.value.gzip/server"})
    public void shouldReceiveMessageValueGzip() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Specification({
        "${net}/message.value.snappy/client",
        "${net}/message.value.snappy/server"})
    public void shouldReceiveMessageValueSnappy() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Specification({
        "${net}/message.value.lz4/client",
        "${net}/message.value.lz4/server"})
    public void shouldReceiveMessageValueLz4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/message.value.distinct/client",
        "${net}/message.value.distinct/server"})
    public void shouldReceiveMessageValueDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/message.value.avro/client",
        "${net}/message.value.avro/server"})
    public void shouldReceiveMessageValueAvro() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/message.header/client",
        "${net}/message.header/server"})
    public void shouldReceiveMessageHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/message.header.null/client",
        "${net}/message.header.null/server"})
    public void shouldReceiveMessageHeaderNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/message.headers.distinct/client",
        "${net}/message.headers.distinct/server"})
    public void shouldReceiveMessageHeadersDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/message.headers.repeated/client",
        "${net}/message.headers.repeated/server"})
    public void shouldReceiveMessageHeadersRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/filter.none/client",
        "${net}/filter.none/server"})
    public void shouldReceiveMessagesWithNoFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/filter.sync/client",
        "${net}/filter.sync/server"})
    public void shouldFetchFilterSync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/isolation.read.committed/client",
        "${net}/isolation.read.committed/server"})
    public void shouldReceiveIsolationReadCommitted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/isolation.read.uncommitted.aborting/client",
        "${net}/isolation.read.uncommitted.aborting/server"})
    public void shouldReceiveIsolationReadUncommittedAborting() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/partition.leader.distinct/client",
        "${net}/partition.leader.distinct/server"})
    public void shouldReceiveDistinctPartitionLeader() throws Exception
    {
        k3po.finish();
    }
}
