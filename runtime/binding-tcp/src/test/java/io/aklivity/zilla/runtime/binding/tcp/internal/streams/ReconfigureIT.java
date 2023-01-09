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
package io.aklivity.zilla.runtime.binding.tcp.internal.streams;

import static io.aklivity.zilla.runtime.binding.tcp.internal.TcpConfiguration.TCP_MAX_CONNECTIONS;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class ReconfigureIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/tcp/streams/network/rfc793")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/tcp/streams/application/rfc793");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(TCP_MAX_CONNECTIONS, 3)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io.aklivity.zilla.runtime.binding.tcp.internal.streams")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("zilla.reconfigure.json")
    @Specification({
        "${app}/client.sent.data.reconfigure/server",
        "${net}/client.sent.data.reconfigure/client"
    })
    public void shouldReceiveClientSentDataOnNewPortAfterReconfigure() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("FIRST_READ");

        String resourceName = "io.aklivity.zilla.runtime.binding.tcp.internal.streams/zilla.reconfigure.json";
        InputStream input = this.getClass().getClassLoader().getResourceAsStream(resourceName);
        String configText = new String(input.readAllBytes(), UTF_8);
        String newConfigText = configText.replace("8080", "8088");


        FileOutputStream outputStream = new FileOutputStream(new File("target/test-classes/" +
            "io.aklivity.zilla.runtime.binding.tcp.internal.streams/zilla.reconfigure.json").getAbsolutePath());
        outputStream.write(newConfigText.getBytes());
        outputStream.close();
        Thread.sleep(500);

        k3po.notifyBarrier("CONFIG_CHANGED");

        k3po.finish();
    }
}
