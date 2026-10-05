/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.http.internal.streams.eventstream.server;

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

public class EventStreamIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/event.stream")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/http/streams/application/event.stream");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/http/config/v1.1")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.event/client",
        "${app}/response.event/server" })
    public void shouldReceiveResponseEvent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.data.non.empty/client",
        "${app}/response.data.non.empty/server" })
    public void shouldReceiveResponseDataNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.data.empty/client",
        "${app}/response.data.empty/server" })
    public void shouldReceiveResponseDataEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.data.multi.line/client",
        "${app}/response.data.multi.line/server" })
    public void shouldReceiveResponseDataMultiLine() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.data.multiple/client",
        "${app}/response.data.multiple/server" })
    public void shouldReceiveResponseDataMultiple() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.data.fragmented.10k/client",
        "${app}/response.data.fragmented.10k/server" })
    public void shouldReceiveResponseDataFragmented10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.data.fragmented.100k/client",
        "${app}/response.data.fragmented.100k/server" })
    public void shouldReceiveResponseDataFragmented100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.id.non.empty/client",
        "${app}/response.id.non.empty/server" })
    public void shouldReceiveResponseIdNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.id.empty/client",
        "${app}/response.id.empty/server" })
    public void shouldReceiveResponseIdEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.type.non.empty/client",
        "${app}/response.type.non.empty/server" })
    public void shouldReceiveResponseTypeNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.type.empty/client",
        "${app}/response.type.empty/server" })
    public void shouldReceiveResponseTypeEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.type.fragmented/client",
        "${app}/response.type.fragmented/server" })
    public void shouldReceiveResponseTypeFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/response.raw/client",
        "${app}/response.raw/server" })
    public void shouldReceiveResponseRaw() throws Exception
    {
        k3po.finish();
    }
}
