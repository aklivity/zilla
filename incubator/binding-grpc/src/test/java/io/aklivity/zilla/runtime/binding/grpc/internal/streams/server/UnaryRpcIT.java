/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.grpc.internal.streams.server;

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

public class UnaryRpcIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/grpc/streams/network/unary.rpc")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/grpc/streams/application/unary.rpc");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/binding/grpc/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.when.json")
    @Specification({
        "${net}/message.exchange/client",
        "${app}/message.exchange/server"
    })
    public void shouldEstablishUnaryRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.json")
    @Specification({
        "${net}/binary.metadata/client",
        "${app}/binary.metadata/server",
    })
    public void shouldEstablishWithBinaryMetadata() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.json")
    @Specification({
        "${net}/grpc.web/client",
        "${app}/message.exchange/server",
    })
    public void shouldEstablishGrpcWeb() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.json")
    @Specification({
        "${net}/server.send.read.abort.on.open.request/client",
        "${app}/server.send.read.abort.on.open.request/server"
    })
    public void shouldRejectServerResetUnaryRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.json")
    @Specification({
        "${app}/server.send.read.abort.on.open.request/client",
        "${app}/server.send.read.abort.on.open.request/server"
    })
    public void serverSendsReadAbortOnOpenRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.json")
    @Specification({
        "${app}/server.send.write.abort.on.open.response/client",
        "${app}/server.send.write.abort.on.open.response/server"
    })
    public void serverSendsWriteAbortOnOpenResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.json")
    @Specification({
        "${app}/server.send.write.abort.on.open.request.response/client",
        "${app}/server.send.write.abort.on.open.request.response/server"
    })
    public void serverSendsWriteAbortOnOpenRequestResponse() throws Exception
    {
        k3po.finish();
    }

}
