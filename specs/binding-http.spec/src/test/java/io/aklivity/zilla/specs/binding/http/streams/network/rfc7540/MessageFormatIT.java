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

public class MessageFormatIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7540/message.format");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/continuation.frames/client",
        "${net}/continuation.frames/server",
    })
    public void continuationFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/dynamic.table.requests/client",
        "${net}/dynamic.table.requests/server",
    })
    public void dynamicTableRequests() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.max.frame.size/client",
        "${net}/server.max.frame.size/server",
    })
    public void serverMaxFrameSize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.max.frame.size/client",
        "${net}/client.max.frame.size/server",
    })
    public void clientMaxFrameSize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/max.frame.size.error/client",
        "${net}/max.frame.size.error/server",
    })
    public void maxFrameSizeError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/ping.frame.size.error/client",
        "${net}/ping.frame.size.error/server"
    })
    public void pingFrameSizeError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connection.window.frame.size.error/client",
        "${net}/connection.window.frame.size.error/server"
    })
    public void connectionWindowFrameSizeError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/window.frame.size.error/client",
        "${net}/window.frame.size.error/server"
    })
    public void windowFrameSizeError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/rst.stream.frame.size.error/client",
        "${net}/rst.stream.frame.size.error/server"
    })
    public void rstStreanFrameSizeError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/priority.frame.size.error/client",
        "${net}/priority.frame.size.error/server"
    })
    public void priorityFrameSizeError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/max.zilla.data.frame.size/client",
        "${net}/max.zilla.data.frame.size/server",
    })
    public void maxZillaDataFrameSize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connection.headers/client",
        "${net}/connection.headers/server",
    })
    public void connectionHeaders() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/stream.id.order/client",
        "${net}/stream.id.order/server",
    })
    public void streamIdOrder() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/invalid.hpack.index/client",
        "${net}/invalid.hpack.index/server",
    })
    public void invalidHpackIndex() throws Exception
    {
        k3po.finish();
    }
}
