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
package io.aklivity.zilla.specs.binding.kafka.grpc.internal.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class GrpcIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("grpc", "io/aklivity/zilla/specs/binding/kafka/grpc/streams/grpc");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${grpc}/unary.rpc/client",
        "${grpc}/unary.rpc/server"})
    public void shouldExchangeMessageInUnary() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${grpc}/unary.rpc.message.value.100k/client",
        "${grpc}/unary.rpc.message.value.100k/server"})
    public void shouldExchangeMessageValue100kInUnary() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${grpc}/client.stream.rpc/client",
        "${grpc}/client.stream.rpc/server"})
    public void shouldExchangeMessageInClientStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${grpc}/server.stream.rpc/client",
        "${grpc}/server.stream.rpc/server"})
    public void shouldExchangeMessageWithServerStreamRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${grpc}/bidi.stream.rpc/client",
        "${grpc}/bidi.stream.rpc/server"})
    public void shouldExchangeMessageWithBidiStreamRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${grpc}/retry.on.unavailable.server/client",
        "${grpc}/retry.on.unavailable.server/server"})
    public void shouldRetryOnUnavailableServer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${grpc}/client.sent.write.abort/client",
        "${grpc}/client.sent.write.abort/server"})
    public void shouldNotProduceOnClientSentAbort() throws Exception
    {
        k3po.finish();
    }
}
