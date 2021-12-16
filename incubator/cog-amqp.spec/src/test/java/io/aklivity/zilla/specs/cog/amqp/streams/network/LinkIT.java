/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.specs.cog.amqp.streams.network;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class LinkIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/cog/amqp/streams/network/link");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/attach.as.receiver.only/client",
        "${net}/attach.as.receiver.only/server"})
    public void shouldExchangeAttachAsReceiver() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/attach.as.sender.only/client",
        "${net}/attach.as.sender.only/server"})
    public void shouldExchangeAttachAsSender() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/attach.as.sender.then.receiver/client",
        "${net}/attach.as.sender.then.receiver/server"})
    public void shouldExchangeAttachAsSenderThenReceiver() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/attach.as.receiver.then.sender/client",
        "${net}/attach.as.receiver.then.sender/server"})
    public void shouldExchangeAttachAsReceiverThenSender() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/attach.as.receiver.when.source.does.not.exist/client",
        "${net}/attach.as.receiver.when.source.does.not.exist/server"})
    public void shouldAttachAsReceiverWhenSourceDoesNotExist() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/attach.as.sender.when.target.does.not.exist/client",
        "${net}/attach.as.sender.when.target.does.not.exist/server"})
    public void shouldAttachAsSenderWhenTargetDoesNotExist() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.at.least.once/client",
        "${net}/transfer.to.client.at.least.once/server"})
    public void shouldTransferToClientAtLeastOnce() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.at.least.once/client",
        "${net}/transfer.to.server.at.least.once/server"})
    public void shouldTransferToServerAtLeastOnce() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/detach.exchange/client",
        "${net}/detach.exchange/server"})
    public void shouldDetachLink() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/link.credit.exceeded/client",
        "${net}/link.credit.exceeded/server"})
    public void shouldDetachLinkCreditExceeded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.headers/client",
        "${net}/transfer.to.client.with.headers/server"})
    public void shouldTransferToClientWithHeaders() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.delivery.annotations/client",
        "${net}/transfer.to.client.with.delivery.annotations/server"})
    public void shouldTransferToClientWithDeliveryAnnotations() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.annotations/client",
        "${net}/transfer.to.client.with.annotations/server"})
    public void shouldTransferToClientWithAnnotations() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.headers/client",
        "${net}/transfer.to.server.with.headers/server"})
    public void shouldTransferToServerWithHeaders() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.delivery.annotations/client",
        "${net}/transfer.to.server.with.delivery.annotations/server"})
    public void shouldTransferToServerWithDeliveryAnnotations() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.annotations/client",
        "${net}/transfer.to.server.with.annotations/server"})
    public void shouldTransferToServerWithAnnotations() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.properties/client",
        "${net}/transfer.to.client.with.properties/server"})
    public void shouldTransferToClientWithProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.properties/client",
        "${net}/transfer.to.server.with.properties/server"})
    public void shouldTransferToServerWithProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.footer/client",
        "${net}/transfer.to.client.with.footer/server"})
    public void shouldTransferToClientWithFooter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.footer/client",
        "${net}/transfer.to.server.with.footer/server"})
    public void shouldTransferToServerWithFooter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.when.max.frame.size.exceeded/client",
        "${net}/transfer.to.client.when.max.frame.size.exceeded/server"})
    public void shouldTransferToClientWhenMaxFrameSizeExceeded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.when.max.frame.size.exceeded/client",
        "${net}/transfer.to.server.when.max.frame.size.exceeded/server"})
    public void shouldTransferToServerWhenMaxFrameSizeExceeded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.when.fragmented/client",
        "${net}/transfer.to.client.when.fragmented/server"})
    public void shouldTransferToClientWhenFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.when.fragmented/client",
        "${net}/transfer.to.server.when.fragmented/server"})
    public void shouldTransferToServerWhenFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.when.links.interleaved/client",
        "${net}/transfer.to.client.when.links.interleaved/server"})
    public void shouldTransferToClientWhenLinksInterleaved() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.when.links.interleaved/client",
        "${net}/transfer.to.server.when.links.interleaved/server"})
    public void shouldTransferToServerWhenLinksInterleaved() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.when.links.interleaved.and.max.frame.size.exceeded/client",
        "${net}/transfer.to.client.when.links.interleaved.and.max.frame.size.exceeded/server"})
    public void shouldTransferToClientWhenLinksInterleavedAndMaxFrameSizeExceeded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.when.links.interleaved.and.fragmented/client",
        "${net}/transfer.to.client.when.links.interleaved.and.fragmented/server"})
    public void shouldTransferToClientWhenLinksInterleavedAndFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.when.links.interleaved.and.fragmented/client",
        "${net}/transfer.to.server.when.links.interleaved.and.fragmented/server"})
    public void shouldTransferToServerWhenLinksInterleavedAndFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/max.frame.size.exceeded.with.multiple.sessions.and.links/client",
        "${net}/max.frame.size.exceeded.with.multiple.sessions.and.links/server"})
    public void shouldCloseConnectionWhenMaxFrameSizeExceededWithMultipleSessions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.array8/client",
        "${net}/transfer.to.client.with.array8/server"})
    public void shouldTransferToClientWithArray8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.array8/client",
        "${net}/transfer.to.server.with.array8/server"})
    public void shouldTransferToServerWithArray8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.array32/client",
        "${net}/transfer.to.client.with.array32/server"})
    public void shouldTransferToClientWithArray32() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.array32/client",
        "${net}/transfer.to.server.with.array32/server"})
    public void shouldTransferToServerWithArray32() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.boolean/client",
        "${net}/transfer.to.client.with.boolean/server"})
    public void shouldTransferToClientWithBoolean() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.boolean/client",
        "${net}/transfer.to.server.with.boolean/server"})
    public void shouldTransferToServerWithBoolean() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.byte/client",
        "${net}/transfer.to.client.with.byte/server"})
    public void shouldTransferToClientWithByte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.byte/client",
        "${net}/transfer.to.server.with.byte/server"})
    public void shouldTransferToServerWithByte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.char/client",
        "${net}/transfer.to.client.with.char/server"})
    public void shouldTransferToClientWithChar() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.char/client",
        "${net}/transfer.to.server.with.char/server"})
    public void shouldTransferToServerWithChar() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.false/client",
        "${net}/transfer.to.client.with.false/server"})
    public void shouldTransferToClientWithFalse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.false/client",
        "${net}/transfer.to.server.with.false/server"})
    public void shouldTransferToServerWithFalse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.int/client",
        "${net}/transfer.to.client.with.int/server"})
    public void shouldTransferToClientWithInt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.int/client",
        "${net}/transfer.to.server.with.int/server"})
    public void shouldTransferToServerWithInt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.list0/client",
        "${net}/transfer.to.client.with.list0/server"})
    public void shouldTransferToClientWithList0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.list0/client",
        "${net}/transfer.to.server.with.list0/server"})
    public void shouldTransferToServerWithList0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.list8/client",
        "${net}/transfer.to.client.with.list8/server"})
    public void shouldTransferToClientWithList8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.list8/client",
        "${net}/transfer.to.server.with.list8/server"})
    public void shouldTransferToServerWithList8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.list32/client",
        "${net}/transfer.to.client.with.list32/server"})
    public void shouldTransferToClientWithList32() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.list32/client",
        "${net}/transfer.to.server.with.list32/server"})
    public void shouldTransferToServerWithList32() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.long/client",
        "${net}/transfer.to.client.with.long/server"})
    public void shouldTransferToClientWithLong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.long/client",
        "${net}/transfer.to.server.with.long/server"})
    public void shouldTransferToServerWithLong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.map8/client",
        "${net}/transfer.to.client.with.map8/server"})
    public void shouldTransferToClientWithMap8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.map8/client",
        "${net}/transfer.to.server.with.map8/server"})
    public void shouldTransferToServerWithMap8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.map32/client",
        "${net}/transfer.to.client.with.map32/server"})
    public void shouldTransferToClientWithMap32() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.map32/client",
        "${net}/transfer.to.server.with.map32/server"})
    public void shouldTransferToServerWithMap32() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.multiple.data/client",
        "${net}/transfer.to.client.with.multiple.data/server"})
    public void shouldTransferToClientWithMultipleData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.multiple.data/client",
        "${net}/transfer.to.server.with.multiple.data/server"})
    public void shouldTransferToServerWithMultipleData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.multiple.sequence/client",
        "${net}/transfer.to.client.with.multiple.sequence/server"})
    public void shouldTransferToClientWithMultipleSequence() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.multiple.sequence/client",
        "${net}/transfer.to.server.with.multiple.sequence/server"})
    public void shouldTransferToServerWithMultipleSequence() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.null/client",
        "${net}/transfer.to.client.with.null/server"})
    public void shouldTransferToClientWithNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.null/client",
        "${net}/transfer.to.server.with.null/server"})
    public void shouldTransferToServerWithNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.short/client",
        "${net}/transfer.to.client.with.short/server"})
    public void shouldTransferToClientWithShort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.short/client",
        "${net}/transfer.to.server.with.short/server"})
    public void shouldTransferToServerWithShort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.single.data/client",
        "${net}/transfer.to.client.with.single.data/server"})
    public void shouldTransferToClientWithSingleData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.single.data/client",
        "${net}/transfer.to.server.with.single.data/server"})
    public void shouldTransferToServerWithSingleData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.single.sequence/client",
        "${net}/transfer.to.client.with.single.sequence/server"})
    public void shouldTransferToClientWithSingleSequence() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.single.sequence/client",
        "${net}/transfer.to.server.with.single.sequence/server"})
    public void shouldTransferToServerWithSingleSequence() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.smallint/client",
        "${net}/transfer.to.client.with.smallint/server"})
    public void shouldTransferToClientWithSmallInt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.smallint/client",
        "${net}/transfer.to.server.with.smallint/server"})
    public void shouldTransferToServerWithSmallInt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.smalllong/client",
        "${net}/transfer.to.client.with.smalllong/server"})
    public void shouldTransferToClientWithSmallLong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.smalllong/client",
        "${net}/transfer.to.server.with.smalllong/server"})
    public void shouldTransferToServerWithSmallLong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.smalluint/client",
        "${net}/transfer.to.client.with.smalluint/server"})
    public void shouldTransferToClientWithSmallUint() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.smalluint/client",
        "${net}/transfer.to.server.with.smalluint/server"})
    public void shouldTransferToServerWithSmallUint() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.smallulong/client",
        "${net}/transfer.to.client.with.smallulong/server"})
    public void shouldTransferToClientWithSmallUlong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.smallulong/client",
        "${net}/transfer.to.server.with.smallulong/server"})
    public void shouldTransferToServerWithSmallUlong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.str8utf8/client",
        "${net}/transfer.to.client.with.str8utf8/server"})
    public void shouldTransferToClientWithStr8Utf8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.str8utf8/client",
        "${net}/transfer.to.server.with.str8utf8/server"})
    public void shouldTransferToServerWithStr8Utf8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.str32utf8/client",
        "${net}/transfer.to.client.with.str32utf8/server"})
    public void shouldTransferToClientWithStr32Utf8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.str32utf8/client",
        "${net}/transfer.to.server.with.str32utf8/server"})
    public void shouldTransferToServerWithStr32Utf8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.sym8/client",
        "${net}/transfer.to.client.with.sym8/server"})
    public void shouldTransferToClientWithSym8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.sym8/client",
        "${net}/transfer.to.server.with.sym8/server"})
    public void shouldTransferToServerWithSym8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.sym32/client",
        "${net}/transfer.to.client.with.sym32/server"})
    public void shouldTransferToClientWithSym32() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.sym32/client",
        "${net}/transfer.to.server.with.sym32/server"})
    public void shouldTransferToServerWithSym32() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.timestamp/client",
        "${net}/transfer.to.client.with.timestamp/server"})
    public void shouldTransferToClientWithTimestamp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.timestamp/client",
        "${net}/transfer.to.server.with.timestamp/server"})
    public void shouldTransferToServerWithTimestamp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.true/client",
        "${net}/transfer.to.client.with.true/server"})
    public void shouldTransferToClientWithTrue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.true/client",
        "${net}/transfer.to.server.with.true/server"})
    public void shouldTransferToServerWithTrue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.ubyte/client",
        "${net}/transfer.to.client.with.ubyte/server"})
    public void shouldTransferToClientWithUbyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.ubyte/client",
        "${net}/transfer.to.server.with.ubyte/server"})
    public void shouldTransferToServerWithUbyte() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.uint/client",
        "${net}/transfer.to.client.with.uint/server"})
    public void shouldTransferToClientWithUint() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.uint/client",
        "${net}/transfer.to.server.with.uint/server"})
    public void shouldTransferToServerWithUint() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.uint0/client",
        "${net}/transfer.to.client.with.uint0/server"})
    public void shouldTransferToClientWithUint0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.uint0/client",
        "${net}/transfer.to.server.with.uint0/server"})
    public void shouldTransferToServerWithUint0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.ulong/client",
        "${net}/transfer.to.client.with.ulong/server"})
    public void shouldTransferToClientWithUlong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.ulong/client",
        "${net}/transfer.to.server.with.ulong/server"})
    public void shouldTransferToServerWithUlong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.ulong0/client",
        "${net}/transfer.to.client.with.ulong0/server"})
    public void shouldTransferToClientWithUlong0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.ulong0/client",
        "${net}/transfer.to.server.with.ulong0/server"})
    public void shouldTransferToServerWithUlong0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.ushort/client",
        "${net}/transfer.to.client.with.ushort/server"})
    public void shouldTransferToClientWithUshort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.ushort/client",
        "${net}/transfer.to.server.with.ushort/server"})
    public void shouldTransferToServerWithUshort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.vbin8/client",
        "${net}/transfer.to.client.with.vbin8/server"})
    public void shouldTransferToClientWithVbin8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.vbin8/client",
        "${net}/transfer.to.server.with.vbin8/server"})
    public void shouldTransferToServerWithVbin8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.vbin32/client",
        "${net}/transfer.to.client.with.vbin32/server"})
    public void shouldTransferToClientWithVbin32() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.vbin32/client",
        "${net}/transfer.to.server.with.vbin32/server"})
    public void shouldTransferToServerWithVbin32() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.when.first.fragment.aborted/client",
        "${net}/transfer.to.client.when.first.fragment.aborted/server"})
    public void shouldTransferToClientWhenFirstFragmentAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/attach.as.receiver.then.detach.with.error.then.flow/client",
        "${net}/attach.as.receiver.then.detach.with.error.then.flow/server"})
    public void shouldNotTriggerErrorWhenReceivingFlowAfterDetach() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.when.middle.fragment.aborted/client",
        "${net}/transfer.to.client.when.middle.fragment.aborted/server"})
    public void shouldTransferToClientWhenMiddleFragmentAborted() throws Exception
    {
        k3po.finish();
    }
    @Test
    @Specification({
        "${net}/handle.max.exceeded/client",
        "${net}/handle.max.exceeded/server"})
    public void shouldCloseConnectionWhenHandleMaxExceeded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.when.last.fragment.aborted/client",
        "${net}/transfer.to.client.when.last.fragment.aborted/server"})
    public void shouldTransferToClientWhenLastFragmentAborted() throws Exception
    {
        k3po.finish();
    }
    @Test
    @Specification({
        "${net}/reject.attach.when.handle.in.use/client",
        "${net}/reject.attach.when.handle.in.use/server"})
    public void shouldCloseConnectionWhenAttachWithHandleInUse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.when.first.fragment.aborted/client",
        "${net}/transfer.to.server.when.first.fragment.aborted/server"})
    public void shouldTransferToServerWhenFirstFragmentAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.max.message.size.exceeded/client",
        "${net}/transfer.to.server.max.message.size.exceeded/server"})
    public void shouldAttachAsSenderThenDetachWhenMaxMessageSizeExceeded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.when.middle.fragment.aborted/client",
        "${net}/transfer.to.server.when.middle.fragment.aborted/server"})
    public void shouldTransferToServerWhenMiddleFragmentAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.max.message.size.exceeded/client",
        "${net}/transfer.to.client.max.message.size.exceeded/server"})
    public void shouldAttachAsReceiverThenDetachWhenMaxMessageSizeExceeded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.when.last.fragment.aborted/client",
        "${net}/transfer.to.server.when.last.fragment.aborted/server"})
    public void shouldTransferToServerWhenLastFragmentAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.then.flow.with.echo.on.session/client",
        "${net}/transfer.to.server.then.flow.with.echo.on.session/server"})
    public void shouldTransferToServerThenFlowWithEchoOnSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.then.flow.with.echo.on.link/client",
        "${net}/transfer.to.server.then.flow.with.echo.on.link/server"})
    public void shouldTransferToServerThenFlowWithEchoOnLink() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.flow.with.inconsistent.fields/client",
        "${net}/reject.flow.with.inconsistent.fields/server"})
    public void shouldCloseConnectionWhenFlowWithInconsistentFields() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/flow.without.handle/client",
        "${net}/flow.without.handle/server"})
    public void shouldSupportFlowWithoutHandle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/flow.with.unattached.handle/client",
        "${net}/flow.with.unattached.handle/server"})
    public void shouldRejectFlowWithUnattachedHandle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.large.delivery.count/client",
        "${net}/transfer.to.server.with.large.delivery.count/server"})
    public void shouldTransferToServerWithLargeDeliveryCount() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.transfer.with.more.inconsistent.fields/client",
        "${net}/reject.transfer.with.more.inconsistent.fields/server"})
    public void shouldRejectTransferWithMoreWhenFieldsAreInconsistent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.large.next.incoming.id/client",
        "${net}/transfer.to.server.with.large.next.incoming.id/server"})
    public void shouldTransferToServerWithLargeNextIncomingId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.invalid.delivery.id/client",
        "${net}/transfer.to.server.with.invalid.delivery.id/server"})
    public void shouldTransferToServerWithInvalidDeliveryId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.server.with.application.properties/client",
        "${net}/transfer.to.server.with.application.properties/server"})
    public void shouldTransferToServerWithApplicationProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/transfer.to.client.with.application.properties/client",
        "${net}/transfer.to.client.with.application.properties/server"})
    public void shouldTransferToClientWithApplicationProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.durable.message.when.durable.not.supported/client",
        "${net}/reject.durable.message.when.durable.not.supported/server"})
    public void shouldRejectDurableMessageWhenDurableNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/server.sent.flush/client",
        "${net}/server.sent.flush/server"})
    public void shouldReceiveServerSentFlush() throws Exception
    {
        k3po.finish();
    }
}
