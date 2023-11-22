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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.ScriptProperty;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class CacheConsumerIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/kafka/streams/application/group")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/kafka/streams/application/consumer");

    private final TestRule timeout = new DisableOnDebug(new Timeout(15, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/kafka/config")
        .external("app1")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("cache.when.topic.yaml")
    @Specification({
        "${app}/partition.assignment/client",
        "${net}/partition.assignment/server"
    })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldAssignPartition() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/reassign.new.topic/client",
        "${net}/reassign.new.topic/server"
    })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldReassignOnUpdatedTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.when.topic.yaml")
    @Specification({
        "${app}/acknowledge.message.offset/client",
        "${app}/commit.acknowledge.message.offset/server"})
    public void shouldCommitAcknowledgeMessageOffset() throws Exception
    {
        k3po.finish();
    }
}
