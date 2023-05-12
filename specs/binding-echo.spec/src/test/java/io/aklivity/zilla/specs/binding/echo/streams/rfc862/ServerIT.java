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
package io.aklivity.zilla.specs.binding.echo.streams.rfc862;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class ServerIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("scripts", "io/aklivity/zilla/specs/binding/echo/streams/rfc862");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${scripts}/connection.established/client",
        "${scripts}/connection.established/server"})
    public void shouldEstablishConnection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${scripts}/client.sent.data/client",
        "${scripts}/client.sent.data/server"})
    public void shouldEchoClientSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${scripts}/client.sent.flush/client",
        "${scripts}/client.sent.flush/server"})
    public void shouldEchoClientSentFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${scripts}/client.sent.challenge/client",
        "${scripts}/client.sent.challenge/server"})
    public void shouldEchoClientSentChallenge() throws Exception
    {
        k3po.finish();
    }
}
