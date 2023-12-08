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

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;
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
import java.util.Properties;
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
import org.agrona.collections.Int2IntHashMap;
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
import io.aklivity.zilla.runtime.command.dump.internal.types.Flyweight;
import io.aklivity.zilla.runtime.command.dump.internal.types.IPv6HeaderFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.PcapGlobalHeaderFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.PcapPacketHeaderFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.TcpFlag;
import io.aklivity.zilla.runtime.command.dump.internal.types.TcpHeaderFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.FrameFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.binding.function.MessagePredicate;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.reader.BindingsReader;

@Command(name = "dump", description = "Dump stream content")
public final class ZillaDumpCommand extends ZillaCommand
{
    private static final String OPTION_PROPERTIES_PATH_DEFAULT = ".zilla/zilla.properties";
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

    private static final byte[] PSEUDO_ETHERNET_FRAME = BitUtil.fromHex("2052454356002053454e440086dd");
    private static final int PSEUDO_IPV6_PREFIX = 1629561669;
    private static final short PSEUDO_NEXT_HEADER_AND_HOP_LIMIT = 0x0640;
    private static final int IPV6_LOCAL_ADDRESS = 0xfe80;

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

    private static final int ZILLA_HEADER_OFFSET = TCP_HEADER_LIMIT;
    private static final int ZILLA_PROTOCOL_TYPE_OFFSET = ZILLA_HEADER_OFFSET + 4;
    private static final int ZILLA_HEADER_SIZE = 8;
    private static final int ZILLA_HEADER_LIMIT = ZILLA_HEADER_OFFSET + ZILLA_HEADER_SIZE;

    private static final int TYPE_ID_INDEX = 0;
    private static final int KIND_ID_INDEX = 1;
    private static final int ORIGIN_TYPE_ID_INDEX = 2;
    private static final int ROUTED_TYPE_ID_INDEX = 3;

    private static final String SERVER_KIND = KindConfig.SERVER.name().toLowerCase();
    private static final String CLIENT_KIND = KindConfig.CLIENT.name().toLowerCase();
    private static final String UNKNOWN_LABEL = "??";
    private static final byte[] EMPTY_BYTES = new byte[0];

    private static final short TCP_DEST_PORT = 7114;
    private static final short TCP_SRC_PORT = 0;
    private static final short PSH_ACK = (short) (TcpFlag.PSH.value() | TcpFlag.ACK.value());
    private static final short PSH_ACK_SYN = (short) (TcpFlag.PSH.value() | TcpFlag.ACK.value() | TcpFlag.SYN.value());
    private static final short PSH_ACK_FIN = (short) (TcpFlag.PSH.value() | TcpFlag.ACK.value() | TcpFlag.FIN.value());

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

    @Option(name = {"-P", "--property"},
        description = "Property name=value",
        hidden = true)
    public List<String> properties;

    @Option(name = {"-p", "--properties"},
        description = "Path to properties",
        hidden = true)
    public String propertiesPath;

    boolean continuous = true;

    private final FrameFW frameRO = new FrameFW();
    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final FlushFW flushRO = new FlushFW();
    private final SignalFW signalRO = new SignalFW();
    private final ChallengeFW challengeRO = new ChallengeFW();
    private final PcapGlobalHeaderFW.Builder pcapGlobalHeaderRW = new PcapGlobalHeaderFW.Builder();
    private final PcapPacketHeaderFW.Builder pcapPacketHeaderRW = new PcapPacketHeaderFW.Builder();
    private final IPv6HeaderFW.Builder ipv6HeaderRW = new IPv6HeaderFW.Builder();
    private final TcpHeaderFW.Builder tcpHeaderRW = new TcpHeaderFW.Builder();
    private final MutableDirectBuffer writeBuffer;

    private Path directory;
    private BeginFW begin2RO = new BeginFW();
    private DataFW data2RO = new DataFW();
    private EndFW end2RO = new EndFW();
    private AbortFW abort2RO = new AbortFW();
    private ResetFW reset2RO = new ResetFW();
    private FlushFW flush2RO = new FlushFW();
    private ChallengeFW challenge2RO = new ChallengeFW();
    private ExtensionFW extension = new ExtensionFW();

    public ZillaDumpCommand()
    {
        this.writeBuffer = new UnsafeBuffer(ByteBuffer.allocate(BUFFER_SLOT_CAPACITY));
    }

    @Override
    public void run()
    {
        Properties props = new Properties();
        props.setProperty(ENGINE_DIRECTORY.name(), ".zilla/engine");

        Path path = Paths.get(propertiesPath != null ? propertiesPath : OPTION_PROPERTIES_PATH_DEFAULT);
        if (Files.exists(path) || propertiesPath != null)
        {
            try
            {
                props.load(Files.newInputStream(path));
            }
            catch (IOException ex)
            {
                System.out.println("Failed to load properties: " + path);
                rethrowUnchecked(ex);
            }
        }

        if (properties != null)
        {
            for (String property : properties)
            {
                final int equalsAt = property.indexOf('=');
                String name = equalsAt != -1 ? property.substring(0, equalsAt) : property;
                String value = equalsAt != -1 ? property.substring(equalsAt + 1) : "true";

                props.put(name, value);
            }
        }

        this.directory = Paths.get(props.getProperty(ENGINE_DIRECTORY.name()));
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
        private final Long2LongHashMap sequence;
        private final Int2IntHashMap crcCache;

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
            this.extensionRO = new ExtensionFW();
            this.labelsBuffer = new UnsafeBuffer(ByteBuffer.allocate(LABELS_BUFFER_SLOT_CAPACITY));
            this.sequence = new Long2LongHashMap(0L);
            this.crcCache = new Int2IntHashMap(0);
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
            case SignalFW.TYPE_ID:
                onSignal(signalRO.wrap(buffer, index, index + length));
                break;
            case ChallengeFW.TYPE_ID:
                onChallenge(challengeRO.wrap(buffer, index, index + length));
                break;
            default:
                break;
            }

            return true;
        }

        private void onBegin(
            BeginFW begin)
        {
            if (allowedBinding.test(begin.routedId()))
            {
                final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(begin.sizeof()));
                begin2RO = new BeginFW.Builder().wrap(buffer, 0, begin.sizeof()).set(begin).build();
                extension = begin2RO.extension().get(extensionRO::tryWrap);
                patchExtension(buffer, extension, BeginFW.FIELD_OFFSET_EXTENSION);

                final boolean initial = begin.streamId() % 2 != 0;
                short tcpFlags = initial ? PSH_ACK_SYN : PSH_ACK;
                writeFrame(BeginFW.TYPE_ID, begin2RO.originId(), begin2RO.routedId(), begin2RO.streamId(), begin2RO.timestamp(),
                    begin2RO, tcpFlags);
            }
        }

        private void onData(
            DataFW data)
        {
            if (allowedBinding.test(data.routedId()))
            {
                final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(data.sizeof()));
                data2RO = new DataFW.Builder().wrap(buffer, 0, data.sizeof()).set(data).build();
                extension = data2RO.extension().get(extensionRO::tryWrap);
                patchExtension(buffer, extension, DataFW.FIELD_OFFSET_EXTENSION);

                writeFrame(DataFW.TYPE_ID, data2RO.originId(), data2RO.routedId(), data2RO.streamId(), data2RO.timestamp(),
                    data2RO, PSH_ACK);
            }
        }

        private void onEnd(
            EndFW end)
        {
            if (allowedBinding.test(end.routedId()))
            {
                final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(end.sizeof()));
                end2RO = new EndFW.Builder().wrap(buffer, 0, end.sizeof()).set(end).build();
                extension = end2RO.extension().get(extensionRO::tryWrap);
                patchExtension(buffer, extension, EndFW.FIELD_OFFSET_EXTENSION);

                writeFrame(EndFW.TYPE_ID, end2RO.originId(), end2RO.routedId(), end2RO.streamId(), end2RO.timestamp(),
                    end2RO, PSH_ACK_FIN);
            }
        }

        private void onAbort(
            AbortFW abort)
        {
            if (allowedBinding.test(abort.routedId()))
            {
                final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(abort.sizeof()));
                abort2RO = new AbortFW.Builder().wrap(buffer, 0, abort.sizeof()).set(abort).build();
                extension = abort2RO.extension().get(extensionRO::tryWrap);
                patchExtension(buffer, extension, AbortFW.FIELD_OFFSET_EXTENSION);

                writeFrame(AbortFW.TYPE_ID, abort2RO.originId(), abort2RO.routedId(), abort2RO.streamId(), abort2RO.timestamp(),
                    abort2RO, PSH_ACK_FIN);
            }
        }

        private void onWindow(
            WindowFW window)
        {
            if (allowedBinding.test(window.routedId()))
            {
                writeFrame(WindowFW.TYPE_ID, window.originId(), window.routedId(), window.streamId(), window.timestamp(), window,
                    PSH_ACK);
            }
        }

        private void onReset(
            ResetFW reset)
        {
            if (allowedBinding.test(reset.routedId()))
            {
                final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(reset.sizeof()));
                reset2RO = new ResetFW.Builder().wrap(buffer, 0, reset.sizeof()).set(reset).build();
                extension = reset2RO.extension().get(extensionRO::tryWrap);
                patchExtension(buffer, extension, ResetFW.FIELD_OFFSET_EXTENSION);

                writeFrame(ResetFW.TYPE_ID, reset2RO.originId(), reset2RO.routedId(), reset2RO.streamId(), reset2RO.timestamp(),
                    reset2RO, PSH_ACK_FIN);
            }
        }

        private void onFlush(
            FlushFW flush)
        {
            if (allowedBinding.test(flush.routedId()))
            {
                final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(flush.sizeof()));
                flush2RO = new FlushFW.Builder().wrap(buffer, 0, flush.sizeof()).set(flush).build();
                extension = flush2RO.extension().get(extensionRO::tryWrap);
                patchExtension(buffer, extension, FlushFW.FIELD_OFFSET_EXTENSION);

                writeFrame(FlushFW.TYPE_ID, flush2RO.originId(), flush2RO.routedId(), flush2RO.streamId(), flush2RO.timestamp(),
                    flush2RO, PSH_ACK);
            }
        }

        private void onSignal(
            SignalFW signal)
        {
            if (allowedBinding.test(signal.routedId()))
            {
                writeFrame(SignalFW.TYPE_ID, signal.originId(), signal.routedId(), signal.streamId(), signal.timestamp(), signal,
                    PSH_ACK);
            }
        }

        private void onChallenge(
            ChallengeFW challenge)
        {
            if (allowedBinding.test(challenge.routedId()))
            {
                final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(challenge.sizeof()));
                challenge2RO = new ChallengeFW.Builder().wrap(buffer, 0, challenge.sizeof()).set(challenge).build();
                extension = challenge2RO.extension().get(extensionRO::tryWrap);
                patchExtension(buffer, extension, ChallengeFW.FIELD_OFFSET_EXTENSION);

                writeFrame(ChallengeFW.TYPE_ID, challenge2RO.originId(), challenge2RO.routedId(), challenge2RO.streamId(),
                    challenge2RO.timestamp(), challenge2RO, PSH_ACK);
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

        private int calculateLabelCrc(
            int labelId)
        {
            int result = 0;
            if (labelId != 0)
            {
                result = crcCache.get(labelId);
                if (result == 0)
                {
                    crc.reset();
                    crc.update(resolveLabelAsBytes(labelId));
                    result = (int) crc.getValue();
                    crcCache.put(labelId, result);
                }
            }
            return result;
        }

        private byte[] resolveLabelAsBytes(
            int labelId)
        {
            byte[] result = EMPTY_BYTES;
            if (labelId != 0)
            {
                String label = lookupLabel.apply(labelId);
                if (!UNKNOWN_LABEL.equals(label))
                {
                    result = label.getBytes(StandardCharsets.UTF_8);
                }
            }
            return result;
        }

        private void writeFrame(
            int frameTypeId,
            long originId,
            long routedId,
            long streamId,
            long timestamp,
            Flyweight frame,
            short tcpFlags)
        {
            final int labelsLength = encodeZillaLabels(labelsBuffer, originId, routedId);
            final int tcpSegmentLength = ZILLA_HEADER_SIZE + labelsLength + frame.sizeof();
            final int ipv6Length = TCP_HEADER_SIZE + tcpSegmentLength;
            final int pcapLength = ETHER_HEADER_SIZE + IPV6_HEADER_SIZE + ipv6Length;

            encodePcapHeader(writeBuffer, pcapLength, timestamp);
            encodeEtherHeader(writeBuffer);
            encodeIpv6Header(writeBuffer, streamId ^ 1L, streamId, ipv6Length);

            final boolean initial = streamId % 2 != 0;
            final long seq = sequence.get(streamId);
            final long ack = sequence.get(streamId ^ 1L);
            sequence.put(streamId, sequence.get(streamId) + tcpSegmentLength);
            encodeTcpHeader(writeBuffer, initial, seq, ack, tcpFlags);

            final int protocolTypeId = resolveProtocolTypeId(originId, routedId);
            encodeZillaHeader(writeBuffer, frameTypeId, protocolTypeId);

            writePcapOutput(writer, writeBuffer, PCAP_HEADER_OFFSET, ZILLA_HEADER_LIMIT);
            writePcapOutput(writer, labelsBuffer, 0, labelsLength);
            writePcapOutput(writer, frame.buffer(), frame.offset(), frame.sizeof());
        }

        private int resolveProtocolTypeId(
            long originId,
            long routedId)
        {
            int protocolTypeLabelId = 0;
            long[] origin = getBindingInfo.apply(originId);
            long[] routed = getBindingInfo.apply(routedId);

            if (origin != null && routed != null)
            {
                String routedBindingKind = lookupLabel.apply(localId(routed[KIND_ID_INDEX]));
                if (SERVER_KIND.equals(routedBindingKind))
                {
                    protocolTypeLabelId = localId(routed[ROUTED_TYPE_ID_INDEX]);
                }
                String originBindingKind = lookupLabel.apply(localId(routed[KIND_ID_INDEX]));
                if (protocolTypeLabelId == 0 && CLIENT_KIND.equals(originBindingKind))
                {
                    protocolTypeLabelId = localId(origin[ORIGIN_TYPE_ID_INDEX]);
                }
            }
            return calculateLabelCrc(protocolTypeLabelId);
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
            int payloadLength)
        {
            ipv6HeaderRW.wrap(buffer, IPV6_HEADER_OFFSET, buffer.capacity())
                .prefix(PSEUDO_IPV6_PREFIX)
                .payload_length((short) payloadLength)
                .next_header_and_hop_limit(PSEUDO_NEXT_HEADER_AND_HOP_LIMIT)
                .src_addr_part1(IPV6_LOCAL_ADDRESS)
                .src_addr_part2(source)
                .dst_addr_part1(IPV6_LOCAL_ADDRESS)
                .dst_addr_part2(destination)
                .build();
        }

        private void encodeTcpHeader(
            MutableDirectBuffer buffer,
            boolean initial,
            long sequence,
            long acknowledge,
            short flagValue)
        {
            final short otherFields = (short) (0x5000 | flagValue);
            short sourcePort = initial ? TCP_SRC_PORT : TCP_DEST_PORT;
            short destPort = initial ? TCP_DEST_PORT : TCP_SRC_PORT;

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

        private void encodeZillaHeader(
            MutableDirectBuffer buffer,
            int frameTypeId,
            int protocolTypeId)
        {
            buffer.putInt(ZILLA_HEADER_OFFSET, frameTypeId);
            buffer.putInt(ZILLA_PROTOCOL_TYPE_OFFSET, protocolTypeId);
        }

        private int encodeZillaLabels(
            MutableDirectBuffer buffer,
            long originId,
            long routedId)
        {
            byte[] originNamespace = resolveLabelAsBytes(namespaceId(originId));
            byte[] originBinding = resolveLabelAsBytes(localId(originId));
            byte[] routedNamespace = resolveLabelAsBytes(namespaceId(routedId));
            byte[] routedBinding = resolveLabelAsBytes(localId(routedId));
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
