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
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.reader.BindingsReader;

@Command(name = "dump", description = "Dump stream content")
public final class ZillaDumpCommand extends ZillaCommand
{
    private static final Pattern PATTERN_NAMESPACED_BINDING = Pattern.compile("(?<namespace>[^\\.]+)\\.(?<binding>[^\\\\.]+)");

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

    private static final int ZILLA_HEADER_OFFSET = ETHER_HEADER_LIMIT;
    private static final int ZILLA_HEADER_SIZE = 8;
    private static final int ZILLA_HEADER_LIMIT = ZILLA_HEADER_OFFSET + ZILLA_HEADER_SIZE;

    /*private static final int IPV6_HEADER_OFFSET = ETHER_HEADER_LIMIT;
    private static final int IPV6_HEADER_SIZE = 40;
    private static final int IPV6_HEADER_LIMIT = IPV6_HEADER_OFFSET + IPV6_HEADER_SIZE;

    private static final int TCP_HEADER_OFFSET = IPV6_HEADER_LIMIT;
    private static final int TCP_HEADER_SIZE = 20;
    private static final int TCP_HEADER_LIMIT = TCP_HEADER_OFFSET + TCP_HEADER_SIZE;*/

    //private static final byte[] PSEUDO_ETHERNET_FRAME = BitUtil.fromHex("fe0000000002fe000000000186dd");
    private static final byte[] PSEUDO_ETHERNET_FRAME = BitUtil.fromHex("2052454356002053454e44005a41");
    private static final int PSEUDO_IPV6_PREFIX = 1629561669;
    private static final short PSEUDO_NEXT_HEADER_AND_HOP_LIMIT = 1536;

    private static final Pattern STREAMS_PATTERN = Pattern.compile("data(\\d+)");

    private static final Integer NO_LABEL_ID = Integer.valueOf(-1);
    /*private static final Map<String, Integer> TYPES = Map.of(
        "amqp", 1,
        "filesystem", 2,
        "grpc", 3,
        "http", 4,
        "kafka", 5,
        "proxy", 6,
        "mqtt", 7,
        "sse", 8,
        "ws", 9);*/
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
    private final IPv6HeaderFW.Builder ipv6HeaderRW = new IPv6HeaderFW.Builder();
    private final TcpHeaderFW.Builder tcpHeaderRW = new TcpHeaderFW.Builder();
    private final PcapGlobalHeaderFW.Builder pcapGlobalHeaderRW = new PcapGlobalHeaderFW.Builder();
    private final PcapPacketHeaderFW.Builder pcapPacketHeaderRW = new PcapPacketHeaderFW.Builder();
    private final MutableDirectBuffer writeBuffer;

    //private final Map<Integer, String> typeIds;
    //private Map<Long, Integer> streamTypes = new HashMap<>();
    //private LabelManager labels;

    public ZillaDumpCommand()
    {
        //this.typeIds = new HashMap<>();
        //TYPES.keySet().forEach(this::putTypeId);
        this.writeBuffer = new UnsafeBuffer(ByteBuffer.allocate(BUFFER_SLOT_CAPACITY));
    }

    /*private void putTypeId(
        String type)
    {
        int typeId = this.labels.lookupLabelId(type);
        if (typeId != NO_LABEL_ID)
        {
            this.typeIds.put(typeId, type);
        }
    }*/

    @Override
    public void run()
    {
        LabelManager labels = new LabelManager(directory);
        //System.out.println("run " + directory);

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

    private final class DumpHandler
    {
        private final LongPredicate allowedBinding;
        private final WritableByteChannel writer;
        private final IntFunction<String> lookupLabel;
        private final Function<Long, long[]> getBindingInfo;
        private final CRC32C crc;

        private long nextTimestamp = Long.MAX_VALUE;

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

        /*private void onFrame(
            int msgTypeId,
            FrameFW frame)
        {
            final long bindingId = frame.routedId();

            if (allowedBinding.test(bindingId) && msgTypeId == DataFW.TYPE_ID)
            {
                final MutableDirectBuffer buffer = writeBuffer;
                final long timestamp = frame.timestamp();

                encodePcapHeader(buffer, Integer.BYTES + frame.sizeof(), timestamp);
                writePcapOutput(writer, buffer, 0, PCAP_HEADER_LIMIT);

                buffer.putInt(PCAP_HEADER_LIMIT, msgTypeId);
                writePcapOutput(writer, buffer, PCAP_HEADER_LIMIT, Integer.BYTES);

                writePcapOutput(writer, frame.buffer(), frame.offset(), frame.sizeof());

            }
        }*/

        private void onBegin(
            BeginFW begin)
        {
            final long bindingId = begin.routedId();

            if (allowedBinding.test(bindingId))
            {
                final MutableDirectBuffer buffer = writeBuffer;
                final long streamId = begin.streamId();
                //final long initialId = resolveInitialId(begin.streamId());
                final long timestamp = begin.timestamp();
                final long sequence = begin.sequence();
                final int streamTypeId = resolveStreamTypeId(begin.originId(), begin.routedId());

                /*encodePcapHeader(buffer, ETHER_HEADER_SIZE + IPV6_HEADER_SIZE + TCP_HEADER_SIZE, timestamp);
                encodeEtherHeader(buffer);
                encodeIpv6Header(buffer, bindingId, streamId ^ 1L, streamId, TCP_HEADER_SIZE);
                encodeTcpHeader(buffer, streamId ^ 1L, streamId, sequence - 1L, 0L, TcpFlag.SYN);

                writePcapOutput(writer, buffer, 0, TCP_HEADER_LIMIT);*/

                /*if (begin.extension().sizeof() > 0)
                {
                    int typeId = begin.extension().buffer().getInt(begin.extension().offset());
                    System.out.printf("sid=%s iid=%s%n", streamId, initialId);
                    System.out.println(typeIds.get(typeId));
                    System.out.println(TYPES.get(typeIds.get(typeId)));
                    streamTypes.put(initialId, TYPES.get(typeIds.get(typeId)));
                }
                else
                {
                    System.out.println("begin has no extension");
                }*/

                //ProxyBeginExFW beginEx = begin.extension().get(proxyBeginExRO::tryWrap);

                encodePcapHeader(buffer, ETHER_HEADER_SIZE + ZILLA_HEADER_SIZE + begin.sizeof(), timestamp);
                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, PCAP_HEADER_LIMIT);

                encodeEtherHeader(buffer);
                writePcapOutput(writer, buffer, ETHER_HEADER_OFFSET, ETHER_HEADER_SIZE);

                //buffer.putInt(PCAP_HEADER_LIMIT + ETHER_HEADER_SIZE, BeginFW.TYPE_ID);
                //writePcapOutput(writer, buffer, PCAP_HEADER_LIMIT + ETHER_HEADER_SIZE, Integer.BYTES);
                encodeZillaHeader(buffer, BeginFW.TYPE_ID, streamTypeId);
                writePcapOutput(writer, buffer, ZILLA_HEADER_OFFSET, ZILLA_HEADER_SIZE);

                writePcapOutput(writer, begin.buffer(), begin.offset(), begin.sizeof());
            }
        }

        private void onData(
            DataFW data)
        {
            final long bindingId = data.routedId();

            if (allowedBinding.test(bindingId))
            {
                final OctetsFW payload = data.payload();

                if (payload != null)
                {
                    final MutableDirectBuffer buffer = writeBuffer;
                    final long streamId = data.streamId();
                    //final long initialId = resolveInitialId(data.streamId());
                    //final int streamTypeId = streamTypes.get(initialId);
                    final long timestamp = data.timestamp();
                    final long sequence = data.sequence();
                    final int sizeof = payload.sizeof();
                    final int streamTypeId = resolveStreamTypeId(data.originId(), data.routedId());

                    /*encodePcapHeader(buffer, ETHER_HEADER_SIZE + IPV6_HEADER_SIZE + TCP_HEADER_SIZE + sizeof, timestamp);
                    encodeEtherHeader(buffer);
                    encodeIpv6Header(buffer, bindingId, streamId ^ 1L, streamId, TCP_HEADER_SIZE + sizeof);
                    encodeTcpHeader(buffer, streamId ^ 1L, streamId, sequence, 0L, TcpFlag.PSH);

                    writePcapOutput(writer, buffer, 0, TCP_HEADER_LIMIT);
                    writePcapOutput(writer, payload.buffer(), payload.offset(), sizeof);*/

                    encodePcapHeader(buffer, ETHER_HEADER_SIZE + ZILLA_HEADER_SIZE + data.sizeof(), timestamp);
                    writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, PCAP_HEADER_LIMIT);

                    encodeEtherHeader(buffer);
                    writePcapOutput(writer, buffer, ETHER_HEADER_OFFSET, ETHER_HEADER_SIZE);

                    encodeZillaHeader(buffer, DataFW.TYPE_ID, streamTypeId);
                    //buffer.putInt(PCAP_HEADER_LIMIT + ETHER_HEADER_SIZE, DataFW.TYPE_ID);
                    //writePcapOutput(writer, buffer, PCAP_HEADER_LIMIT + ETHER_HEADER_SIZE, Integer.BYTES);
                    writePcapOutput(writer, buffer, ZILLA_HEADER_OFFSET, ZILLA_HEADER_SIZE);

                    //int dataFrameSize = data.sizeof() - payload.sizeof();
                    //writePcapOutput(writer, data.buffer(), data.offset(), dataFrameSize);
                    writePcapOutput(writer, data.buffer(), data.offset(), data.sizeof());

                    ////buffer.putInt(PCAP_HEADER_LIMIT + ETHER_HEADER_SIZE + Integer.BYTES, streamTypeId);
                    ////writePcapOutput(writer, buffer, PCAP_HEADER_LIMIT + ETHER_HEADER_SIZE + Integer.BYTES, Integer.BYTES);

                    //writePcapOutput(writer, payload.buffer(), payload.offset(), payload.sizeof());
                }
            }
        }

        private void onEnd(
            EndFW end)
        {
            final long bindingId = end.routedId();

            if (allowedBinding.test(bindingId))
            {
                final MutableDirectBuffer buffer = writeBuffer;
                final long streamId = end.streamId();
                final long timestamp = end.timestamp();
                final long sequence = end.sequence();
                final int streamTypeId = resolveStreamTypeId(end.originId(), end.routedId());

                /*encodePcapHeader(buffer, ETHER_HEADER_SIZE + IPV6_HEADER_SIZE + TCP_HEADER_SIZE, timestamp);
                encodeEtherHeader(buffer);
                encodeIpv6Header(buffer, bindingId, streamId ^ 1L, streamId, TCP_HEADER_SIZE);
                encodeTcpHeader(buffer, streamId ^ 1L, streamId, sequence, 0L, TcpFlag.FIN);

                writePcapOutput(writer, buffer, 0, TCP_HEADER_LIMIT);*/
                encodePcapHeader(buffer, ETHER_HEADER_SIZE + ZILLA_HEADER_SIZE + end.sizeof(), timestamp);
                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, PCAP_HEADER_LIMIT);

                encodeEtherHeader(buffer);
                writePcapOutput(writer, buffer, ETHER_HEADER_OFFSET, ETHER_HEADER_SIZE);

                //buffer.putInt(PCAP_HEADER_LIMIT + ETHER_HEADER_SIZE, EndFW.TYPE_ID);
                //writePcapOutput(writer, buffer, PCAP_HEADER_LIMIT + ETHER_HEADER_SIZE, Integer.BYTES);
                encodeZillaHeader(buffer, EndFW.TYPE_ID, streamTypeId);
                writePcapOutput(writer, buffer, ZILLA_HEADER_OFFSET, ZILLA_HEADER_SIZE);

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
                final long streamId = abort.streamId();
                final long timestamp = abort.timestamp();
                final long sequence = abort.sequence();
                final int streamTypeId = resolveStreamTypeId(abort.originId(), abort.routedId());

                /*encodePcapHeader(buffer, ETHER_HEADER_SIZE + IPV6_HEADER_SIZE + TCP_HEADER_SIZE, timestamp);
                encodeEtherHeader(buffer);
                encodeIpv6Header(buffer, bindingId, streamId ^ 1L, streamId, TCP_HEADER_SIZE);
                encodeTcpHeader(buffer, streamId ^ 1L, streamId, sequence, 0L, TcpFlag.RST);

                writePcapOutput(writer, buffer, 0, TCP_HEADER_LIMIT);*/
                encodePcapHeader(buffer, ETHER_HEADER_SIZE + ZILLA_HEADER_SIZE + abort.sizeof(), timestamp);
                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, PCAP_HEADER_LIMIT);

                encodeEtherHeader(buffer);
                writePcapOutput(writer, buffer, ETHER_HEADER_OFFSET, ETHER_HEADER_SIZE);

                //buffer.putInt(PCAP_HEADER_LIMIT + ETHER_HEADER_SIZE, AbortFW.TYPE_ID);
                //writePcapOutput(writer, buffer, PCAP_HEADER_LIMIT + ETHER_HEADER_SIZE, Integer.BYTES);
                encodeZillaHeader(buffer, AbortFW.TYPE_ID, streamTypeId);
                writePcapOutput(writer, buffer, ZILLA_HEADER_OFFSET, ZILLA_HEADER_SIZE);

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
                final long streamId = flush.streamId();
                final long timestamp = flush.timestamp();
                final long sequence = flush.sequence();
                final int streamTypeId = resolveStreamTypeId(flush.originId(), flush.routedId());

                /*encodePcapHeader(buffer, ETHER_HEADER_SIZE + IPV6_HEADER_SIZE + TCP_HEADER_SIZE, timestamp);
                encodeEtherHeader(buffer);
                encodeIpv6Header(buffer, bindingId, streamId ^ 1L, streamId, TCP_HEADER_SIZE);
                encodeTcpHeader(buffer, streamId ^ 1L, streamId, sequence, 0L, TcpFlag.PSH);

                writePcapOutput(writer, buffer, 0, TCP_HEADER_LIMIT);*/
                encodePcapHeader(buffer, ETHER_HEADER_SIZE + ZILLA_HEADER_SIZE + flush.sizeof(), timestamp);
                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, PCAP_HEADER_LIMIT);

                encodeEtherHeader(buffer);
                writePcapOutput(writer, buffer, ETHER_HEADER_OFFSET, ETHER_HEADER_SIZE);

                //buffer.putInt(PCAP_HEADER_LIMIT + ETHER_HEADER_SIZE, FlushFW.TYPE_ID);
                //writePcapOutput(writer, buffer, PCAP_HEADER_LIMIT + ETHER_HEADER_SIZE, Integer.BYTES);
                encodeZillaHeader(buffer, FlushFW.TYPE_ID, streamTypeId);
                writePcapOutput(writer, buffer, ZILLA_HEADER_OFFSET, ZILLA_HEADER_SIZE);

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
                final long streamId = reset.streamId();
                final long timestamp = reset.timestamp();
                final long sequence = reset.sequence();
                final int streamTypeId = resolveStreamTypeId(reset.originId(), reset.routedId());

                /*encodePcapHeader(buffer, ETHER_HEADER_SIZE + IPV6_HEADER_SIZE + TCP_HEADER_SIZE, timestamp);
                encodeEtherHeader(buffer);
                encodeIpv6Header(buffer, bindingId, streamId, streamId ^ 1L, TCP_HEADER_SIZE);
                encodeTcpHeader(buffer, streamId, streamId ^ 1L, sequence, 0L, TcpFlag.RST);

                writePcapOutput(writer, buffer, 0, TCP_HEADER_LIMIT);*/
                encodePcapHeader(buffer, ETHER_HEADER_SIZE + ZILLA_HEADER_SIZE + reset.sizeof(), timestamp);
                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, PCAP_HEADER_LIMIT);

                encodeEtherHeader(buffer);
                writePcapOutput(writer, buffer, ETHER_HEADER_OFFSET, ETHER_HEADER_SIZE);

                //buffer.putInt(PCAP_HEADER_LIMIT + ETHER_HEADER_SIZE, ResetFW.TYPE_ID);
                //writePcapOutput(writer, buffer, PCAP_HEADER_LIMIT + ETHER_HEADER_SIZE, Integer.BYTES);
                encodeZillaHeader(buffer, ResetFW.TYPE_ID, streamTypeId);
                writePcapOutput(writer, buffer, ZILLA_HEADER_OFFSET, ZILLA_HEADER_SIZE);

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
                final long streamId = window.streamId();
                final long timestamp = window.timestamp();
                final long sequence = window.sequence();
                final long acknowledge = window.acknowledge();
                final int streamTypeId = resolveStreamTypeId(window.originId(), window.routedId());

                /*encodePcapHeader(buffer, ETHER_HEADER_SIZE + IPV6_HEADER_SIZE + TCP_HEADER_SIZE, timestamp);
                encodeEtherHeader(buffer);
                encodeIpv6Header(buffer, bindingId, streamId, streamId ^ 1L, TCP_HEADER_SIZE);
                encodeTcpHeader(buffer, streamId, streamId ^ 1L, sequence, acknowledge, TcpFlag.ACK);

                writePcapOutput(writer, buffer, 0, TCP_HEADER_LIMIT);*/
                encodePcapHeader(buffer, ETHER_HEADER_SIZE + ZILLA_HEADER_SIZE + window.sizeof(), timestamp);
                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, PCAP_HEADER_LIMIT);

                encodeEtherHeader(buffer);
                writePcapOutput(writer, buffer, ETHER_HEADER_OFFSET, ETHER_HEADER_SIZE);

                //buffer.putInt(PCAP_HEADER_LIMIT + ETHER_HEADER_SIZE, WindowFW.TYPE_ID);
                //writePcapOutput(writer, buffer, PCAP_HEADER_LIMIT + ETHER_HEADER_SIZE, Integer.BYTES);
                encodeZillaHeader(buffer, WindowFW.TYPE_ID, streamTypeId);
                writePcapOutput(writer, buffer, ZILLA_HEADER_OFFSET, ZILLA_HEADER_SIZE);

                writePcapOutput(writer, window.buffer(), window.offset(), window.sizeof());
            }
        }

        private int resolveStreamTypeId(
            long originId,
            long routedId)
        {
            long[] origin = getBindingInfo.apply(originId);
            long[] routed = getBindingInfo.apply(routedId);
            System.out.printf("origin id=%016x t=%016x k=%016x ot=%016x rt=%016x%n", originId,
                origin[0], origin[1], origin[2], origin[3]);
            System.out.printf("routed id=%016x t=%016x k=%016x ot=%016x rt=%016x%n", routedId,
                routed[0], routed[1], routed[2], routed[3]);

            long streamTypeLabelId = 0;
            String routedBindingKind = lookupLabel.apply(localId(routed[KIND_ID_INDEX]));
            if (SERVER_KIND.equals(routedBindingKind))
            {
                streamTypeLabelId = routed[ROUTED_TYPE_ID_INDEX];
            }
            String originBindingKind = lookupLabel.apply(localId(routed[KIND_ID_INDEX]));
            if (streamTypeLabelId == 0 && CLIENT_KIND.equals(originBindingKind))
            {
                streamTypeLabelId = origin[ORIGIN_TYPE_ID_INDEX];
            }

            int streamTypeCrc = 0;
            if (streamTypeLabelId != 0)
            {
                String streamType = lookupLabel.apply(localId(streamTypeLabelId));
                if (!UNKNOWN_LABEL.equals(streamType))
                {
                    crc.reset();
                    crc.update(streamType.getBytes(StandardCharsets.UTF_8));
                    streamTypeCrc = (int) crc.getValue();
                }
                System.out.printf("stlid = %016x; st = %s; crc = %08x%n%n", streamTypeLabelId, streamType, streamTypeCrc);
            }
            return streamTypeCrc;
            // TODO: Ati - cache
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
        buffer.putBytes(ETHER_HEADER_OFFSET, PSEUDO_ETHERNET_FRAME);
    }

    private void encodeZillaHeader(
        MutableDirectBuffer buffer,
        int frameTypeId,
        int streamTypeId)
    {
        buffer.putInt(ZILLA_HEADER_OFFSET, frameTypeId);
        buffer.putInt(ZILLA_HEADER_OFFSET + Integer.BYTES, streamTypeId);
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
    }*/

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

    public static int localId(
        long bindingId)
    {
        return (int)(bindingId >> 0) & 0xffff_ffff;
    }


    /*public static long resolveInitialId(
        long streamId)
    {
        long initialId;
        if ((streamId & 0x0000_0000_0000_0001L) != 0L)
        {
            initialId = streamId;
        }
        else
        {
            initialId = streamId ^ 1L;
        }
        return initialId;
    }*/
}
