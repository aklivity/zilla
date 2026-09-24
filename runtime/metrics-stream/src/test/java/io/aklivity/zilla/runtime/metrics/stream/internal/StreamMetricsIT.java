/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.metrics.stream.internal;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.ScriptProperty;
import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class StreamMetricsIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/engine/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/metrics/stream/config")
        .external("app1")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.write.close.yaml")
    @Specification({
        "${app}/client.write.close/client",
        "${app}/client.write.close/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordClientWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.sent.write.abort.yaml")
    @Specification({
        "${app}/client.sent.write.abort/client",
        "${app}/client.sent.write.abort/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordClientSentWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.sent.read.abort.yaml")
    @Specification({
        "${app}/server.sent.read.abort/client",
        "${app}/server.sent.read.abort/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordServerSentReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.write.close.yaml")
    @Specification({
        "${app}/server.write.close/client",
        "${app}/server.write.close/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordServerWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.sent.write.abort.yaml")
    @Specification({
        "${app}/server.sent.write.abort/client",
        "${app}/server.sent.write.abort/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordServerSentWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.sent.read.abort.yaml")
    @Specification({
        "${app}/client.sent.read.abort/client",
        "${app}/client.sent.read.abort/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordClientSentReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.sent.data.yaml")
    @Specification({
        "${app}/client.sent.data/client",
        "${app}/client.sent.data/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordClientSentData() throws Exception
    {
        k3po.finish();
    }
}
