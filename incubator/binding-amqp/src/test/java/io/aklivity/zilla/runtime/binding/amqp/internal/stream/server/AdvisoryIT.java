/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.amqp.internal.stream.server;

import static io.aklivity.zilla.runtime.binding.amqp.internal.AmqpConfiguration.AMQP_CLOSE_EXCHANGE_TIMEOUT;
import static io.aklivity.zilla.runtime.binding.amqp.internal.AmqpConfiguration.AMQP_CONTAINER_ID;
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

public class AdvisoryIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/amqp/streams/network/link")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/amqp/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(2048)
        .responseBufferCapacity(2048)
        .counterValuesBufferCapacity(8192)
        .configure(AMQP_CONTAINER_ID, "server")
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configure(AMQP_CLOSE_EXCHANGE_TIMEOUT, 500)
        .configurationRoot("io/aklivity/zilla/specs/binding/amqp/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/server.sent.flush/client",
        "${app}/server.sent.flush/server" })
    public void shouldReceiveServerSentFlush() throws Exception
    {
        k3po.finish();
    }
}
