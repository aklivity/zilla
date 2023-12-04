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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfigurationTest
    .KAFKA_CLIENT_CONNECTION_POOL_CLEANUP_MILLIS_NAME;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfigurationTest.KAFKA_CLIENT_INSTANCE_ID_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

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

public class ClientGroupIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/kafka/streams/network/group.f1.j5.s3.l3.h3")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/kafka/streams/application/group");

    private final TestRule timeout = new DisableOnDebug(new Timeout(15, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(KAFKA_CLIENT_INSTANCE_ID_NAME,
            "io.aklivity.zilla.runtime.binding.kafka.internal.stream.ClientGroupIT::supplyInstanceId")
        .configurationRoot("io/aklivity/zilla/specs/binding/kafka/config")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/client.sent.write.abort.before.coordinator.response/client",
        "${net}/client.sent.write.abort.before.coordinator.response/server"})
    @Configure(name = KAFKA_CLIENT_CONNECTION_POOL_CLEANUP_MILLIS_NAME, value = "0")
    public void shouldHandleClientSentWriteAbortBeforeCoordinatorResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/client.sent.write.abort.after.sync.group.response/client",
        "${net}/client.sent.write.abort.after.sync.group.response/server"})
    @Configure(name = KAFKA_CLIENT_CONNECTION_POOL_CLEANUP_MILLIS_NAME, value = "0")
    public void shouldHandleClientSentWriteAbortAfterSyncGroupResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/client.sent.read.abort.after.sync.group.response/client",
        "${net}/client.sent.read.abort.after.sync.group.response/server"})
    @Configure(name = KAFKA_CLIENT_CONNECTION_POOL_CLEANUP_MILLIS_NAME, value = "0")
    public void shouldHandleClientSentReadAbortAfterSyncGroupResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/rebalance.protocol.highlander/client",
        "${net}/rebalance.protocol.highlander/server"})
    public void shouldLeaveGroupOnGroupRebalanceError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/leader/client",
        "${net}/coordinator.not.available/server"})
    public void shouldHandleCoordinatorNotAvailableError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/leader/client",
        "${net}/coordinator.reject.invalid.consumer/server"})
    public void shouldRejectInvalidConsumer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/leader/client",
        "${net}/rebalance.protocol.highlander.unknown.member.id/server"})
    public void shouldRebalanceProtocolHighlanderUnknownMemberId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/rebalance.protocol.highlander.migrate.leader/client",
        "${net}/rebalance.protocol.highlander.migrate.leader/server"})
    public void shouldRebalanceProtocolHighlanderMigrateLeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/rebalance.protocol.highlander.migrate.leader.in.parallel/client",
        "${net}/rebalance.protocol.highlander.migrate.leader.in.parallel/server"})
    public void shouldRebalanceProtocolHighlanderMigrateLeaderInParallel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/rebalance.protocol.unknown/client",
        "${net}/rebalance.protocol.unknown/server"})
    public void shouldRejectSecondStreamOnUnknownProtocol() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/rebalance.sync.group/client",
        "${net}/rebalance.sync.group/server"})
    public void shouldHandleRebalanceSyncGroup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/ignore.heartbeat.before.handshake/client",
        "${net}/ignore.heartbeat.before.handshake/server"})
    public void shouldIgnoreHeartbeatBeforeHandshakeComplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/rebalance.multiple.members.with.same.group.id/client",
        "${net}/rebalance.multiple.members.with.same.group.id/server"})
    public void shouldRebalanceMultipleMembersWithSameGroupId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/rebalance.protocol.highlander.heartbeat.unknown.member/client",
        "${net}/rebalance.protocol.highlander.heartbeat.unknown.member/server"})
    public void shouldRebalanceProtocolHighlanderOnHeartbeatUnknownMember() throws Exception
    {
        k3po.finish();
    }

    public static String supplyInstanceId()
    {
        return "zilla";
    }
}
