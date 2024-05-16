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
package io.aklivity.zilla.runtime.command.dump.internal;

import static io.aklivity.zilla.runtime.binding.amqp.internal.AmqpConfiguration.AMQP_CLOSE_EXCHANGE_TIMEOUT;
import static io.aklivity.zilla.runtime.binding.amqp.internal.AmqpConfiguration.AMQP_CONTAINER_ID;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_TIMESTAMPS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.command.dump.internal.test.DumpRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class AmqpServerIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/command/dump/binding/amqp/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/command/dump/binding/amqp/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(AMQP_CONTAINER_ID, "server")
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configure(AMQP_CLOSE_EXCHANGE_TIMEOUT, 500)
        .configure(ENGINE_TIMESTAMPS, false)
        .configurationRoot("io/aklivity/zilla/specs/binding/amqp/config")
        .external("app0")
        .clean();

    private final DumpRule dump = new DumpRule();

    @Rule
    public final TestRule chain = outerRule(dump).around(engine).around(k3po).around(timeout);

    @Ignore
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/attach.as.receiver.only/client",
        "${app}/connect.as.receiver.only/server" })
    public void shouldConnectAsReceiverOnly() throws Exception
    {
        k3po.finish();
    }
}
