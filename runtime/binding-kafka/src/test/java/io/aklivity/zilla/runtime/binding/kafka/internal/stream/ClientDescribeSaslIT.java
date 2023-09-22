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

import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_DESCRIBE_MAX_AGE_MILLIS;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfigurationTest.KAFKA_CLIENT_SASL_SCRAM_NONCE_NAME;
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

public class ClientDescribeSaslIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/kafka/streams/network/describe.configs.v0.sasl.handshake.v1")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/kafka/streams/application/describe");

    private final TestRule timeout = new DisableOnDebug(new Timeout(15, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(KAFKA_CLIENT_DESCRIBE_MAX_AGE_MILLIS, 0)
        .configurationRoot("io/aklivity/zilla/specs/binding/kafka/config")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.options.sasl.plain.yaml")
    @Specification({
        "${app}/topic.config.info/client",
        "${net}/topic.config.info.sasl.plain/server"})
    public void shouldReceiveTopicConfigInfoWithSaslPlain() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.options.sasl.scram.yaml")
    @Specification({
        "${app}/topic.config.info/client",
        "${net}/topic.config.info.sasl.scram/server"})
    @Configure(name = KAFKA_CLIENT_SASL_SCRAM_NONCE_NAME,
            value = "io.aklivity.zilla.runtime.binding.kafka.internal.stream.ClientDescribeSaslIT::supplyNonce")
    public void shouldReceiveTopicConfigInfoWithSaslScram() throws Exception
    {
        k3po.finish();
    }

    public static String supplyNonce()
    {
        return "fyko+d2lbbFgONRv9qkxdawL";
    }
}
