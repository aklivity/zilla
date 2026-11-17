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

public class ShareCacheIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/kafka/streams/application/share.cache");

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
        "${app}/member.receive.message.value/client",
        "${app}/member.receive.message.value/server"})
    public void shouldMemberReceiveMessageValue() throws Exception
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
        "${app}/member.receive.message.value.10k/client",
        "${app}/member.receive.message.value.10k/server"})
    public void shouldMemberReceiveMessageValue10k() throws Exception
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
        "${app}/member.receive.message.key.with.header/client",
        "${app}/member.receive.message.key.with.header/server"})
    public void shouldMemberReceiveMessageKeyWithHeader() throws Exception
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
        "${app}/member.receive.message.values.outside.acquired/client",
        "${app}/member.receive.message.values.outside.acquired/server"})
    public void shouldMemberReceiveMessageValuesOutsideAcquired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/distribute.messages.two.streams/client",
        "${app}/distribute.messages.two.streams/server"})
    public void shouldDistributeMessagesTwoStreams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/member.distribute.messages.two.streams/client",
        "${app}/member.distribute.messages.two.streams/server"})
    public void shouldMemberDistributeMessagesTwoStreams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/distribute.messages.window/client",
        "${app}/distribute.messages.window/server"})
    public void shouldDistributeMessagesWindow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/member.distribute.messages.window/client",
        "${app}/member.distribute.messages.window/server"})
    public void shouldMemberDistributeMessagesWindow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reroute.messages.stream.abort/client",
        "${app}/reroute.messages.stream.abort/server"})
    public void shouldRerouteMessagesStreamAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/member.reroute.messages.stream.abort/client",
        "${app}/member.reroute.messages.stream.abort/server"})
    public void shouldMemberRerouteMessagesStreamAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reroute.messages.stream.end/client",
        "${app}/reroute.messages.stream.end/server"})
    public void shouldRerouteMessagesStreamEnd() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/member.reroute.messages.stream.end/client",
        "${app}/member.reroute.messages.stream.end/server"})
    public void shouldMemberRerouteMessagesStreamEnd() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/leave.release.unacknowledged/client",
        "${app}/leave.release.unacknowledged/server"})
    public void shouldLeaveReleaseUnacknowledged() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/member.leave.release.unacknowledged/client",
        "${app}/member.leave.release.unacknowledged/server"})
    public void shouldMemberLeaveReleaseUnacknowledged() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/leave.release.staged/client",
        "${app}/leave.release.staged/server"})
    public void shouldLeaveReleaseStaged() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/member.leave.release.staged/client",
        "${app}/member.leave.release.staged/server"})
    public void shouldMemberLeaveReleaseStaged() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.key/client",
        "${app}/filter.key/server"})
    public void shouldFilterKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/member.filter.key/client",
        "${app}/member.filter.key/server"})
    public void shouldMemberFilterKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.key.skip.all/client",
        "${app}/filter.key.skip.all/server"})
    public void shouldFilterKeySkipAll() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/member.filter.key.skip.all/client",
        "${app}/member.filter.key.skip.all/server"})
    public void shouldMemberFilterKeySkipAll() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/filter.header/client",
        "${app}/filter.header/server"})
    public void shouldFilterHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/member.filter.header/client",
        "${app}/member.filter.header/server"})
    public void shouldMemberFilterHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reject.filter.mismatch/client",
        "${app}/reject.filter.mismatch/server"})
    public void shouldRejectFilterMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/member.reject.filter.mismatch/client",
        "${app}/member.reject.filter.mismatch/server"})
    public void shouldMemberRejectFilterMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reject.topic.mismatch/client",
        "${app}/reject.topic.mismatch/server"})
    public void shouldRejectTopicMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/member.reject.topic.mismatch/client",
        "${app}/member.reject.topic.mismatch/server"})
    public void shouldMemberRejectTopicMismatch() throws Exception
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
        "${app}/member.acknowledge.invalid.record.state/client",
        "${app}/member.acknowledge.invalid.record.state/server"})
    public void shouldMemberAcknowledgeInvalidRecordState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/acknowledge.unknown.record/client",
        "${app}/acknowledge.unknown.record/server"})
    public void shouldAcknowledgeUnknownRecord() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/member.acknowledge.unknown.record/client",
        "${app}/member.acknowledge.unknown.record/server"})
    public void shouldMemberAcknowledgeUnknownRecord() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/expire.lock/client",
        "${app}/expire.lock/server"})
    public void shouldExpireLock() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/member.expire.lock/client",
        "${app}/member.expire.lock/server"})
    public void shouldMemberExpireLock() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/expire.lock.lapsed/client",
        "${app}/expire.lock.lapsed/server"})
    public void shouldExpireLockLapsed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/member.expire.lock.lapsed/client",
        "${app}/member.expire.lock.lapsed/server"})
    public void shouldMemberExpireLockLapsed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/skip.lock.margin/client",
        "${app}/skip.lock.margin/server"})
    public void shouldSkipLockMargin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/member.skip.lock.margin/client",
        "${app}/member.skip.lock.margin/server"})
    public void shouldMemberSkipLockMargin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/limit.staging.bytes/client",
        "${app}/limit.staging.bytes/server"})
    public void shouldLimitStagingBytes() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/member.limit.staging.bytes/client",
        "${app}/member.limit.staging.bytes/server"})
    public void shouldMemberLimitStagingBytes() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.abort.after.data/client",
        "${app}/server.sent.abort.after.data/server"})
    public void shouldServerSentAbortAfterData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/member.server.sent.abort.after.data/client",
        "${app}/member.server.sent.abort.after.data/server"})
    public void shouldMemberServerSentAbortAfterData() throws Exception
    {
        k3po.finish();
    }
}
