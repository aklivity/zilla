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
//public class KafkaClientInitProducerIdIT
//{
//    private final K3poRule k3po = new K3poRule()
//        .addScriptRoot("net", "io/aklivity/zilla/specs/command/dump/binding/kafka/streams/network/init.producer.id.v4")
//        .addScriptRoot("app", "io/aklivity/zilla/specs/command/dump/binding/kafka/streams/application/init.producer.id");
//
//    private final TestRule timeout = new DisableOnDebug(new Timeout(15, SECONDS));
//
//    private final EngineRule engine = new EngineRule()
//        .directory("target/zilla-itests")
//        .countersBufferCapacity(8192)
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
//
//    @Test
//    @Configuration("client.yaml")
//    @Specification({
//        "${app}/produce.new.id/client",
//        "${net}/produce.new.id/server"})
//    public void shouldGenerateNewProducerId() throws Exception
//    {
//        k3po.finish();
//    }
//}
