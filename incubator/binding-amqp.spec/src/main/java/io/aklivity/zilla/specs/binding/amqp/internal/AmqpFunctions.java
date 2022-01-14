/*
 * Copyright 2021-2022 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.specs.binding.amqp.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.kaazing.k3po.lang.el.BytesMatcher;
import org.kaazing.k3po.lang.el.Function;
import org.kaazing.k3po.lang.el.spi.FunctionMapperSpi;

import io.aklivity.zilla.specs.binding.amqp.internal.types.AmqpAnnotationFW;
import io.aklivity.zilla.specs.binding.amqp.internal.types.AmqpApplicationPropertyFW;
import io.aklivity.zilla.specs.binding.amqp.internal.types.AmqpBinaryFW;
import io.aklivity.zilla.specs.binding.amqp.internal.types.AmqpBodyKind;
import io.aklivity.zilla.specs.binding.amqp.internal.types.AmqpCapabilities;
import io.aklivity.zilla.specs.binding.amqp.internal.types.AmqpPropertiesFW;
import io.aklivity.zilla.specs.binding.amqp.internal.types.AmqpReceiverSettleMode;
import io.aklivity.zilla.specs.binding.amqp.internal.types.AmqpSenderSettleMode;
import io.aklivity.zilla.specs.binding.amqp.internal.types.AmqpTransferFlag;
import io.aklivity.zilla.specs.binding.amqp.internal.types.Array32FW;
import io.aklivity.zilla.specs.binding.amqp.internal.types.stream.AmqpAbortExFW;
import io.aklivity.zilla.specs.binding.amqp.internal.types.stream.AmqpBeginExFW;
import io.aklivity.zilla.specs.binding.amqp.internal.types.stream.AmqpDataExFW;

public final class AmqpFunctions
{
    private static final int MAX_BUFFER_SIZE = 1024 * 8;

    private static final byte[] NULL_TYPE = new byte[] {0x40};
    private static final byte BOOLEAN_TYPE = (byte) 0x56;
    private static final byte[] TRUE_TYPE = new byte[] {0x41};
    private static final byte[] FALSE_TYPE = new byte[] {0x42};
    private static final byte UBYTE_TYPE = (byte) 0x50;
    private static final byte USHORT_TYPE = (byte) 0x60;
    private static final byte UINT_TYPE = (byte) 0x70;
    private static final byte SMALLUINT_TYPE = (byte) 0x52;
    private static final byte[] UINT0_TYPE = new byte[] {0x43};
    private static final byte ULONG_TYPE = (byte) 0x80;
    private static final byte SMALLULONG_TYPE = (byte) 0x53;
    private static final byte[] ULONG0_TYPE = new byte[] {0x44};
    private static final byte BYTE_TYPE = (byte) 0x51;
    private static final byte SHORT_TYPE = (byte) 0x61;
    private static final byte INT_TYPE = (byte) 0x71;
    private static final byte SMALLINT_TYPE = (byte) 0x54;
    private static final byte LONG_TYPE = (byte) 0x81;
    private static final byte SMALLLONG_TYPE = (byte) 0x55;
    private static final byte CHAR_TYPE = (byte) 0x73;
    private static final byte TIMESTAMP_TYPE = (byte) 0x83;

    private static final byte VBIN8_TYPE = (byte) 0xa0;
    private static final byte VBIN32_TYPE = (byte) 0xb0;
    private static final byte STR8UTF8_TYPE = (byte) 0xa1;
    private static final byte STR32UTF8_TYPE = (byte) 0xb1;
    private static final byte SYM8_TYPE = (byte) 0xa3;
    private static final byte SYM32_TYPE = (byte) 0xb3;

    private static final int CONSTRUCTOR_BYTE_SIZE = 1;
    private static final int FIXED_SIZE1 = 1;
    private static final int FIXED_SIZE2 = 2;
    private static final int FIXED_SIZE4 = 4;
    private static final int FIXED_SIZE8 = 8;

    private static final Map<String, Byte> BYTES_BY_NAMES;
    static
    {
        final Map<String, Byte> byteByNames = new HashMap<>();
        byteByNames.put("null", NULL_TYPE[0]);
        byteByNames.put("boolean", BOOLEAN_TYPE);
        byteByNames.put("true", TRUE_TYPE[0]);
        byteByNames.put("false", FALSE_TYPE[0]);
        byteByNames.put("ubyte", UBYTE_TYPE);
        byteByNames.put("ushort", USHORT_TYPE);
        byteByNames.put("uint", UINT_TYPE);
        byteByNames.put("smalluint", SMALLUINT_TYPE);
        byteByNames.put("uint0", UINT0_TYPE[0]);
        byteByNames.put("ulong", ULONG_TYPE);
        byteByNames.put("smallulong", SMALLULONG_TYPE);
        byteByNames.put("ulong0", ULONG0_TYPE[0]);
        byteByNames.put("byte", BYTE_TYPE);
        byteByNames.put("short", SHORT_TYPE);
        byteByNames.put("int", INT_TYPE);
        byteByNames.put("smallint", SMALLINT_TYPE);
        byteByNames.put("long", LONG_TYPE);
        byteByNames.put("smalllong", SMALLLONG_TYPE);
        byteByNames.put("char", CHAR_TYPE);
        byteByNames.put("timestamp", TIMESTAMP_TYPE);
        byteByNames.put("vbin8", VBIN8_TYPE);
        byteByNames.put("vbin32", VBIN32_TYPE);
        byteByNames.put("str8-utf8", STR8UTF8_TYPE);
        byteByNames.put("str32-utf8", STR32UTF8_TYPE);
        byteByNames.put("sym8", SYM8_TYPE);
        byteByNames.put("sym32", SYM32_TYPE);
        BYTES_BY_NAMES = byteByNames;
    }

    public static class AmqpBeginExBuilder
    {
        private final AmqpBeginExFW.Builder beginExRW;

        public AmqpBeginExBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[MAX_BUFFER_SIZE]);
            this.beginExRW = new AmqpBeginExFW.Builder()
                .wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public AmqpBeginExBuilder typeId(
            int typeId)
        {
            beginExRW.typeId(typeId);
            return this;
        }

        public AmqpBeginExBuilder address(
            String address)
        {
            beginExRW.address(address);
            return this;
        }

        public AmqpBeginExBuilder capabilities(
            String capabilities)
        {
            beginExRW.capabilities(r -> r.set(AmqpCapabilities.valueOf(capabilities)));
            return this;
        }

        public AmqpBeginExBuilder senderSettleMode(
            String senderSettleMode)
        {
            beginExRW.senderSettleMode(s -> s.set(AmqpSenderSettleMode.valueOf(senderSettleMode)));
            return this;
        }

        public AmqpBeginExBuilder receiverSettleMode(
            String receiverSettleMode)
        {
            beginExRW.receiverSettleMode(r -> r.set(AmqpReceiverSettleMode.valueOf(receiverSettleMode)));
            return this;
        }

        public byte[] build()
        {
            final AmqpBeginExFW amqpBeginEx = beginExRW.build();
            final byte[] result = new byte[amqpBeginEx.sizeof()];
            amqpBeginEx.buffer().getBytes(0, result);
            return result;
        }
    }

    public static class AmqpDataExBuilder
    {
        private final AmqpDataExFW.Builder dataExRW;
        private AmqpPropertiesFW.Builder propertiesRW;
        private boolean isPropertiesSet;

        public AmqpDataExBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[MAX_BUFFER_SIZE]);
            this.dataExRW = new AmqpDataExFW.Builder()
                .wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public AmqpDataExBuilder typeId(
            int typeId)
        {
            dataExRW.typeId(typeId);
            return this;
        }

        public AmqpDataExBuilder deferred(
            int deferred)
        {
            dataExRW.deferred(deferred);
            return this;
        }

        public AmqpDataExBuilder deliveryTag(
            String deliveryTag)
        {
            dataExRW.deliveryTag(d -> d.bytes(b -> b.set(deliveryTag.getBytes(UTF_8))));
            return this;
        }

        public AmqpDataExBuilder messageFormat(
            long messageFormat)
        {
            dataExRW.messageFormat(messageFormat);
            return this;
        }

        public AmqpDataExBuilder flags(
            String... flags)
        {
            int value = 0;
            for (String flag : flags)
            {
                AmqpTransferFlag transferFlag = AmqpTransferFlag.valueOf(flag);
                switch (transferFlag)
                {
                case SETTLED:
                    value |= 1;
                    break;
                case RESUME:
                    value |= 2;
                    break;
                case ABORTED:
                    value |= 4;
                    break;
                case BATCHABLE:
                    value |= 8;
                    break;
                }
            }
            dataExRW.flags(value);
            return this;
        }

        public AmqpDataExBuilder annotation(
            Object key,
            byte[] value)
        {
            return key instanceof Long ? annotations((long) key, value) : annotations((String) key, value);
        }

        private AmqpDataExBuilder annotations(
            long key,
            byte[] value)
        {
            dataExRW.annotationsItem(a -> a.key(k -> k.id(key))
                                           .value(v -> v.bytes(o -> o.set(value))));
            return this;
        }

        private AmqpDataExBuilder annotations(
            String key,
            byte[] value)
        {
            dataExRW.annotationsItem(a -> a.key(k -> k.name(key))
                                           .value(v -> v.bytes(o -> o.set(value))));
            return this;
        }

        public AmqpDataExBuilder messageId(
            Object messageId)
        {
            AmqpPropertiesFW.Builder properties = properties();
            if (messageId instanceof Long)
            {
                properties.messageId(m -> m.ulong((long) messageId));
                return this;
            }
            else if (messageId instanceof byte[])
            {
                properties.messageId(m -> m.binary(b -> b.bytes(x -> x.set((byte[]) messageId))));
                return this;
            }
            properties.messageId(m -> m.stringtype((String) messageId));
            return this;
        }

        public AmqpDataExBuilder userId(
            String userId)
        {
            properties().userId(u -> u.bytes(b -> b.set(userId.getBytes(UTF_8))));
            return this;
        }

        public AmqpDataExBuilder to(
            String to)
        {
            properties().to(to);
            return this;
        }

        public AmqpDataExBuilder subject(
            String subject)
        {
            properties().subject(subject);
            return this;
        }

        public AmqpDataExBuilder replyTo(
            String replyTo)
        {
            properties().replyTo(replyTo);
            return this;
        }

        public AmqpDataExBuilder correlationId(
            Object correlationId)
        {
            AmqpPropertiesFW.Builder properties = properties();
            if (correlationId instanceof Long)
            {
                properties.correlationId(m -> m.ulong((long) correlationId));
                return this;
            }
            else if (correlationId instanceof byte[])
            {
                properties.correlationId(m -> m.binary(b -> b.bytes(x -> x.set((byte[]) correlationId))));
                return this;
            }
            properties.correlationId(m -> m.stringtype((String) correlationId));
            return this;
        }

        public AmqpDataExBuilder contentType(
            String contentType)
        {
            properties().contentType(contentType);
            return this;
        }

        public AmqpDataExBuilder contentEncoding(
            String contentEncoding)
        {
            properties().contentEncoding(contentEncoding);
            return this;
        }

        public AmqpDataExBuilder absoluteExpiryTime(
            long absoluteExpiryTime)
        {
            properties().absoluteExpiryTime(absoluteExpiryTime);
            return this;
        }

        public AmqpDataExBuilder creationTime(
            long creationTime)
        {
            properties().creationTime(creationTime);
            return this;
        }

        public AmqpDataExBuilder groupId(
            String groupId)
        {
            properties().groupId(groupId);
            return this;
        }

        public AmqpDataExBuilder groupSequence(
            int groupSequence)
        {
            properties().groupSequence(groupSequence);
            return this;
        }

        public AmqpDataExBuilder replyToGroupId(
            String replyToGroupId)
        {
            properties().replyToGroupId(replyToGroupId);
            return this;
        }

        public AmqpDataExBuilder property(
            String key,
            byte[] value)
        {
            if (propertiesRW != null && !isPropertiesSet)
            {
                final AmqpPropertiesFW properties = propertiesRW.build();
                dataExRW.properties(properties);
                isPropertiesSet = true;
            }
            dataExRW.applicationPropertiesItem(a -> a.key(key)
                                                     .value(v -> v.bytes(o -> o.set(value))));
            return this;
        }

        private AmqpPropertiesFW.Builder properties()
        {
            if (propertiesRW == null)
            {
                propertiesRW = new AmqpPropertiesFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
            }
            return propertiesRW;
        }

        public AmqpDataExBuilder bodyKind(
            String bodyKind)
        {
            if (propertiesRW != null && !isPropertiesSet)
            {
                final AmqpPropertiesFW properties = propertiesRW.build();
                dataExRW.properties(properties);
                isPropertiesSet = true;
            }
            dataExRW.bodyKind(b -> b.set(AmqpBodyKind.valueOf(bodyKind)));
            return this;
        }

        public byte[] build()
        {
            if (propertiesRW != null && !isPropertiesSet)
            {
                final AmqpPropertiesFW properties = propertiesRW.build();
                dataExRW.properties(properties);
            }
            final AmqpDataExFW amqpDataEx = dataExRW.build();
            final byte[] result = new byte[amqpDataEx.sizeof()];
            amqpDataEx.buffer().getBytes(0, result);
            return result;
        }
    }

    public static final class AmqpDataExMatcherBuilder
    {
        private final DirectBuffer bufferRO = new UnsafeBuffer();

        private final AmqpDataExFW dataExRO = new AmqpDataExFW();

        private Integer typeId;
        private Integer deferred;
        private AmqpBinaryFW.Builder deliveryTagRW;
        private Long messageFormat;
        private Integer flags;
        private String bodyKind;
        private Array32FW.Builder<AmqpAnnotationFW.Builder, AmqpAnnotationFW> annotationsRW;
        private AmqpPropertiesFW.Builder propertiesRW;
        private Array32FW.Builder<AmqpApplicationPropertyFW.Builder, AmqpApplicationPropertyFW> applicationPropertiesRW;

        public AmqpDataExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public AmqpDataExMatcherBuilder deferred(
            int deferred)
        {
            this.deferred = deferred;
            return this;
        }

        public AmqpDataExMatcherBuilder deliveryTag(
            String deliveryTag)
        {
            assert deliveryTagRW == null;
            deliveryTagRW = new AmqpBinaryFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
            deliveryTagRW.bytes(b -> b.set(deliveryTag.getBytes(UTF_8)));
            return this;
        }

        public AmqpDataExMatcherBuilder messageFormat(
            long messageFormat)
        {
            assert this.messageFormat == null;
            this.messageFormat = messageFormat;
            return this;
        }

        public AmqpDataExMatcherBuilder flags(
            String... flags)
        {
            assert this.flags == null;
            int value = 0;
            for (String flag : flags)
            {
                AmqpTransferFlag transferFlag = AmqpTransferFlag.valueOf(flag);
                switch (transferFlag)
                {
                case SETTLED:
                    value |= 1;
                    break;
                case RESUME:
                    value |= 2;
                    break;
                case ABORTED:
                    value |= 4;
                    break;
                case BATCHABLE:
                    value |= 8;
                    break;
                }
            }
            this.flags = value;
            return this;
        }

        public AmqpDataExMatcherBuilder annotation(
            Object key,
            byte[] value)
        {
            if (annotationsRW == null)
            {
                this.annotationsRW = new Array32FW.Builder<>(new AmqpAnnotationFW.Builder(), new AmqpAnnotationFW())
                    .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
            }
            return key instanceof Long ? annotations((long) key, value) : annotations((String) key, value);
        }

        private AmqpDataExMatcherBuilder annotations(
            long key,
            byte[] value)
        {
            annotationsRW.item(a -> a.key(k -> k.id(key))
                                     .value(v -> v.bytes(o -> o.set(value))));
            return this;
        }

        private AmqpDataExMatcherBuilder annotations(
            String key,
            byte[] value)
        {
            annotationsRW.item(a -> a.key(k -> k.name(key))
                                     .value(v -> v.bytes(o -> o.set(value))));
            return this;
        }

        public AmqpDataExMatcherBuilder messageId(
            Object messageId)
        {
            AmqpPropertiesFW.Builder properties = properties();
            if (messageId instanceof Long)
            {
                properties.messageId(m -> m.ulong((long) messageId));
                return this;
            }
            else if (messageId instanceof byte[])
            {
                properties.messageId(m -> m.binary(b -> b.bytes(x -> x.set((byte[]) messageId))));
                return this;
            }
            properties.messageId(m -> m.stringtype((String) messageId));
            return this;
        }

        public AmqpDataExMatcherBuilder userId(
            String userId)
        {
            properties().userId(u -> u.bytes(b -> b.set(userId.getBytes(UTF_8))));
            return this;
        }

        public AmqpDataExMatcherBuilder to(
            String to)
        {
            properties().to(to);
            return this;
        }

        public AmqpDataExMatcherBuilder subject(
            String subject)
        {
            properties().subject(subject);
            return this;
        }

        public AmqpDataExMatcherBuilder replyTo(
            String replyTo)
        {
            properties().replyTo(replyTo);
            return this;
        }

        public AmqpDataExMatcherBuilder correlationId(
            Object correlationId)
        {
            AmqpPropertiesFW.Builder properties = properties();
            if (correlationId instanceof Long)
            {
                properties.correlationId(m -> m.ulong((long) correlationId));
                return this;
            }
            else if (correlationId instanceof byte[])
            {
                properties.correlationId(m -> m.binary(b -> b.bytes(x -> x.set((byte[]) correlationId))));
                return this;
            }
            properties.correlationId(m -> m.stringtype((String) correlationId));
            return this;
        }

        public AmqpDataExMatcherBuilder contentType(
            String contentType)
        {
            properties().contentType(contentType);
            return this;
        }

        public AmqpDataExMatcherBuilder contentEncoding(
            String contentEncoding)
        {
            properties().contentEncoding(contentEncoding);
            return this;
        }

        public AmqpDataExMatcherBuilder absoluteExpiryTime(
            long absoluteExpiryTime)
        {
            properties().absoluteExpiryTime(absoluteExpiryTime);
            return this;
        }

        public AmqpDataExMatcherBuilder creationTime(
            long creationTime)
        {
            properties().creationTime(creationTime);
            return this;
        }

        public AmqpDataExMatcherBuilder groupId(
            String groupId)
        {
            properties().groupId(groupId);
            return this;
        }

        public AmqpDataExMatcherBuilder groupSequence(
            int groupSequence)
        {
            properties().groupSequence(groupSequence);
            return this;
        }

        public AmqpDataExMatcherBuilder replyToGroupId(
            String replyToGroupId)
        {
            properties().replyToGroupId(replyToGroupId);
            return this;
        }

        private AmqpPropertiesFW.Builder properties()
        {
            if (propertiesRW == null)
            {
                propertiesRW = new AmqpPropertiesFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
            }
            return propertiesRW;
        }

        public AmqpDataExMatcherBuilder property(
            String key,
            byte[] value)
        {
            if (applicationPropertiesRW == null)
            {
                this.applicationPropertiesRW = new Array32FW.Builder<>(new AmqpApplicationPropertyFW.Builder(),
                    new AmqpApplicationPropertyFW())
                    .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
            }
            applicationPropertiesRW.item(a -> a.key(key)
                                               .value(v -> v.bytes(o -> o.set(value))));
            return this;
        }

        public AmqpDataExMatcherBuilder bodyKind(
            String bodyKind)
        {
            assert this.bodyKind == null;
            this.bodyKind = bodyKind;
            return this;
        }

        public BytesMatcher build()
        {
            return typeId != null ? this::match : buf -> null;
        }

        private AmqpDataExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            bufferRO.wrap(byteBuf);
            final AmqpDataExFW dataEx = dataExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (dataEx != null &&
                matchTypeId(dataEx) &&
                matchDeferred(dataEx) &&
                matchDeliveryTag(dataEx) &&
                matchMessageFormat(dataEx) &&
                matchFlags(dataEx) &&
                matchAnnotations(dataEx) &&
                matchProperties(dataEx) &&
                matchApplicationProperties(dataEx) &&
                matchBodyKind(dataEx))
            {
                byteBuf.position(byteBuf.position() + dataEx.sizeof());
                return dataEx;
            }

            throw new Exception(dataEx.toString());
        }

        private boolean matchTypeId(
            final AmqpDataExFW dataEx)
        {
            return typeId == dataEx.typeId();
        }

        private boolean matchDeferred(
            final AmqpDataExFW dataEx)
        {
            return deferred == null || deferred == dataEx.deferred();
        }

        private boolean matchDeliveryTag(
            final AmqpDataExFW dataEx)
        {
            return deliveryTagRW == null || deliveryTagRW.build().equals(dataEx.deliveryTag());
        }

        private boolean matchMessageFormat(
            final AmqpDataExFW dataEx)
        {
            return messageFormat == null || messageFormat == dataEx.messageFormat();
        }

        private boolean matchFlags(
            final AmqpDataExFW dataEx)
        {
            return flags == null || flags == dataEx.flags();
        }

        private boolean matchAnnotations(
            final AmqpDataExFW dataEx)
        {
            return annotationsRW == null || annotationsRW.build().equals(dataEx.annotations());
        }

        private boolean matchProperties(
            final AmqpDataExFW dataEx)
        {
            return propertiesRW == null || propertiesRW.build().equals(dataEx.properties());
        }

        private boolean matchApplicationProperties(
            final AmqpDataExFW dataEx)
        {
            return applicationPropertiesRW == null || applicationPropertiesRW.build().equals(dataEx.applicationProperties());
        }

        private boolean matchBodyKind(
            final AmqpDataExFW dataEx)
        {
            return bodyKind == null || bodyKind.equals(dataEx.bodyKind().get().name());
        }
    }

    public static class AmqpAbortExBuilder
    {
        private final AmqpAbortExFW.Builder abortExRW;

        public AmqpAbortExBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[MAX_BUFFER_SIZE]);
            this.abortExRW = new AmqpAbortExFW.Builder()
                .wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public AmqpAbortExBuilder typeId(
            int typeId)
        {
            abortExRW.typeId(typeId);
            return this;
        }

        public AmqpAbortExBuilder condition(
            String condition)
        {
            abortExRW.condition(condition);
            return this;
        }

        public byte[] build()
        {
            final AmqpAbortExFW amqpAbortEx = abortExRW.build();
            final byte[] result = new byte[amqpAbortEx.sizeof()];
            amqpAbortEx.buffer().getBytes(0, result);
            return result;
        }
    }

    @Function
    public static AmqpBeginExBuilder beginEx()
    {
        return new AmqpBeginExBuilder();
    }

    @Function
    public static AmqpDataExBuilder dataEx()
    {
        return new AmqpDataExBuilder();
    }

    @Function
    public static AmqpDataExMatcherBuilder matchDataEx()
    {
        return new AmqpDataExMatcherBuilder();
    }

    @Function
    public static AmqpAbortExBuilder abortEx()
    {
        return new AmqpAbortExBuilder();
    }

    @Function
    public static byte[] randomBytes(
        int length)
    {
        Random random = ThreadLocalRandom.current();
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++)
        {
            bytes[i] = (byte) random.nextInt(0x100);
        }
        return bytes;
    }

    @Function
    public static String randomString(
        int length)
    {
        int leftLimit = 97;
        int rightLimit = 122;
        Random random = new Random();
        StringBuilder buffer = new StringBuilder(length);
        for (int i = 0; i < length; i++)
        {
            int randomLimitedInt = leftLimit + (int) (random.nextFloat() * (rightLimit - leftLimit + 1));
            buffer.append((char) randomLimitedInt);
        }
        return buffer.toString();
    }

    @Function(name = "_null")
    public static byte[] nullValue()
    {
        return NULL_TYPE;
    }

    @Function(name = "boolean")
    public static byte[] booleanValue(
        boolean value)
    {
        int byteLength = CONSTRUCTOR_BYTE_SIZE + FIXED_SIZE1;
        byte byteValue = (byte) (value ? 0x01 : 0x00);
        return ByteBuffer.allocate(byteLength).put(BOOLEAN_TYPE).put(byteValue).array();
    }

    @Function(name = "_true")
    public static byte[] trueValue()
    {
        return TRUE_TYPE;
    }

    @Function(name = "_false")
    public static byte[] falseValue()
    {
        return FALSE_TYPE;
    }

    @Function
    public static byte[] ubyte(
        int value)
    {
        int byteLength = CONSTRUCTOR_BYTE_SIZE + FIXED_SIZE1;
        return ByteBuffer.allocate(byteLength).put(UBYTE_TYPE).put((byte) value).array();
    }

    @Function
    public static byte[] ushort(
        int value)
    {
        int byteLength = CONSTRUCTOR_BYTE_SIZE + FIXED_SIZE2;
        return ByteBuffer.allocate(byteLength).put(USHORT_TYPE).putShort((short) value).array();
    }

    @Function
    public static byte[] uint(
        long value)
    {
        int byteLength = CONSTRUCTOR_BYTE_SIZE + FIXED_SIZE4;
        return ByteBuffer.allocate(byteLength).put(UINT_TYPE).putInt((int) value).array();
    }

    @Function
    public static byte[] smalluint(
        int value)
    {
        int byteLength = CONSTRUCTOR_BYTE_SIZE + FIXED_SIZE1;
        return ByteBuffer.allocate(byteLength).put(SMALLUINT_TYPE).put((byte) value).array();
    }

    @Function
    public static byte[] uint0()
    {
        return UINT0_TYPE;
    }

    @Function
    public static byte[] ulong(
        long value)
    {
        int byteLength = CONSTRUCTOR_BYTE_SIZE + FIXED_SIZE8;
        return ByteBuffer.allocate(byteLength).put(ULONG_TYPE).putLong(value).array();
    }

    @Function
    public static byte[] smallulong(
        int value)
    {
        int byteLength = CONSTRUCTOR_BYTE_SIZE + FIXED_SIZE1;
        return ByteBuffer.allocate(byteLength).put(SMALLULONG_TYPE).put((byte) value).array();
    }

    @Function
    public static byte[] ulong0()
    {
        return ULONG0_TYPE;
    }

    @Function(name = "byte")
    public static byte[] byteValue(
        int value)
    {
        int byteLength = CONSTRUCTOR_BYTE_SIZE + FIXED_SIZE1;
        return ByteBuffer.allocate(byteLength).put(BYTE_TYPE).put((byte) value).array();
    }

    @Function(name = "short")
    public static byte[] shortValue(
        int value)
    {
        int byteLength = CONSTRUCTOR_BYTE_SIZE + FIXED_SIZE2;
        return ByteBuffer.allocate(byteLength).put(SHORT_TYPE).putShort((short) value).array();
    }

    @Function(name = "int")
    public static byte[] intValue(
        int value)
    {
        int byteLength = CONSTRUCTOR_BYTE_SIZE + FIXED_SIZE4;
        return ByteBuffer.allocate(byteLength).put(INT_TYPE).putInt(value).array();
    }

    @Function
    public static byte[] smallint(
        int value)
    {
        int byteLength = CONSTRUCTOR_BYTE_SIZE + FIXED_SIZE1;
        return ByteBuffer.allocate(byteLength).put(SMALLINT_TYPE).put((byte) value).array();
    }

    @Function(name = "long")
    public static byte[] longValue(
        long value)
    {
        int byteLength = CONSTRUCTOR_BYTE_SIZE + FIXED_SIZE8;
        return ByteBuffer.allocate(byteLength).put(LONG_TYPE).putLong(value).array();
    }

    @Function
    public static byte[] smalllong(
        int value)
    {
        int byteLength = CONSTRUCTOR_BYTE_SIZE + FIXED_SIZE1;
        return ByteBuffer.allocate(byteLength).put(SMALLLONG_TYPE).put((byte) value).array();
    }

    @Function(name = "char")
    public static byte[] charValue(
        String value)
    {
        int byteLength = CONSTRUCTOR_BYTE_SIZE + FIXED_SIZE4;
        int codePoint = value.codePointAt(0);

        byte[] valueArray = value.getBytes(UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(byteLength).put(CHAR_TYPE);
        if (codePoint <= 0x7f)
        {
            buffer.putShort((short) 0)
                .put((byte) 0)
                .put((byte) (valueArray[0] & 0x7f));
        }
        else if (codePoint <= 0x07ff)
        {
            int byte1 = valueArray[0] & 0x1f;
            int byte2 = valueArray[1] & 0x3f;
            buffer.putShort((short) 0)
                .put((byte) (byte1 >> 2))
                .put((byte) (byte2 | byte1 << 6));
        }
        else if (codePoint <= 0xffff)
        {
            int byte1 = valueArray[0] & 0x0f;
            int byte2 = valueArray[1] & 0x3f;
            int byte3 = valueArray[2] & 0x3f;
            buffer.put((byte) 0)
                .put((byte) (byte1 >> 4))
                .put((byte) (byte2 >> 2 | byte1 << 4))
                .put((byte) (byte3 | byte2 << 6));
        }
        else if (codePoint <= 0x10ffff)
        {
            int byte1 = valueArray[0] & 0x07;
            int byte2 = valueArray[1] & 0x3f;
            int byte3 = valueArray[2] & 0x3f;
            int byte4 = valueArray[3] & 0x3f;
            buffer.put((byte) (byte1 >> 6))
                .put((byte) (byte2 >> 4 | byte1 << 6))
                .put((byte) (byte3 >> 2 | byte2 << 4))
                .put((byte) (byte4 | byte3 << 6));
        }
        return buffer.array();
    }

    @Function
    public static byte[] timestamp(
        long value)
    {
        int byteLength = CONSTRUCTOR_BYTE_SIZE + FIXED_SIZE8;
        return ByteBuffer.allocate(byteLength).put(TIMESTAMP_TYPE).putLong(value).array();
    }

    @Function
    public static byte[] binary8(
        String value)
    {
        int length = value.length();
        int byteLength = CONSTRUCTOR_BYTE_SIZE + FIXED_SIZE1 + length;

        return ByteBuffer.allocate(byteLength).put(VBIN8_TYPE).put((byte) length).put(value.getBytes(UTF_8)).array();
    }

    @Function
    public static byte[] binary32(
        String value)
    {
        int length = value.length();
        int byteLength = CONSTRUCTOR_BYTE_SIZE + FIXED_SIZE4 + length;

        return ByteBuffer.allocate(byteLength).put(VBIN32_TYPE).putInt(length).put(value.getBytes(UTF_8)).array();
    }

    @Function
    public static byte[] string8(
        String value)
    {
        int length = value.length();
        int byteLength = CONSTRUCTOR_BYTE_SIZE + FIXED_SIZE1 + length;

        return ByteBuffer.allocate(byteLength).put(STR8UTF8_TYPE).put((byte) length).put(value.getBytes(UTF_8)).array();
    }

    @Function
    public static byte[] string32(
        String value)
    {
        int length = value.length();
        int byteLength = CONSTRUCTOR_BYTE_SIZE + FIXED_SIZE4 + length;

        return ByteBuffer.allocate(byteLength).put(STR32UTF8_TYPE).putInt(length).put(value.getBytes(UTF_8)).array();
    }

    @Function
    public static byte[] symbol8(
        String value)
    {
        int length = value.length();
        int byteLength = CONSTRUCTOR_BYTE_SIZE + FIXED_SIZE1 + length;

        return ByteBuffer.allocate(byteLength).put(SYM8_TYPE).put((byte) length).put(value.getBytes(UTF_8)).array();
    }

    @Function
    public static byte[] symbol32(
        String value)
    {
        int length = value.length();
        int byteLength = CONSTRUCTOR_BYTE_SIZE + FIXED_SIZE4 + length;

        return ByteBuffer.allocate(byteLength).put(SYM32_TYPE).putInt(length).put(value.getBytes(UTF_8)).array();
    }

    @Function
    public static byte[] propertyTypes(
        String... values)
    {
        ByteBuffer buffer = ByteBuffer.allocate(values.length);
        for (String value : values)
        {
            buffer.put(BYTES_BY_NAMES.get(value));
        }
        return buffer.array();
    }

    public static class Mapper extends FunctionMapperSpi.Reflective
    {
        public Mapper()
        {
            super(AmqpFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "amqp";
        }
    }

    private AmqpFunctions()
    {
        // utility
    }
}
