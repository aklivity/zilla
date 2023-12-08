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
package io.aklivity.zilla.runtime.command.dump.internal.airline;

import static java.lang.Integer.parseInt;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.CRC32C;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2LongHashMap;
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
import io.aklivity.zilla.runtime.command.dump.internal.types.PcapGlobalHeaderFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.PcapPacketHeaderFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.TcpFlag;
import io.aklivity.zilla.runtime.command.dump.internal.types.TcpHeaderFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.FrameFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.binding.function.MessagePredicate;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.reader.BindingsReader;

@Command(name = "dump", description = "Dump stream content")
public final class ZillaDumpCommand extends ZillaCommand
{
    private static final Pattern PATTERN_NAMESPACED_BINDING = Pattern.compile("(?<namespace>[^\\.]+)\\.(?<binding>[^\\\\.]+)");
    private static final Pattern STREAMS_PATTERN = Pattern.compile("data(\\d+)");

    private static final long MAX_PARK_NS = MILLISECONDS.toNanos(100L);
    private static final long MIN_PARK_NS = MILLISECONDS.toNanos(1L);
    private static final int MAX_YIELDS = 30;
    private static final int MAX_SPINS = 20;
    private static final int BUFFER_SLOT_CAPACITY = 64 * 1024;
    private static final int LABELS_BUFFER_SLOT_CAPACITY = 4 * 128;

    private static final long PCAP_GLOBAL_MAGIC = 2712847316L;
    private static final short PCAP_GLOBAL_VERSION_MAJOR = 2;
    private static final short PCAP_GLOBAL_VERSION_MINOR = 4;
    private static final int PCAP_GLOBAL_SIZE = 24;
    private static final int PCAP_LINK_TYPE_IPV6 = 1;

    //private static final byte[] PSEUDO_ETHERNET_FRAME = BitUtil.fromHex("2052454356002053454e44005a41");
    private static final byte[] PSEUDO_ETHERNET_FRAME = BitUtil.fromHex("2052454356002053454e440086dd");
    private static final int PSEUDO_IPV6_PREFIX = 1629561669;
    private static final short PSEUDO_NEXT_HEADER_AND_HOP_LIMIT = 1536;
    private static final short TCP_DEST_PORT = 7114;
    private static final short TCP_SRC_PORT = 0;

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

    //private static final int ZILLA_HEADER_OFFSET = ETHER_HEADER_LIMIT;
    private static final int ZILLA_HEADER_OFFSET = TCP_HEADER_LIMIT;
    private static final int ZILLA_HEADER_SIZE = 8;
    private static final int ZILLA_HEADER_LIMIT = ZILLA_HEADER_OFFSET + ZILLA_HEADER_SIZE;

    private static final int TYPE_ID_INDEX = 0;
    private static final int KIND_ID_INDEX = 1;
    private static final int ORIGIN_TYPE_ID_INDEX = 2;
    private static final int ROUTED_TYPE_ID_INDEX = 3;

    private static final String SERVER_KIND = KindConfig.SERVER.name().toLowerCase();
    private static final String CLIENT_KIND = KindConfig.CLIENT.name().toLowerCase();
    private static final String UNKNOWN_LABEL = "??";

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
    private final PcapGlobalHeaderFW.Builder pcapGlobalHeaderRW = new PcapGlobalHeaderFW.Builder();
    private final PcapPacketHeaderFW.Builder pcapPacketHeaderRW = new PcapPacketHeaderFW.Builder();
    private final IPv6HeaderFW.Builder ipv6HeaderRW = new IPv6HeaderFW.Builder();
    private final TcpHeaderFW.Builder tcpHeaderRW = new TcpHeaderFW.Builder();
    private final MutableDirectBuffer writeBuffer;

    public ZillaDumpCommand()
    {
        this.writeBuffer = new UnsafeBuffer(ByteBuffer.allocate(BUFFER_SLOT_CAPACITY));
    }

    @Override
    public void run()
    {
        LabelManager labels = new LabelManager(directory);

        final LongHashSet filtered = new LongHashSet();
        bindings.stream()
            .map(PATTERN_NAMESPACED_BINDING::matcher)
            .filter(Matcher::matches)
            .map(m ->
                (((long) labels.lookupLabelId(m.group("namespace"))) << 32) |
                    (labels.lookupLabelId(m.group("binding")) & 0xffff_ffffL))
            .forEach(filtered::add);
        final LongPredicate filter = filtered.isEmpty() ? b -> true : filtered::contains;

        try (Stream<Path> files = Files.walk(directory, 3);
             WritableByteChannel writer = Files.newByteChannel(output, CREATE, WRITE, TRUNCATE_EXISTING))
        {
            final RingBufferSpy[] streamBuffers = files
                .filter(this::isStreamsFile)
                .peek(this::onDiscovered)
                .map(this::createStreamBuffer)
                .collect(Collectors.toList())
                .toArray(RingBufferSpy[]::new);
            final int streamBufferCount = streamBuffers.length;

            final IdleStrategy idleStrategy = new BackoffIdleStrategy(MAX_SPINS, MAX_YIELDS, MIN_PARK_NS, MAX_PARK_NS);
            final BindingsReader bindings = BindingsReader.builder().directory(directory).build();
            final DumpHandler dumpHandler = new DumpHandler(filter, labels::lookupLabel, bindings.bindings()::get, writer);
            final MessagePredicate spyHandler = dumpHandler::handleFrame;

            final MutableDirectBuffer buffer = writeBuffer;
            encodePcapGlobal(buffer);
            writePcapOutput(writer, buffer, 0, PCAP_GLOBAL_SIZE);

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
            .spyAt(RingBufferSpy.SpyPosition.ZERO)
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

    private void writePcapOutput(
        WritableByteChannel writer,
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
            writer.write(byteBuf);
        }
        catch (IOException ex)
        {
            System.out.println("Could not write to file. Reason: " + ex.getMessage());
            rethrowUnchecked(ex);
        }
    }

    public static int namespaceId(
        long bindingId)
    {
        return (int)(bindingId >> Integer.SIZE) & 0xffff_ffff;
    }

    private static int localId(
        long bindingId)
    {
        return (int)(bindingId >> 0) & 0xffff_ffff;
    }

    private final class DumpHandler
    {
        private final LongPredicate allowedBinding;
        private final WritableByteChannel writer;
        private final IntFunction<String> lookupLabel;
        private final Function<Long, long[]> getBindingInfo;
        private final CRC32C crc;
        private final ExtensionFW extensionRO;
        private final MutableDirectBuffer labelsBuffer;

        private long nextTimestamp = Long.MAX_VALUE;

        private final Long2LongHashMap seq = new Long2LongHashMap(0L);

        private DumpHandler(
            LongPredicate allowedBinding,
            IntFunction<String> lookupLabel,
            Function<Long, long[]> getBindingInfo,
            WritableByteChannel writer)
        {
            this.allowedBinding = allowedBinding;
            this.lookupLabel = lookupLabel;
            this.getBindingInfo = getBindingInfo;
            this.writer = writer;
            this.crc = new CRC32C();
            this.extensionRO = new ExtensionFW();
            this.labelsBuffer = new UnsafeBuffer(ByteBuffer.allocate(LABELS_BUFFER_SLOT_CAPACITY));
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
                //onAbort(abortRO.wrap(buffer, index, index + length));
                break;
            case WindowFW.TYPE_ID:
                //onWindow(windowRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                //onReset(resetRO.wrap(buffer, index, index + length));
                break;
            case FlushFW.TYPE_ID:
                //onFlush(flushRO.wrap(buffer, index, index + length));
                break;
            default:
                break;
            }

            return true;
        }

        private void onBegin(
            BeginFW begin)
        {
            final long bindingId = begin.routedId();

            if (allowedBinding.test(bindingId))
            {
                final MutableDirectBuffer buffer = writeBuffer;
                final long timestamp = begin.timestamp();
                final int protocolTypeId = resolveProtocolTypeId(begin.originId(), begin.routedId());

                final MutableDirectBuffer begin2buffer = new UnsafeBuffer(ByteBuffer.allocate(begin.sizeof()));
                final BeginFW begin2 = new BeginFW.Builder().wrap(begin2buffer, 0, begin.sizeof()).set(begin).build();
                final ExtensionFW extension = begin2.extension().get(extensionRO::tryWrap);
                patchExtension(begin2buffer, extension, BeginFW.FIELD_OFFSET_EXTENSION);

                int labelsLength = encodeZillaLabels(labelsBuffer, begin2.originId(), begin2.routedId());
                /*int pcapLength = ETHER_HEADER_SIZE + ZILLA_HEADER_SIZE + labelsLength + begin2.sizeof();
                encodePcapHeader(buffer, pcapLength, timestamp);
                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, PCAP_HEADER_LIMIT);

                encodeEtherHeader(buffer);
                writePcapOutput(writer, buffer, ETHER_HEADER_OFFSET, ETHER_HEADER_SIZE);

                encodeZillaHeader(buffer, BeginFW.TYPE_ID, protocolTypeId);
                writePcapOutput(writer, buffer, ZILLA_HEADER_OFFSET, ZILLA_HEADER_SIZE);

                writePcapOutput(writer, labelsBuffer, 0, labelsLength);

                writePcapOutput(writer, begin2.buffer(), begin2.offset(), begin2.sizeof());*/

                int tcpSegmentLength = ZILLA_HEADER_SIZE + labelsLength + begin.sizeof();
                int ipv6Length = TCP_HEADER_SIZE + tcpSegmentLength;
                int pcapLength = ETHER_HEADER_SIZE + IPV6_HEADER_SIZE + ipv6Length;
                boolean ini = begin.streamId() % 2 != 0;

                encodePcapHeader(buffer, pcapLength, timestamp);
                encodeEtherHeader(buffer);
                encodeIpv6Header(buffer, begin.streamId() ^ 1L, begin.streamId(), ipv6Length);
                System.out.printf("BEGIN len=%d seq=%d ack=%d%n",
                    tcpSegmentLength, seq.get(begin.streamId()), seq.get(begin.streamId() ^ 1L));
                short flagValue = (short) (TcpFlag.PSH.value() | TcpFlag.ACK.value());
                //encodeTcpHeader(buffer, ini, begin.sequence(), 0L, flagValue);
                encodeTcpHeader(buffer, ini, seq.get(begin.streamId()), seq.get(begin.streamId() ^ 1L), flagValue);
                seq.put(begin.streamId(), seq.get(begin.streamId()) + tcpSegmentLength);
                encodeZillaHeader(buffer, BeginFW.TYPE_ID, protocolTypeId);

                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, ZILLA_HEADER_LIMIT);
                writePcapOutput(writer, labelsBuffer, 0, labelsLength);
                writePcapOutput(writer, begin.buffer(), begin.offset(), begin.sizeof());
            }
            // begin -> SYN PSH ACK
            // data, window, flush, signal, challenge -> PSH ACK
            // end -> FIN PSH ACK
            // abort, reset -> RST PSH ACK

            // dst+src port: 7114
            // ipv6 source: streamId ^ 1L
            // ipv6 dst: streamId
        }

        private void onData(
            DataFW data)
        {
            final long bindingId = data.routedId();

            if (allowedBinding.test(bindingId))
            {
                final MutableDirectBuffer buffer = writeBuffer;
                final long timestamp = data.timestamp();
                final int protocolTypeId = resolveProtocolTypeId(data.originId(), data.routedId());

                final MutableDirectBuffer data2buffer = new UnsafeBuffer(ByteBuffer.allocate(data.sizeof()));
                final DataFW data2 = new DataFW.Builder().wrap(data2buffer, 0, data.sizeof()).set(data).build();
                final ExtensionFW extension = data2.extension().get(extensionRO::tryWrap);
                patchExtension(data2buffer, extension, DataFW.FIELD_OFFSET_EXTENSION);

                int labelsLength = encodeZillaLabels(labelsBuffer, data.originId(), data.routedId());

                /*int pcapLength = ETHER_HEADER_SIZE + ZILLA_HEADER_SIZE + labelsLength + data.sizeof();
                encodePcapHeader(buffer, pcapLength, timestamp);
                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, PCAP_HEADER_LIMIT);

                encodeEtherHeader(buffer);
                writePcapOutput(writer, buffer, ETHER_HEADER_OFFSET, ETHER_HEADER_SIZE);

                encodeZillaHeader(buffer, DataFW.TYPE_ID, protocolTypeId);
                writePcapOutput(writer, buffer, ZILLA_HEADER_OFFSET, ZILLA_HEADER_SIZE);

                writePcapOutput(writer, labelsBuffer, 0, labelsLength);

                writePcapOutput(writer, data.buffer(), data.offset(), data.sizeof());*/

                int tcpSegmentLength = ZILLA_HEADER_SIZE + labelsLength + data.sizeof();
                int ipv6Length = TCP_HEADER_SIZE + tcpSegmentLength;
                int pcapLength = ETHER_HEADER_SIZE + IPV6_HEADER_SIZE + ipv6Length;
                boolean ini = data.streamId() % 2 != 0;

                encodePcapHeader(buffer, pcapLength, timestamp);
                encodeEtherHeader(buffer);
                encodeIpv6Header(buffer, data.streamId() ^ 1L, data.streamId(), ipv6Length);
                //encodeTcpHeader(buffer, sourcePort, destPort, data.sequence(), 0L, TcpFlag.PSH);
                //prevSeq = seq;
                //seq += TCP_HEADER_SIZE + labelsLength + data.sizeof();
                //ack = prevSeq;
                //System.out.printf("DATA seq=%d!!! ack=%d%n", seq, 0L);
                //encodeTcpHeader(buffer, sourcePort, destPort, seq, 0L, TcpFlag.PSH);
                System.out.printf("DATA len=%d seq=%d ack=%d%n",
                    tcpSegmentLength, seq.get(data.streamId()), seq.get(data.streamId() ^ 1L));
                short flagValue = (short) (TcpFlag.PSH.value() | TcpFlag.ACK.value());
                //encodeTcpHeader(buffer, ini, data.sequence(), 0L, flagValue);
                encodeTcpHeader(buffer, ini, seq.get(data.streamId()), seq.get(data.streamId() ^ 1L), flagValue);
                seq.put(data.streamId(), seq.get(data.streamId()) + tcpSegmentLength);
                encodeZillaHeader(buffer, DataFW.TYPE_ID, protocolTypeId);

                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, ZILLA_HEADER_LIMIT);
                writePcapOutput(writer, labelsBuffer, 0, labelsLength);
                writePcapOutput(writer, data.buffer(), data.offset(), data.sizeof());
            }
        }

        private void onEnd(
            EndFW end)
        {
            final long bindingId = end.routedId();

            if (allowedBinding.test(bindingId))
            {
                final MutableDirectBuffer buffer = writeBuffer;
                final long timestamp = end.timestamp();
                final int protocolTypeId = resolveProtocolTypeId(end.originId(), end.routedId());

                final MutableDirectBuffer end2buffer = new UnsafeBuffer(ByteBuffer.allocate(end.sizeof()));
                final EndFW end2 = new EndFW.Builder().wrap(end2buffer, 0, end.sizeof()).set(end).build();
                final ExtensionFW extension = end2.extension().get(extensionRO::tryWrap);
                patchExtension(end2buffer, extension, EndFW.FIELD_OFFSET_EXTENSION);

                int labelsLength = encodeZillaLabels(labelsBuffer, end.originId(), end.routedId());
                /*int pcapLength = ETHER_HEADER_SIZE + ZILLA_HEADER_SIZE + labelsLength + end.sizeof();
                encodePcapHeader(buffer, pcapLength, timestamp);
                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, PCAP_HEADER_LIMIT);

                encodeEtherHeader(buffer);
                writePcapOutput(writer, buffer, ETHER_HEADER_OFFSET, ETHER_HEADER_SIZE);

                encodeZillaHeader(buffer, EndFW.TYPE_ID, protocolTypeId);
                writePcapOutput(writer, buffer, ZILLA_HEADER_OFFSET, ZILLA_HEADER_SIZE);

                writePcapOutput(writer, labelsBuffer, 0, labelsLength);

                writePcapOutput(writer, end.buffer(), end.offset(), end.sizeof());*/

                int tcpSegmentLength = ZILLA_HEADER_SIZE + labelsLength + end.sizeof();
                int ipv6Length = TCP_HEADER_SIZE + tcpSegmentLength;
                int pcapLength = ETHER_HEADER_SIZE + IPV6_HEADER_SIZE + ipv6Length;
                boolean ini = end.streamId() % 2 != 0;

                encodePcapHeader(buffer, pcapLength, timestamp);
                encodeEtherHeader(buffer);
                encodeIpv6Header(buffer, end.streamId() ^ 1L, end.streamId(), ipv6Length);
                encodeZillaHeader(buffer, EndFW.TYPE_ID, protocolTypeId);

                System.out.printf("END len=%d seq=%d ack=%d%n",
                    tcpSegmentLength, seq.get(end.streamId()), seq.get(end.streamId() ^ 1L));
                short flagValue = (short) (TcpFlag.FIN.value() | TcpFlag.PSH.value() | TcpFlag.ACK.value());
                encodeTcpHeader(buffer, ini, seq.get(end.streamId()), seq.get(end.streamId() ^ 1L), flagValue);
                seq.put(end.streamId(), seq.get(end.streamId()) + tcpSegmentLength);

                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, ZILLA_HEADER_LIMIT);
                writePcapOutput(writer, labelsBuffer, 0, labelsLength);
                writePcapOutput(writer, end.buffer(), end.offset(), end.sizeof());
            }
        }

        private void onAbort(
            AbortFW abort)
        {
            final long bindingId = abort.routedId();

            if (allowedBinding.test(bindingId))
            {
                final MutableDirectBuffer buffer = writeBuffer;
                final long timestamp = abort.timestamp();
                final int protocolTypeId = resolveProtocolTypeId(abort.originId(), abort.routedId());

                final MutableDirectBuffer abort2buffer = new UnsafeBuffer(ByteBuffer.allocate(abort.sizeof()));
                final AbortFW abort2 = new AbortFW.Builder().wrap(abort2buffer, 0, abort.sizeof()).set(abort).build();
                final ExtensionFW extension = abort2.extension().get(extensionRO::tryWrap);
                patchExtension(abort2buffer, extension, AbortFW.FIELD_OFFSET_EXTENSION);

                int labelsLength = encodeZillaLabels(labelsBuffer, abort.originId(), abort.routedId());
                /*int pcapLength = ETHER_HEADER_SIZE + ZILLA_HEADER_SIZE + labelsLength + abort.sizeof();
                encodePcapHeader(buffer, pcapLength, timestamp);
                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, PCAP_HEADER_LIMIT);

                encodeEtherHeader(buffer);
                writePcapOutput(writer, buffer, ETHER_HEADER_OFFSET, ETHER_HEADER_SIZE);

                encodeZillaHeader(buffer, AbortFW.TYPE_ID, protocolTypeId);
                writePcapOutput(writer, buffer, ZILLA_HEADER_OFFSET, ZILLA_HEADER_SIZE);

                writePcapOutput(writer, labelsBuffer, 0, labelsLength);

                writePcapOutput(writer, abort.buffer(), abort.offset(), abort.sizeof());*/
                int ipv6Length = TCP_HEADER_SIZE + ZILLA_HEADER_SIZE + labelsLength + abort.sizeof();
                int pcapLength = ETHER_HEADER_SIZE + IPV6_HEADER_SIZE + ipv6Length;
                boolean ini = abort.streamId() % 2 != 0;

                encodePcapHeader(buffer, pcapLength, timestamp);
                encodeEtherHeader(buffer);
                encodeIpv6Header(buffer, abort.streamId() ^ 1L, abort.streamId(), ipv6Length);
                System.out.printf("ABORT seq=%d ack=%d%n", abort.sequence(), abort.acknowledge());
                short flagValue = (short) (TcpFlag.RST.value() | TcpFlag.PSH.value() | TcpFlag.ACK.value());
                encodeTcpHeader(buffer, ini, abort.sequence(), 0L, flagValue);
                encodeZillaHeader(buffer, AbortFW.TYPE_ID, protocolTypeId);

                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, ZILLA_HEADER_LIMIT);
                writePcapOutput(writer, labelsBuffer, 0, labelsLength);
                writePcapOutput(writer, abort.buffer(), abort.offset(), abort.sizeof());
            }
        }

        private void onFlush(
            FlushFW flush)
        {
            final long bindingId = flush.routedId();

            if (allowedBinding.test(bindingId))
            {
                final MutableDirectBuffer buffer = writeBuffer;
                final long timestamp = flush.timestamp();
                final int protocolTypeId = resolveProtocolTypeId(flush.originId(), flush.routedId());

                final MutableDirectBuffer flush2buffer = new UnsafeBuffer(ByteBuffer.allocate(flush.sizeof()));
                final FlushFW flush2 = new FlushFW.Builder().wrap(flush2buffer, 0, flush.sizeof()).set(flush).build();
                final ExtensionFW extension = flush2.extension().get(extensionRO::tryWrap);
                patchExtension(flush2buffer, extension, FlushFW.FIELD_OFFSET_EXTENSION);

                int labelsLength = encodeZillaLabels(labelsBuffer, flush.originId(), flush.routedId());
                /*int pcapLength = ETHER_HEADER_SIZE + ZILLA_HEADER_SIZE + labelsLength + flush.sizeof();
                encodePcapHeader(buffer, pcapLength, timestamp);
                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, PCAP_HEADER_LIMIT);

                encodeEtherHeader(buffer);
                writePcapOutput(writer, buffer, ETHER_HEADER_OFFSET, ETHER_HEADER_SIZE);

                encodeZillaHeader(buffer, FlushFW.TYPE_ID, protocolTypeId);
                writePcapOutput(writer, buffer, ZILLA_HEADER_OFFSET, ZILLA_HEADER_SIZE);

                writePcapOutput(writer, labelsBuffer, 0, labelsLength);

                writePcapOutput(writer, flush.buffer(), flush.offset(), flush.sizeof());*/
                int ipv6Length = TCP_HEADER_SIZE + ZILLA_HEADER_SIZE + labelsLength + flush.sizeof();
                int pcapLength = ETHER_HEADER_SIZE + IPV6_HEADER_SIZE + ipv6Length;
                boolean ini = flush.streamId() % 2 != 0;

                encodePcapHeader(buffer, pcapLength, timestamp);
                encodeEtherHeader(buffer);
                encodeIpv6Header(buffer, flush.streamId() ^ 1L, flush.streamId(), ipv6Length);
                System.out.printf("FLUSH seq=%d ack=%d%n", flush.sequence(), flush.acknowledge());
                short flagValue = (short) (TcpFlag.PSH.value() | TcpFlag.ACK.value());
                encodeTcpHeader(buffer, ini, flush.sequence(), 0L, flagValue);
                encodeZillaHeader(buffer, FlushFW.TYPE_ID, protocolTypeId);

                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, ZILLA_HEADER_LIMIT);
                writePcapOutput(writer, labelsBuffer, 0, labelsLength);
                writePcapOutput(writer, flush.buffer(), flush.offset(), flush.sizeof());
            }
        }

        private void onReset(
            ResetFW reset)
        {
            final long bindingId = reset.routedId();

            if (allowedBinding.test(bindingId))
            {
                final MutableDirectBuffer buffer = writeBuffer;
                final long timestamp = reset.timestamp();
                final int protocolTypeId = resolveProtocolTypeId(reset.originId(), reset.routedId());

                final MutableDirectBuffer reset2buffer = new UnsafeBuffer(ByteBuffer.allocate(reset.sizeof()));
                final ResetFW reset2 = new ResetFW.Builder().wrap(reset2buffer, 0, reset.sizeof()).set(reset).build();
                final ExtensionFW extension = reset2.extension().get(extensionRO::tryWrap);
                patchExtension(reset2buffer, extension, ResetFW.FIELD_OFFSET_EXTENSION);

                int labelsLength = encodeZillaLabels(labelsBuffer, reset.originId(), reset.routedId());
                /*int pcapLength = ETHER_HEADER_SIZE + ZILLA_HEADER_SIZE + labelsLength + reset.sizeof();
                encodePcapHeader(buffer, pcapLength, timestamp);
                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, PCAP_HEADER_LIMIT);

                encodeEtherHeader(buffer);
                writePcapOutput(writer, buffer, ETHER_HEADER_OFFSET, ETHER_HEADER_SIZE);

                encodeZillaHeader(buffer, ResetFW.TYPE_ID, protocolTypeId);
                writePcapOutput(writer, buffer, ZILLA_HEADER_OFFSET, ZILLA_HEADER_SIZE);

                writePcapOutput(writer, labelsBuffer, 0, labelsLength);

                writePcapOutput(writer, reset.buffer(), reset.offset(), reset.sizeof());*/
                int ipv6Length = TCP_HEADER_SIZE + ZILLA_HEADER_SIZE + labelsLength + reset.sizeof();
                int pcapLength = ETHER_HEADER_SIZE + IPV6_HEADER_SIZE + ipv6Length;
                boolean ini = reset.streamId() % 2 != 0;

                encodePcapHeader(buffer, pcapLength, timestamp);
                encodeEtherHeader(buffer);
                encodeIpv6Header(buffer, reset.streamId() ^ 1L, reset.streamId(), ipv6Length);
                System.out.printf("RESET seq=%d ack=%d%n", reset.sequence(), reset.acknowledge());
                short flagValue = (short) (TcpFlag.RST.value() | TcpFlag.PSH.value() | TcpFlag.ACK.value());
                encodeTcpHeader(buffer, ini, reset.sequence(), 0L, flagValue);
                encodeZillaHeader(buffer, ResetFW.TYPE_ID, protocolTypeId);

                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, ZILLA_HEADER_LIMIT);
                writePcapOutput(writer, labelsBuffer, 0, labelsLength);
                writePcapOutput(writer, reset.buffer(), reset.offset(), reset.sizeof());
            }
        }

        private void onWindow(
            WindowFW window)
        {
            final long bindingId = window.routedId();

            if (allowedBinding.test(bindingId))
            {
                final MutableDirectBuffer buffer = writeBuffer;
                final long timestamp = window.timestamp();
                final int protocolTypeId = resolveProtocolTypeId(window.originId(), window.routedId());

                int labelsLength = encodeZillaLabels(labelsBuffer, window.originId(), window.routedId());
                /*int pcapLength = ETHER_HEADER_SIZE + ZILLA_HEADER_SIZE + labelsLength + window.sizeof();
                encodePcapHeader(buffer, pcapLength, timestamp);
                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, PCAP_HEADER_LIMIT);

                encodeEtherHeader(buffer);
                writePcapOutput(writer, buffer, ETHER_HEADER_OFFSET, ETHER_HEADER_SIZE);

                encodeZillaHeader(buffer, WindowFW.TYPE_ID, protocolTypeId);
                writePcapOutput(writer, buffer, ZILLA_HEADER_OFFSET, ZILLA_HEADER_SIZE);

                writePcapOutput(writer, labelsBuffer, 0, labelsLength);

                writePcapOutput(writer, window.buffer(), window.offset(), window.sizeof());*/

                int ipv6Length = TCP_HEADER_SIZE + ZILLA_HEADER_SIZE + labelsLength + window.sizeof();
                int pcapLength = ETHER_HEADER_SIZE + IPV6_HEADER_SIZE + ipv6Length;
                boolean ini = window.streamId() % 2 != 0;

                encodePcapHeader(buffer, pcapLength, timestamp);
                encodeEtherHeader(buffer);
                encodeIpv6Header(buffer, window.streamId() ^ 1L, window.streamId(), ipv6Length);
                //ack = seq;
                //System.out.printf("WINDOW seq=%d ack=%d!!!!%n", window.sequence(), ack);
                //encodeTcpHeader(buffer, sourcePort, destPort, window.sequence(), ack, TcpFlag.ACK);
                System.out.printf("WINDOW seq=%d ack=%d%n", window.sequence(), window.acknowledge());
                short flagValue = (short) (TcpFlag.PSH.value() | TcpFlag.ACK.value());
                encodeTcpHeader(buffer, ini, window.sequence(), window.acknowledge(), flagValue);
                encodeZillaHeader(buffer, WindowFW.TYPE_ID, protocolTypeId);

                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, ZILLA_HEADER_LIMIT);
                writePcapOutput(writer, labelsBuffer, 0, labelsLength);
                writePcapOutput(writer, window.buffer(), window.offset(), window.sizeof());
            }
        }

        private void patchExtension(
            MutableDirectBuffer buffer,
            ExtensionFW extension,
            int offset)
        {
            if (extension != null)
            {
                int streamTypeId = calculateLabelCrc(extension.typeId());
                buffer.putInt(offset, streamTypeId);
            }
        }

        private int resolveProtocolTypeId(
            long originId,
            long routedId)
        {
            long[] origin = getBindingInfo.apply(originId);
            long[] routed = getBindingInfo.apply(routedId);

            long protocolTypeLabelId = 0;
            String routedBindingKind = lookupLabel.apply(localId(routed[KIND_ID_INDEX]));
            if (SERVER_KIND.equals(routedBindingKind))
            {
                protocolTypeLabelId = routed[ROUTED_TYPE_ID_INDEX];
            }
            String originBindingKind = lookupLabel.apply(localId(routed[KIND_ID_INDEX]));
            if (protocolTypeLabelId == 0 && CLIENT_KIND.equals(originBindingKind))
            {
                protocolTypeLabelId = origin[ORIGIN_TYPE_ID_INDEX];
            }

            return calculateLabelCrc(localId(protocolTypeLabelId));
        }

        private int calculateLabelCrc(
            int labelId)
        {
            int result = 0;
            if (labelId != 0)
            {
                String label = lookupLabel.apply(labelId);
                if (!UNKNOWN_LABEL.equals(label))
                {
                    crc.reset();
                    crc.update(label.getBytes(StandardCharsets.UTF_8));
                    result = (int) crc.getValue();
                }
            }
            return result;
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
            buffer.putBytes(ETHER_HEADER_OFFSET, PSEUDO_ETHERNET_FRAME);
        }

        private void encodeIpv6Header(
            MutableDirectBuffer buffer,
            long source,
            long destination,
            //long bindingId,
            //long sourceId,
            //long targetId,
            int payloadLength)
        {
            ipv6HeaderRW.wrap(buffer, IPV6_HEADER_OFFSET, buffer.capacity())
                .prefix(PSEUDO_IPV6_PREFIX)
                .payload_length((short) payloadLength)
                .next_header_and_hop_limit(PSEUDO_NEXT_HEADER_AND_HOP_LIMIT)
                .src_addr_part1(0)
                .src_addr_part2(source)
                .dst_addr_part1(0)
                .dst_addr_part2(destination)
                .build();
        }

        /*private void encodeIpv6Header(
            MutableDirectBuffer buffer,
            long bindingId,
            long sourceId,
            long targetId,
            int payloadLength)
        {
            ipv6HeaderRW.wrap(buffer, IPV6_HEADER_OFFSET, buffer.capacity())
                .prefix(PSEUDO_IPV6_PREFIX)
                .payload_length((short) payloadLength)
                .next_header_and_hop_limit(PSEUDO_NEXT_HEADER_AND_HOP_LIMIT)
                .src_addr_part1(bindingId)
                .src_addr_part2(sourceId)
                .dst_addr_part1(bindingId)
                .dst_addr_part2(targetId)
                .build();
        }*/

        private void encodeTcpHeader(
            MutableDirectBuffer buffer,
            //long sourceId,
            //long targetId,
            boolean ini,
            //short sourcePort,
            //short destPort,
            long sequence,
            long acknowledge,
            short flagValue)
        //TcpFlag flag)
        {
            //final short sourcePort = (short) (sourceId & 0xffffL);
            //final short destPort = (short) (targetId & 0xffffL);
            //final short otherFields = (short) (0x5000 | flag.value());
            final short otherFields = (short) (0x5000 | flagValue);
            //final short otherFields = (short) (0x5000 | TcpFlag.PSH.value() | TcpFlag.ACK.value());
            short sourcePort = ini ? TCP_SRC_PORT : TCP_DEST_PORT;
            short destPort = ini ? TCP_DEST_PORT : TCP_SRC_PORT;

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

        /*private void encodeTcpHeader(
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
        }*/

        private void encodeZillaHeader(
            MutableDirectBuffer buffer,
            int frameTypeId,
            int protocolTypeId)
        {
            buffer.putInt(ZILLA_HEADER_OFFSET, frameTypeId);
            buffer.putInt(ZILLA_HEADER_OFFSET + Integer.BYTES, protocolTypeId);
        }

        private int encodeZillaLabels(
            MutableDirectBuffer buffer,
            long originId,
            long routedId)
        {
            byte[] originNamespace = lookupLabel.apply(namespaceId(originId)).getBytes(StandardCharsets.UTF_8);
            byte[] originBinding = lookupLabel.apply(localId(originId)).getBytes(StandardCharsets.UTF_8);
            byte[] routedNamespace = lookupLabel.apply(namespaceId(routedId)).getBytes(StandardCharsets.UTF_8);
            byte[] routedBinding = lookupLabel.apply(localId(routedId)).getBytes(StandardCharsets.UTF_8);
            int contentLength = 4 * Integer.BYTES + originNamespace.length + originBinding.length + routedNamespace.length +
                routedBinding.length;
            int totalLength = contentLength + Integer.BYTES;

            int offset = 0;
            buffer.putInt(offset, contentLength);
            offset += Integer.BYTES;

            buffer.putInt(offset, originNamespace.length);
            offset += Integer.BYTES;
            buffer.putBytes(offset, originNamespace);
            offset += originNamespace.length;

            buffer.putInt(offset, originBinding.length);
            offset += Integer.BYTES;
            buffer.putBytes(offset, originBinding);
            offset += originBinding.length;

            buffer.putInt(offset, routedNamespace.length);
            offset += Integer.BYTES;
            buffer.putBytes(offset, routedNamespace);
            offset += routedNamespace.length;

            buffer.putInt(offset, routedBinding.length);
            offset += Integer.BYTES;
            buffer.putBytes(offset, routedBinding);
            offset += routedBinding.length;

            assert offset == totalLength;

            return totalLength;
        }
    }
}
