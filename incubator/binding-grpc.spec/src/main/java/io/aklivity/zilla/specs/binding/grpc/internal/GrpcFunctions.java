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
package io.aklivity.zilla.specs.binding.grpc.internal;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.MutableBoolean;
import org.agrona.concurrent.UnsafeBuffer;
import org.kaazing.k3po.lang.el.BytesMatcher;
import org.kaazing.k3po.lang.el.Function;
import org.kaazing.k3po.lang.el.spi.FunctionMapperSpi;

import io.aklivity.zilla.specs.binding.grpc.internal.types.OctetsFW;
import io.aklivity.zilla.specs.binding.grpc.internal.types.stream.GrpcBeginExFW;
import io.aklivity.zilla.specs.binding.grpc.internal.types.stream.GrpcKind;
import io.aklivity.zilla.specs.engine.internal.types.Varuint32FW;

public final class GrpcFunctions
{
    private static final int VARINT_WIRE_TYPE = 0;
    private static final int I32_WIRE_TYPE = 5;
    private static final int I64_WIRE_TYPE = 1;
    private static final int BYTES_WIRE_TYPE = 2;
    private static final int START_GROUP_WIRE_TYPE = 3;
    private static final int END_GROUP_WIRE_TYPE = 4;

    @Function
    public static GrpcMessageBuilder message()
    {
        return new GrpcMessageBuilder();
    }

    @Function
    public static ProtobufBuilder protobuf()
    {
        return new ProtobufBuilder();
    }

    @Function
    public static GrpcBeginExBuilder beginEx()
    {
        return new GrpcBeginExBuilder();
    }

    @Function
    public static GrpcBeginExMatcherBuilder matchBeginEx()
    {
        return new GrpcBeginExMatcherBuilder();
    }

    public static final class GrpcBeginExBuilder
    {
        private final GrpcBeginExFW.Builder beginExRW;
        private final OctetsFW.Builder valueBuilder;

        private GrpcBeginExBuilder()
        {
            valueBuilder = new OctetsFW.Builder().wrap(new UnsafeBuffer(new byte[1024 * 8]), 0, 1024 * 8);
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            this.beginExRW = new GrpcBeginExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public GrpcBeginExBuilder typeId(
            int typeId)
        {
            beginExRW.typeId(typeId);
            return this;
        }

        public GrpcBeginExBuilder method(
            String method)
        {
            beginExRW.method(method);
            return this;
        }

        public GrpcBeginExBuilder request(
            String request)
        {
            beginExRW.request(r -> r.set(GrpcKind.valueOf(request)));
            return this;
        }

        public GrpcBeginExBuilder response(
            String response)
        {
            beginExRW.response(r -> r.set(GrpcKind.valueOf(response)));
            return this;
        }

        public GrpcBeginExBuilder metadata(
            String name,
            String value)
        {
            beginExRW.metadataItem(b -> b.name(name).valueLen(value.length())
                .value(valueBuilder.set(value.getBytes()).build()));
            return this;
        }

        public byte[] build()
        {
            final GrpcBeginExFW beginEx = beginExRW.build();
            final byte[] array = new byte[beginEx.sizeof()];
            beginEx.buffer().getBytes(beginEx.offset(), array);
            return array;
        }
    }

    public static final class GrpcBeginExMatcherBuilder
    {
        private final DirectBuffer bufferRO = new UnsafeBuffer();

        private final GrpcBeginExFW beginExRO = new GrpcBeginExFW();
        private final OctetsFW.Builder valueBuilder =
            new OctetsFW.Builder().wrap(new UnsafeBuffer(new byte[1024 * 8]), 0, 1024 * 8);

        private final Map<String, Predicate<OctetsFW>> metadata = new LinkedHashMap<>();


        private String method;
        private GrpcKind request;
        private GrpcKind response;
        private int typeId;

        public GrpcBeginExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public GrpcBeginExMatcherBuilder method(
            String method)
        {
            this.method = method;
            return this;
        }

        public GrpcBeginExMatcherBuilder request(
            String request)
        {
            this.request = GrpcKind.valueOf(request);
            return this;
        }

        public GrpcBeginExMatcherBuilder response(
            String response)
        {
            this.response = GrpcKind.valueOf(response);
            return this;
        }

        public GrpcBeginExMatcherBuilder metadata(
            String name,
            String value)
        {
            metadata.put(name, valueBuilder.set(value.getBytes()).build()::equals);
            return this;
        }

        public BytesMatcher build()
        {
            return method != null ? this::match : buf -> null;
        }

        private GrpcBeginExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final GrpcBeginExFW beginEx = beginExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (beginEx != null &&
                matchTypeId(beginEx) &&
                matchMethod(beginEx) &&
                matchRequest(beginEx) &&
                matchResponse(beginEx) &&
                matchMetadata(beginEx))
            {
                byteBuf.position(byteBuf.position() + beginEx.sizeof());
                return beginEx;
            }

            throw new Exception(beginEx.toString());
        }

        private boolean matchMetadata(
            GrpcBeginExFW beginEx)
        {
            MutableBoolean match = new MutableBoolean(true);
            metadata.forEach((k, v) -> match.value &= beginEx.metadata().anyMatch(h -> k.equals(h.name().asString()) &&
                v.test(h.value())));
            return match.value;
        }

        private boolean matchMethod(
            GrpcBeginExFW beginEx)
        {
            return method.equals(beginEx.method().asString());
        }

        private boolean matchRequest(
            GrpcBeginExFW beginEx)
        {
            return request == beginEx.request().get();
        }

        private boolean matchResponse(
            GrpcBeginExFW beginEx)
        {
            return response == beginEx.response().get();
        }

        private boolean matchTypeId(
            GrpcBeginExFW beginEx)
        {
            return typeId == beginEx.typeId();
        }
    }

    public static final class GrpcMessageBuilder
    {
        private final Varuint32FW.Builder keyRW;
        private final Varuint32FW.Builder lenRW;
        private final MutableDirectBuffer messageBuffer = new UnsafeBuffer(new byte[1024 * 8]);

        private int messageBufferLimit = 5;


        private GrpcMessageBuilder()
        {
            MutableDirectBuffer keyBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            keyRW = new Varuint32FW.Builder().wrap(keyBuffer, 0, keyBuffer.capacity());
            MutableDirectBuffer lenBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            lenRW = new Varuint32FW.Builder().wrap(lenBuffer, 0, lenBuffer.capacity());
        }

        public GrpcMessageBuilder string(
            int field,
            String value)
        {
            Varuint32FW key = keyRW.set(field << 3 | BYTES_WIRE_TYPE).build();
            final int keySize = key.sizeof();
            messageBuffer.putBytes(messageBufferLimit, key.buffer(), key.offset(), key.sizeof());
            messageBufferLimit += keySize;

            final byte[] bytes = value.getBytes();
            Varuint32FW len = lenRW.set(bytes.length).build();
            int lenSize = len.sizeof();
            messageBuffer.putBytes(messageBufferLimit, len.buffer(), len.offset(), lenSize);
            messageBufferLimit += lenSize;
            messageBuffer.putBytes(messageBufferLimit, bytes);
            messageBufferLimit += bytes.length;
            return this;
        }

        public byte[] build()
        {
            final byte[] array = new byte[messageBufferLimit];
            messageBuffer.putByte(0, (byte) 0);
            messageBuffer.putInt(1, messageBufferLimit - 5, ByteOrder.BIG_ENDIAN);
            messageBuffer.getBytes(0, array);
            return array;
        }
    }

    public static final class ProtobufBuilder
    {
        private final Varuint32FW.Builder keyRW;
        private final Varuint32FW.Builder lenRW;
        private final MutableDirectBuffer messageBuffer = new UnsafeBuffer(new byte[1024 * 8]);
        private int messageBufferLimit = 0;


        private ProtobufBuilder()
        {
            MutableDirectBuffer keyBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            keyRW = new Varuint32FW.Builder().wrap(keyBuffer, 0, keyBuffer.capacity());
            MutableDirectBuffer lenBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            lenRW = new Varuint32FW.Builder().wrap(lenBuffer, 0, lenBuffer.capacity());
        }

        public ProtobufBuilder string(
            int field,
            String value)
        {
            Varuint32FW key = keyRW.set(field << 3 | BYTES_WIRE_TYPE).build();
            final int keySize = key.sizeof();
            messageBuffer.putBytes(messageBufferLimit, key.buffer(), key.offset(), key.sizeof());
            messageBufferLimit += keySize;

            final byte[] bytes = value.getBytes();
            Varuint32FW len = lenRW.set(bytes.length).build();
            int lenSize = len.sizeof();
            messageBuffer.putBytes(messageBufferLimit, len.buffer(), len.offset(), lenSize);
            messageBufferLimit += lenSize;
            messageBuffer.putBytes(messageBufferLimit, bytes);
            messageBufferLimit += bytes.length;
            return this;
        }

        public byte[] build()
        {
            final byte[] array = new byte[messageBufferLimit];
            messageBuffer.getBytes(0, array);
            return array;
        }
    }

    public static class Mapper extends FunctionMapperSpi.Reflective
    {
        public Mapper()
        {
            super(GrpcFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "grpc";
        }
    }

    private GrpcFunctions()
    {
        // utility
    }
}
