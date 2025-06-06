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
package io.aklivity.zilla.runtime.binding.http.filesystem.internal.stream;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
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

public class HttpFileSystemProxyIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("http", "io/aklivity/zilla/specs/binding/http/filesystem/streams/http")
        .addScriptRoot("filesystem", "io/aklivity/zilla/specs/binding/http/filesystem/streams/filesystem");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/http/filesystem/config")
        .external("filesystem0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.with.path.yaml")
    @Specification({
        "${http}/client.read.file/client",
        "${filesystem}/client.read.file/server"})
    public void shouldReceiveClientReadFile() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.yaml")
    @Specification({
        "${http}/client.read.file.map.modified/client",
        "${filesystem}/client.read.file.map.modified/server"})
    public void shouldReceiveClientReadFileMapModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.yaml")
    @Specification({
        "${http}/client.read.file.map.not.modified/client",
        "${filesystem}/client.read.file.map.not.modified/server"})
    public void shouldReceiveClientReadFileMapNotModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.yaml")
    @Specification({
        "${http}/client.read.file.with.query/client",
        "${filesystem}/client.read.file/server"})
    public void shouldReceiveClientReadFileWithQuery() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.yaml")
    @Specification({
        "${http}/client.read.file.info/client",
        "${filesystem}/client.read.file.info/server"})
    public void shouldReceiveClientReadFileInfo() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.dynamic.yaml")
    @Specification({
        "${http}/client.read.file/client",
        "${filesystem}/client.read.file/server"})
    public void shouldReceiveClientReadFileWithDyanamicPath() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.dynamic.yaml")
    @Specification({
        "${http}/client.read.file.info/client",
        "${filesystem}/client.read.file.info/server"})
    public void shouldReceiveClientReadFileInfoWithDyanamicPath() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.yaml")
    @Specification({
        "${http}/client.rejected/client"})
    public void shouldRejectClientWithNoBinding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/client.rejected/client"})
    public void shouldRejectClientWithNoRoute() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.yaml")
    @Specification({
        "${http}/client.sent.message/client",
        "${filesystem}/client.sent.abort/server"})
    public void shouldRejectClientSentMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.yaml")
    @Specification({
        "${http}/server.sent.abort/client",
        "${filesystem}/server.sent.abort/server"})
    public void shouldReceiveServerSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.yaml")
    @Specification({
        "${http}/server.sent.reset/client",
        "${filesystem}/server.sent.reset/server"})
    public void shouldReceiveServerSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.yaml")
    @Specification({
        "${http}/server.sent.flush/client",
        "${filesystem}/server.sent.flush/server"})
    public void shouldReceiveServerSentFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.yaml")
    @Specification({
        "${http}/client.sent.reset/client",
        "${filesystem}/client.sent.reset/server"})
    public void shouldReceiveClientSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.yaml")
    @Specification({
        "${http}/client.sent.abort/client",
        "${filesystem}/client.sent.abort/server"})
    public void shouldReceiveClientSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.yaml")
    @Specification({
        "${http}/client.create.file/client",
        "${filesystem}/client.create.file/server"})
    public void shouldReceiveClientCreateFile() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.yaml")
    @Specification({
        "${http}/client.create.existing.file.failed/client",
        "${filesystem}/client.create.existing.file.failed/server"})
    public void shouldRejectClientCreateExistingFileFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.yaml")
    @Specification({
        "${http}/client.write.file/client",
        "${filesystem}/client.write.file/server"})
    public void shouldReceiveClientWriteFile() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.yaml")
    @Specification({
        "${http}/client.write.file.failed/client",
        "${filesystem}/client.write.file.failed/server"})
    public void shouldRejectClientWriteFileFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.yaml")
    @Specification({
        "${http}/client.delete.file/client",
        "${filesystem}/client.delete.file/server"})
    public void shouldReceiveClientDeleteFile() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.yaml")
    @Specification({
        "${http}/client.delete.non.existent.file/client",
        "${filesystem}/client.delete.non.existent.file/server"})
    public void shouldRejectClientDeleteNonExistentFile() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.directory.dynamic.yaml")
    @Specification({
        "${http}/client.read.directory/client",
        "${filesystem}/client.read.directory/server"})
    public void shouldReceiveClientReadDirectory() throws Exception
    {
        k3po.finish();
    }
}
