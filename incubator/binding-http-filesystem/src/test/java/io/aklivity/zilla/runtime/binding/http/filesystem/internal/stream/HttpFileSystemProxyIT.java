/*
 * Copyright 2021-2022 Aklivity Inc
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
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

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
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/http/filesystem/config")
        .external("filesystem0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.with.path.json")
    @Specification({
        "${http}/client.read.file/client",
        "${filesystem}/client.read.file/server"})
    public void shouldReceiveClientReadFile() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.json")
    @Specification({
        "${http}/client.read.file.info/client",
        "${filesystem}/client.read.file.info/server"})
    public void shouldReceiveClientReadFileInfo() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.dynamic.json")
    @Specification({
        "${http}/client.read.file/client",
        "${filesystem}/client.read.file/server"})
    public void shouldReceiveClientReadFileWithDyanamicPath() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.dynamic.json")
    @Specification({
        "${http}/client.read.file.info/client",
        "${filesystem}/client.read.file.info/server"})
    public void shouldReceiveClientReadFileInfoWithDyanamicPath() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.json")
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
    @Configuration("proxy.with.path.json")
    @Specification({
        "${http}/client.sent.message/client",
        "${filesystem}/client.sent.abort/server"})
    public void shouldRejectClientSentMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.json")
    @Specification({
        "${http}/server.sent.abort/client",
        "${filesystem}/server.sent.abort/server"})
    public void shouldReceiveServerSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.json")
    @Specification({
        "${http}/server.sent.reset/client",
        "${filesystem}/server.sent.reset/server"})
    public void shouldReceiveServerSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.json")
    @Specification({
        "${http}/server.sent.flush/client",
        "${filesystem}/server.sent.flush/server"})
    public void shouldReceiveServerSentFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.json")
    @Specification({
        "${http}/client.sent.reset/client",
        "${filesystem}/client.sent.reset/server"})
    public void shouldReceiveClientSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.with.path.json")
    @Specification({
        "${http}/client.sent.abort/client",
        "${filesystem}/client.sent.abort/server"})
    public void shouldReceiveClientSentAbort() throws Exception
    {
        k3po.finish();
    }
}
