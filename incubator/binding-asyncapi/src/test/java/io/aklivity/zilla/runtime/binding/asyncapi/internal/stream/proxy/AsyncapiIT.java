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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.stream.proxy;

import static io.aklivity.zilla.runtime.binding.asyncapi.internal.AsyncapiConfigurationTest.ASYNCAPI_TARGET_ROUTE_ID_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.INSTANCE_ID_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.LIFETIME_ID_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.SESSION_ID_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.TIME_NAME;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfigurationTest.WILL_ID_NAME;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
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
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class AsyncapiIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("asyncapi", "io/aklivity/zilla/specs/binding/asyncapi/streams/asyncapi");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/binding/asyncapi/config")
        .configure(SESSION_ID_NAME,
            "io.aklivity.zilla.runtime.binding.asyncapi.internal.stream.proxy.AsyncapiIT::supplySessionId")
        .configure(INSTANCE_ID_NAME,
            "io.aklivity.zilla.runtime.binding.asyncapi.internal.stream.proxy.AsyncapiIT::supplyInstanceId")
        .configure(TIME_NAME,
            "io.aklivity.zilla.runtime.binding.asyncapi.internal.stream.proxy.AsyncapiIT::supplyTime")
        .configure(LIFETIME_ID_NAME,
            "io.aklivity.zilla.runtime.binding.asyncapi.internal.stream.proxy.AsyncapiIT::supplyLifetimeId")
        .configure(WILL_ID_NAME,
            "io.aklivity.zilla.runtime.binding.asyncapi.internal.stream.proxy.AsyncapiIT::supplyWillId")
        .external("asyncapi_kafka0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.mqtt.kafka.yaml")
    @Specification({
        "${asyncapi}/proxy.mqtt.publish.and.subscribe/client",
        "${asyncapi}/proxy.kafka.publish.and.subscribe/server"
    })
    public void shouldPublishAndSubscribe() throws Exception
    {
        k3po.finish();
    }

    public static String supplySessionId()
    {
        return "sender-1";
    }

    public static String supplyWillId()
    {
        return "d252a6bd-abb5-446a-b0f7-d0a3d8c012e2";
    }

    public static String supplyLifetimeId()
    {
        return "1e6a1eb5-810a-459d-a12c-a6fa08f228d1";
    }

    public static String supplyInstanceId()
    {
        return "zilla-1";
    }

    public static long supplyTime()
    {
        return 1000L;
    }
}
