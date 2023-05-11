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
package io.aklivity.zilla.specs.binding.http.filesystem.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

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
}
