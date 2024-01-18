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
package io.aklivity.zilla.runtime.engine.internal;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_CONFIG_URL_NAME;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
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


public class ReconfigureFileIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/engine/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/engine/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/runtime/engine/internal")
        .external("app0")
        .external("app1")
        .external("app2")
        .exceptions(m -> m.endsWith("ParseFailed"))
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    private static final String PACKAGE_NAME = ReconfigureFileIT.class.getPackageName();
    private static final Path CONFIG_DIR = Paths.get("target/test-classes", PACKAGE_NAME.replace(".", "/"));

    @BeforeClass
    public static void createSymlinks() throws IOException
    {
        Path simpleLink =  CONFIG_DIR.resolve("zilla.reconfigure.modify.symlink.json");
        Path simpleTarget = CONFIG_DIR.resolve("symlink/zilla.reconfigure.modify.symlink.source.json").toAbsolutePath();

        // zilla.reconfigure.modify.complex.chain.json -> data/configs/zilla.reconfigure.modify.complex.chain.json
        // data -> symlink
        // configs -> realconfigs
        // Real config is at: symlink/realconfigs/zilla.reconfigure.modify.complex.chain.json

        Path link1 =  CONFIG_DIR.resolve("zilla.reconfigure.modify.complex.chain.json");
        Path target1 = Paths.get("data/configs/zilla.reconfigure.modify.complex.chain.json");
        Path link2 = CONFIG_DIR.resolve("data");
        Path target2 = Paths.get("symlink");
        Path link3 =  CONFIG_DIR.resolve("symlink/configs");
        Path target3 = Paths.get("realconfigs");

        Files.deleteIfExists(simpleLink);
        Files.deleteIfExists(link1);
        Files.deleteIfExists(link2);
        Files.deleteIfExists(link3);

        Files.createSymbolicLink(simpleLink, simpleTarget);
        Files.createSymbolicLink(link1, target1);
        Files.createSymbolicLink(link2, target2);
        Files.createSymbolicLink(link3, target3);
    }

    @Before
    public void setupRegisterLatch() throws Exception
    {
        EngineTest.TestEngineExt.registerLatch.await();
        EngineTest.TestEngineExt.registerLatch = new CountDownLatch(1);
    }

    @Ignore
    @Test
    @Configuration("zilla.reconfigure.modify.json")
    @Specification({
        "${app}/reconfigure.modify.via.file/server",
        "${net}/reconfigure.modify.via.file/client"
    })
    public void shouldReconfigureWhenModified() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("CONNECTED");

        Path source = Paths.get(ReconfigureFileIT.class.getResource("zilla.reconfigure.after.json").toURI());
        Path target = CONFIG_DIR.resolve("zilla.reconfigure.modify.json");

        Files.move(source, target, ATOMIC_MOVE);

        EngineTest.TestEngineExt.registerLatch.await();
        k3po.notifyBarrier("CONFIG_CHANGED");

        k3po.finish();
    }

    @Ignore
    @Test
    @Configuration("zilla.reconfigure.modify.symlink.json")
    @Specification({
        "${app}/reconfigure.modify.via.file/server",
        "${net}/reconfigure.modify.via.file/client"
    })
    public void shouldReconfigureWhenModifiedUsingSymlink() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("CONNECTED");

        Path source = Paths.get(ReconfigureFileIT.class.getResource("zilla.reconfigure.symlink.after.json").toURI());
        Path target = CONFIG_DIR.resolve("symlink/zilla.reconfigure.modify.symlink.source.json");

        Files.move(source, target, ATOMIC_MOVE);

        EngineTest.TestEngineExt.registerLatch.await();
        k3po.notifyBarrier("CONFIG_CHANGED");

        k3po.finish();
    }

    @Ignore("Fails on JDK 13")
    @Test
    @Configuration("zilla.reconfigure.modify.complex.chain.json")
    @Specification({
        "${app}/reconfigure.modify.complex.chain.via.file/server",
        "${net}/reconfigure.modify.complex.chain.via.file/client"
    })
    public void shouldReconfigureWhenModifiedUsingComplexSymlinkChain() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("CONNECTED");

        // config symlink targets realconfigs.after.modify
        Path target = Paths.get("realconfigs.after.modify");

        Path link = CONFIG_DIR.resolve("symlink/configs");
        File linkFile = new File(String.valueOf(link));
        linkFile.delete();
        Files.createSymbolicLink(link, target);

        EngineTest.TestEngineExt.registerLatch.await();
        k3po.notifyBarrier("CONFIG_CHANGED1");
        k3po.awaitBarrier("CONNECTED2");

        // zilla.reconfigure.modify.complex.chain.json symlink targets zilla.reconfigure.modify.complex.chain.after.json
        EngineTest.TestEngineExt.registerLatch = new CountDownLatch(1);
        link = CONFIG_DIR.resolve("zilla.reconfigure.modify.complex.chain.json");
        linkFile = new File(String.valueOf(link));
        linkFile.delete();
        target = Paths.get("zilla.reconfigure.modify.complex.chain.after.json");
        Files.createSymbolicLink(link, target);

        EngineTest.TestEngineExt.registerLatch.await();
        k3po.notifyBarrier("CONFIG_CHANGED2");

        k3po.finish();
    }

    @Ignore
    @Test
    @Configure(
        name = ENGINE_CONFIG_URL_NAME,
        value = "file:target/test-classes/io/aklivity/zilla/runtime/engine/internal/zilla.reconfigure.missing.json")
    @Specification({
        "${app}/reconfigure.create.via.file/server",
        "${net}/reconfigure.create.via.file/client"
    })
    public void shouldReconfigureWhenCreated() throws Exception
    {
        k3po.start();

        Path source = Paths.get(ReconfigureFileIT.class.getResource("zilla.reconfigure.original.json").toURI());
        Path target = CONFIG_DIR.resolve("zilla.reconfigure.missing.json");

        Files.move(source, target, ATOMIC_MOVE);
        EngineTest.TestEngineExt.registerLatch.await();
        k3po.notifyBarrier("CONFIG_CREATED");

        k3po.finish();
    }

    @Ignore
    @Test
    @Configuration("zilla.reconfigure.delete.json")
    @Specification({
        "${app}/reconfigure.delete.via.file/server",
        "${net}/reconfigure.delete.via.file/client"
    })
    public void shouldReconfigureWhenDeleted() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("CONNECTED");

        CONFIG_DIR.resolve("zilla.reconfigure.delete.json").toFile().delete();

        EngineTest.TestEngineExt.registerLatch.await();
        k3po.notifyBarrier("CONFIG_DELETED");

        k3po.finish();
    }

    @Test
    @Configuration("zilla.reconfigure.not.modify.parse.failed.json")
    @Specification({
        "${app}/reconfigure.not.modify.parse.failed.via.file/server",
        "${net}/reconfigure.not.modify.parse.failed.via.file/client"
    })
    public void shouldNotReconfigureWhenModifiedButParseFailed() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("CONNECTED");

        Path source = Paths.get(ReconfigureFileIT.class.getResource("zilla.reconfigure.not.modify.parse.failed.after.json")
            .toURI());
        Path target = CONFIG_DIR.resolve("zilla.reconfigure.not.modify.parse.failed.json");

        Files.move(source, target, ATOMIC_MOVE);

        k3po.notifyBarrier("CONFIG_CHANGED");
        k3po.finish();
    }
}
