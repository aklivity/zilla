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
package io.aklivity.zilla.specs.binding.http.filesystem.streams;

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
        .addScriptRoot("filesystem", "io/aklivity/zilla/specs/binding/http/filesystem/streams/filesystem");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${filesystem}/client.read.file/client",
        "${filesystem}/client.read.file/server"})
    public void shouldReceiveClientReadFile() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${filesystem}/client.read.file.map.modified/client",
        "${filesystem}/client.read.file.map.modified/server"})
    public void shouldReceiveClientReadFileMapModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${filesystem}/client.read.file.map.not.modified/client",
        "${filesystem}/client.read.file.map.not.modified/server"})
    public void shouldReceiveClientReadFileMapNotModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${filesystem}/client.read.file.info/client",
        "${filesystem}/client.read.file.info/server"})
    public void shouldReceiveClientReadFileInfo() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${filesystem}/server.sent.abort/client",
        "${filesystem}/server.sent.abort/server"})
    public void shouldReceiveServerSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${filesystem}/server.sent.reset/client",
        "${filesystem}/server.sent.reset/server"})
    public void shouldReceiveServerSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${filesystem}/server.sent.flush/client",
        "${filesystem}/server.sent.flush/server"})
    public void shouldReceiveServerSentFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${filesystem}/client.sent.reset/client",
        "${filesystem}/client.sent.reset/server"})
    public void shouldReceiveClientSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${filesystem}/client.sent.abort/client",
        "${filesystem}/client.sent.abort/server"})
    public void shouldReceiveClientSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${filesystem}/client.create.file/client",
        "${filesystem}/client.create.file/server"})
    public void shouldReceiveClientCreateFile() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${filesystem}/client.create.existing.file.failed/client",
        "${filesystem}/client.create.existing.file.failed/server"})
    public void shouldRejectClientCreateExistingFileFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${filesystem}/client.write.file/client",
        "${filesystem}/client.write.file/server"})
    public void shouldReceiveClientWriteFile() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${filesystem}/client.write.file.failed/client",
        "${filesystem}/client.write.file.failed/server"})
    public void shouldRejectClientWriteFileFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${filesystem}/client.delete.file/client",
        "${filesystem}/client.delete.file/server"})
    public void shouldReceiveClientDeleteFile() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${filesystem}/client.delete.non.existent.file/client",
        "${filesystem}/client.delete.non.existent.file/server"})
    public void shouldRejectClientDeleteNonExistentFile() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${filesystem}/client.read.directory/client",
        "${filesystem}/client.read.directory/server"})
    public void shouldReceiveClientReadDirectory() throws Exception
    {
        k3po.finish();
    }
}
