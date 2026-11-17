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
package io.aklivity.zilla.specs.binding.kafka.streams.network;

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
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/kafka/streams/network/share.group.m13.f6.h1.s1.a1")
        .addScriptRoot("sasl",
            "io/aklivity/zilla/specs/binding/kafka/streams/network/share.group.sasl.m13.f6.h1.s1.a1.handshake.v1");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/receive.message.value/client",
        "${net}/receive.message.value/server"})
    public void shouldReceiveMessageValue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/receive.message.values/client",
        "${net}/receive.message.values/server"})
    public void shouldReceiveMessageValues() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/receive.message.key.with.header/client",
        "${net}/receive.message.key.with.header/server"})
    public void shouldReceiveMessageKeyWithHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/receive.message.value.10k/client",
        "${net}/receive.message.value.10k/server"})
    public void shouldReceiveMessageValue10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/receive.message.value.100k/client",
        "${net}/receive.message.value.100k/server"})
    public void shouldReceiveMessageValue100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/receive.message.values.batch/client",
        "${net}/receive.message.values.batch/server"})
    public void shouldReceiveMessageValuesBatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/receive.message.values.outside.acquired/client",
        "${net}/receive.message.values.outside.acquired/server"})
    public void shouldReceiveMessageValuesOutsideAcquired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reduce.lock.timeout.delayed.response/client",
        "${net}/reduce.lock.timeout.delayed.response/server"})
    public void shouldReduceLockTimeoutDelayedResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/acknowledge.accept/client",
        "${net}/acknowledge.accept/server"})
    public void shouldAcknowledgeAccept() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/acknowledge.release/client",
        "${net}/acknowledge.release/server"})
    public void shouldAcknowledgeRelease() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/acknowledge.reject/client",
        "${net}/acknowledge.reject/server"})
    public void shouldAcknowledgeReject() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/acknowledge.accept.gap/client",
        "${net}/acknowledge.accept.gap/server"})
    public void shouldAcknowledgeAcceptGap() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.compressed.batch/client",
        "${net}/reject.compressed.batch/server"})
    public void shouldRejectCompressedBatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/acknowledge.invalid.record.state/client",
        "${net}/acknowledge.invalid.record.state/server"})
    public void shouldAcknowledgeInvalidRecordState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/acknowledge.invalid.record.state.uncorrelated/client",
        "${net}/acknowledge.invalid.record.state.uncorrelated/server"})
    public void shouldAcknowledgeInvalidRecordStateUncorrelated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/receive.multiple.partitions/client",
        "${net}/receive.multiple.partitions/server"})
    public void shouldReceiveMultiplePartitions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/change.assignment/client",
        "${net}/change.assignment/server"})
    public void shouldChangeAssignment() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/change.assignment.acknowledge/client",
        "${net}/change.assignment.acknowledge/server"})
    public void shouldChangeAssignmentAcknowledge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/await.assignment/client",
        "${net}/await.assignment/server"})
    public void shouldAwaitAssignment() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/flow.control.window/client",
        "${net}/flow.control.window/server"})
    public void shouldFlowControlWindow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/leave/client",
        "${net}/leave/server"})
    public void shouldLeave() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/leave.during.fetch/client",
        "${net}/leave.during.fetch/server"})
    public void shouldLeaveDuringFetch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.write.abort.after.data/client",
        "${net}/client.sent.write.abort.after.data/server"})
    public void shouldClientSentWriteAbortAfterData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.read.abort.after.data/client",
        "${net}/client.sent.read.abort.after.data/server"})
    public void shouldClientSentReadAbortAfterData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/broker.leader.connection.abort/client",
        "${net}/broker.leader.connection.abort/server"})
    public void shouldBrokerLeaderConnectionAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/broker.coordinator.connection.abort/client",
        "${net}/broker.coordinator.connection.abort/server"})
    public void shouldBrokerCoordinatorConnectionAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/handle.coordinator.not.available/client",
        "${net}/handle.coordinator.not.available/server"})
    public void shouldHandleCoordinatorNotAvailable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/handle.heartbeat.not.coordinator/client",
        "${net}/handle.heartbeat.not.coordinator/server"})
    public void shouldHandleHeartbeatNotCoordinator() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/rejoin.unknown.member.id/client",
        "${net}/rejoin.unknown.member.id/server"})
    public void shouldRejoinUnknownMemberId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/rejoin.fenced.member.epoch/client",
        "${net}/rejoin.fenced.member.epoch/server"})
    public void shouldRejoinFencedMemberEpoch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.group.authorization.failed/client",
        "${net}/reject.group.authorization.failed/server"})
    public void shouldRejectGroupAuthorizationFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.topic.authorization.failed/client",
        "${net}/reject.topic.authorization.failed/server"})
    public void shouldRejectTopicAuthorizationFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.unknown.topic.or.partition/client",
        "${net}/reject.unknown.topic.or.partition/server"})
    public void shouldRejectUnknownTopicOrPartition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/handle.partition.not.leader/client",
        "${net}/handle.partition.not.leader/server"})
    public void shouldHandlePartitionNotLeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.unsupported.version.heartbeat.absent/client",
        "${net}/reject.unsupported.version.heartbeat.absent/server"})
    public void shouldRejectUnsupportedVersionHeartbeatAbsent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.unsupported.version.heartbeat.range/client",
        "${net}/reject.unsupported.version.heartbeat.range/server"})
    public void shouldRejectUnsupportedVersionHeartbeatRange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.unsupported.version.heartbeat.error/client",
        "${net}/reject.unsupported.version.heartbeat.error/server"})
    public void shouldRejectUnsupportedVersionHeartbeatError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.unsupported.version.metadata.range/client",
        "${net}/reject.unsupported.version.metadata.range/server"})
    public void shouldRejectUnsupportedVersionMetadataRange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.unsupported.version.fetch.range/client",
        "${net}/reject.unsupported.version.fetch.range/server"})
    public void shouldRejectUnsupportedVersionFetchRange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${sasl}/receive.message.value.with.sasl.plain/client",
        "${sasl}/receive.message.value.with.sasl.plain/server"})
    public void shouldReceiveMessageValueWithSaslPlain() throws Exception
    {
        k3po.finish();
    }
}
