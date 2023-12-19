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
package io.aklivity.zilla.specs.binding.grpc.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.MutableBoolean;
import org.agrona.concurrent.UnsafeBuffer;
import org.kaazing.k3po.lang.el.BytesMatcher;
import org.kaazing.k3po.lang.el.Function;
import org.kaazing.k3po.lang.el.spi.FunctionMapperSpi;

import io.aklivity.zilla.specs.binding.grpc.internal.types.OctetsFW;
import io.aklivity.zilla.specs.binding.grpc.internal.types.stream.GrpcAbortExFW;
import io.aklivity.zilla.specs.binding.grpc.internal.types.stream.GrpcBeginExFW;
import io.aklivity.zilla.specs.binding.grpc.internal.types.stream.GrpcDataExFW;
import io.aklivity.zilla.specs.binding.grpc.internal.types.stream.GrpcResetExFW;
import io.aklivity.zilla.specs.binding.grpc.internal.types.stream.GrpcType;
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
    public static String randomString(
        int length)
    {
        Random random = ThreadLocalRandom.current();
        byte[] result = new byte[length];
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ" +
            "1234567890!@#$%^&*()_+-=`~[]\\{}|;':\",./<>?";
        for (int i = 0; i < length; i++)
        {
            result[i] = (byte) alphabet.charAt(random.nextInt(alphabet.length()));
        }

        return new String(result, StandardCharsets.UTF_8);
    }

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

    @Function
    public static GrpcResetExBuilder resetEx()
    {
        return new GrpcResetExBuilder();
    }

    @Function
    public static GrpcAbortExBuilder abortEx()
    {
        return new GrpcAbortExBuilder();
    }

    @Function
    public static GrpcDataExBuilder dataEx()
    {
        return new GrpcDataExBuilder();
    }

    public static final class GrpcBeginExBuilder
    {
        private final GrpcBeginExFW.Builder beginExRW;
        private final OctetsFW.Builder nameBuilder;
        private final OctetsFW.Builder valueBuilder;

        private GrpcBeginExBuilder()
        {
            nameBuilder = new OctetsFW.Builder().wrap(new UnsafeBuffer(new byte[1024 * 8]), 0, 1024 * 8);
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

        public GrpcBeginExBuilder scheme(
            String scheme)
        {
            beginExRW.scheme(scheme);
            return this;
        }

        public GrpcBeginExBuilder authority(
            String authority)
        {
            beginExRW.authority(authority);
            return this;
        }

        public GrpcBeginExBuilder service(
            String service)
        {
            beginExRW.service(service);
            return this;
        }

        public GrpcBeginExBuilder method(
            String method)
        {
            beginExRW.method(method);
            return this;
        }

        public GrpcBeginExBuilder metadata(
            String name,
            String value)
        {
            return metadata("TEXT", name, value);
        }

        public GrpcBeginExBuilder metadata(
            String type,
            String name,
            String value)
        {
            beginExRW.metadataItem(b -> b.type(t -> t.set(GrpcType.valueOf(type)))
                .nameLen(name.length())
                .name(nameBuilder.set(name.getBytes()).build())
                .valueLen(value.length())
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
        private final OctetsFW.Builder nameBuilder =
            new OctetsFW.Builder().wrap(new UnsafeBuffer(new byte[1024 * 8]), 0, 1024 * 8);
        private final OctetsFW.Builder valueBuilder =
            new OctetsFW.Builder().wrap(new UnsafeBuffer(new byte[1024 * 8]), 0, 1024 * 8);

        private final Map<OctetsFW, MetadataValue> metadata = new LinkedHashMap<>();

        private int typeId;
        private String scheme;
        private String authority;
        private String service;
        private String method;

        public GrpcBeginExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public GrpcBeginExMatcherBuilder scheme(
            String scheme)
        {
            this.scheme = scheme;
            return this;
        }

        public GrpcBeginExMatcherBuilder authority(
            String authority)
        {
            this.authority = authority;
            return this;
        }

        public GrpcBeginExMatcherBuilder service(
            String service)
        {
            this.service = service;
            return this;
        }

        public GrpcBeginExMatcherBuilder method(
            String method)
        {
            this.method = method;
            return this;
        }

        public GrpcBeginExMatcherBuilder metadata(
            String name,
            String value)
        {
            return metadata("TEXT", name, value);
        }

        public GrpcBeginExMatcherBuilder metadata(
            String type,
            String name,
            String value)
        {
            metadata.put(nameBuilder.set(name.getBytes()).build(),
                new MetadataValue(GrpcType.valueOf(type), valueBuilder.set(value.getBytes()).build()::equals));
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
                matchScheme(beginEx) &&
                matchAuthority(beginEx) &&
                matchService(beginEx) &&
                matchMethod(beginEx) &&
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
            metadata.forEach((k, v) -> match.value &= beginEx.metadata().anyMatch(h ->
                 h.type().get() == v.type && k.equals(h.name()) && v.value.test(h.value())));
            return match.value;
        }

        private boolean matchMethod(
            GrpcBeginExFW beginEx)
        {
            return method.equals(beginEx.method().asString());
        }

        private boolean matchScheme(
            GrpcBeginExFW beginEx)
        {
            return scheme.equals(beginEx.scheme().asString());
        }

        private boolean matchAuthority(
            GrpcBeginExFW beginEx)
        {
            return authority.equals(beginEx.authority().asString());
        }

        private boolean matchService(
            GrpcBeginExFW beginEx)
        {
            return service.equals(beginEx.service().asString());
        }

        private boolean matchTypeId(
            GrpcBeginExFW beginEx)
        {
            return typeId == beginEx.typeId();
        }

        private class MetadataValue
        {
            private GrpcType type;
            private Predicate<OctetsFW> value;

            MetadataValue(
                GrpcType type,
                Predicate<OctetsFW> value)
            {

                this.type = type;
                this.value = value;
            }
        }
    }

    public static final class GrpcDataExBuilder
    {
        private final GrpcDataExFW.Builder dataExRW;

        private GrpcDataExBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            this.dataExRW = new GrpcDataExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public GrpcDataExBuilder typeId(
            int typeId)
        {
            dataExRW.typeId(typeId);
            return this;
        }

        public GrpcDataExBuilder deferred(
            int deferred)
        {
            dataExRW.deferred(deferred);
            return this;
        }

        public byte[] build()
        {
            final GrpcDataExFW dataEx = dataExRW.build();
            final byte[] array = new byte[dataEx.sizeof()];
            dataEx.buffer().getBytes(dataEx.offset(), array);
            return array;
        }
    }

    public static final class GrpcAbortExBuilder
    {
        private final GrpcAbortExFW.Builder abortExRW;

        private GrpcAbortExBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            this.abortExRW = new GrpcAbortExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public GrpcAbortExBuilder typeId(
            int typeId)
        {
            abortExRW.typeId(typeId);
            return this;
        }

        public GrpcAbortExBuilder status(
            String status)
        {
            abortExRW.status(status);
            return this;
        }

        public byte[] build()
        {
            final GrpcAbortExFW abortEx = abortExRW.build();
            final byte[] array = new byte[abortEx.sizeof()];
            abortEx.buffer().getBytes(abortEx.offset(), array);
            return array;
        }
    }

    public static final class GrpcResetExBuilder
    {
        private final GrpcResetExFW.Builder resetExRW;

        private GrpcResetExBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            this.resetExRW = new GrpcResetExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public GrpcResetExBuilder typeId(
            int typeId)
        {
            resetExRW.typeId(typeId);
            return this;
        }

        public GrpcResetExBuilder status(
            String status)
        {
            resetExRW.status(status);
            return this;
        }

        public byte[] build()
        {
            final GrpcResetExFW resetEx = resetExRW.build();
            final byte[] array = new byte[resetEx.sizeof()];
            resetEx.buffer().getBytes(resetEx.offset(), array);
            return array;
        }
    }

    public static final class GrpcMessageBuilder
    {
        private final Varuint32FW.Builder keyRW;
        private final Varuint32FW.Builder lenRW;
        private final MutableDirectBuffer messageBuffer = new UnsafeBuffer(new byte[1024 * 200]);

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
        private final MutableDirectBuffer messageBuffer = new UnsafeBuffer(new byte[1024 * 200]);
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
            bytes(field, value.getBytes(UTF_8));
            return this;
        }

        public ProtobufBuilder bytes(
            int field,
            byte[] value)
        {
            Varuint32FW key = keyRW.set(field << 3 | BYTES_WIRE_TYPE).build();
            final int keySize = key.sizeof();
            messageBuffer.putBytes(messageBufferLimit, key.buffer(), key.offset(), key.sizeof());
            messageBufferLimit += keySize;

            Varuint32FW len = lenRW.set(value.length).build();
            int lenSize = len.sizeof();
            messageBuffer.putBytes(messageBufferLimit, len.buffer(), len.offset(), lenSize);
            messageBufferLimit += lenSize;
            messageBuffer.putBytes(messageBufferLimit, value);
            messageBufferLimit += value.length;
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
