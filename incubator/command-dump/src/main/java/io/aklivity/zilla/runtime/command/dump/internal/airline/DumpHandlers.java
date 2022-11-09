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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.function.Predicate;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.command.dump.internal.airline.labels.LabelManager;
import io.aklivity.zilla.runtime.command.dump.internal.types.Flyweight;
import io.aklivity.zilla.runtime.command.dump.internal.types.PcapGlobalHeaderFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.PcapPacketHeaderFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.TcpHeaderFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.WindowFW;

public class DumpHandlers
{
    private final LabelManager labelManager;
    private final Predicate<String> hasExtensionType;

    public enum Flag
    {
        URG,
        ACK,
        PSH,
        RST,
        SYN,
        FIN
    }
    private final MutableDirectBuffer writeBuffer;
    private final TcpHeaderFW.Builder tcpHeaderRW = new TcpHeaderFW.Builder();
    private final PcapGlobalHeaderFW.Builder pcapGlobalHeaderRW = new PcapGlobalHeaderFW.Builder();
    private final PcapPacketHeaderFW.Builder pcapPacketHeaderRW = new PcapPacketHeaderFW.Builder();
    private final FileChannel channel;

    private final ExtensionFW extensionRO = new ExtensionFW();

    private static final int TCP_HEADER_SIZE = 20;

    private static final byte[] PSEUDO_ETHERNET_FRAME = hexStringToByteArray("fe0000000002fe000000000186dd612123450014060" +
        "00000000000000000000000000000000000000000000000000000000000000000");

    public static byte[] hexStringToByteArray(String s)
    {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
        {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public DumpHandlers(FileChannel channel, int bufferSlotCapacity, LabelManager labelManager,
                        Predicate<String> hasExtensionType)
    {
        this.writeBuffer = new UnsafeBuffer(new byte[bufferSlotCapacity]);
        this.channel = channel;
        this.labelManager = labelManager;
        this.hasExtensionType = hasExtensionType;

        PcapGlobalHeaderFW globalHeaderFW = pcapGlobalHeaderRW.wrap(writeBuffer, 0, bufferSlotCapacity)
            .magic_number(2712847316L)
            .version_major((short) 2)
            .version_minor((short) 4)
            .thiszone(0)
            .sigfigs(0)
            .snaplen(65535)
            .link_type(1) //Ipv6 link type number
            .build();
        writeToPcapFile(globalHeaderFW);
    }


    public void onBegin(BeginFW begin)
    {
        final ExtensionFW extension = begin.extension().get(extensionRO::tryWrap);
        if (extension != null)
        {
            //Get the lower 32 bits from the long
            int bindingId = (int) begin.routeId();
            if (hasExtensionType.test(labelManager.lookupLabel(bindingId)))
            {
                final long streamId = begin.streamId();
                TcpHeaderFW tcpHeader = createTcpHeader(streamId, Flag.SYN);
                PcapPacketHeaderFW pcapHeader = createPcapPacketHeader(PSEUDO_ETHERNET_FRAME.length + tcpHeader.sizeof(),
                    begin.timestamp());
                writeToPcapFile(pcapHeader);
                writeToPcapFile(PSEUDO_ETHERNET_FRAME);
                writeToPcapFile(tcpHeader);
            }
        }
    }

    public void onData(DataFW data)
    {
        final ExtensionFW extension = data.extension().get(extensionRO::tryWrap);
        if (extension != null)
        {
            int bindingId = (int) data.routeId();
            if (hasExtensionType.test(labelManager.lookupLabel(bindingId)))
            {
                final long streamId = data.streamId();
                byte[] bytes = new byte[data.offset() + data.limit()];

                if (data.payload() != null)
                {
                    DirectBuffer buffer = data.payload().buffer();
                    buffer.getBytes(0, bytes, data.offset(), data.limit());

                    TcpHeaderFW tcpHeader = createTcpHeader(streamId, Flag.PSH);
                    PcapPacketHeaderFW pcapHeader = createPcapPacketHeader(PSEUDO_ETHERNET_FRAME.length +
                        tcpHeader.sizeof() + bytes.length, data.timestamp());
                    writeToPcapFile(pcapHeader);
                    writeToPcapFile(PSEUDO_ETHERNET_FRAME);
                    writeToPcapFile(tcpHeader);
                    writeToPcapFile(bytes);
                }
            }
        }
    }

    public void onEnd(EndFW end)
    {
        final ExtensionFW extension = end.extension().get(extensionRO::tryWrap);
        if (extension != null)
        {
            int bindingId = (int) end.routeId();
            if (hasExtensionType.test(labelManager.lookupLabel(bindingId)))
            {
                final long streamId = end.streamId();
                TcpHeaderFW tcpHeader = createTcpHeader(streamId, Flag.FIN);
                PcapPacketHeaderFW pcapHeader = createPcapPacketHeader(PSEUDO_ETHERNET_FRAME.length + tcpHeader.sizeof(),
                    end.timestamp());
                writeToPcapFile(pcapHeader);
                writeToPcapFile(PSEUDO_ETHERNET_FRAME);
                writeToPcapFile(tcpHeader);
            }
        }
    }

    public void onAbort(AbortFW abort)
    {
        final long streamId = abort.streamId();
        TcpHeaderFW tcpHeader = createTcpHeader(streamId, Flag.RST);
        PcapPacketHeaderFW pcapHeader = createPcapPacketHeader(PSEUDO_ETHERNET_FRAME.length + tcpHeader.sizeof(),
            abort.timestamp());
        writeToPcapFile(pcapHeader);
        writeToPcapFile(PSEUDO_ETHERNET_FRAME);
        writeToPcapFile(tcpHeader);
    }

    public void onReset(ResetFW reset)
    {
        final ExtensionFW extension = reset.extension().get(extensionRO::tryWrap);
        if (extension != null)
        {
            int bindingId = (int) reset.routeId();
            if (hasExtensionType.test(labelManager.lookupLabel(bindingId)))
            {
                final long streamId = reset.streamId();

                TcpHeaderFW tcpHeader = createTcpHeader(streamId, Flag.RST);
                PcapPacketHeaderFW pcapHeader = createPcapPacketHeader(PSEUDO_ETHERNET_FRAME.length + tcpHeader.sizeof(),
                    reset.timestamp());
                writeToPcapFile(pcapHeader);
                writeToPcapFile(PSEUDO_ETHERNET_FRAME);
                writeToPcapFile(tcpHeader);
            }
        }
    }

    public void onWindow(WindowFW window)
    {
        final long streamId = window.streamId();

        TcpHeaderFW tcpHeader = createTcpHeader(streamId, Flag.ACK);
        PcapPacketHeaderFW pcapHeader = createPcapPacketHeader(PSEUDO_ETHERNET_FRAME.length + tcpHeader.sizeof(),
            window.timestamp());
        writeToPcapFile(pcapHeader);
        writeToPcapFile(PSEUDO_ETHERNET_FRAME);
        writeToPcapFile(tcpHeader);
    }

    public void onSignal(SignalFW signal)
    {
        final long streamId = signal.streamId();
        TcpHeaderFW tcpHeader = createTcpHeader(streamId, Flag.PSH);
        PcapPacketHeaderFW pcapHeader = createPcapPacketHeader(PSEUDO_ETHERNET_FRAME.length + tcpHeader.sizeof(),
            signal.timestamp());
        writeToPcapFile(pcapHeader);
        writeToPcapFile(PSEUDO_ETHERNET_FRAME);
        writeToPcapFile(tcpHeader);
    }

    public void onChallenge(ChallengeFW challenge)
    {
        final long streamId = challenge.streamId();
        TcpHeaderFW tcpHeader = createTcpHeader(streamId, Flag.PSH);
        PcapPacketHeaderFW pcapHeader = createPcapPacketHeader(PSEUDO_ETHERNET_FRAME.length + tcpHeader.sizeof(),
            challenge.timestamp());
        writeToPcapFile(pcapHeader);
        writeToPcapFile(PSEUDO_ETHERNET_FRAME);
        writeToPcapFile(tcpHeader);
    }

    public void onFlush(FlushFW flush)
    {
        final ExtensionFW extension = flush.extension().get(extensionRO::tryWrap);
        if (extension != null)
        {
            int bindingId = (int) flush.routeId();
            if (hasExtensionType.test(labelManager.lookupLabel(bindingId)))
            {
                final long streamId = flush.streamId();
                TcpHeaderFW tcpHeader = createTcpHeader(streamId, Flag.RST);
                PcapPacketHeaderFW pcapHeader = createPcapPacketHeader(PSEUDO_ETHERNET_FRAME.length + tcpHeader.sizeof(),
                    flush.timestamp());
                writeToPcapFile(pcapHeader);
                writeToPcapFile(PSEUDO_ETHERNET_FRAME);
                writeToPcapFile(tcpHeader);
            }
        }
    }

    private void writeToPcapFile(Flyweight flyweight)
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

    private void writeToPcapFile(byte[] bytes)
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

    private PcapPacketHeaderFW createPcapPacketHeader(long length, long timestamp)
    {
        return pcapPacketHeaderRW.wrap(writeBuffer, TCP_HEADER_SIZE, writeBuffer.capacity())
            .ts_sec(timestamp / 1000)
            .ts_usec(0)
            .incl_len(length)
            .orig_len(length)
            .build();
    }

    private TcpHeaderFW createTcpHeader(long streamId, Flag flag)
    {
        short port = getPort(streamId);
        short other = getOtherFields(flag);
        return tcpHeaderRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .src_port(port)
            .dst_port(port)
            .sequence_number(1000)
            .acknowledgment_number(2000)
            .other_fields(other)
            .window((short) 1024)
            .checksum((short) 0)
            .urgent_pointer((short) 0)
            .build();
    }

    private short getOtherFields(Flag flag)
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
        byte[] bytes = new byte[] {flags, dataOffsetAndReserved};
        ByteBuffer buffer = ByteBuffer.allocate(2).put(bytes);
        return buffer.getShort(0);
    }

    private short getPort(long streamId)
    {
        byte[] streamIdBytes =  longToBytes(streamId);
        return byteArrayToShort(Arrays.copyOfRange(streamIdBytes, streamIdBytes.length - 2, streamIdBytes.length));
    }

    private byte[] longToBytes(long x)
    {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }

    private short byteArrayToShort(byte[] array)
    {
        assert array.length == 2;
        return (short) ((array[0] << 8) | (array[1] & 0xFF));
    }
}
