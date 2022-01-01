/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.sse.internal.streams.server;

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

public class ReconnectIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/cog/sse/streams/network/reconnect")
        .addScriptRoot("app", "io/aklivity/zilla/specs/cog/sse/streams/application/reconnect");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/cog/sse/config")
        .external("app#0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.when.json")
    @Specification({
        "${net}/request.header.last.event.id.and.data/request",
        "${app}/last.event.id.data/response" })
    public void shouldReconnectWithLastEventIdOnData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.json")
    @Specification({
        "${net}/request.header.last.event.id/request",
        "${app}/last.event.id.end/response" })
    public void shouldReconnectWithLastEventIdOnEnd() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.json")
    @Specification({
        "${net}/request.header.last.event.id.fragmented/request",
        "${app}/last.event.id.end.fragmented/response" })
    public void shouldReconnectWithLastEventIdOnEndFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.json")
    @Specification({
        "${net}/request.header.and.parameter.last.event.id/request",
        "${app}/initial.last.event.id/response" })
    public void shouldReconnectWithInitialLastEventId() throws Exception
    {
        k3po.finish();
    }
}
