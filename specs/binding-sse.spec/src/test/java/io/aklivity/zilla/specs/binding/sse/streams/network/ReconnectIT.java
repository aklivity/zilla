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
package io.aklivity.zilla.specs.binding.sse.streams.network;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class ReconnectIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/sse/streams/network/reconnect");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/request.header.last.event.id/request",
        "${net}/request.header.last.event.id/response" })
    public void shouldReconnectWithRequestHeaderLastEventIdAfterReceivingIdOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.header.last.event.id.fragmented/request",
        "${net}/request.header.last.event.id.fragmented/response" })
    public void shouldReconnectWithRequestHeaderLastEventIdWithFlowControlAfterReceivingIdOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.header.last.event.id.and.data/request",
        "${net}/request.header.last.event.id.and.data/response" })
    public void shouldReconnectWithRequestHeaderLastEventIdAfterReceivingIdAndData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.header.last.event.id.and.data.null/request",
        "${net}/request.header.last.event.id.and.data.null/response" })
    public void shouldReconnectWithRequestHeaderLastEventIdAfterReceivingIdAndDataNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.header.and.parameter.last.event.id/request",
        "${net}/request.header.and.parameter.last.event.id/response" })
    public void shouldReconnectWithRequestHeaderAndParameterLastEventId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.status.code.200/request",
        "${net}/response.status.code.200/response" })
    public void shouldReconnectWhenResponseStatus200() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.status.code.500/request",
        "${net}/response.status.code.500/response" })
    public void shouldReconnectWhenResponseStatus500() throws Exception
    {
        k3po.finish();
    }
}
