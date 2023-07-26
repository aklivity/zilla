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

import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_GROUP_INSTANCE_ID;
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

public class ClientGroupSaslIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/kafka/streams/network/group.sasl")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/kafka/streams/application/group");

    private final TestRule timeout = new DisableOnDebug(new Timeout(15, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(KAFKA_CLIENT_GROUP_INSTANCE_ID, "client1")
        .configurationRoot("io/aklivity/zilla/specs/binding/kafka/config")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);


    @Test
    @Configuration("client.options.sasl.plain.yaml")
    @Specification({
        "${app}/leader/client",
        "${net}/leader/server"})
    public void shouldBecomeLeader() throws Exception
    {
        k3po.finish();
    }
}
