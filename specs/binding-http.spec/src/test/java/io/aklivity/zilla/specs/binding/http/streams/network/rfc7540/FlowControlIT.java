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
package io.aklivity.zilla.specs.binding.http.streams.network.rfc7540;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class FlowControlIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7540/flow.control");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/client.stream.flow/client",
        "${net}/client.stream.flow/server",
    })
    public void clientStreamFlow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.stream.flow/client",
        "${net}/server.stream.flow/server",
    })
    public void serverStreamFlow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.100k.message/client",
        "${net}/client.sent.100k.message/server",
    })
    public void clientSent100kMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.100k.message/client",
        "${net}/server.sent.100k.message/server",
    })
    public void serverSent100kMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.rst.stream.last.frame/client",
        "${net}/client.rst.stream.last.frame/server"
    })
    public void clientRstStreamLastFrame() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.rst.stream.last.frame/client",
        "${net}/server.rst.stream.last.frame/server"
    })
    public void serverRstStreamLastFrame() throws Exception
    {
        k3po.finish();
    }
}
