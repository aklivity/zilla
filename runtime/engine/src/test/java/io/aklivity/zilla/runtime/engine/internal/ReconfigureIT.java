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
package io.aklivity.zilla.runtime.engine.internal;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;
import org.openjdk.jmh.util.FileUtils;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;


public class ReconfigureIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/engine/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/engine/streams/application");

    //Trivial test binding: test type, proxy kind (conifg file)
    // Test: change the app0 exit to app1, and net0 to net1
    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/runtime/engine/internal")
        .external("app0")
        .external("app1")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @BeforeClass
    public static void copyConfigToCorrectLocation() throws Exception
    {
        String source = new File(ReconfigureIT.class.getClassLoader()
            .getResource("io/aklivity/zilla/runtime/engine/internal/zilla.reconfigure.original.json")
            .toURI()).getAbsolutePath();
        String target = new File("target/test-classes/" +
            "io/aklivity/zilla/runtime/engine/internal/zilla.reconfigure.json").getAbsolutePath();
        FileUtils.copy(source, target);
    }

    @Test
    @Configuration("zilla.reconfigure.json")
    @Specification({
        "${app}/client.sent.data.reconfigure.modify/server",
        "${net}/client.sent.data.reconfigure.modify/client"
    })
    public void shouldConnectToZillaOnNewPortWhenConfigModified() throws Exception
    {
        k3po.start();

        Thread.sleep(2000);
        // 2 configs in resources, not dynamic modification,
        // listen to created, deleted. Create 3 test scenarios, 0-1, 1-1, 1-0
        // replace app0 to app1
        String resourceName = "io/aklivity/zilla/runtime/engine/internal/zilla.reconfigure.json";
        InputStream input = this.getClass().getClassLoader().getResourceAsStream(resourceName);
        String configText = new String(input.readAllBytes(), UTF_8);
        String newConfigText = configText.replace("net0", "net1").replace("app0", "app1");


        FileOutputStream outputStream = new FileOutputStream(new File("target/test-classes/" +
            "io/aklivity/zilla/runtime/engine/internal/zilla.reconfigure.json").getAbsolutePath());
        outputStream.write(newConfigText.getBytes());
        outputStream.close();
        Thread.sleep(2000);

        k3po.notifyBarrier("CONFIG_CHANGED");

        k3po.finish();
    }

    @Test
    @Configuration("zilla.reconfigure.json")
    @Specification({
        "${app}/client.sent.data.reconfigure.delete_create/server",
        "${net}/client.sent.data.reconfigure.delete_create/client"
    })
    public void shouldConnectToZillaOnNewPortWhenConfigDeletedThanCreated() throws Exception
    {
        k3po.start();

        // 2 configs in resources, not dynamic modification,
        // listen to created, deleted. Create 3 test scenarios, 0-1, 1-1, 1-0
        // replace app0 to app1
        String resourceName = "io.aklivity.zilla.runtime.engine.internal/zilla.reconfigure.json";
        URL configUrl = this.getClass().getClassLoader().getResource(resourceName);
        File config = new File(configUrl.toURI());
        config.delete();
        Thread.sleep(2000);

        k3po.notifyBarrier("CONFIG_DELETED");


        copyConfigToCorrectLocation();
        Thread.sleep(2000);
        k3po.notifyBarrier("CONFIG_CREATED");

        k3po.finish();
    }
}
