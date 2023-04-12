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


public class KafkaIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("kafka", "io/aklivity/zilla/specs/binding/kafka/grpc/streams/kafka");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${kafka}/unary.rpc/client",
        "${kafka}/unary.rpc/server"})
    public void shouldExchangeMessageInUnary() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/client.stream.rpc/client",
        "${kafka}/client.stream.rpc/server"})
    public void shouldExchangeMessageInClientStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/server.stream.rpc/client",
        "${kafka}/server.stream.rpc/server"})
    public void shouldExchangeMessageWithServerStreamRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/bidi.stream.rpc/client",
        "${kafka}/bidi.stream.rpc/server"})
    public void shouldExchangeMessageWithBidiStreamRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/missing.service.and.method.headers/client",
        "${kafka}/missing.service.and.method.headers/server"})
    public void shouldRejectOnMissingServiceAndMethodHeaders() throws Exception
    {
        k3po.finish();
    }
}
