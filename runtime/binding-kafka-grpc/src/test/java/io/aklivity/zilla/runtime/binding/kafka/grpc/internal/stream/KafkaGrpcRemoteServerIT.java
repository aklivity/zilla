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
package io.aklivity.zilla.runtime.binding.kafka.grpc.internal.stream;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
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

public class KafkaGrpcRemoteServerIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("grpc", "io/aklivity/zilla/specs/binding/kafka/grpc/streams/grpc")
        .addScriptRoot("kafka", "io/aklivity/zilla/specs/binding/kafka/grpc/streams/kafka");

    private final TestRule timeout = new DisableOnDebug(new Timeout(2, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/binding/kafka/grpc/config")
        .external("grpc0")
        .external("kafka0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("remote.server.rpc.yaml")
    @Specification({
        "${kafka}/unary.rpc/server",
        "${grpc}/unary.rpc/server"})
    public void shouldExchangeMessageWithUnaryRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("remote.server.rpc.yaml")
    @Specification({
        "${kafka}/client.stream.rpc/server",
        "${grpc}/client.stream.rpc/server"})
    public void shouldExchangeMessageWithClientStreamRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("remote.server.rpc.yaml")
    @Specification({
        "${kafka}/server.stream.rpc/server",
        "${grpc}/server.stream.rpc/server"})
    public void shouldExchangeMessageWithServerStreamRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("remote.server.rpc.yaml")
    @Specification({
        "${kafka}/bidi.stream.rpc/server",
        "${grpc}/bidi.stream.rpc/server"})
    public void shouldExchangeMessageWithBidiStreamRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("remote.server.rpc.yaml")
    @Specification({
        "${kafka}/unary.rpc/server",
        "${grpc}/retry.on.unavailable.server/server"})
    public void shouldRetryOnUnavailableServer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("remote.server.rpc.yaml")
    @Specification({
        "${kafka}/missing.service.and.method.headers/server"})
    public void shouldRejectOnMissingServiceAndMethodHeaders() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("remote.server.rpc.yaml")
    @Specification({
        "${kafka}/client.sent.write.abort/server",
        "${grpc}/client.sent.write.abort/server"})
    public void shouldNotProduceOnClientSentAbort() throws Exception
    {
        k3po.finish();
    }
}
