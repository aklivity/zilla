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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.stream.client;

import static io.aklivity.zilla.runtime.binding.asyncapi.internal.AsyncapiConfigurationTest.ASYNCAPI_TARGET_ROUTE_ID_NAME;
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
        .addScriptRoot("mqtt", "io/aklivity/zilla/specs/binding/asyncapi/streams/mqtt")
        .addScriptRoot("http", "io/aklivity/zilla/specs/binding/asyncapi/streams/http")
        .addScriptRoot("sse", "io/aklivity/zilla/specs/binding/asyncapi/streams/sse")
        .addScriptRoot("kafka", "io/aklivity/zilla/specs/binding/asyncapi/streams/kafka")
        .addScriptRoot("asyncapi", "io/aklivity/zilla/specs/binding/asyncapi/streams/asyncapi");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/binding/asyncapi/config")
        .external("mqtt0")
        .external("http0")
        .external("kafka0")
        .external("sse0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.mqtt.yaml")
    @Specification({
        "${asyncapi}/mqtt/publish.and.subscribe/client",
        "${mqtt}/publish.and.subscribe/server"
    })
    @Configure(name = ASYNCAPI_TARGET_ROUTE_ID_NAME, value = "4294967298")
    @ScriptProperty("serverAddress \"zilla://streams/mqtt0\"")
    public void shouldPublishAndSubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.http.yaml")
    @Specification({
        "${asyncapi}/http/create.pet/client",
        "${http}/create.pet/server"
    })
    @Configure(name = ASYNCAPI_TARGET_ROUTE_ID_NAME, value = "4294967299")
    @ScriptProperty("serverAddress \"zilla://streams/http0\"")
    public void shouldCreatePet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.kafka.yaml")
    @Specification({
        "${asyncapi}/kafka/produce.message/client",
        "${kafka}/produce.message/server"
    })
    @Configure(name = ASYNCAPI_TARGET_ROUTE_ID_NAME, value = "4294967300")
    @ScriptProperty("serverAddress \"zilla://streams/kafka0\"")
    public void shouldProduceMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.sse.yaml")
    @Specification({
        "${asyncapi}/sse/data.multiple/client",
        "${sse}/data.multiple/server"
    })
    @Configure(name = ASYNCAPI_TARGET_ROUTE_ID_NAME, value = "4294967301")
    @ScriptProperty("serverAddress \"zilla://streams/sse0\"")
    public void shouldReceiveMultipleData() throws Exception
    {
        k3po.finish();
    }
}
