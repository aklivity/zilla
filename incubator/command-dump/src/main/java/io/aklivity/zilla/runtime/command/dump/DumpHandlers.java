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
package io.aklivity.zilla.runtime.command.dump;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.agrona.DirectBuffer;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapDumper;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.packet.EthernetPacket;
import org.pcap4j.packet.IpV6Packet;
import org.pcap4j.packet.IpV6SimpleFlowLabel;
import org.pcap4j.packet.IpV6SimpleTrafficClass;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.TcpPacket;
import org.pcap4j.packet.UnknownPacket;
import org.pcap4j.packet.namednumber.EtherType;
import org.pcap4j.packet.namednumber.IpNumber;
import org.pcap4j.packet.namednumber.IpVersion;
import org.pcap4j.packet.namednumber.TcpPort;
import org.pcap4j.util.MacAddress;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;

import io.aklivity.zilla.runtime.command.dump.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.WindowFW;

public class DumpHandlers implements Handlers
{
    private final PcapDumper dumper;

    public DumpHandlers(PcapDumper dumper)
    {
        this.dumper = dumper;
    }

    @Override
    public void onBegin(BeginFW begin)
    {
        final long routeId = begin.routeId();
        final long streamId = begin.streamId();
        final int bindingId = (int) (routeId >> 0) & 0xffff_ffff;
        Inet6Address address = createAddress(bindingId, streamId);
        writePacketsToPcap(createPseudoEthernetPacket(createPseudoTcpPacketBuilder(address).syn(true), address));
    }

    @Override
    public void onData(DataFW data)
    {
        final long routeId = data.routeId();
        final long streamId = data.streamId();
        final int bindingId = (int) (routeId >> 0) & 0xffff_ffff;
        byte[] bytes = new byte[data.offset() + data.limit()];
        if (data.payload() != null)
        {
            DirectBuffer buffer = data.payload().buffer();
            buffer.getBytes(0, bytes, data.offset(), data.limit());
            Inet6Address address = createAddress(bindingId, streamId);
            writePacketsToPcap(
                createPseudoEthernetPacket(createPseudoTcpPacketBuilderWithData(bytes, address).psh(true), address));
        }
    }

    @Override
    public void onEnd(EndFW end)
    {
        final long routeId = end.routeId();
        final long streamId = end.streamId();
        final int bindingId = (int) (routeId >> 0) & 0xffff_ffff;
        Inet6Address address = createAddress(bindingId, streamId);
        writePacketsToPcap(createPseudoEthernetPacket(createPseudoTcpPacketBuilder(address).fin(true), address));
    }

    @Override
    public void onAbort(AbortFW abort)
    {
        final long routeId = abort.routeId();
        final long streamId = abort.streamId();
        final int bindingId = (int) (routeId >> 0) & 0xffff_ffff;
        Inet6Address address = createAddress(bindingId, streamId);
        writePacketsToPcap(createPseudoEthernetPacket(createPseudoTcpPacketBuilder(address).rst(true), address));
    }

    @Override
    public void onReset(ResetFW reset)
    {
        final long routeId = reset.routeId();
        final long streamId = reset.streamId();
        final int bindingId = (int) (routeId >> 0) & 0xffff_ffff;
        Inet6Address address = createAddress(bindingId, streamId);
        writePacketsToPcap(createPseudoEthernetPacket(createPseudoTcpPacketBuilder(address).rst(true), address));
    }

    @Override
    public void onWindow(WindowFW window)
    {
        final long routeId = window.routeId();
        final long streamId = window.streamId();
        final int bindingId = (int) (routeId >> 0) & 0xffff_ffff;
        Inet6Address address = createAddress(bindingId, streamId);
        writePacketsToPcap(createPseudoEthernetPacket(createPseudoTcpPacketBuilder(address).ack(true), address));
    }

    @Override
    public void onSignal(SignalFW signal)
    {
        final long routeId = signal.routeId();
        final long streamId = signal.streamId();
        final int bindingId = (int) (routeId >> 0) & 0xffff_ffff;
        Inet6Address address = createAddress(bindingId, streamId);
        writePacketsToPcap(createPseudoEthernetPacket(createPseudoTcpPacketBuilder(address).syn(true), address));
    }

    @Override
    public void onChallenge(ChallengeFW challenge)
    {
        final long routeId = challenge.routeId();
        final long streamId = challenge.streamId();
        final int bindingId = (int) (routeId >> 0) & 0xffff_ffff;
        Inet6Address address = createAddress(bindingId, streamId);
        writePacketsToPcap(createPseudoEthernetPacket(createPseudoTcpPacketBuilder(address).syn(true), address));
    }

    @Override
    public void onFlush(FlushFW flush)
    {
        final long routeId = flush.routeId();
        final long streamId = flush.streamId();
        final int bindingId = (int) (routeId >> 0) & 0xffff_ffff;
        Inet6Address address = createAddress(bindingId, streamId);
        writePacketsToPcap(createPseudoEthernetPacket(createPseudoTcpPacketBuilder(address).rst(true), address));
    }

    private Inet6Address createAddress(long bindingId, long streamId)
    {
        InetAddress address;
        try
        {
            address = Inet6Address.getByAddress(
                Bytes.concat(Longs.toByteArray(bindingId), Longs.toByteArray(streamId)));
        }
        catch (UnknownHostException e)
        {
            throw new RuntimeException(e);
        }
        return (Inet6Address) address;
    }

    private TcpPacket.Builder createPseudoTcpPacketBuilder(Inet6Address address)
    {
        TcpPacket.Builder tcpBuilder = new TcpPacket.Builder();
        return tcpBuilder
            .srcAddr(address)
            .srcPort(TcpPort.HELLO_PORT)
            .dstAddr(address)
            .dstPort(TcpPort.HELLO_PORT)
            .window((short) 10)
            .correctLengthAtBuild(true)
            .correctChecksumAtBuild(true);
    }

    private EthernetPacket createPseudoEthernetPacket(TcpPacket.Builder tcpBuilder, Inet6Address address)
    {
        IpV6Packet.Builder ipv6Builder = new IpV6Packet.Builder()
            .srcAddr(address)
            .dstAddr(address)
            .correctLengthAtBuild(true)
            .version(IpVersion.IPV6)
            .trafficClass(IpV6SimpleTrafficClass.newInstance((byte) 0x12))
            .flowLabel(IpV6SimpleFlowLabel.newInstance(0x12345))
            .nextHeader(IpNumber.TCP)
            .payloadBuilder(tcpBuilder);

        EthernetPacket.Builder ethernetBuilder = new EthernetPacket.Builder();
        ethernetBuilder.dstAddr(MacAddress.getByName("fe:00:00:00:00:02"))
            .srcAddr(MacAddress.getByName("fe:00:00:00:00:01")).type(EtherType.IPV6)
            .payloadBuilder(ipv6Builder).paddingAtBuild(true);

        return ethernetBuilder.build();
    }

    private TcpPacket.Builder createPseudoTcpPacketBuilderWithData(byte[] data, Inet6Address address)
    {
        UnknownPacket packet = UnknownPacket.newPacket(data, 0, data.length);
        return createPseudoTcpPacketBuilder(address)
            .payloadBuilder(packet.getBuilder());
    }

    public void writePacketsToPcap(Packet packet)
    {
        try
        {
            dumper.dump(packet);
            dumper.flush();
        }
        catch (NotOpenException | PcapNativeException e)
        {
            throw new RuntimeException(e);
        }
    }
}
