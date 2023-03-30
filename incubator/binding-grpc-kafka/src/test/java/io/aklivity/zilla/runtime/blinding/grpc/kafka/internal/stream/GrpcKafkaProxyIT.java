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
package io.aklivity.zilla.runtime.blinding.grpc.kafka.internal.stream;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
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

public class GrpcKafkaProxyIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("grpc", "io/aklivity/zilla/specs/binding/grpc/kafka/streams/grpc")
        .addScriptRoot("kafka", "io/aklivity/zilla/specs/binding/grpc/kafka/streams/kafka");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/grpc/kafka/config")
        .external("kafka0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.rpc.yaml")
    @Specification({
        "${grpc}/unary.rpc/client",
        "${kafka}/unary.rpc/server"})
    public void shouldExchangeMessageWithUnaryRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.rpc.yaml")
    @Specification({
        "${grpc}/unary.rpc.rejected/client",
        "${kafka}/unary.rpc.rejected/server"})
    public void shouldRejectUnaryRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.rpc.yaml")
    @Specification({
        "${grpc}/client.stream.rpc/client",
        "${kafka}/client.stream.rpc/server"})
    public void shouldExchangeMessageWithClientStreamRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.rpc.yaml")
    @Specification({
        "${grpc}/client.stream.rpc.rejected/client",
        "${kafka}/client.stream.rpc.rejected/server"})
    public void shouldRejectClientStreamRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.rpc.yaml")
    @Specification({
        "${grpc}/server.stream.rpc/client",
        "${kafka}/server.stream.rpc/server"})
    public void shouldExchangeMessageWithServerStreamRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.rpc.yaml")
    @Specification({
        "${grpc}/server.stream.rpc.rejected/client",
        "${kafka}/server.stream.rpc.rejected/server"})
    public void shouldRejectServerStreamRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.rpc.yaml")
    @Specification({
        "${grpc}/bidi.stream.rpc/client",
        "${kafka}/bidi.stream.rpc/server"})
    public void shouldExchangeMessageWithBidiStreamRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.rpc.yaml")
    @Specification({
        "${grpc}/bidi.stream.rpc.rejected/client",
        "${kafka}/bidi.stream.rpc.rejected/server"})
    public void shouldRejectBiStreamRpc() throws Exception
    {
        k3po.finish();
    }
}
