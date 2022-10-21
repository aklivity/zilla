package io.aklivity.zilla.runtime.command.dump;

import io.aklivity.zilla.runtime.command.common.Handlers;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.WindowFW;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.agrona.DirectBuffer;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapDumper;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.EthernetPacket;
import org.pcap4j.packet.IpV6Packet;
import org.pcap4j.packet.IpV6SimpleFlowLabel;
import org.pcap4j.packet.IpV6SimpleTrafficClass;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.TcpPacket;
import org.pcap4j.packet.UnknownPacket;
import org.pcap4j.packet.namednumber.DataLinkType;
import org.pcap4j.packet.namednumber.EtherType;
import org.pcap4j.packet.namednumber.IpNumber;
import org.pcap4j.packet.namednumber.IpVersion;
import org.pcap4j.packet.namednumber.TcpPort;
import org.pcap4j.util.MacAddress;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;

public class DumpHandlers implements Handlers
{
    List<Packet> packetList = new ArrayList<>();
    private int count = 0;

    @Override
    public void onBegin(BeginFW begin)
    {
        final long routeId = begin.routeId();
        final long streamId = begin.streamId();
        final int bindingId = (int) (routeId >> 0) & 0xffff_ffff;
        Inet6Address address = createAddress(bindingId, streamId);
        packetList.add(createPseudoEthernetPacket(createPseudoTcpPacketBuilder(address).syn(true), address));
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
            packetList.add(createPseudoEthernetPacket(createPseudoTcpPacketBuilderWithData(bytes, address).psh(true), address));
        }
    }

    @Override
    public void onEnd(EndFW end)
    {
        final long routeId = end.routeId();
        final long streamId = end.streamId();
        final int bindingId = (int) (routeId >> 0) & 0xffff_ffff;
        Inet6Address address = createAddress(bindingId, streamId);
        packetList.add(createPseudoEthernetPacket(createPseudoTcpPacketBuilder(address).fin(true), address));
    }

    @Override
    public void onAbort(AbortFW abort)
    {
        final long routeId = abort.routeId();
        final long streamId = abort.streamId();
        final int bindingId = (int) (routeId >> 0) & 0xffff_ffff;
        Inet6Address address = createAddress(bindingId, streamId);
        packetList.add(createPseudoEthernetPacket(createPseudoTcpPacketBuilder(address).rst(true), address));
    }

    @Override
    public void onReset(ResetFW reset)
    {
        final long routeId = reset.routeId();
        final long streamId = reset.streamId();
        final int bindingId = (int) (routeId >> 0) & 0xffff_ffff;
        Inet6Address address = createAddress(bindingId, streamId);
        packetList.add(createPseudoEthernetPacket(createPseudoTcpPacketBuilder(address).rst(true), address));
    }

    @Override
    public void onWindow(WindowFW window)
    {
        final long routeId = window.routeId();
        final long streamId = window.streamId();
        final int bindingId = (int) (routeId >> 0) & 0xffff_ffff;
        Inet6Address address = createAddress(bindingId, streamId);
        packetList.add(createPseudoEthernetPacket(createPseudoTcpPacketBuilder(address).ack(true), address));
    }

    @Override
    public void onSignal(SignalFW signal)
    {
        final long routeId = signal.routeId();
        final long streamId = signal.streamId();
        final int bindingId = (int) (routeId >> 0) & 0xffff_ffff;
        Inet6Address address = createAddress(bindingId, streamId);
        packetList.add(createPseudoEthernetPacket(createPseudoTcpPacketBuilder(address).syn(true), address));
    }

    @Override
    public void onChallenge(ChallengeFW challenge)
    {
        final long routeId = challenge.routeId();
        final long streamId = challenge.streamId();
        final int bindingId = (int) (routeId >> 0) & 0xffff_ffff;
        Inet6Address address = createAddress(bindingId, streamId);
        packetList.add(createPseudoEthernetPacket(createPseudoTcpPacketBuilder(address).syn(true), address));
    }

    @Override
    public void onFlush(FlushFW flush)
    {
        final long routeId = flush.routeId();
        final long streamId = flush.streamId();
        final int bindingId = (int) (routeId >> 0) & 0xffff_ffff;
        Inet6Address address = createAddress(bindingId, streamId);
        packetList.add(createPseudoEthernetPacket(createPseudoTcpPacketBuilder(address).rst(true), address));
    }

    private Inet6Address createAddress(long bindingId, long streamId) {
        InetAddress address;
        try
        {
            address = Inet6Address.getByAddress(
                Bytes.concat(Longs.toByteArray(bindingId), Longs.toByteArray(streamId)));
        } catch (UnknownHostException e)
        {
            throw new RuntimeException(e);
        }
        return (Inet6Address) address;
    }

    private TcpPacket.Builder createPseudoTcpPacketBuilder(Inet6Address address) {
        TcpPacket.Builder tcpBuilder = new TcpPacket.Builder();
        return tcpBuilder
            .srcAddr(address)
            .srcPort(TcpPort.HELLO_PORT)
            .dstAddr(address)
            .dstPort(TcpPort.HELLO_PORT)
            .window((short) 10)
            .correctLengthAtBuild(true)
            .correctChecksumAtBuild(true)
            .sequenceNumber(100000 + (++count * 50));
    }

    private EthernetPacket createPseudoEthernetPacket(TcpPacket.Builder tcpBuilder, Inet6Address address) {
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

    private TcpPacket.Builder createPseudoTcpPacketBuilderWithData(byte[] data, Inet6Address address) {
        UnknownPacket packet = UnknownPacket.newPacket(data, 0, data.length);
        return createPseudoTcpPacketBuilder(address)
            .payloadBuilder(packet.getBuilder());
    }

    public void writePacketsToPcap()
    {
        try
        {
            PcapHandle phb = Pcaps.openDead(DataLinkType.EN10MB, 65536);
            PcapDumper dumper = phb.dumpOpen("tmp.pcap");

            for (Packet p : packetList)
            {
                dumper.dump(p);
            }
            dumper.close();
            phb.close();
        } catch (NotOpenException | PcapNativeException e)
        {
            throw new RuntimeException(e);
        }
    }

}
