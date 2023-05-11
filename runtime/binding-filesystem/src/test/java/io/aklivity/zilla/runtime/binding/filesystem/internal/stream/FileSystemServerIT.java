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
package io.aklivity.zilla.runtime.binding.filesystem.internal.stream;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

public class FileSystemServerIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/filesystem/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/filesystem/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/read.file.extension/client",
    })
    public void shouldReadFileExtensionOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/read.file.extension.default/client",
    })
    public void shouldReadFileExtensionDefault() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/read.file.payload/client",
    })
    public void shouldReadFilePayloadOnly() throws Exception
    {
        k3po.finish();
    }

    @Ignore
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/read.file.payload.modified/client"
    })
    public void shouldReadFilePayloadModified() throws Exception
    {
        k3po.start();
        Thread.sleep(1000);

        k3po.awaitBarrier("CONNECTED");
        Path filesDirectory = Paths.get("target/files");
        Path source = filesDirectory.resolve("index_modify_after.html");
        Path target = filesDirectory.resolve("index_modify.html");

        Files.move(source, target, ATOMIC_MOVE);
        k3po.finish();
    }

    @Ignore
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/read.file.payload.modified.multi.client/client"
    })
    public void shouldReadFilePayloadModifiedMultiClient() throws Exception
    {
        Thread.sleep(1000);
        k3po.start();
        k3po.awaitBarrier("CONNECTED1");
        k3po.awaitBarrier("CONNECTED2");
        Path filesDirectory = Paths.get("target/files");
        Path source = filesDirectory.resolve("index_modify_multi_client_after.html");
        Path target = filesDirectory.resolve("index_modify_multi_client.html");

        Files.move(source, target, ATOMIC_MOVE);
        k3po.notifyBarrier("FILE_MODIFIED");

        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/read.file.payload.etag.not.matched/client"
    })
    public void shouldReadFilePayloadEtagNotMatched() throws Exception
    {
        k3po.finish();
    }

    @Ignore
    @Test
    @Configuration("server_symlinks.yaml")
    @Specification({
        "${app}/read.file.payload.modified.follow.symlinks/client"
    })
    public void shouldReadFilePayloadModifiedFollowSymlinks() throws Exception
    {
        Path filesDirectory = Paths.get("target/files").toAbsolutePath();
        Path link = filesDirectory.resolve("index_modify_symlink.html");
        Path targetData = Paths.get("data");
        Path linkData = filesDirectory.resolve("data");
        Path targetFile = Paths.get("symlink_before/index.html");
        Files.createSymbolicLink(link, targetData);
        Files.createSymbolicLink(linkData, targetFile);

        k3po.start();
        Thread.sleep(1000);

        k3po.awaitBarrier("CONNECTED");

        Path targetFileAfter = Paths.get("symlink_after/index.html");
        File linkFile = new File(String.valueOf(linkData));
        linkFile.delete();
        Files.createSymbolicLink(linkData, targetFileAfter);
        k3po.finish();
    }

    @Ignore("Github Actions")
    @Test
    @Configuration("server_symlinks.yaml")
    @Specification({
        "${app}/read.file.payload.modified.follow.symlink.changes/client"
    })
    public void shouldReadFilePayloadModifiedSymlinkChanges() throws Exception
    {
        Path filesDirectory = Paths.get("target/files").toAbsolutePath();
        Path link = filesDirectory.resolve("index_modify_symlink_changes.html");
        Path target1 = filesDirectory.resolve("index.html");
        Files.createSymbolicLink(link, target1);
        k3po.start();
        Thread.sleep(1000);
        k3po.awaitBarrier("CONNECTED");

        Path targetFileAfter = Paths.get("symlink/index.html");
        File linkFile = new File(String.valueOf(link));
        linkFile.delete();
        Files.createSymbolicLink(link, targetFileAfter);

        // Wait for registering the new watched directories.
        Thread.sleep(1000);

        Path source = filesDirectory.resolve("index_actual_after.html");
        Path target = filesDirectory.resolve("symlink/index.html");

        Files.move(source, target, ATOMIC_MOVE);
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/client.read.begin.not.modified/client"
    })
    public void shouldReadBeginNotModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/client.read.file.not.found/client"
    })
    public void shouldAbortFileNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/read.file.payload.extension/client"
    })
    public void shouldReadFilePayloadAndExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/client.write.abort/client",
    })
    public void shouldReceiveClientWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${app}/client.read.abort/client",
    })
    public void shouldReceiveClientReadAbort() throws Exception
    {
        k3po.finish();
    }
}
