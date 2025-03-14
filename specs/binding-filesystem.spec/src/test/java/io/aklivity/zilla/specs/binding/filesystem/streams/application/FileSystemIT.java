/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.specs.binding.filesystem.streams.application;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class FileSystemIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/filesystem/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/read.file.extension/client",
        "${app}/read.file.extension/server",
    })
    public void shouldReadFileExtensionOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/read.file.extension.default/client",
        "${app}/read.file.extension.default/server",
    })
    public void shouldReadFileExtensionDefault() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/read.file.payload.empty/client",
        "${app}/read.file.payload.empty/server",
    })
    public void shouldReadFilePayloadEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/read.file.payload/client",
        "${app}/read.file.payload/server",
    })
    public void shouldReadFilePayloadOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/read.file.payload.modified/client",
        "${app}/read.file.payload.modified/server"
    })
    public void shouldReadFilePayloadModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/read.file.payload.modified.multi.client/client",
        "${app}/read.file.payload.modified.multi.client/server"
    })
    public void shouldReadFilePayloadModifiedMultiClient() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/read.file.payload.tag.not.matched/client",
        "${app}/read.file.payload.tag.not.matched/server"
    })
    public void shouldReadFilePayloadTagNotMatched() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/read.file.payload.modified.follow.symlinks/client",
        "${app}/read.file.payload.modified.follow.symlinks/server"
    })
    public void shouldReadFilePayloadModifiedFollowSymlinks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.read.begin.not.modified/client",
        "${app}/client.read.begin.not.modified/server"
    })
    public void shouldReadBeginNotModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.read.file.not.found/client",
        "${app}/client.read.file.not.found/server"
    })
    public void shouldAbortFileNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/read.file.payload.extension/client",
        "${app}/read.file.payload.extension/server",
    })
    public void shouldReadFilePayloadAndExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.write.abort/client",
        "${app}/client.write.abort/server",
    })
    public void shouldReceiveClientWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.read.abort/client",
        "${app}/client.read.abort/server",
    })
    public void shouldReceiveClientReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/create.file.payload/client",
        "${app}/create.file.payload/server",
    })
    public void shouldCreateFilePayloadOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/write.file.payload.modified/client",
        "${app}/write.file.payload.modified/server",
    })
    public void shouldWriteFilePayloadModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/write.file.payload.modified.abort/client",
        "${app}/write.file.payload.modified.abort/server",
    })
    public void shouldWriteFilePayloadModifiedAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/write.file.payload.interrupt/client",
        "${app}/write.file.payload.interrupt/server",
    })
    public void shouldWriteFilePayloadInterrupt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/delete.file.payload/client",
        "${app}/delete.file.payload/server",
    })
    public void shouldDeleteFilePayloadOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/delete.file.payload.failed/client",
        "${app}/delete.file.payload.failed/server",
    })
    public void shouldDeleteFilePayloadFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/read.directory/client",
        "${app}/read.directory/server",
    })
    public void shouldReadDirectory() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/read.directory.empty/client",
        "${app}/read.directory.empty/server",
    })
    public void shouldReadDirectoryEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/read.directory.failed/client",
        "${app}/read.directory.failed/server",
    })
    public void shouldRejectReadDirectory() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/create.directory/client",
        "${app}/create.directory/server",
    })
    public void shouldCreateDirectory() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/delete.directory/client",
        "${app}/delete.directory/server",
    })
    public void shouldDeleteDirectory() throws Exception
    {
        k3po.finish();
    }
}
