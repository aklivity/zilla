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
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
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
import io.aklivity.zilla.runtime.command.dump.internal.types.IPv6HeaderFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.PcapGlobalHeaderFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.PcapPacketHeaderFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.TcpFlag;
import io.aklivity.zilla.runtime.command.dump.internal.types.TcpHeaderFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.FrameFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.binding.function.MessagePredicate;

@Command(name = "dump", description = "Dump stream content")
public final class ZillaDumpCommand extends ZillaCommand
{
    private static final long MAX_PARK_NS = MILLISECONDS.toNanos(100L);
    private static final long MIN_PARK_NS = MILLISECONDS.toNanos(1L);
    private static final int MAX_YIELDS = 30;
    private static final int MAX_SPINS = 20;
    private static final int BUFFER_SLOT_CAPACITY = 64 * 1024;

    private static final long PCAP_GLOBAL_MAGIC = 2712847316L;
    private static final short PCAP_GLOBAL_VERSION_MAJOR = 2;
    private static final short PCAP_GLOBAL_VERSION_MINOR = 4;

    private static final int PCAP_GLOBAL_SIZE = 24;

    private static final int PCAP_LINK_TYPE_IPV6 = 1;

    private static final int PCAP_HEADER_OFFSET = 0;
    private static final int PCAP_HEADER_SIZE = 16;
    private static final int PCAP_HEADER_LIMIT = PCAP_HEADER_OFFSET + PCAP_HEADER_SIZE;

    private static final int ETHER_HEADER_OFFSET = PCAP_HEADER_LIMIT;
    private static final int ETHER_HEADER_SIZE = 14;
    private static final int ETHER_HEADER_LIMIT = ETHER_HEADER_OFFSET + ETHER_HEADER_SIZE;

    private static final int IPV6_HEADER_OFFSET = ETHER_HEADER_LIMIT;
    private static final int IPV6_HEADER_SIZE = 40;
    private static final int IPV6_HEADER_LIMIT = IPV6_HEADER_OFFSET + IPV6_HEADER_SIZE;

    private static final int TCP_HEADER_OFFSET = IPV6_HEADER_LIMIT;
    private static final int TCP_HEADER_SIZE = 20;
    private static final int TCP_HEADER_LIMIT = TCP_HEADER_OFFSET + TCP_HEADER_SIZE;

    private static final byte[] PSEUDO_ETHERNET_FRAME = BitUtil.fromHex("fe0000000002fe000000000186dd");
    private static final int PSEUDO_IPV6_PREFIX = 1629561669;
    private static final short PSEUDO_NEXT_HEADER_AND_HOP_LIMIT = 1536;

    private static final Pattern STREAMS_PATTERN = Pattern.compile("data(\\d+)");

    @Option(name = {"-v", "--verbose"},
        description = "Show verbose output")
    public boolean verbose;

    @Option(name = {"-b", "--bindings"},
        description = "Dump specific namespaced bindings only, e.g example.http0,example.kafka0")
    public List<String> bindings = new ArrayList<>();

    @Option(name = {"-o", "--output"},
        description = "PCAP output filename",
        typeConverterProvider = ZillaDumpCommandPathConverterProvider.class)
    public Path output;

    @Option(name = {"-a", "--affinity"},
        description = "Affinity mask")
    public long affinity = 0xffff_ffff_ffff_ffffL;

    @Option(name = {"-d", "--directory"},
        hidden = true,
        description = "Configuration directory",
        typeConverterProvider = ZillaDumpCommandPathConverterProvider.class)
    public Path directory = Paths.get(".zilla", "engine");

    boolean continuous = true;

    private final FrameFW frameRO = new FrameFW();
    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final FlushFW flushRO = new FlushFW();
    private final IPv6HeaderFW.Builder ipv6HeaderRW = new IPv6HeaderFW.Builder();
    private final TcpHeaderFW.Builder tcpHeaderRW = new TcpHeaderFW.Builder();
    private final PcapGlobalHeaderFW.Builder pcapGlobalHeaderRW = new PcapGlobalHeaderFW.Builder();
    private final PcapPacketHeaderFW.Builder pcapPacketHeaderRW = new PcapPacketHeaderFW.Builder();

    private LongPredicate allowedBinding;
    private long nextTimestamp = Long.MAX_VALUE;
    private RingBufferSpy.SpyPosition position;
    private FileChannel channel;
    private RandomAccessFile writer;
    private MutableDirectBuffer writeBuffer;

    @Override
    public void run()
    {
        LabelManager labelManager = new LabelManager(directory);

        final LongHashSet allowedBindings = new LongHashSet();
        bindings.forEach(binding ->
        {
            final String[] namespaceAndBindingName = binding.split("\\.");
            final int namespaceId = labelManager.lookupLabelId(namespaceAndBindingName[0]);
            final int bindingNameId = labelManager.lookupLabelId(namespaceAndBindingName[1]);
            final long allowedBinding = (((long) namespaceId) << 32) | (bindingNameId & 0xffffffffL);
            allowedBindings.add(allowedBinding);
        });
        try
        {
            this.writer = new RandomAccessFile(output.toFile(), "rw");
            this.channel = writer.getChannel();
            this.writeBuffer = new UnsafeBuffer(ByteBuffer.allocate(BUFFER_SLOT_CAPACITY));
        }
        catch (IOException e)
        {
            System.out.println("Failed to open dump file: " + e.getMessage());
        }

        this.allowedBinding = allowedBindings.isEmpty() ? b -> true : allowedBindings::contains;
        this.position = RingBufferSpy.SpyPosition.ZERO;

        runDumpCommand();
    }

    private void runDumpCommand()
    {
        final MutableDirectBuffer buffer = writeBuffer;
        encodePcapGlobal(buffer);
        writeToPcapFile(buffer, 0, PCAP_GLOBAL_SIZE);

        try (Stream<Path> files = Files.walk(directory, 3))
        {
            final RingBufferSpy[] streamBuffers = files
                .filter(this::isStreamsFile)
                .peek(this::onDiscovered)
                .map(this::createStreamBuffer)
                .collect(Collectors.toList())
                .toArray(RingBufferSpy[]::new);
            final int streamBufferCount = streamBuffers.length;

            final IdleStrategy idleStrategy = new BackoffIdleStrategy(MAX_SPINS, MAX_YIELDS, MIN_PARK_NS, MAX_PARK_NS);
            final MessagePredicate spyHandler = this::handleFrame;

            final int exitWorkCount = continuous ? -1 : 0;
            int workCount;
            do
            {
                workCount = 0;
                for (int i = 0; i < streamBufferCount; i++)
                {
                    final RingBufferSpy streamBuffer = streamBuffers[i];
                    workCount += streamBuffer.spy(spyHandler, 1);
                }
                idleStrategy.idle(workCount);
            } while (workCount != exitWorkCount);
        }
        catch (IOException ex)
        {
            rethrowUnchecked(ex);
        }
        finally
        {
            closeResources();
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
        case FlushFW.TYPE_ID:
            onFlush(flushRO.wrap(buffer, index, index + length));
            break;
        default:
            break;
        }
        return true;
    }

    private void onBegin(
        BeginFW begin)
    {
        final long bindingId = begin.routeId();

        if (allowedBinding.test(bindingId))
        {
            final MutableDirectBuffer buffer = writeBuffer;
            final long streamId = begin.streamId();
            final long timestamp = begin.timestamp();
            final long sequence = begin.sequence();

            encodePcapHeader(buffer, ETHER_HEADER_SIZE + IPV6_HEADER_SIZE + TCP_HEADER_SIZE, timestamp);
            encodeEtherHeader(buffer);
            encodeIpv6Header(buffer, bindingId, streamId ^ 1L, streamId, TCP_HEADER_SIZE);
            encodeTcpHeader(buffer, streamId ^ 1L, streamId, sequence - 1L, 0L, TcpFlag.SYN);

            writeToPcapFile(buffer, 0, TCP_HEADER_LIMIT);
        }
    }

    private void onData(
        DataFW data)
    {
        final long bindingId = data.routeId();

        if (allowedBinding.test(bindingId))
        {
            final OctetsFW payload = data.payload();

            if (payload != null)
            {
                final MutableDirectBuffer buffer = writeBuffer;
                final long streamId = data.streamId();
                final long timestamp = data.timestamp();
                final long sequence = data.sequence();
                final int sizeof = payload.sizeof();

                encodePcapHeader(buffer, ETHER_HEADER_SIZE + IPV6_HEADER_SIZE + TCP_HEADER_SIZE + sizeof, timestamp);
                encodeEtherHeader(buffer);
                encodeIpv6Header(buffer, bindingId, streamId ^ 1L, streamId, TCP_HEADER_SIZE + sizeof);
                encodeTcpHeader(buffer, streamId ^ 1L, streamId, sequence, 0L, TcpFlag.PSH);

                writeToPcapFile(buffer, 0, TCP_HEADER_LIMIT);
                writeToPcapFile(payload.buffer(), payload.offset(), sizeof);
            }
        }
    }

    private void onEnd(
        EndFW end)
    {
        final long bindingId = end.routeId();

        if (allowedBinding.test(bindingId))
        {
            final MutableDirectBuffer buffer = writeBuffer;
            final long streamId = end.streamId();
            final long timestamp = end.timestamp();
            final long sequence = end.sequence();

            encodePcapHeader(buffer, ETHER_HEADER_SIZE + IPV6_HEADER_SIZE + TCP_HEADER_SIZE, timestamp);
            encodeEtherHeader(buffer);
            encodeIpv6Header(buffer, bindingId, streamId ^ 1L, streamId, TCP_HEADER_SIZE);
            encodeTcpHeader(buffer, streamId ^ 1L, streamId, sequence, 0L, TcpFlag.FIN);

            writeToPcapFile(buffer, 0, TCP_HEADER_LIMIT);
        }
    }

    private void onAbort(
        AbortFW abort)
    {
        final long bindingId = abort.routeId();

        if (allowedBinding.test(bindingId))
        {
            final MutableDirectBuffer buffer = writeBuffer;
            final long streamId = abort.streamId();
            final long timestamp = abort.timestamp();
            final long sequence = abort.sequence();

            encodePcapHeader(buffer, ETHER_HEADER_SIZE + IPV6_HEADER_SIZE + TCP_HEADER_SIZE, timestamp);
            encodeEtherHeader(buffer);
            encodeIpv6Header(buffer, bindingId, streamId ^ 1L, streamId, TCP_HEADER_SIZE);
            encodeTcpHeader(buffer, streamId ^ 1L, streamId, sequence, 0L, TcpFlag.RST);

            writeToPcapFile(buffer, 0, TCP_HEADER_LIMIT);
        }
    }

    private void onFlush(
        FlushFW flush)
    {
        final long bindingId = flush.routeId();

        if (allowedBinding.test(bindingId))
        {
            final MutableDirectBuffer buffer = writeBuffer;
            final long streamId = flush.streamId();
            final long timestamp = flush.timestamp();
            final long sequence = flush.sequence();

            encodePcapHeader(buffer, ETHER_HEADER_SIZE + IPV6_HEADER_SIZE + TCP_HEADER_SIZE, timestamp);
            encodeEtherHeader(buffer);
            encodeIpv6Header(buffer, bindingId, streamId ^ 1L, streamId, TCP_HEADER_SIZE);
            encodeTcpHeader(buffer, streamId ^ 1L, streamId, sequence, 0L, TcpFlag.PSH);

            writeToPcapFile(buffer, 0, TCP_HEADER_LIMIT);
        }
    }

    private void onReset(
        ResetFW reset)
    {
        final long bindingId = reset.routeId();

        if (allowedBinding.test(bindingId))
        {
            final MutableDirectBuffer buffer = writeBuffer;
            final long streamId = reset.streamId();
            final long timestamp = reset.timestamp();
            final long sequence = reset.sequence();

            encodePcapHeader(buffer, ETHER_HEADER_SIZE + IPV6_HEADER_SIZE + TCP_HEADER_SIZE, timestamp);
            encodeEtherHeader(buffer);
            encodeIpv6Header(buffer, bindingId, streamId, streamId ^ 1L, TCP_HEADER_SIZE);
            encodeTcpHeader(buffer, streamId, streamId ^ 1L, sequence, 0L, TcpFlag.RST);

            writeToPcapFile(buffer, 0, TCP_HEADER_LIMIT);
        }
    }

    private void onWindow(
        WindowFW window)
    {
        final long bindingId = window.routeId();

        if (allowedBinding.test(bindingId))
        {
            final MutableDirectBuffer buffer = writeBuffer;
            final long streamId = window.streamId();
            final long timestamp = window.timestamp();
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();

            encodePcapHeader(buffer, ETHER_HEADER_SIZE + IPV6_HEADER_SIZE + TCP_HEADER_SIZE, timestamp);
            encodeEtherHeader(buffer);
            encodeIpv6Header(buffer, bindingId, streamId, streamId ^ 1L, TCP_HEADER_SIZE);
            encodeTcpHeader(buffer, streamId, streamId ^ 1L, sequence, acknowledge, TcpFlag.ACK);

            writeToPcapFile(buffer, 0, TCP_HEADER_LIMIT);
        }
    }

    private void encodePcapGlobal(
        MutableDirectBuffer buffer)
    {
        pcapGlobalHeaderRW.wrap(buffer, 0, buffer.capacity())
            .magic_number(PCAP_GLOBAL_MAGIC)
            .version_major(PCAP_GLOBAL_VERSION_MAJOR)
            .version_minor(PCAP_GLOBAL_VERSION_MINOR)
            .thiszone(0)
            .sigfigs(0)
            .snaplen(65535)
            .link_type(PCAP_LINK_TYPE_IPV6)
            .build();
    }

    private void encodePcapHeader(
        MutableDirectBuffer buffer,
        long length,
        long timestamp)
    {
        pcapPacketHeaderRW.wrap(buffer, PCAP_HEADER_OFFSET, buffer.capacity())
            .ts_sec(timestamp / 1000000)
            .ts_usec(0)
            .incl_len(length)
            .orig_len(length)
            .build();
    }

    private void encodeEtherHeader(
        MutableDirectBuffer buffer)
    {
        writeBuffer.putBytes(ETHER_HEADER_OFFSET, PSEUDO_ETHERNET_FRAME);
    }

    private void encodeIpv6Header(
        MutableDirectBuffer buffer,
        long routeId,
        long sourceId,
        long targetId,
        int payloadLength)
    {
        ipv6HeaderRW.wrap(buffer, IPV6_HEADER_OFFSET, buffer.capacity())
            .prefix(PSEUDO_IPV6_PREFIX)
            .payload_length((short) payloadLength)
            .next_header_and_hop_limit(PSEUDO_NEXT_HEADER_AND_HOP_LIMIT)
            .src_addr_part1(routeId)
            .src_addr_part2(sourceId)
            .dst_addr_part1(routeId)
            .dst_addr_part2(targetId)
            .build();
    }


    private void encodeTcpHeader(
        MutableDirectBuffer buffer,
        long sourceId,
        long targetId,
        long sequence,
        long acknowledge,
        TcpFlag flag)
    {
        final short sourcePort = (short) (sourceId & 0xffffL);
        final short destPort = (short) (targetId & 0xffffL);
        final short otherFields = (short) (0x5000 | flag.value());

        tcpHeaderRW.wrap(buffer, TCP_HEADER_OFFSET, buffer.capacity())
            .src_port(sourcePort)
            .dst_port(destPort)
            .sequence_number((int) sequence)
            .acknowledgment_number((int) acknowledge)
            .other_fields(otherFields)
            .window((short) 1024)
            .checksum((short) 0)
            .urgent_pointer((short) 0)
            .build();
    }

    private void writeToPcapFile(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        try
        {
            ByteBuffer byteBuf = buffer.byteBuffer();
            byteBuf.clear();
            byteBuf.position(offset);
            byteBuf.limit(offset + length);
            channel.write(byteBuf);
            channel.force(true);
        }
        catch (IOException ex)
        {
            System.out.println("Could not write to file. Reason: " + ex.getMessage());
            rethrowUnchecked(ex);
        }
    }

    private void closeResources()
    {
        try
        {
            channel.close();
            writer.close();
        }
        catch (IOException ex)
        {
            System.out.println("Could not close file. Reason: " + ex.getMessage());
            rethrowUnchecked(ex);
        }
    }

}
