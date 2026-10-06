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
package io.aklivity.zilla.specs.binding.http.streams.network.rfc7540;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class EventStreamIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7540/event.stream");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/response.sse.framing.event/client",
        "${net}/response.sse.framing.event/server" })
    public void shouldReceiveEvent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.response.sse.framing.event/client",
        "${net}/server.response.sse.framing.event/server" })
    public void shouldReceiveServerEvent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.data.non.empty/client",
        "${net}/response.sse.framing.data.non.empty/server" })
    public void shouldReceiveDataNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.response.sse.framing.data.non.empty/client",
        "${net}/server.response.sse.framing.data.non.empty/server" })
    public void shouldReceiveServerDataNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.data.multi.line/client",
        "${net}/response.sse.framing.data.multi.line/server" })
    public void shouldReceiveDataMultiLine() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.response.sse.framing.data.multi.line/client",
        "${net}/server.response.sse.framing.data.multi.line/server" })
    public void shouldReceiveServerDataMultiLine() throws Exception
    {
        k3po.finish();
    }
}
