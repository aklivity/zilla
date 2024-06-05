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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.stream.server;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
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

public class AsyncapiIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("mqtt", "io/aklivity/zilla/specs/binding/asyncapi/streams/mqtt")
        .addScriptRoot("http", "io/aklivity/zilla/specs/binding/asyncapi/streams/http")
        .addScriptRoot("sse", "io/aklivity/zilla/specs/binding/asyncapi/streams/sse")
        .addScriptRoot("asyncapi", "io/aklivity/zilla/specs/binding/asyncapi/streams/asyncapi");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/binding/asyncapi/config")
        .external("asyncapi0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.mqtt.yaml")
    @Specification({
        "${mqtt}/publish.and.subscribe/client",
        "${asyncapi}/mqtt/publish.and.subscribe/server"
    })
    public void shouldPublishAndSubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.http.yaml")
    @Specification({
        "${http}/create.pet/client",
        "${asyncapi}/http/create.pet/server"
    })
    public void shouldCreatePet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.sse.yaml")
    @Specification({
        "${sse}/data.multiple/client",
        "${asyncapi}/sse/data.multiple/server"
    })
    public void shouldReceiveMultipleData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.multi.protocol.yaml")
    @Specification({
        "${mqtt}/publish.and.subscribe/client",
        "${asyncapi}/mqtt/publish.and.subscribe/server"
    })
    public void shouldPublishAndSubscribeMultipleSpec() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.multi.protocol.yaml")
    @Specification({
        "${http}/create.pet/client",
        "${asyncapi}/http/create.pet/server"
    })
    public void shouldCreatePetMultipleSpec() throws Exception
    {
        k3po.finish();
    }
}
