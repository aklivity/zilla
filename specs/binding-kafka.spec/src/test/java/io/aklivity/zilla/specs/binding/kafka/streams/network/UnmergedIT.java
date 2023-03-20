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

public class UnmergedIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/kafka/streams/network/unmerged.p3.f5.d0.m5");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/unmerged.fetch.filter.none.read.committed/client",
        "${net}/unmerged.fetch.filter.none.read.committed/server"})
    public void shouldFetchUnmergedMessagesWithNoFilterReadCommitted() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("SEND_MESSAGE_A3");
        k3po.notifyBarrier("SEND_MESSAGE_B3");
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/unmerged.fetch.filter.none.read.uncommitted/client",
        "${net}/unmerged.fetch.filter.none.read.uncommitted/server"})
    public void shouldFetchUnmergedMessagesWithNoFilterReadUncommitted() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("SEND_MESSAGE_A3");
        k3po.notifyBarrier("SEND_MESSAGE_B3");
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/unmerged.fetch.partition.offsets.earliest/client",
        "${net}/unmerged.fetch.partition.offsets.earliest/server"})
    public void shouldFetchUnmergedPartitionOffsetsEarliest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/unmerged.fetch.filter.sync/client",
        "${net}/unmerged.fetch.filter.sync/server"})
    public void shouldFetchUnmergedPartitionLatest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/unmerged.fetch.message.values.read.committed/client",
        "${net}/unmerged.fetch.message.values.read.committed/server"})
    public void shouldFetchUnmergedMessageValuesReadCommitted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/unmerged.fetch.message.values.read.uncommitted/client",
        "${net}/unmerged.fetch.message.values.read.uncommitted/server"})
    public void shouldFetchUnmergedMessageValuesReadUncommitted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/unmerged.produce.message.values/client",
        "${net}/unmerged.produce.message.values/server"})
    public void shouldProduceUnmergedMessageValues() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/unmerged.produce.message.value.10k/client",
        "${net}/unmerged.produce.message.value.10k/server"})
    public void shouldProduceUnmergedMessageValue10k() throws Exception
    {
        k3po.finish();
    }
    @Test
    @Specification({
        "${net}/unmerged.produce.message.value.100k/client",
        "${net}/unmerged.produce.message.value.100k/server"})
    public void shouldProduceUnmergedMessageValue100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/unmerged.produce.message.values.dynamic/client",
        "${net}/unmerged.produce.message.values.dynamic/server"})
    public void shouldProduceUnmergedMessageValuesDynamic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/unmerged.produce.message.values.dynamic.hashed/client",
        "${net}/unmerged.produce.message.values.dynamic.hashed/server"})
    public void shouldProduceUnmergedMessageValuesDynamicHashed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/unmerged.produce.message.values.null/client",
        "${net}/unmerged.produce.message.values.null/server"})
    public void shouldProduceUnmergedMessageValuesNull() throws Exception
    {
        k3po.finish();
    }
}
