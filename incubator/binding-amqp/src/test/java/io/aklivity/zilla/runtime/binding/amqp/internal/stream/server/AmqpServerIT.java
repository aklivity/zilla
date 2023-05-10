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
package io.aklivity.zilla.runtime.binding.amqp.internal.stream.server;

import static io.aklivity.zilla.runtime.binding.amqp.internal.AmqpConfiguration.AMQP_CLOSE_EXCHANGE_TIMEOUT;
import static io.aklivity.zilla.runtime.binding.amqp.internal.AmqpConfiguration.AMQP_CONTAINER_ID;
import static io.aklivity.zilla.runtime.binding.amqp.internal.config.AmqpConfigurationTest.AMQP_HANDLE_MAX_NAME;
import static io.aklivity.zilla.runtime.binding.amqp.internal.config.AmqpConfigurationTest.AMQP_IDLE_TIMEOUT_NAME;
import static io.aklivity.zilla.runtime.binding.amqp.internal.config.AmqpConfigurationTest.AMQP_INCOMING_LOCALES_NAME;
import static io.aklivity.zilla.runtime.binding.amqp.internal.config.AmqpConfigurationTest.AMQP_MAX_FRAME_SIZE_NAME;
import static io.aklivity.zilla.runtime.binding.amqp.internal.config.AmqpConfigurationTest.AMQP_MAX_MESSAGE_SIZE_NAME;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class AmqpServerIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/amqp/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/amqp/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(AMQP_CONTAINER_ID, "server")
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configure(AMQP_CLOSE_EXCHANGE_TIMEOUT, 500)
        .configurationRoot("io/aklivity/zilla/specs/binding/amqp/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);


    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connection/header.exchange/handshake.client" })
    public void shouldExchangeHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connection/sasl.exchange/client" })
    public void shouldExchangeSasl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connection/protocol.header.unmatched/client" })
    public void shouldCloseStreamWhenHeaderUnmatched() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connection/open.exchange/client" })
    public void shouldExchangeOpen() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connection/close.exchange/client" })
    public void shouldExchangeClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connection/close.exchange.server.abandoned/client" })
    @Configure(name = AMQP_IDLE_TIMEOUT_NAME, value = "1000")
    public void shouldCloseStreamWhenServerAbandoned() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session/begin.exchange/client" })
    public void shouldExchangeBegin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session/begin.then.close/client" })
    public void shouldExchangeBeginThenClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session/end.exchange/client" })
    public void shouldExchangeEnd() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/attach.as.receiver.only/client",
        "${app}/connect.as.receiver.only/server" })
    public void shouldConnectAsReceiverOnly() throws Exception
    {
        k3po.finish();
    }

    @Ignore
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/attach.as.receiver.then.sender/client",
        "${app}/connect.as.receiver.then.sender/server" })
    public void shouldConnectAsReceiverThenSender() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/attach.as.sender.only/client",
        "${app}/connect.as.sender.only/server" })
    public void shouldConnectAsSenderOnly() throws Exception
    {
        k3po.finish();
    }

    @Ignore
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/attach.as.sender.then.receiver/client",
        "${app}/connect.as.sender.then.receiver/server" })
    public void shouldConnectAsSenderThenReceiver() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/detach.exchange/client",
        "${app}/disconnect/server" })
    public void shouldExchangeDetach() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/attach.as.receiver.when.source.does.not.exist/client",
        "${app}/connect.and.reset/server" })
    public void shouldConnectAsReceiverAndReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/attach.as.sender.when.target.does.not.exist/client",
        "${app}/connect.and.reset/server" })
    public void shouldConnectAsSenderAndReset() throws Exception
    {
        k3po.finish();
    }

    @Ignore
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.at.least.once/client",
        "${app}/send.to.client.at.least.once/server" })
    public void shouldSendToClientAtLeastOnce() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.array8/client",
        "${app}/send.to.client.with.array8/server" })
    public void shouldSendToClientWithArray8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.array32/client",
        "${app}/send.to.client.with.array32/server" })
    public void shouldSendToClientWithArray32() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.boolean/client",
        "${app}/send.to.client.with.boolean/server" })
    public void shouldSendToClientWithBoolean() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.byte/client",
        "${app}/send.to.client.with.byte/server" })
    public void shouldSendToClientWithByte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.char/client",
        "${app}/send.to.client.with.char/server" })
    public void shouldSendToClientWithChar() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.false/client",
        "${app}/send.to.client.with.false/server" })
    public void shouldSendToClientWithFalse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.int/client",
        "${app}/send.to.client.with.int/server" })
    public void shouldSendToClientWithInt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.list0/client",
        "${app}/send.to.client.with.list0/server" })
    public void shouldSendToClientWithList0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.list8/client",
        "${app}/send.to.client.with.list8/server" })
    public void shouldSendToClientWithList8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.list32/client",
        "${app}/send.to.client.with.list32/server" })
    public void shouldSendToClientWithList32() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.long/client",
        "${app}/send.to.client.with.long/server" })
    public void shouldSendToClientWithLong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.map8/client",
        "${app}/send.to.client.with.map8/server" })
    public void shouldSendToClientWithMap8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.map32/client",
        "${app}/send.to.client.with.map32/server" })
    public void shouldSendToClientWithMap32() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.multiple.data/client",
        "${app}/send.to.client.with.multiple.data/server" })
    public void shouldSendToClientWithMultipleData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.multiple.sequence/client",
        "${app}/send.to.client.with.multiple.sequence/server" })
    public void shouldSendToClientWithMultipleSequence() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.null/client",
        "${app}/send.to.client.with.null/server" })
    public void shouldSendToClientWithNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.short/client",
        "${app}/send.to.client.with.short/server" })
    public void shouldSendToClientWithShort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.single.data/client",
        "${app}/send.to.client.with.single.data/server" })
    public void shouldSendToClientWithSingleData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.single.sequence/client",
        "${app}/send.to.client.with.single.sequence/server" })
    public void shouldSendToClientWithSingleSequence() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.smallint/client",
        "${app}/send.to.client.with.smallint/server" })
    public void shouldSendToClientWithSmallInt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.smalllong/client",
        "${app}/send.to.client.with.smalllong/server" })
    public void shouldSendToClientWithSmallLong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.smalluint/client",
        "${app}/send.to.client.with.smalluint/server" })
    public void shouldSendToClientWithSmallUint() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.smallulong/client",
        "${app}/send.to.client.with.smallulong/server" })
    public void shouldSendToClientWithSmallUlong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.str8utf8/client",
        "${app}/send.to.client.with.str8utf8/server" })
    public void shouldSendToClientWithStr8utf8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.str32utf8/client",
        "${app}/send.to.client.with.str32utf8/server" })
    public void shouldSendToClientWithStr32utf8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.sym8/client",
        "${app}/send.to.client.with.sym8/server" })
    public void shouldSendToClientWithSym8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.sym32/client",
        "${app}/send.to.client.with.sym32/server" })
    public void shouldSendToClientWithSym32() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.timestamp/client",
        "${app}/send.to.client.with.timestamp/server" })
    public void shouldSendToClientWithTimestamp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.true/client",
        "${app}/send.to.client.with.true/server" })
    public void shouldSendToClientWithTrue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.ubyte/client",
        "${app}/send.to.client.with.ubyte/server" })
    public void shouldSendToClientWithUbyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.uint/client",
        "${app}/send.to.client.with.uint/server" })
    public void shouldSendToClientWithUint() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.uint0/client",
        "${app}/send.to.client.with.uint0/server" })
    public void shouldSendToClientWithUint0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.ulong/client",
        "${app}/send.to.client.with.ulong/server" })
    public void shouldSendToClientWithUlong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.ulong0/client",
        "${app}/send.to.client.with.ulong0/server" })
    public void shouldSendToClientWithUlong0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.ushort/client",
        "${app}/send.to.client.with.ushort/server" })
    public void shouldSendToClientWithUshort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.vbin8/client",
        "${app}/send.to.client.with.vbin8/server" })
    public void shouldSendToClientWithVbin8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.vbin32/client",
        "${app}/send.to.client.with.vbin32/server" })
    public void shouldSendToClientWithVbin32() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.array8/client",
        "${app}/send.to.server.with.array8/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithArray8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.array32/client",
        "${app}/send.to.server.with.array32/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithArray32() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.boolean/client",
        "${app}/send.to.server.with.boolean/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithBoolean() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.byte/client",
        "${app}/send.to.server.with.byte/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithByte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.char/client",
        "${app}/send.to.server.with.char/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithChar() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.false/client",
        "${app}/send.to.server.with.false/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithFalse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.int/client",
        "${app}/send.to.server.with.int/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithInt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.list0/client",
        "${app}/send.to.server.with.list0/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithList0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.list8/client",
        "${app}/send.to.server.with.list8/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithlist8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.list32/client",
        "${app}/send.to.server.with.list32/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithList32() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.long/client",
        "${app}/send.to.server.with.long/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithLong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.map8/client",
        "${app}/send.to.server.with.map8/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithMap8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.map32/client",
        "${app}/send.to.server.with.map32/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithMap32() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.multiple.data/client",
        "${app}/send.to.server.with.multiple.data/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithMultipleData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.multiple.sequence/client",
        "${app}/send.to.server.with.multiple.sequence/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithMultipleSequence() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.null/client",
        "${app}/send.to.server.with.null/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.properties/client",
        "${app}/send.to.server.with.properties/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.short/client",
        "${app}/send.to.server.with.short/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithShort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.single.data/client",
        "${app}/send.to.server.with.single.data/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithSingleData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.single.sequence/client",
        "${app}/send.to.server.with.single.sequence/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithSingleSequence() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.smallint/client",
        "${app}/send.to.server.with.smallint/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithSmallInt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.smalllong/client",
        "${app}/send.to.server.with.smalllong/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithSmallLong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.smalluint/client",
        "${app}/send.to.server.with.smalluint/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithSmallUint() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.smallulong/client",
        "${app}/send.to.server.with.smallulong/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithSmallUlong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.str8utf8/client",
        "${app}/send.to.server.with.str8utf8/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithStr8utf8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.str32utf8/client",
        "${app}/send.to.server.with.str32utf8/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithStr32utf8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.sym8/client",
        "${app}/send.to.server.with.sym8/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithSym8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.sym32/client",
        "${app}/send.to.server.with.sym32/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithSym32() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.timestamp/client",
        "${app}/send.to.server.with.timestamp/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithTimestamp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.true/client",
        "${app}/send.to.server.with.true/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithTrue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.ubyte/client",
        "${app}/send.to.server.with.ubyte/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithUbyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.uint/client",
        "${app}/send.to.server.with.uint/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithUint() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.uint0/client",
        "${app}/send.to.server.with.uint0/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithUint0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.ulong/client",
        "${app}/send.to.server.with.ulong/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithUlong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.ulong0/client",
        "${app}/send.to.server.with.ulong0/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithUlong0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.ushort/client",
        "${app}/send.to.server.with.ushort/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithUshort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.vbin8/client",
        "${app}/send.to.server.with.vbin8/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithVbin8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.vbin32/client",
        "${app}/send.to.server.with.vbin32/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithVbin32() throws Exception
    {
        k3po.finish();
    }

    @Ignore
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.at.least.once/client",
        "${app}/send.to.server.at.least.once/server" })
    public void shouldSendToServerAtLeastOnce() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.headers/client",
        "${app}/send.to.server.with.headers/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithHeaders() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.delivery.annotations/client",
        "${app}/send.to.server.with.delivery.annotations/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithDeliveryAnnotations() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.annotations/client",
        "${app}/send.to.server.with.annotations/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithAnnotations() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.footer/client",
        "${app}/send.to.server.with.footer/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithFooter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.when.max.frame.size.exceeded/client",
        "${app}/send.to.server.when.max.frame.size.exceeded/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWhenMaxFrameSizeExceeded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.when.fragmented/client",
        "${app}/send.to.server.when.fragmented/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "8000")
    public void shouldSendToServerWhenFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.when.links.interleaved/client",
        "${app}/send.to.server.when.links.interleaved/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "8000")
    public void shouldSendToServerWhenLinksInterleaved() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.when.links.interleaved.and.fragmented/client",
        "${app}/send.to.server.when.links.interleaved.and.fragmented/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "8000")
    public void shouldSendToServerWhenLinksInterleavedAndFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session/transfer.to.server.when.sessions.interleaved/client",
        "${app}/send.to.server.when.sessions.interleaved/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "8000")
    public void shouldSendToServerWhenSessionsInterleaved() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session/transfer.to.server.when.sessions.interleaved.and.fragmented/client",
        "${app}/send.to.server.when.sessions.interleaved.and.fragmented/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "8000")
    public void shouldSendToServerWhenSessionsInterleavedAndFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session/incoming.window.exceeded/client",
        "${app}/incoming.window.exceeded/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "8192")
    public void shouldEndSessionWhenIncomingWindowExceeded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session/send.to.client.multiple.sessions/client",
        "${app}/send.to.client.through.multiple.sessions/server" })
    public void shouldSendToClientThroughMultipleSessions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.annotations/client",
        "${app}/send.to.client.with.annotations/server" })
    public void shouldSendToClientWithAnnotations() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.properties/client",
        "${app}/send.to.client.with.properties/server" })
    public void shouldSendToClientWithProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.when.max.frame.size.exceeded/client",
        "${app}/send.to.client.when.max.frame.size.exceeded/server" })
    public void shouldSendToClientWhenMaxFrameSizeExceeded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.when.fragmented/client",
        "${app}/send.to.client.when.fragmented/server" })
    public void shouldSendToClientWhenFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.when.links.interleaved/client",
        "${app}/send.to.client.when.links.interleaved/server" })
    public void shouldSendToClientWhenLinksInterleaved() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.when.links.interleaved.and.max.frame.size.exceeded/client",
        "${app}/send.to.client.when.links.interleaved.and.max.frame.size.exceeded/server" })
    public void shouldSendToClientWhenLinksInterleavedAndMaxFrameSizeExceeded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.when.links.interleaved.and.fragmented/client",
        "${app}/send.to.client.when.links.interleaved.and.fragmented/server" })
    public void shouldSendToClientWhenLinksInterleavedAndFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session/transfer.to.client.when.sessions.interleaved/client",
        "${app}/send.to.client.when.sessions.interleaved/server" })
    public void shouldSendToClientWhenSessionsInterleaved() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session/transfer.to.client.when.sessions.interleaved.and.max.frame.size.exceeded/client",
        "${app}/send.to.client.when.sessions.interleaved.and.max.frame.size.exceeded/server" })
    public void shouldSendToClientWhenSessionsInterleavedAndMaxFrameSizeExceeded() throws Exception
    {
        k3po.finish();
    }

    @Ignore("requires k3po parallel reads")
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session/transfer.to.client.when.sessions.interleaved.and.fragmented/client",
        "${app}/send.to.client.when.sessions.interleaved.and.fragmented/server" })
    public void shouldSendToClientWhenSessionsInterleavedAndFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/link.credit.exceeded/client",
        "${app}/link.credit.exceeded/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "8192")
    public void shouldDetachLinkWhenLinkCreditExceeded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/max.frame.size.exceeded.with.multiple.sessions.and.links/client",
        "${app}/max.frame.size.exceeded.with.multiple.sessions.and.links/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "8000")
    public void shouldCloseConnectionWhenMaxFrameSizeExceededWithMultipleSessions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connection/server.idle.timeout.expires/client" })
    @Configure(name = AMQP_IDLE_TIMEOUT_NAME, value = "1000")
    public void shouldCloseConnectionWithTimeout() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connection/server.idle.timeout.does.not.expire/client" })
    @Configure(name = AMQP_IDLE_TIMEOUT_NAME, value = "1000")
    public void shouldPreventTimeoutSentByServer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connection/client.idle.timeout.does.not.expire/client" })
    public void shouldPreventTimeoutSentByClient() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session/incoming.window.reduced/client",
        "${app}/incoming.window.reduced/server" })
    public void shouldHandleReducedIncomingWindow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connection/open.exchange.pipelined/client" })
    public void shouldExchangeOpenPipelined() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connection/sasl.exchange.then.open.exchange.pipelined/client" })
    public void shouldExchangeOpenPipelinedAfterSasl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connection/close.exchange.simultaneous/client" })
    @Configure(name = AMQP_IDLE_TIMEOUT_NAME, value = "1000")
    public void shouldExchangeCloseSimultaneously() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session/end.exchange.simultaneous/client",
        "${app}/incoming.window.exceeded/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "8192")
    public void shouldEndSessionSimultaneously() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session/discard.after.end/client",
        "${app}/incoming.window.exceeded/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "8192")
    public void shouldDiscardInboundAfterOutboundEnd() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session/reject.errant.link/client",
        "${app}/link.credit.exceeded/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "8192")
    public void shouldRejectErrantLinks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.when.first.fragment.aborted/client",
        "${app}/send.to.server.when.first.fragment.aborted/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWhenFirstFragmentAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connection/open.with.outgoing.locales.negotiated.default/client" })
    public void shouldSendOpenWithOutgoingLocalesNegotiatedDefault() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connection/open.with.outgoing.locales.negotiated.non.default/client" })
    @Configure(name = AMQP_INCOMING_LOCALES_NAME, value = "jp")
    public void shouldOpenWithOutgoingLocakesNegotiatedNonDefault() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/attach.as.receiver.then.detach.with.error.then.flow/client",
        "${app}/connect.and.reset/server" })
    public void shouldNotTriggerErrorWhenReceivingFlowAfterDetach() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/handle.max.exceeded/client" })
    @Configure(name = AMQP_HANDLE_MAX_NAME, value = "10")
    public void shouldCloseConnectionWhenHandleMaxExceeded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/reject.attach.when.handle.in.use/client",
        "${app}/connect.as.receiver.then.abort/server" })
    public void shouldCloseConnectionWhenAttachWithHandleInUse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.max.message.size.exceeded/client",
        "${app}/send.to.client.with.vbin32/server" })
    public void shouldSendToClientAndHandleMaxMessageSize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.max.message.size.exceeded/client",
        "${app}/connect.as.sender.then.abort/server" })
    @Configure(name = AMQP_MAX_MESSAGE_SIZE_NAME, value = "100")
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerAndHandleMaxMessageSize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.then.flow.with.echo.on.link/client",
        "${app}/send.to.server.then.flow.with.echo/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerThenFlowWithEchoOnLink() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.then.flow.with.echo.on.session/client",
        "${app}/send.to.server.then.flow.with.echo/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerThenFlowWithEchoOnSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/reject.flow.with.inconsistent.fields/client",
        "${app}/connect.as.receiver.then.abort/server" })
    public void shouldCloseConnectionWhenFlowHasInconsistentFields() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/flow.without.handle/client",
        "${app}/send.to.client.with.str8utf8/server" })
    public void shouldAllowFlowWithoutHandle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/flow.with.unattached.handle/client",
        "${app}/connect.as.receiver.then.abort/server" })
    public void shouldEndSessionWhenFlowWithUnattachedHandle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.large.delivery.count/client",
        "${app}/send.to.server.with.large.delivery.count/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithLargeDeliveryCount() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/reject.transfer.with.more.inconsistent.fields/client",
        "${app}/abort.after.sending.first.fragment/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldCloseConnectionWhenTransferWithMoreAndInconsistentFields() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.when.middle.fragment.aborted/client",
        "${app}/send.to.server.when.middle.fragment.aborted/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWhenMiddleFragmentAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.large.next.incoming.id/client",
        "${app}/send.to.server.with.str8utf8/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithLargeNextIncomingId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.when.last.fragment.aborted/client",
        "${app}/send.to.server.when.last.fragment.aborted/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWhenLastFragmentAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.invalid.delivery.id/client",
        "${app}/send.to.server.with.invalid.delivery.id/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithInvalidDeliveryId() throws Exception
    {
        k3po.finish();
    }

    @Ignore("GitHub Actions")
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.when.first.fragment.aborted/client",
        "${app}/send.to.client.when.first.fragment.aborted/server" })
    public void shouldSendToClientWhenFirstFragmentAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/connection/reject.incorrect.fields.key.type/client" })
    public void shouldRejectIncorrectFieldsKeyType() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.when.middle.fragment.aborted/client",
        "${app}/send.to.client.when.middle.fragment.aborted/server" })
    public void shouldSendToClientWhenMiddleFragmentAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.with.application.properties/client",
        "${app}/send.to.client.with.application.properties/server" })
    public void shouldSendToClientWithApplicationProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.client.when.last.fragment.aborted/client",
        "${app}/send.to.client.when.last.fragment.aborted/server" })
    public void shouldSendToClientWhenLastFragmentAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/transfer.to.server.with.application.properties/client",
        "${app}/send.to.server.with.application.properties/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldSendToServerWithApplicationProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/link/reject.durable.message.when.durable.not.supported/client",
        "${app}/connect.as.sender.then.abort/server" })
    @Configure(name = AMQP_MAX_FRAME_SIZE_NAME, value = "1000")
    public void shouldRejectDurableMessageWhenDurableNotSupported() throws Exception
    {
        k3po.finish();
    }
}
