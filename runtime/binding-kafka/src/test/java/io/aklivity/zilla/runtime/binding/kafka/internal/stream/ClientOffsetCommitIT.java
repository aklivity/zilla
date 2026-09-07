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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfigurationTest.KAFKA_CLIENT_API_VERSIONS_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class ClientOffsetCommitIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/kafka/streams/network/offset.commit.v7")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/kafka/streams/application/offset.commit");

    private final TestRule timeout = new DisableOnDebug(new Timeout(15, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/kafka/config")
        .configure("zilla.binding.kafka.client.connection.pool", "false")
        .configure("zilla.binding.kafka.client.api.versions", "false")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);


    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/update.topic.partition.offset/client",
        "${net}/update.topic.partition.offset/server"})
    public void shouldUpdateTopicPartitionOffset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/update.topic.partition.offsets/client",
        "${net}/update.topic.partition.offsets/server"})
    public void shouldUpdateTopicPartitionOffsets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/update.unknown.topic.partition.offset/client",
        "${net}/update.unknown.topic.partition.offset/server"})
    public void shouldRejectUnknownTopicPartitionOffset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.event.offset.commit.error.yaml")
    @Specification({
        "${app}/offset.commit.error/client",
        "${net}/offset.commit.error/server"})
    public void shouldHandleOffsetCommitError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/bare.commit/client",
        "${net}/bare.commit/server"})
    public void shouldCommitBareOffset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/reset.offsets.dead/client",
        "${net}/reset.offsets.dead/server"})
    public void shouldResetOffsetsForDeadGroup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/find.then.describe/client",
        "${net}/find.then.describe/server"})
    public void shouldChainFindCoordinatorThenDescribeGroups() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/find.coordinator.only/client",
        "${net}/find.coordinator.only/server"})
    public void shouldFindCoordinatorOnly() throws Exception
    {
        k3po.finish();
    }

    // Same chaining as shouldChainFindCoordinatorThenDescribeGroups, but with api.versions
    // negotiation enabled (the production default). Every other test in this class disables
    // api.versions, so none of them exercise doEncodeRequest's apiVersionRangeByApiKey-driven
    // branch for the second pooled request once apiVersionRangeByApiKey is already populated
    // from the leading ApiVersions exchange - reproduces https://github.com/aklivity/zilla/issues/2532.
    @Test
    @Configuration("client.yaml")
    @Configure(name = KAFKA_CLIENT_API_VERSIONS_NAME, value = "true")
    @Specification({
        "${app}/find.then.describe/client",
        "${net}/find.then.describe.negotiated/server"})
    public void shouldChainFindCoordinatorThenDescribeGroupsWithApiVersionsNegotiated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/find.then.describe.no.wait/client",
        "${net}/find.then.describe/server"})
    public void shouldChainFindCoordinatorThenDescribeGroupsNoWait() throws Exception
    {
        k3po.finish();
    }
}
