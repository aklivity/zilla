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

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class AsyncapiProxyIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("asyncapi", "io/aklivity/zilla/specs/binding/asyncapi/streams/asyncapi");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/binding/asyncapi/config")
        .external("asyncapi_kafka0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.mqtt.kafka.yaml")
    @Specification({
        "${asyncapi}/proxy.mqtt.publish/client",
        "${asyncapi}/proxy.kafka.publish/server"
    })
    public void shouldPublish() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.http.kafka.yaml")
    @Specification({
        "${asyncapi}/proxy.http.create.pet/client",
        "${asyncapi}/proxy.kafka.create.pet/server"
    })
    public void shouldCreatePet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.http.kafka.async.yaml")
    @Specification({
        "${asyncapi}/proxy.http.async.verify.customer/client",
        "${asyncapi}/proxy.kafka.async.verify.customer/server"
    })
    public void shouldVerifyCustomerAsync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.sse.kafka.yaml")
    @Specification({
        "${asyncapi}/proxy.sse.server.sent.messages/client",
        "${asyncapi}/proxy.kafka.server.sent.messages/server"
    })
    public void shouldReceiveServerSentMessages() throws Exception
    {
        k3po.finish();
    }
}
