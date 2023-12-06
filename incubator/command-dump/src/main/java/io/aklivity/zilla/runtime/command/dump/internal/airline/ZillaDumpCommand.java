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
import io.aklivity.zilla.runtime.command.dump.internal.types.PcapGlobalHeaderFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.PcapPacketHeaderFW;
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

    private static final long PCAP_GLOBAL_MAGIC = 2712847316L;
    private static final short PCAP_GLOBAL_VERSION_MAJOR = 2;
    private static final short PCAP_GLOBAL_VERSION_MINOR = 4;
    private static final int PCAP_GLOBAL_SIZE = 24;
    private static final int PCAP_LINK_TYPE_IPV6 = 1;
    private static final byte[] PSEUDO_ETHERNET_FRAME = BitUtil.fromHex("2052454356002053454e44005a41");

    private static final int PCAP_HEADER_OFFSET = 0;
    private static final int PCAP_HEADER_SIZE = 16;
    private static final int PCAP_HEADER_LIMIT = PCAP_HEADER_OFFSET + PCAP_HEADER_SIZE;

    private static final int ETHER_HEADER_OFFSET = PCAP_HEADER_LIMIT;
    private static final int ETHER_HEADER_SIZE = 14;
    private static final int ETHER_HEADER_LIMIT = ETHER_HEADER_OFFSET + ETHER_HEADER_SIZE;

    private static final int ZILLA_HEADER_OFFSET = ETHER_HEADER_LIMIT;
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
        int protocolTypeId)
    {
        buffer.putInt(ZILLA_HEADER_OFFSET, frameTypeId);
        buffer.putInt(ZILLA_HEADER_OFFSET + Integer.BYTES, protocolTypeId);
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
        private final CRC32C crc = new CRC32C();
        private final ExtensionFW extensionRO = new ExtensionFW();

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
            final long bindingId = begin.routedId();

            if (allowedBinding.test(bindingId))
            {
                final MutableDirectBuffer buffer = writeBuffer;
                final long timestamp = begin.timestamp();
                final int protocolTypeId = resolveProtocolTypeId(begin.originId(), begin.routedId());

                final MutableDirectBuffer begin2buffer = new UnsafeBuffer(ByteBuffer.allocate(begin.sizeof()));
                final BeginFW begin2 = new BeginFW.Builder().wrap(begin2buffer, 0, begin.sizeof()).set(begin).build();
                final ExtensionFW extension = begin2.extension().get(extensionRO::tryWrap);
                if (extension != null)
                {
                    int streamTypeId = calculateLabelCrc(extension.typeId());
                    begin2buffer.putInt(BeginFW.FIELD_OFFSET_EXTENSION, streamTypeId);
                }

                encodePcapHeader(buffer, ETHER_HEADER_SIZE + ZILLA_HEADER_SIZE + begin.sizeof(), timestamp);
                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, PCAP_HEADER_LIMIT);

                encodeEtherHeader(buffer);
                writePcapOutput(writer, buffer, ETHER_HEADER_OFFSET, ETHER_HEADER_SIZE);

                encodeZillaHeader(buffer, BeginFW.TYPE_ID, protocolTypeId);
                writePcapOutput(writer, buffer, ZILLA_HEADER_OFFSET, ZILLA_HEADER_SIZE);

                writePcapOutput(writer, begin2.buffer(), begin2.offset(), begin2.sizeof());
            }
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

                encodePcapHeader(buffer, ETHER_HEADER_SIZE + ZILLA_HEADER_SIZE + data.sizeof(), timestamp);
                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, PCAP_HEADER_LIMIT);

                encodeEtherHeader(buffer);
                writePcapOutput(writer, buffer, ETHER_HEADER_OFFSET, ETHER_HEADER_SIZE);

                encodeZillaHeader(buffer, DataFW.TYPE_ID, protocolTypeId);
                writePcapOutput(writer, buffer, ZILLA_HEADER_OFFSET, ZILLA_HEADER_SIZE);

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

                encodePcapHeader(buffer, ETHER_HEADER_SIZE + ZILLA_HEADER_SIZE + end.sizeof(), timestamp);
                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, PCAP_HEADER_LIMIT);

                encodeEtherHeader(buffer);
                writePcapOutput(writer, buffer, ETHER_HEADER_OFFSET, ETHER_HEADER_SIZE);

                encodeZillaHeader(buffer, EndFW.TYPE_ID, protocolTypeId);
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
                final long timestamp = abort.timestamp();
                final int protocolTypeId = resolveProtocolTypeId(abort.originId(), abort.routedId());

                encodePcapHeader(buffer, ETHER_HEADER_SIZE + ZILLA_HEADER_SIZE + abort.sizeof(), timestamp);
                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, PCAP_HEADER_LIMIT);

                encodeEtherHeader(buffer);
                writePcapOutput(writer, buffer, ETHER_HEADER_OFFSET, ETHER_HEADER_SIZE);

                encodeZillaHeader(buffer, AbortFW.TYPE_ID, protocolTypeId);
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
                final long timestamp = flush.timestamp();
                final int protocolTypeId = resolveProtocolTypeId(flush.originId(), flush.routedId());

                encodePcapHeader(buffer, ETHER_HEADER_SIZE + ZILLA_HEADER_SIZE + flush.sizeof(), timestamp);
                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, PCAP_HEADER_LIMIT);

                encodeEtherHeader(buffer);
                writePcapOutput(writer, buffer, ETHER_HEADER_OFFSET, ETHER_HEADER_SIZE);

                encodeZillaHeader(buffer, FlushFW.TYPE_ID, protocolTypeId);
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
                final long timestamp = reset.timestamp();
                final int protocolTypeId = resolveProtocolTypeId(reset.originId(), reset.routedId());

                encodePcapHeader(buffer, ETHER_HEADER_SIZE + ZILLA_HEADER_SIZE + reset.sizeof(), timestamp);
                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, PCAP_HEADER_LIMIT);

                encodeEtherHeader(buffer);
                writePcapOutput(writer, buffer, ETHER_HEADER_OFFSET, ETHER_HEADER_SIZE);

                encodeZillaHeader(buffer, ResetFW.TYPE_ID, protocolTypeId);
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
                final long timestamp = window.timestamp();
                final int protocolTypeId = resolveProtocolTypeId(window.originId(), window.routedId());

                encodePcapHeader(buffer, ETHER_HEADER_SIZE + ZILLA_HEADER_SIZE + window.sizeof(), timestamp);
                writePcapOutput(writer, buffer, PCAP_HEADER_OFFSET, PCAP_HEADER_LIMIT);

                encodeEtherHeader(buffer);
                writePcapOutput(writer, buffer, ETHER_HEADER_OFFSET, ETHER_HEADER_SIZE);

                encodeZillaHeader(buffer, WindowFW.TYPE_ID, protocolTypeId);
                writePcapOutput(writer, buffer, ZILLA_HEADER_OFFSET, ZILLA_HEADER_SIZE);

                writePcapOutput(writer, window.buffer(), window.offset(), window.sizeof());
            }
        }

        private int resolveProtocolTypeId(
            long originId,
            long routedId)
        {
            long[] origin = getBindingInfo.apply(originId);
            long[] routed = getBindingInfo.apply(routedId);
            /*System.out.printf("origin id=%016x t=%016x k=%016x ot=%016x rt=%016x%n", originId,
                origin[0], origin[1], origin[2], origin[3]);
            System.out.printf("routed id=%016x t=%016x k=%016x ot=%016x rt=%016x%n", routedId,
                routed[0], routed[1], routed[2], routed[3]);*/

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
    }
}
