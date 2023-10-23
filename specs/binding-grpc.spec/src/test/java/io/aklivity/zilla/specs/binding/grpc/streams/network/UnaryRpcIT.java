/*
 * Copyright 2021-2023 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.specs.binding.grpc.streams.network;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;


public class UnaryRpcIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/grpc/streams/network/unary.rpc");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/message.exchange/client",
        "${net}/message.exchange/server",
    })
    public void shouldEstablishUnaryRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/empty.message.exchange/client",
        "${net}/empty.message.exchange/server",
    })
    public void shouldExchangeEmptyMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/binary.metadata/client",
        "${net}/binary.metadata/server",
    })
    public void shouldEstablishWithBinaryMetadata() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/grpc.web/client",
        "${net}/grpc.web/server",
    })
    public void shouldEstablishGrpcWeb() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.timeout/client",
        "${net}/response.timeout/server",
    })
    public void shouldTimeoutOnNoResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.with.grpc.error/client",
        "${net}/response.with.grpc.error/server",
    })
    public void shouldAbortResponseOnGrpcError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.send.read.abort.on.open.request/client",
        "${net}/server.send.read.abort.on.open.request/server"
    })
    public void serverSendsReadAbortOnOpenRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.send.write.abort.on.open.response/client",
        "${net}/server.send.write.abort.on.open.response/server"
    })
    public void serverSendsWriteAbortOnOpenResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.send.write.abort.on.open.request.response/client",
        "${net}/server.send.write.abort.on.open.request.response/server"
    })
    public void serverSendsWriteAbortOnOpenRequestResponse() throws Exception
    {
        k3po.finish();
    }
}
