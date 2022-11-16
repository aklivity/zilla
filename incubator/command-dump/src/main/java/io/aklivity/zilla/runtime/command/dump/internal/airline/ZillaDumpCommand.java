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
package io.aklivity.zilla.runtime.command.dump.internal.airline;

import static java.lang.Integer.parseInt;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.LongHashSet;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;

import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;

import io.aklivity.zilla.runtime.command.ZillaCommand;
import io.aklivity.zilla.runtime.command.dump.internal.airline.labels.LabelManager;
import io.aklivity.zilla.runtime.command.dump.internal.airline.layouts.StreamsLayout;
import io.aklivity.zilla.runtime.command.dump.internal.airline.spy.RingBufferSpy;
import io.aklivity.zilla.runtime.command.dump.internal.types.Flyweight;
import io.aklivity.zilla.runtime.command.dump.internal.types.IPv6HeaderFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.PcapGlobalHeaderFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.PcapPacketHeaderFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.TcpHeaderFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.FrameFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.WindowFW;

@Command(name = "dump", description = "Dump stream content")
public final class ZillaDumpCommand extends ZillaCommand
{
    @Option(name = {"-v", "--verbose"},
        description = "Show verbose output")
    public boolean verbose;

    @Option(name = {"-b", "--bindingNames"},
        description = "Dump specific namespaces, bindings only, e.g example.http0,example.kafka0")
    public List<String> bindingNames = new ArrayList<>();

    @Option(name = {"-d", "--directory"},
        description = "Configuration directory",
        typeConverterProvider = ZillaDumpCommandPathConverterProvider.class)
    public Path directory = Paths.get(".zilla", "engine");

    @Option(name = {"-o", "--output"},
        description = "PCAP file location to dump stream")
    public URI pcapLocation;

    @Option(name = {"-a", "--affinity"},
        description = "Affinity mask")
    public long affinity = 0xffff_ffff_ffff_ffffL;

    public enum Flag
    {
        URG,
        ACK,
        PSH,
        RST,
        SYN,
        FIN
    }

    boolean continuous = true;

    private static final long MAX_PARK_NS = MILLISECONDS.toNanos(100L);
    private static final long MIN_PARK_NS = MILLISECONDS.toNanos(1L);
    private static final int MAX_YIELDS = 30;
    private static final int MAX_SPINS = 20;
    private static final Pattern STREAMS_PATTERN = Pattern.compile("data(\\d+)");
    private static final short TCP_HEADER_SIZE = 20;
    private static final short IPV6_HEADER_SIZE = 20;
    private static final int BUFFER_SLOT_CAPACITY = 64 * 1024;
    private static final byte[] PSEUDO_ETHERNET_FRAME = hexStringToByteArray("fe0000000002fe000000000186dd");
    private static final int PSEUDO_IPV6_PREFIX = 1629561669;
    private static final short PSEUDO_NEXT_HEADER_AND_HOP_LIMIT = 1536;

    private static byte[] hexStringToByteArray(String s)
    {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
        {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes)
    {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++)
        {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    private final FrameFW frameRO = new FrameFW();
    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final SignalFW signalRO = new SignalFW();
    private final ChallengeFW challengeRO = new ChallengeFW();
    private final FlushFW flushRO = new FlushFW();
    private final IPv6HeaderFW.Builder ipv6HeaderRW = new IPv6HeaderFW.Builder();
    private final TcpHeaderFW.Builder tcpHeaderRW = new TcpHeaderFW.Builder();
    private final PcapGlobalHeaderFW.Builder pcapGlobalHeaderRW = new PcapGlobalHeaderFW.Builder();
    private final PcapPacketHeaderFW.Builder pcapPacketHeaderRW = new PcapPacketHeaderFW.Builder();
    private final LongHashSet allowedBindings = new LongHashSet();

    private long nextTimestamp = Long.MAX_VALUE;
    private RingBufferSpy.SpyPosition position;
    private FileChannel channel;
    private RandomAccessFile writer;
    private MutableDirectBuffer writeBuffer;

    @Override
    public void run()
    {
        LabelManager labelManager = new LabelManager(directory);

        bindingNames.forEach(binding ->
        {
            final String[] namespaceAndBindingName = binding.split("\\.");
            final int namespaceId = labelManager.lookupLabelId(namespaceAndBindingName[0]);
            final int bindingNameId = labelManager.lookupLabelId(namespaceAndBindingName[1]);
            final long allowedBinding = (((long) namespaceId) << 32) | (bindingNameId & 0xffffffffL);
            allowedBindings.add(allowedBinding);
        });
        try
        {
            this.writer = new RandomAccessFile(pcapLocation.getPath(), "rw");
            this.channel = writer.getChannel();
            this.writeBuffer = new UnsafeBuffer(new byte[BUFFER_SLOT_CAPACITY]);
        }
        catch (IOException e)
        {
            System.out.println("Failed to open dump file: " + e.getMessage());
        }

        this.position = RingBufferSpy.SpyPosition.ZERO;

        runDumpCommand();
    }

    private void runDumpCommand()
    {
        PcapGlobalHeaderFW globalHeaderFW = pcapGlobalHeaderRW.wrap(writeBuffer, 0, BUFFER_SLOT_CAPACITY)
            .magic_number(2712847316L)
            .version_major((short) 2)
            .version_minor((short) 4)
            .thiszone(0)
            .sigfigs(0)
            .snaplen(65535)
            .link_type(1) //Ipv6 link type number
            .build();
        writeToPcapFile(globalHeaderFW);

        try (Stream<Path> files = Files.walk(directory, 3))
        {
            List<RingBufferSpy> streamBuffers = files
                .filter(this::isStreamsFile)
                .peek(this::onDiscovered)
                .map(this::createStreamBuffer)
                .collect(Collectors.toList());
            final IdleStrategy idleStrategy = new BackoffIdleStrategy(MAX_SPINS, MAX_YIELDS, MIN_PARK_NS, MAX_PARK_NS);
            final int exitWorkCount = continuous ? -1 : 0;
            int workCount;
            do
            {
                workCount = 0;
                for (RingBufferSpy streamBuffer : streamBuffers)
                {
                    workCount += streamBuffer.spy(this::handleFrame, 1);

                }
                idleStrategy.idle(workCount);
            } while (workCount != exitWorkCount);
            closeResources();
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
    }

    private RingBufferSpy createStreamBuffer(
        Path path)
    {
        final String filename = path.getFileName().toString();
        final Matcher matcher = STREAMS_PATTERN.matcher(filename);
        matcher.matches();

        StreamsLayout layout = new StreamsLayout.Builder()
            .path(path)
            .readonly(true)
            .spyAt(position)
            .build();
        return layout.streamsBuffer();
    }

    private boolean handleFrame(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        final FrameFW frame = frameRO.wrap(buffer, index, index + length);
        final long timestamp = frame.timestamp();

        if (!nextTimestamp(timestamp))
        {
            return false;
        }

        switch (msgTypeId)
        {
        case BeginFW.TYPE_ID:
            onBegin(beginRO.wrap(buffer, index, index + length));
            break;
        case DataFW.TYPE_ID:
            onData(dataRO.wrap(buffer, index, index + length));
            break;
        case EndFW.TYPE_ID:
            onEnd(endRO.wrap(buffer, index, index + length));
            break;
        case AbortFW.TYPE_ID:
            onAbort(abortRO.wrap(buffer, index, index + length));
            break;
        case WindowFW.TYPE_ID:
            onWindow(windowRO.wrap(buffer, index, index + length));
            break;
        case ResetFW.TYPE_ID:
            onReset(resetRO.wrap(buffer, index, index + length));
            break;
        case ChallengeFW.TYPE_ID:
            onChallenge(challengeRO.wrap(buffer, index, index + length));
            break;
        case SignalFW.TYPE_ID:
            onSignal(signalRO.wrap(buffer, index, index + length));
            break;
        case FlushFW.TYPE_ID:
            onFlush(flushRO.wrap(buffer, index, index + length));
            break;
        default:
            //Should not happen
            throw new IllegalStateException("Unexpected frame type: " + msgTypeId);
        }
        return true;
    }

    private boolean isStreamsFile(
        Path path)
    {
        final int depth = path.getNameCount() - directory.getNameCount();
        if (depth != 1 || !Files.isRegularFile(path))
        {
            return false;
        }

        final Matcher matcher = STREAMS_PATTERN.matcher(path.getName(path.getNameCount() - 1).toString());
        return matcher.matches() && ((1L << parseInt(matcher.group(1))) & affinity) != 0L;
    }

    private void onDiscovered(
        Path path)
    {
        if (verbose)
        {
            System.out.printf("Discovered: %s\n", path);
        }
    }

    private boolean nextTimestamp(
        final long timestamp)
    {
        if (timestamp != nextTimestamp)
        {
            nextTimestamp = Math.min(timestamp, nextTimestamp);
            return false;
        }
        else
        {
            nextTimestamp = Long.MAX_VALUE;
            return true;
        }
    }

    private void onBegin(
        BeginFW begin)
    {
        if (allowedBindings.isEmpty() || allowedBindings.contains(begin.routeId()))
        {
            final long streamId = begin.streamId();
            TcpHeaderFW tcpHeader = createTcpHeader(streamId, Flag.SYN);
            IPv6HeaderFW ipv6Header = createIpv6Header(begin.routeId(), streamId, IPV6_HEADER_SIZE);
            PcapPacketHeaderFW pcapHeader = createPcapPacketHeader(PSEUDO_ETHERNET_FRAME.length + tcpHeader.sizeof() +
                ipv6Header.sizeof(), begin.timestamp());
            writeToPcapFile(pcapHeader);
            writeToPcapFile(PSEUDO_ETHERNET_FRAME);
            writeToPcapFile(ipv6Header);
            writeToPcapFile(tcpHeader);
        }
    }

    private void onData(
        DataFW data)
    {
        if (allowedBindings.isEmpty() || allowedBindings.contains(data.routeId()))
        {
            if (data.payload() != null)
            {
                OctetsFW payload = data.payload();
                final long streamId = data.streamId();
                byte[] bytes = new byte[payload.limit() - payload.offset()];
                payload.buffer().getBytes(payload.offset(), bytes);
                IPv6HeaderFW ipv6Header = createIpv6Header(data.routeId(), streamId, (short) (IPV6_HEADER_SIZE +
                    bytes.length));
                TcpHeaderFW tcpHeader = createTcpHeader(streamId, Flag.PSH);
                PcapPacketHeaderFW pcapHeader = createPcapPacketHeader(PSEUDO_ETHERNET_FRAME.length + ipv6Header.sizeof() +
                    tcpHeader.sizeof() + bytes.length, data.timestamp());
                writeToPcapFile(pcapHeader);
                writeToPcapFile(PSEUDO_ETHERNET_FRAME);
                writeToPcapFile(ipv6Header);
                writeToPcapFile(tcpHeader);
                writeToPcapFile(bytes);
            }
        }
    }

    private void onEnd(
        EndFW end)
    {
        if (allowedBindings.isEmpty() || allowedBindings.contains(end.routeId()))
        {
            final long streamId = end.streamId();
            IPv6HeaderFW ipv6Header = createIpv6Header(end.routeId(), streamId, IPV6_HEADER_SIZE);
            TcpHeaderFW tcpHeader = createTcpHeader(streamId, Flag.FIN);
            PcapPacketHeaderFW pcapHeader = createPcapPacketHeader(PSEUDO_ETHERNET_FRAME.length + tcpHeader.sizeof() +
                ipv6Header.sizeof(), end.timestamp());
            writeToPcapFile(pcapHeader);
            writeToPcapFile(PSEUDO_ETHERNET_FRAME);
            writeToPcapFile(ipv6Header);
            writeToPcapFile(tcpHeader);
        }
    }

    private void onAbort(
        AbortFW abort)
    {
        final long streamId = abort.streamId();
        IPv6HeaderFW ipv6Header = createIpv6Header(abort.routeId(), streamId, IPV6_HEADER_SIZE);
        TcpHeaderFW tcpHeader = createTcpHeader(streamId, Flag.RST);
        PcapPacketHeaderFW pcapHeader = createPcapPacketHeader(PSEUDO_ETHERNET_FRAME.length + tcpHeader.sizeof() +
            ipv6Header.sizeof(), abort.timestamp());
        writeToPcapFile(pcapHeader);
        writeToPcapFile(PSEUDO_ETHERNET_FRAME);
        writeToPcapFile(ipv6Header);
        writeToPcapFile(tcpHeader);
    }

    private void onReset(
        ResetFW reset)
    {
        if (allowedBindings.isEmpty() || allowedBindings.contains(reset.routeId()))
        {
            final long streamId = reset.streamId();
            IPv6HeaderFW ipv6Header = createIpv6Header(reset.routeId(), streamId, IPV6_HEADER_SIZE);
            TcpHeaderFW tcpHeader = createTcpHeader(streamId, Flag.RST);
            PcapPacketHeaderFW pcapHeader = createPcapPacketHeader(PSEUDO_ETHERNET_FRAME.length + tcpHeader.sizeof() +
                ipv6Header.sizeof(), reset.timestamp());
            writeToPcapFile(pcapHeader);
            writeToPcapFile(PSEUDO_ETHERNET_FRAME);
            writeToPcapFile(ipv6Header);
            writeToPcapFile(tcpHeader);
        }
    }

    private void onWindow(
        WindowFW window)
    {
        final long streamId = window.streamId();
        IPv6HeaderFW ipv6Header = createIpv6Header(window.routeId(), streamId, IPV6_HEADER_SIZE);
        TcpHeaderFW tcpHeader = createTcpHeader(streamId, Flag.ACK);
        PcapPacketHeaderFW pcapHeader = createPcapPacketHeader(PSEUDO_ETHERNET_FRAME.length + tcpHeader.sizeof() +
            ipv6Header.sizeof(), window.timestamp());
        writeToPcapFile(pcapHeader);
        writeToPcapFile(PSEUDO_ETHERNET_FRAME);
        writeToPcapFile(ipv6Header);
        writeToPcapFile(tcpHeader);
    }

    private void onSignal(
        SignalFW signal)
    {
        final long streamId = signal.streamId();
        IPv6HeaderFW ipv6Header = createIpv6Header(signal.routeId(), streamId, IPV6_HEADER_SIZE);
        TcpHeaderFW tcpHeader = createTcpHeader(streamId, Flag.PSH);
        PcapPacketHeaderFW pcapHeader = createPcapPacketHeader(PSEUDO_ETHERNET_FRAME.length + tcpHeader.sizeof() +
            ipv6Header.sizeof(), signal.timestamp());
        writeToPcapFile(pcapHeader);
        writeToPcapFile(PSEUDO_ETHERNET_FRAME);
        writeToPcapFile(ipv6Header);
        writeToPcapFile(tcpHeader);
    }

    private void onChallenge(
        ChallengeFW challenge)
    {
        final long streamId = challenge.streamId();
        IPv6HeaderFW ipv6Header = createIpv6Header(challenge.routeId(), streamId, IPV6_HEADER_SIZE);
        TcpHeaderFW tcpHeader = createTcpHeader(streamId, Flag.PSH);
        PcapPacketHeaderFW pcapHeader = createPcapPacketHeader(PSEUDO_ETHERNET_FRAME.length + tcpHeader.sizeof() +
            ipv6Header.sizeof(), challenge.timestamp());
        writeToPcapFile(pcapHeader);
        writeToPcapFile(PSEUDO_ETHERNET_FRAME);
        writeToPcapFile(ipv6Header);
        writeToPcapFile(tcpHeader);
    }

    private void onFlush(
        FlushFW flush)
    {
        if (allowedBindings.isEmpty() || allowedBindings.contains(flush.routeId()))
        {
            final long streamId = flush.streamId();
            IPv6HeaderFW ipv6Header = createIpv6Header(flush.routeId(), streamId, IPV6_HEADER_SIZE);
            TcpHeaderFW tcpHeader = createTcpHeader(streamId, Flag.RST);
            PcapPacketHeaderFW pcapHeader = createPcapPacketHeader(PSEUDO_ETHERNET_FRAME.length + tcpHeader.sizeof() +
                ipv6Header.sizeof(), flush.timestamp());
            writeToPcapFile(pcapHeader);
            writeToPcapFile(PSEUDO_ETHERNET_FRAME);
            writeToPcapFile(ipv6Header);
            writeToPcapFile(tcpHeader);
        }
    }

    private void writeToPcapFile(
        Flyweight flyweight)
    {
        try
        {
            byte[] bytes = new byte[flyweight.sizeof()];
            flyweight.buffer().getBytes(flyweight.offset(), bytes);
            channel.write(ByteBuffer.wrap(bytes));
            channel.force(true);
        }
        catch (IOException e)
        {
            System.out.println("Could not write to file. Reason: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void writeToPcapFile(
        byte[] bytes)
    {
        try
        {
            channel.write(ByteBuffer.wrap(bytes));
            channel.force(true);
        }
        catch (IOException e)
        {
            System.out.println("Could not write to file. Reason: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private PcapPacketHeaderFW createPcapPacketHeader(
        long length,
        long timestamp)
    {
        return pcapPacketHeaderRW.wrap(writeBuffer, TCP_HEADER_SIZE + IPV6_HEADER_SIZE, writeBuffer.capacity())
            .ts_sec(timestamp / 1000000)
            .ts_usec(0)
            .incl_len(length)
            .orig_len(length)
            .build();
    }

    private IPv6HeaderFW createIpv6Header(
        long routeId,
        long streamId,
        short payloadLength)
    {
        return ipv6HeaderRW.wrap(writeBuffer, TCP_HEADER_SIZE, writeBuffer.capacity())
            .prefix(PSEUDO_IPV6_PREFIX)
            .payload_length(payloadLength)
            .next_header_and_hop_limit(PSEUDO_NEXT_HEADER_AND_HOP_LIMIT)
            .src_addr_part1(routeId)
            .src_addr_part2(streamId)
            .dst_addr_part1(routeId)
            .dst_addr_part2(streamId)
            .build();
    }

    private TcpHeaderFW createTcpHeader(
        long streamId,
        Flag flag)
    {
        short port = getPort(streamId);
        short other = getOtherFields(flag);
        return tcpHeaderRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .src_port(port)
            .dst_port(port)
            .sequence_number(1000)
            .acknowledgment_number(0)
            .other_fields(other)
            .window((short) 1024)
            .checksum((short) 0)
            .urgent_pointer((short) 0)
            .build();
    }

    private short getOtherFields(
        Flag flag)
    {
        byte flags = 0;
        if (flag == Flag.FIN)
        {
            flags = (byte) 1;
        }
        if (flag == Flag.SYN)
        {
            flags = (byte) (flags | 2);
        }
        if (flag == Flag.RST)
        {
            flags = (byte) (flags | 4);
        }
        if (flag == Flag.PSH)
        {
            flags = (byte) (flags | 8);
        }
        if (flag == Flag.ACK)
        {
            flags = (byte) (flags | 16);
        }
        if (flag == Flag.URG)
        {
            flags = (byte) (flags | 32);
        }
        byte dataOffsetAndReserved = 80; //20 bytes as header + 3 bit of reserved 0
        byte[] bytes = new byte[] {dataOffsetAndReserved, flags};
        ByteBuffer buffer = ByteBuffer.allocate(2).put(bytes);
        return buffer.getShort(0);
    }

    private short getPort(
        long streamId)
    {
        byte[] streamIdBytes = longToBytes(streamId);
        return byteArrayToShort(Arrays.copyOfRange(streamIdBytes, streamIdBytes.length - 2, streamIdBytes.length));
    }

    private byte[] longToBytes(
        long x)
    {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }

    private short byteArrayToShort(
        byte[] array)
    {
        assert array.length == 2;
        return (short) ((array[0] << 8) | (array[1] & 0xFF));
    }

    private void closeResources()
    {
        try
        {
            channel.close();
            writer.close();
        }
        catch (IOException e)
        {
            System.out.println("Could not close file. Reason: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

}
