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
package io.aklivity.zilla.runtime.binding.sse.internal.streams.server;

import static io.aklivity.zilla.runtime.binding.sse.internal.stream.SseServerFactory.MAXIMUM_HEADER_SIZE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.ScriptProperty;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class TypeIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/sse/streams/network/type")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/sse/streams/application/type");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/binding/sse/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.when.yaml")
    @Specification({
        "${net}/empty/request",
        "${app}/empty/server" })
    public void shouldReceiveEmptyType() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.yaml")
    @Specification({
        "${net}/non.empty/request",
        "${app}/non.empty/server" })
    public void shouldReceiveNonEmptyType() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.yaml")
    @Specification({
        "${net}/non.empty.trailing/request",
        "${app}/non.empty.trailing/server" })
    public void shouldReceiveNonEmptyTypeTrailing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.yaml")
    @Specification({
        "${net}/non.empty.interleaved/request",
        "${app}/non.empty.interleaved/server" })
    public void shouldReceiveNonEmptyTypeInterleaved() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.yaml")
    @Specification({
        "${net}/fragmented/request",
        "${app}/fragmented/server" })
    @ScriptProperty("padding " + MAXIMUM_HEADER_SIZE)
    public void shouldReceiveNonEmptyTypeWithFragmentedMessage() throws Exception
    {
        k3po.finish();
    }
}
