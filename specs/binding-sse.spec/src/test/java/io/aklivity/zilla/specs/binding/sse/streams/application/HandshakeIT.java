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
package io.aklivity.zilla.specs.binding.sse.streams.application;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class HandshakeIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/sse/streams/application/handshake");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/connection.succeeded/client",
        "${app}/connection.succeeded/server" })
    public void shouldHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connection.closed/client",
        "${app}/connection.closed/server" })
    public void shouldHandshakeThenClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connection.closed.deferred/client",
        "${app}/connection.closed.deferred/server" })
    public void shouldHandshakeThenCloseDeferred() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/last.event.id/client",
        "${app}/last.event.id/server" })
    public void shouldHandshakeWithLastEventId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/last.event.id.empty/client",
        "${app}/last.event.id.empty/server" })
    public void shouldHandshakeWithLastEventIdEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connection.failed/client",
        "${app}/connection.failed/server" })
    public void shouldFailHandshake() throws Exception
    {
        k3po.finish();
    }
}
