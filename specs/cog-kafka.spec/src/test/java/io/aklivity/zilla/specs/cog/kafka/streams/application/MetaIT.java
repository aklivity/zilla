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
package io.aklivity.zilla.specs.cog.kafka.streams.application;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class MetaIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/cog/kafka/streams/application/meta");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/topic.unknown/client",
        "${app}/topic.unknown/server"})
    public void shouldRejectWhenTopicUnknown() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/topic.invalid/client",
        "${app}/topic.invalid/server"})
    public void shouldRejectWhenTopicInvalid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/topic.partition.info/client",
        "${app}/topic.partition.info/server"})
    public void shouldReceiveTopicPartitionInfo() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/topic.partition.info.changed/client",
        "${app}/topic.partition.info.changed/server"})
    public void shouldReceiveTopicPartitionInfoChanged() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_FIRST_META");
        k3po.notifyBarrier("SEND_SECOND_META");
        k3po.finish();
    }
}
