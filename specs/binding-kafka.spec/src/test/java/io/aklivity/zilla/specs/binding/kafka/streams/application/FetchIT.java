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

public class FetchIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/kafka/streams/application/fetch");

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
        "${app}/partition.incomplete/client",
        "${app}/partition.incomplete/server"})
    public void shouldRejectWhenPartitionIncomplete() throws Exception
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
        "${app}/partition.offset/client",
        "${app}/partition.offset/server"})
    public void shouldRequestPartitionOffset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/partition.offset.earliest/client",
        "${app}/partition.offset.earliest/server"})
    public void shouldRequestPartitionOffsetEarliest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/partition.offset.latest/client",
        "${app}/partition.offset.latest/server"})
    public void shouldRequestPartitionOffsetLatest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/partition.offset.zero/client",
        "${app}/partition.offset.zero/server"})
    public void shouldRequestPartitionOffsetZero() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.key/client",
        "${app}/message.key/server"})
    public void shouldReceiveMessageKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.key.null/client",
        "${app}/message.key.null/server"})
    public void shouldReceiveMessageKeyNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.key.with.value.null/client",
        "${app}/message.key.with.value.null/server"})
    public void shouldReceiveMessageKeyWithValueNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.key.with.value.distinct/client",
        "${app}/message.key.with.value.distinct/server"})
    public void shouldReceiveMessageKeyWithValueDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.key.with.header/client",
        "${app}/message.key.with.header/server"})
    public void shouldReceiveMessageKeyWithHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.key.distinct/client",
        "${app}/message.key.distinct/server"})
    public void shouldReceiveMessageKeyDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.value/client",
        "${app}/message.value/server"})
    public void shouldReceiveMessageValue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.value.empty/client",
        "${app}/message.value.empty/server"})
    public void shouldReceiveMessageValueEmpty() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${app}/message.value.null/client",
        "${app}/message.value.null/server"})
    public void shouldReceiveMessageValueNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.value.10k/client",
        "${app}/message.value.10k/server"})
    public void shouldReceiveMessageValue10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.value.100k/client",
        "${app}/message.value.100k/server"})
    public void shouldReceiveMessageValue100k() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Specification({
        "${app}/message.value.gzip/client",
        "${app}/message.value.gzip/server"})
    public void shouldReceiveMessageValueGzip() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Specification({
        "${app}/message.value.snappy/client",
        "${app}/message.value.snappy/server"})
    public void shouldReceiveMessageValueSnappy() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Specification({
        "${app}/message.value.lz4/client",
        "${app}/message.value.lz4/server"})
    public void shouldReceiveMessageValueLz4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.value.distinct/client",
        "${app}/message.value.distinct/server"})
    public void shouldReceiveMessageValueDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.header/client",
        "${app}/message.header/server"})
    public void shouldReceiveMessageHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.header.null/client",
        "${app}/message.header.null/server"})
    public void shouldReceiveMessageHeaderNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.headers.distinct/client",
        "${app}/message.headers.distinct/server"})
    public void shouldReceiveMessageHeadersDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/message.headers.repeated/client",
        "${app}/message.headers.repeated/server"})
    public void shouldReceiveMessageHeadersRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.none/client",
        "${app}/filter.none/server"})
    public void shouldReceiveMessagesWithNoFilter() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("SEND_MESSAGE_3");
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.sync/client",
        "${app}/filter.sync/server"})
    public void shouldFetchFilterSync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.key/client",
        "${app}/filter.key/server"})
    public void shouldReceiveMessagesWithKeyFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.key.and.header/client",
        "${app}/filter.key.and.header/server"})
    public void shouldReceiveMessagesWithKeyAndHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.key.or.header/client",
        "${app}/filter.key.or.header/server"})
    public void shouldReceiveMessagesWithKeyOrHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.header/client",
        "${app}/filter.header/server"})
    public void shouldReceiveMessagesWithHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.header.and.header/client",
        "${app}/filter.header.and.header/server"})
    public void shouldReceiveMessagesWithHeaderAndHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.header.or.header/client",
        "${app}/filter.header.or.header/server"})
    public void shouldReceiveMessagesWithHeaderOrHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.key.and.header.or.header/client",
        "${app}/filter.key.and.header.or.header/server"})
    public void shouldReceiveMessagesWithKeyAndHeaderOrHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.key.or.header.and.header/client",
        "${app}/filter.key.or.header.and.header/server"})
    public void shouldReceiveMessagesWithKeyOrHeaderAndHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.none.json/client",
        "${app}/filter.none.json/server"})
    public void shouldReceiveJsonMessagesWithNoFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.none.json.patch/client",
        "${app}/filter.none.json.patch/server"})
    public void shouldReceiveJsonPatchMessagesWithNoFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.header.json.patch/client",
        "${app}/filter.header.json.patch/server"})
    public void shouldReceiveJsonPatchMessagesWithHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.key.and.header.json.patch/client",
        "${app}/filter.key.and.header.json.patch/server"})
    public void shouldReceiveJsonPatchMessagesWithKeyAndHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/compact.message.with.message/client",
        "${app}/compact.message.with.message/server"})
    public void shouldCompactMessageWithMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/compact.message.with.tombstone/client",
        "${app}/compact.message.with.tombstone/server"})
    public void shouldCompactMessageWithTombstone() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/compact.tombstone.with.message/client",
        "${app}/compact.tombstone.with.message/server"})
    public void shouldCompactTombstoneWithMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/compact.tombstone.with.tombstone/client",
        "${app}/compact.tombstone.with.tombstone/server"})
    public void shouldCompactTombstoneWithTombstone() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/compacted.message.with.message/client",
        "${app}/compacted.message.with.message/server"})
    public void shouldReceiveMessageAfterCompactedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/compacted.message.with.tombstone/client",
        "${app}/compacted.message.with.tombstone/server"})
    public void shouldReceiveTombstoneAfterCompactedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/compacted.tombstone.with.message/client",
        "${app}/compacted.tombstone.with.message/server"})
    public void shouldReceiveMessageAfterCompactedTombstone() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/compacted.tombstone.with.tombstone/client",
        "${app}/compacted.tombstone.with.tombstone/server"})
    public void shouldReceiveTombstoneAfterCompactedTombstone() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/follow.message.with.message/client",
        "${app}/follow.message.with.message/server"})
    public void shouldFollowMessageWithMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/followed.message.with.message/client",
        "${app}/followed.message.with.message/server"})
    public void shouldReceiveMessageFollowedAfterMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.not.header/client",
        "${app}/filter.not.header/server"})
    public void shouldReceiveMessagesWithNotHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.not.key/client",
        "${app}/filter.not.key/server"})
    public void shouldReceiveMessagesWithNotKeyFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.key.and.not.header/client",
        "${app}/filter.key.and.not.header/server"})
    public void shouldFetchMergedMessagesWithKeyAndNotHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.headers.one/client",
        "${app}/filter.headers.one/server"})
    public void shouldReceiveMessagesWithHeadersOneFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.headers.one.empty/client",
        "${app}/filter.headers.one.empty/server"})
    public void shouldReceiveMessagesWithHeadersOneEmptyFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.headers.many/client",
        "${app}/filter.headers.many/server"})
    public void shouldReceiveMessagesWithHeadersManyFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.headers.many.empty/client",
        "${app}/filter.headers.many.empty/server"})
    public void shouldReceiveMessagesWithHeadersManyEmptyFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/isolation.read.committed/client",
        "${app}/isolation.read.committed/server"})
    public void shouldReceiveIsolationReadCommitted() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("SEND_MESSAGE_3");
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/isolation.read.committed.aborted/client",
        "${app}/isolation.read.committed.aborted/server"})
    public void shouldReceiveIsolationReadCommittedWhenAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/isolation.read.committed.aborting/client",
        "${app}/isolation.read.committed.aborting/server"})
    public void shouldReceiveIsolationReadCommittedWhenAborting() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/isolation.read.committed.committing/client",
        "${app}/isolation.read.committed.committing/server"})
    public void shouldReceiveIsolationReadCommittedWhenCommitting() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/isolation.read.uncommitted.aborted/client",
        "${app}/isolation.read.uncommitted.aborted/server"})
    public void shouldReceiveIsolationReadUncommittedWhenAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/isolation.read.uncommitted.aborting/client",
        "${app}/isolation.read.uncommitted.aborting/server"})
    public void shouldReceiveIsolationReadUncommittedWhenAborting() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/isolation.read.uncommitted.committing/client",
        "${app}/isolation.read.uncommitted.committing/server"})
    public void shouldReceiveIsolationReadUncommittedWhenCommitting() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_UNCOMMITTED");
        k3po.notifyBarrier("SEND_COMMIT");
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/partition.leader.distinct/client",
        "${app}/partition.leader.distinct/server"})
    public void shouldReceiveDistinctPartitionLeader() throws Exception
    {
        k3po.finish();
    }
}
