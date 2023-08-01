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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class GroupIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/kafka/streams/network/group.f1.j5.s3.l3.h3");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/rebalance.protocol.highlander/client",
        "${net}/rebalance.protocol.highlander/server"})
    public void shouldLeaveGroupOnGroupRebalanceError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.write.abort.before.coordinator.response/client",
        "${net}/client.sent.write.abort.before.coordinator.response/server"})
    public void shouldHandleClientSentWriteAbortBeforeCoordinatorResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/coordinator.not.available/client",
        "${net}/coordinator.not.available/server"})
    public void shouldHandleCoordinatorNotAvailableError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/coordinator.reject.invalid.consumer/client",
        "${net}/coordinator.reject.invalid.consumer/server"})
    public void shouldHRejectInvalidConsumer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/rebalance.protocol.highlander.unknown.member.id/client",
        "${net}/rebalance.protocol.highlander.unknown.member.id/server"})
    public void shouldRebalanceProtocolHighlanderUnknownMemberId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/rebalance.protocol.highlander.migrate.leader/client",
        "${net}/rebalance.protocol.highlander.migrate.leader/server"})
    public void shouldRebalanceProtocolHighlanderMigrateLeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/rebalance.protocol.unknown/client",
        "${net}/rebalance.protocol.unknown/server"})
    public void shouldRejectSecondStreamOnUnknownProtocol() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/rebalance.sync.group//client",
        "${net}/rebalance.sync.group/server"})
    public void shouldHandleRebalanceSyncGroup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/ignore.heartbeat.before.handshake/client",
        "${net}/ignore.heartbeat.before.handshake/server"})
    public void shouldIgnoreHeartbeatBeforeHandshakeComplete() throws Exception
    {
        k3po.finish();
    }
}
