/*
 * Copyright 2021-2023 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
//package io.aklivity.zilla.runtime.command.dump.internal.old;
//
//import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_META_MAX_AGE_MILLIS;
//import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_PRODUCE_MAX_BYTES;
//import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
//import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_TIMESTAMPS;
//import static java.util.concurrent.TimeUnit.SECONDS;
//import static org.junit.rules.RuleChain.outerRule;
//
//import org.junit.Ignore;
//import org.junit.Rule;
//import org.junit.Test;
//import org.junit.rules.DisableOnDebug;
//import org.junit.rules.TestRule;
//import org.junit.rules.Timeout;
//import org.kaazing.k3po.junit.annotation.Specification;
//import org.kaazing.k3po.junit.rules.K3poRule;
//
//import io.aklivity.zilla.runtime.command.dump.internal.test.DumpRule;
//import io.aklivity.zilla.runtime.engine.test.EngineRule;
//import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
//
//public class KafkaClientMergedIT
//{
//    private final K3poRule k3po = new K3poRule()
//        .addScriptRoot("net", "io/aklivity/zilla/specs/command/dump/binding/kafka/streams/network/unmerged.p3.f5.d0.m5")
//        .addScriptRoot("app", "io/aklivity/zilla/specs/command/dump/binding/kafka/streams/application/merged");
//
//    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));
//
//    private final EngineRule engine = new EngineRule()
//        .directory("target/zilla-itests")
//        .countersBufferCapacity(8192)
//        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
//        .configure(KAFKA_CLIENT_META_MAX_AGE_MILLIS, 1000)
//        .configure(KAFKA_CLIENT_PRODUCE_MAX_BYTES, 528)
//        .configure(ENGINE_TIMESTAMPS, false)
//        .configurationRoot("io/aklivity/zilla/specs/command/dump/binding/kafka/config")
//        .external("net0")
//        .clean();
//
//    private final DumpRule dump = new DumpRule();
//
//    @Rule
//    public final TestRule chain = outerRule(dump).around(engine).around(k3po).around(timeout);
//
//    @Test
//    @Configuration("client.options.merged.yaml")
//    @Specification({
//        "${app}/merged.fetch.filter.sync/client",
//        "${net}/unmerged.fetch.filter.sync/server"})
//    public void shouldFetchMergedFilterSync() throws Exception
//    {
//        k3po.finish();
//    }
//}
