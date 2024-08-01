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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.stream;

import static io.aklivity.zilla.runtime.binding.asyncapi.internal.AsyncapiConfigurationTest.ASYNCAPI_COMPOSITE_ROUTE_ID_NAME;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_VERBOSE;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_VERBOSE_COMPOSITES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.ScriptProperty;
import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class AsyncapiClientIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("asyncapi", "io/aklivity/zilla/specs/binding/asyncapi/streams/asyncapi")
        .addScriptRoot("composite", "io/aklivity/zilla/specs/binding/asyncapi/streams/composite");

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
        .configure(ENGINE_VERBOSE, false)
        .configure(ENGINE_VERBOSE_COMPOSITES, false)
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.mqtt.yaml")
    @Specification({
        "${asyncapi}/mqtt/publish.and.subscribe/client",
        "${composite}/mqtt/publish.and.subscribe/server"
    })
    @Configure(name = ASYNCAPI_COMPOSITE_ROUTE_ID_NAME, value = "0x0000000100000002")
    @ScriptProperty("serverAddress \"zilla://streams/mqtt0\"")
    public void shouldPublishAndSubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.http.yaml")
    @Specification({
        "${asyncapi}/http/create.pet/client",
        "${composite}/http/create.pet/server"
    })
    public void shouldCreatePet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.kafka.yaml")
    @Specification({
        "${asyncapi}/kafka/produce.message/client",
        "${composite}/kafka/produce.message/server"
    })
    @Configure(name = ASYNCAPI_COMPOSITE_ROUTE_ID_NAME, value = "0x0000000100000004")
    @ScriptProperty("serverAddress \"zilla://streams/kafka0\"")
    public void shouldProduceMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.sse.yaml")
    @Specification({
        "${asyncapi}/sse/data.multiple/client",
        "${composite}/sse/data.multiple/server"
    })
    public void shouldReceiveMultipleData() throws Exception
    {
        k3po.finish();
    }
}
