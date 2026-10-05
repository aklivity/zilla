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
package io.aklivity.zilla.runtime.binding.http.internal.streams.eventstream.client;

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
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.event/client",
        "${net}/response.event/server" })
    public void shouldReceiveResponseEvent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.data.non.empty/client",
        "${net}/response.data.non.empty/server" })
    public void shouldReceiveResponseDataNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.data.empty/client",
        "${net}/response.data.empty/server" })
    public void shouldReceiveResponseDataEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.data.multi.line/client",
        "${net}/response.data.multi.line/server" })
    public void shouldReceiveResponseDataMultiLine() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.data.multiple/client",
        "${net}/response.data.multiple/server" })
    public void shouldReceiveResponseDataMultiple() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.data.fragmented.10k/client",
        "${net}/response.data.fragmented.10k/server" })
    public void shouldReceiveResponseDataFragmented10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.data.fragmented.100k/client",
        "${net}/response.data.fragmented.100k/server" })
    public void shouldReceiveResponseDataFragmented100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.data.initial.whitespace/client",
        "${net}/response.data.initial.whitespace/server" })
    public void shouldReceiveResponseDataInitialWhitespace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.data.name.only/client",
        "${net}/response.data.name.only/server" })
    public void shouldReceiveResponseDataNameOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.data.invalid.utf8/client",
        "${net}/response.data.invalid.utf8/server" })
    public void shouldReceiveResponseDataInvalidUtf8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.bom.empty/client",
        "${net}/response.bom.empty/server" })
    public void shouldReceiveResponseBomEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.bom.non.empty/client",
        "${net}/response.bom.non.empty/server" })
    public void shouldReceiveResponseBomNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.end.of.line.line.feed/client",
        "${net}/response.end.of.line.line.feed/server" })
    public void shouldReceiveResponseEndOfLineLineFeed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.end.of.line.carriage.return/client",
        "${net}/response.end.of.line.carriage.return/server" })
    public void shouldReceiveResponseEndOfLineCarriageReturn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.end.of.line.carriage.return.line.feed/client",
        "${net}/response.end.of.line.carriage.return.line.feed/server" })
    public void shouldReceiveResponseEndOfLineCarriageReturnLineFeed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.end.of.line.carriage.return.line.feed.fragmented/client",
        "${net}/response.end.of.line.carriage.return.line.feed.fragmented/server" })
    public void shouldReceiveResponseEndOfLineCarriageReturnLineFeedFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.id.non.empty/client",
        "${net}/response.id.non.empty/server" })
    public void shouldReceiveResponseIdNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.id.empty/client",
        "${net}/response.id.empty/server" })
    public void shouldReceiveResponseIdEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.id.initial.whitespace/client",
        "${net}/response.id.initial.whitespace/server" })
    public void shouldReceiveResponseIdInitialWhitespace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.id.invalid.utf8/client",
        "${net}/response.id.invalid.utf8/server" })
    public void shouldReceiveResponseIdInvalidUtf8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.type.non.empty/client",
        "${net}/response.type.non.empty/server" })
    public void shouldReceiveResponseTypeNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.type.empty/client",
        "${net}/response.type.empty/server" })
    public void shouldReceiveResponseTypeEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.type.fragmented/client",
        "${net}/response.type.fragmented/server" })
    public void shouldReceiveResponseTypeFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.type.initial.whitespace/client",
        "${net}/response.type.initial.whitespace/server" })
    public void shouldReceiveResponseTypeInitialWhitespace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.type.name.only/client",
        "${net}/response.type.name.only/server" })
    public void shouldReceiveResponseTypeNameOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.type.invalid.utf8/client",
        "${net}/response.type.invalid.utf8/server" })
    public void shouldReceiveResponseTypeInvalidUtf8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.type.non.empty.interleaved/client",
        "${net}/response.type.non.empty.interleaved/server" })
    public void shouldReceiveResponseTypeNonEmptyInterleaved() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.type.non.empty.trailing/client",
        "${net}/response.type.non.empty.trailing/server" })
    public void shouldReceiveResponseTypeNonEmptyTrailing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/response.raw/client",
        "${net}/response.raw/server" })
    public void shouldReceiveResponseRaw() throws Exception
    {
        k3po.finish();
    }
}
