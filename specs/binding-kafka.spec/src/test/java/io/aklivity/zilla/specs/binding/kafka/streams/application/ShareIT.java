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
package io.aklivity.zilla.specs.binding.kafka.streams.application;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class ShareIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/kafka/streams/application/share.group");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/receive.message.value/client",
        "${app}/receive.message.value/server"})
    public void shouldReceiveMessageValue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/receive.message.values/client",
        "${app}/receive.message.values/server"})
    public void shouldReceiveMessageValues() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/receive.message.key.with.header/client",
        "${app}/receive.message.key.with.header/server"})
    public void shouldReceiveMessageKeyWithHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/receive.message.value.10k/client",
        "${app}/receive.message.value.10k/server"})
    public void shouldReceiveMessageValue10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/receive.message.value.100k/client",
        "${app}/receive.message.value.100k/server"})
    public void shouldReceiveMessageValue100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/receive.message.values.batch/client",
        "${app}/receive.message.values.batch/server"})
    public void shouldReceiveMessageValuesBatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/receive.message.values.outside.acquired/client",
        "${app}/receive.message.values.outside.acquired/server"})
    public void shouldReceiveMessageValuesOutsideAcquired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reduce.lock.timeout.delayed.response/client",
        "${app}/reduce.lock.timeout.delayed.response/server"})
    public void shouldReduceLockTimeoutDelayedResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/acknowledge.accept/client",
        "${app}/acknowledge.accept/server"})
    public void shouldAcknowledgeAccept() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/acknowledge.release/client",
        "${app}/acknowledge.release/server"})
    public void shouldAcknowledgeRelease() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/acknowledge.reject/client",
        "${app}/acknowledge.reject/server"})
    public void shouldAcknowledgeReject() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/acknowledge.accept.gap/client",
        "${app}/acknowledge.accept.gap/server"})
    public void shouldAcknowledgeAcceptGap() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reject.compressed.batch/client",
        "${app}/reject.compressed.batch/server"})
    public void shouldRejectCompressedBatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/acknowledge.invalid.record.state/client",
        "${app}/acknowledge.invalid.record.state/server"})
    public void shouldAcknowledgeInvalidRecordState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/acknowledge.invalid.record.state.uncorrelated/client",
        "${app}/acknowledge.invalid.record.state.uncorrelated/server"})
    public void shouldAcknowledgeInvalidRecordStateUncorrelated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/receive.multiple.partitions/client",
        "${app}/receive.multiple.partitions/server"})
    public void shouldReceiveMultiplePartitions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/change.assignment/client",
        "${app}/change.assignment/server"})
    public void shouldChangeAssignment() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/await.assignment/client",
        "${app}/await.assignment/server"})
    public void shouldAwaitAssignment() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/flow.control.window/client",
        "${app}/flow.control.window/server"})
    public void shouldFlowControlWindow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/leave/client",
        "${app}/leave/server"})
    public void shouldLeave() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/leave.during.fetch/client",
        "${app}/leave.during.fetch/server"})
    public void shouldLeaveDuringFetch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.write.abort.after.data/client",
        "${app}/client.sent.write.abort.after.data/server"})
    public void shouldClientSentWriteAbortAfterData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.read.abort.after.data/client",
        "${app}/client.sent.read.abort.after.data/server"})
    public void shouldClientSentReadAbortAfterData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/broker.leader.connection.abort/client",
        "${app}/broker.leader.connection.abort/server"})
    public void shouldBrokerLeaderConnectionAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/broker.coordinator.connection.abort/client",
        "${app}/broker.coordinator.connection.abort/server"})
    public void shouldBrokerCoordinatorConnectionAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/handle.coordinator.not.available/client",
        "${app}/handle.coordinator.not.available/server"})
    public void shouldHandleCoordinatorNotAvailable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/handle.heartbeat.not.coordinator/client",
        "${app}/handle.heartbeat.not.coordinator/server"})
    public void shouldHandleHeartbeatNotCoordinator() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/rejoin.unknown.member.id/client",
        "${app}/rejoin.unknown.member.id/server"})
    public void shouldRejoinUnknownMemberId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/rejoin.fenced.member.epoch/client",
        "${app}/rejoin.fenced.member.epoch/server"})
    public void shouldRejoinFencedMemberEpoch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reject.group.authorization.failed/client",
        "${app}/reject.group.authorization.failed/server"})
    public void shouldRejectGroupAuthorizationFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reject.topic.authorization.failed/client",
        "${app}/reject.topic.authorization.failed/server"})
    public void shouldRejectTopicAuthorizationFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reject.unknown.topic.or.partition/client",
        "${app}/reject.unknown.topic.or.partition/server"})
    public void shouldRejectUnknownTopicOrPartition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/handle.partition.not.leader/client",
        "${app}/handle.partition.not.leader/server"})
    public void shouldHandlePartitionNotLeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reject.unsupported.version.heartbeat.absent/client",
        "${app}/reject.unsupported.version.heartbeat.absent/server"})
    public void shouldRejectUnsupportedVersionHeartbeatAbsent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reject.unsupported.version.heartbeat.range/client",
        "${app}/reject.unsupported.version.heartbeat.range/server"})
    public void shouldRejectUnsupportedVersionHeartbeatRange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reject.unsupported.version.heartbeat.error/client",
        "${app}/reject.unsupported.version.heartbeat.error/server"})
    public void shouldRejectUnsupportedVersionHeartbeatError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reject.unsupported.version.metadata.range/client",
        "${app}/reject.unsupported.version.metadata.range/server"})
    public void shouldRejectUnsupportedVersionMetadataRange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reject.unsupported.version.fetch.range/client",
        "${app}/reject.unsupported.version.fetch.range/server"})
    public void shouldRejectUnsupportedVersionFetchRange() throws Exception
    {
        k3po.finish();
    }
}
