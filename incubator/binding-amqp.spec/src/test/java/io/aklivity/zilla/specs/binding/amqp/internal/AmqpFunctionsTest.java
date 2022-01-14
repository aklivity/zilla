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

import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.abortEx;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.beginEx;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.binary32;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.binary8;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.booleanValue;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.byteValue;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.charValue;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.dataEx;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.falseValue;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.intValue;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.longValue;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.matchDataEx;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.nullValue;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.propertyTypes;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.randomBytes;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.randomString;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.shortValue;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.smallint;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.smalllong;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.smalluint;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.smallulong;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.string32;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.string8;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.symbol32;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.symbol8;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.timestamp;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.trueValue;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.ubyte;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.uint;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.uint0;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.ulong;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.ulong0;
import static io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.ushort;
import static io.aklivity.zilla.specs.binding.amqp.internal.types.AmqpBodyKind.VALUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.kaazing.k3po.lang.internal.el.ExpressionFactoryUtils.newExpressionFactory;

import java.nio.ByteBuffer;

import javax.el.ELContext;
import javax.el.ExpressionFactory;
import javax.el.ValueExpression;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.kaazing.k3po.lang.el.BytesMatcher;
import org.kaazing.k3po.lang.internal.el.ExpressionContext;

import io.aklivity.zilla.specs.binding.amqp.internal.AmqpFunctions.AmqpBeginExBuilder;
import io.aklivity.zilla.specs.binding.amqp.internal.types.AmqpPropertiesFW;
import io.aklivity.zilla.specs.binding.amqp.internal.types.stream.AmqpAbortExFW;
import io.aklivity.zilla.specs.binding.amqp.internal.types.stream.AmqpBeginExFW;
import io.aklivity.zilla.specs.binding.amqp.internal.types.stream.AmqpDataExFW;

public class AmqpFunctionsTest
{
    private ExpressionFactory factory;
    private ELContext ctx;

    @Before
    public void setUp() throws Exception
    {
        factory = newExpressionFactory();
        ctx = new ExpressionContext();
    }

    @Test
    public void shouldLoadFunctions() throws Exception
    {
        String expressionText = "${amqp:beginEx()}";
        ValueExpression expression = factory.createValueExpression(ctx, expressionText, AmqpBeginExBuilder.class);
        AmqpBeginExBuilder builder = (AmqpBeginExBuilder) expression.getValue(ctx);
        assertNotNull(builder);
    }

    @Test
    public void shouldEncodeAmqpBeginExtension()
    {
        final byte[] array = beginEx()
            .typeId(0)
            .address("clients")
            .capabilities("RECEIVE_ONLY")
            .senderSettleMode("SETTLED")
            .receiverSettleMode("FIRST")
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        AmqpBeginExFW amqpBeginEx = new AmqpBeginExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(amqpBeginEx.address().asString(), "clients");
        assertEquals(amqpBeginEx.capabilities().toString(), "RECEIVE_ONLY");
        assertEquals(amqpBeginEx.senderSettleMode().toString(), "SETTLED");
        assertEquals(amqpBeginEx.receiverSettleMode().toString(), "FIRST");
    }

    @Test
    public void shouldEncodeAmqpDataExtensionWithRequiredFields()
    {
        final byte[] array = dataEx()
            .typeId(0)
            .deliveryTag("00")
            .messageFormat(0)
            .flags("SETTLED")
            .bodyKind("VALUE")
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        AmqpDataExFW amqpDataEx = new AmqpDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(amqpDataEx.deliveryTag().toString(), "AMQP_BINARY [length=2, bytes=octets[2]]");
        assertEquals(amqpDataEx.messageFormat(), 0);
        assertEquals(amqpDataEx.flags(), 1);
        assertEquals(amqpDataEx.bodyKind().get(), VALUE);
    }

    @Test
    public void shouldEncodeAmqpDataExtensionWithDeferred()
    {
        final byte[] array = dataEx()
            .typeId(0)
            .deliveryTag("00")
            .messageFormat(0)
            .flags("SETTLED")
            .bodyKind("VALUE")
            .deferred(100)
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        AmqpDataExFW amqpDataEx = new AmqpDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(amqpDataEx.deliveryTag().toString(), "AMQP_BINARY [length=2, bytes=octets[2]]");
        assertEquals(amqpDataEx.messageFormat(), 0);
        assertEquals(amqpDataEx.flags(), 1);
        assertEquals(amqpDataEx.deferred(), 100);
        assertEquals(amqpDataEx.bodyKind().get(), VALUE);
    }

    @Test
    public void shouldEncodeAmqpDataExtensionWithAnnotations()
    {
        final byte[] array = dataEx()
            .typeId(0)
            .deliveryTag("00")
            .messageFormat(0)
            .flags("SETTLED")
            .annotation("annotation1", "1".getBytes(UTF_8))
            .annotation(1L, "0".getBytes(UTF_8))
            .bodyKind("VALUE")
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        AmqpDataExFW amqpDataEx = new AmqpDataExFW().wrap(buffer, 0, buffer.capacity());
        amqpDataEx.annotations().forEach(a ->
            assertEquals(a.value().toString(), "AMQP_BINARY [length=1, bytes=octets[1]]"));
    }

    @Test
    public void shouldEncodeAmqpDataExtensionWithProperties()
    {
        final byte[] array = dataEx()
            .typeId(0)
            .deliveryTag("00")
            .messageFormat(0)
            .flags("SETTLED")
            .messageId("message1")
            .userId("user1")
            .to("clients")
            .subject("subject1")
            .replyTo("localhost")
            .correlationId("correlationId1")
            .contentType("content_type")
            .contentEncoding("content_encoding")
            .absoluteExpiryTime(12345L)
            .creationTime(12345L)
            .groupId("group_id1")
            .groupSequence(1)
            .replyToGroupId("reply_group_id")
            .bodyKind("VALUE")
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        AmqpDataExFW amqpDataEx = new AmqpDataExFW().wrap(buffer, 0, buffer.capacity());
        AmqpPropertiesFW properties = amqpDataEx.properties();
        assertTrue(properties.hasMessageId());
        assertEquals("message1", properties.messageId().stringtype().asString());
        assertTrue(properties.hasUserId());
        assertEquals("octets[5]", properties.userId().bytes().toString());
        assertTrue(properties.hasTo());
        assertEquals("clients", properties.to().asString());
        assertTrue(properties.hasSubject());
        assertEquals("subject1", properties.subject().asString());
        assertTrue(properties.hasReplyTo());
        assertEquals("localhost", properties.replyTo().asString());
        assertTrue(properties.hasCorrelationId());
        assertEquals("correlationId1", properties.correlationId().stringtype().asString());
        assertTrue(properties.hasContentType());
        assertEquals("content_type", properties.contentType().asString());
        assertTrue(properties.hasContentEncoding());
        assertEquals("content_encoding", properties.contentEncoding().asString());
        assertTrue(properties.hasAbsoluteExpiryTime());
        assertEquals(12345L, properties.absoluteExpiryTime());
        assertTrue(properties.hasCreationTime());
        assertEquals(12345L, properties.creationTime());
        assertTrue(properties.hasGroupId());
        assertEquals("group_id1", properties.groupId().asString());
        assertTrue(properties.hasGroupSequence());
        assertEquals(1, properties.groupSequence());
        assertTrue(properties.hasReplyToGroupId());
        assertEquals("reply_group_id", properties.replyToGroupId().asString());
    }

    @Test
    public void shouldEncodeAmqpDataExtensionWithLongProperties()
    {
        final byte[] array = dataEx()
            .typeId(0)
            .deliveryTag("00")
            .messageFormat(0)
            .flags("SETTLED")
            .messageId(12345L)
            .userId("user1")
            .to("clients")
            .subject("subject1")
            .replyTo("localhost")
            .correlationId(12345L)
            .bodyKind("VALUE")
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        AmqpDataExFW amqpDataEx = new AmqpDataExFW().wrap(buffer, 0, buffer.capacity());
        AmqpPropertiesFW properties = amqpDataEx.properties();
        assertTrue(properties.hasMessageId());
        assertEquals(12345L, properties.messageId().ulong());
        assertTrue(properties.hasUserId());
        assertEquals("octets[5]", properties.userId().bytes().toString());
        assertTrue(properties.hasTo());
        assertEquals("clients", properties.to().asString());
        assertTrue(properties.hasSubject());
        assertEquals("subject1", properties.subject().asString());
        assertTrue(properties.hasReplyTo());
        assertEquals("localhost", properties.replyTo().asString());
        assertTrue(properties.hasCorrelationId());
        assertEquals(12345L, properties.correlationId().ulong());
    }

    @Test
    public void shouldEncodeAmqpDataExtensionWithByteArrayProperties()
    {
        final byte[] array = dataEx()
            .typeId(0)
            .deliveryTag("00")
            .messageFormat(0)
            .flags("SETTLED")
            .messageId("message1".getBytes(UTF_8))
            .userId("user1")
            .to("clients")
            .subject("subject1")
            .replyTo("localhost")
            .correlationId("correlation1".getBytes(UTF_8))
            .bodyKind("VALUE")
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        AmqpDataExFW amqpDataEx = new AmqpDataExFW().wrap(buffer, 0, buffer.capacity());
        AmqpPropertiesFW properties = amqpDataEx.properties();
        assertTrue(properties.hasMessageId());
        assertEquals("octets[8]", properties.messageId().binary().bytes().toString());
        assertTrue(properties.hasUserId());
        assertEquals("octets[5]", properties.userId().bytes().toString());
        assertTrue(properties.hasTo());
        assertEquals("clients", properties.to().asString());
        assertTrue(properties.hasSubject());
        assertEquals("subject1", properties.subject().asString());
        assertTrue(properties.hasReplyTo());
        assertEquals("localhost", properties.replyTo().asString());
        assertTrue(properties.hasCorrelationId());
        assertEquals("octets[12]", properties.correlationId().binary().bytes().toString());
    }

    @Test
    public void shouldEncodeAmqpDataExtensionWithApplicationProperties()
    {
        final byte[] array = dataEx()
            .typeId(0)
            .deliveryTag("00")
            .messageFormat(0)
            .flags("SETTLED")
            .property("annotation", "property1".getBytes(UTF_8))
            .bodyKind("VALUE")
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        AmqpDataExFW amqpDataEx = new AmqpDataExFW().wrap(buffer, 0, buffer.capacity());
        amqpDataEx.applicationProperties().forEach(a ->
        {
            assertEquals(a.key().asString(), "annotation");
            assertEquals(a.value().bytes().toString(), "octets[9]");
        });
    }

    @Test
    public void shouldEncodeAmqpDataExtensionWithAllAmqpTransferFlagSet()
    {
        final byte[] array = dataEx()
            .typeId(0)
            .deliveryTag("00")
            .messageFormat(0)
            .flags("BATCHABLE", "ABORTED", "RESUME", "SETTLED")
            .bodyKind("VALUE")
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        AmqpDataExFW amqpDataEx = new AmqpDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x0F, amqpDataEx.flags());
    }

    @Test
    public void shouldEncodeAmqpDataExtensionWithPropertiesAndApplicationProperties()
    {
        final byte[] array = dataEx()
            .typeId(0)
            .deliveryTag("00")
            .messageFormat(0)
            .flags("SETTLED")
            .messageId("message1")
            .property("annotation1", "property1".getBytes(UTF_8))
            .property("annotation2", "property12".getBytes(UTF_8))
            .bodyKind("VALUE")
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        AmqpDataExFW amqpDataEx = new AmqpDataExFW().wrap(buffer, 0, buffer.capacity());
        AmqpPropertiesFW properties = amqpDataEx.properties();
        assertTrue(properties.hasMessageId());
        assertEquals("message1", properties.messageId().stringtype().asString());
        amqpDataEx.applicationProperties().forEach(a ->
        {
            String key = a.key().asString();
            switch (key)
            {
            case "annotation1":
                assertEquals(a.value().bytes().toString(), "octets[9]");
                break;
            case "annotation2":
                assertEquals(a.value().bytes().toString(), "octets[10]");
                break;
            }
        });
    }

    @Test
    public void shouldFailWhenBuildWithoutSettingField() throws Exception
    {
        BytesMatcher matcher = matchDataEx().build();
        ByteBuffer byteBuf = ByteBuffer.allocate(1024);
        assertNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldFailWhenBuildWithoutSettingDeliveryId() throws Exception
    {
        BytesMatcher matcher = matchDataEx()
            .typeId(0)
            .deliveryTag("00")
            .messageFormat(0)
            .flags("BATCHABLE", "ABORTED", "RESUME", "SETTLED")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new AmqpDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .deliveryTag(b -> b.bytes(b2 -> b2.set("00".getBytes())))
            .messageFormat(0)
            .flags(15)
            .bodyKind(b -> b.set(VALUE))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchAmqpDataExtension() throws Exception
    {
        BytesMatcher matcher = matchDataEx()
            .typeId(0)
            .deliveryTag("00")
            .messageFormat(0)
            .flags("BATCHABLE", "ABORTED", "RESUME", "SETTLED")
            .bodyKind("VALUE")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new AmqpDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .deliveryTag(b -> b.bytes(b2 -> b2.set("00".getBytes())))
            .messageFormat(0)
            .flags(15)
            .bodyKind(b -> b.set(VALUE))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchAmqpDataExtensionWithDeferred() throws Exception
    {
        BytesMatcher matcher = matchDataEx()
            .typeId(0)
            .deliveryTag("00")
            .messageFormat(0)
            .flags("BATCHABLE", "ABORTED", "RESUME", "SETTLED")
            .bodyKind("VALUE")
            .deferred(100)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new AmqpDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .deliveryTag(b -> b.bytes(b2 -> b2.set("00".getBytes())))
            .messageFormat(0)
            .flags(15)
            .bodyKind(b -> b.set(VALUE))
            .deferred(100)
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchAmqpDataExtensionTypeId() throws Exception
    {
        BytesMatcher matcher = matchDataEx()
            .typeId(5)
            .deliveryTag("00")
            .messageFormat(0)
            .flags("SETTLED")
            .bodyKind("VALUE")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new AmqpDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .deliveryTag(b -> b.bytes(b2 -> b2.set("00".getBytes())))
            .messageFormat(0)
            .flags(1)
            .bodyKind(b -> b.set(VALUE))
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchAmqpDataExtensionDeferred() throws Exception
    {
        BytesMatcher matcher = matchDataEx()
            .typeId(0)
            .deliveryTag("00")
            .messageFormat(0)
            .flags("SETTLED")
            .bodyKind("VALUE")
            .deferred(120)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new AmqpDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .deliveryTag(b -> b.bytes(b2 -> b2.set("00".getBytes())))
            .messageFormat(0)
            .flags(1)
            .bodyKind(b -> b.set(VALUE))
            .deferred(60)
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchAmqpDataExtensionDeliveryTag() throws Exception
    {
        BytesMatcher matcher = matchDataEx()
            .typeId(0)
            .deliveryTag("01")
            .messageFormat(0)
            .flags("SETTLED")
            .bodyKind("VALUE")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new AmqpDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .deliveryTag(b -> b.bytes(b2 -> b2.set("00".getBytes())))
            .messageFormat(0)
            .flags(1)
            .bodyKind(b -> b.set(VALUE))
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchAmqpDataExtensionMessageFormat() throws Exception
    {
        BytesMatcher matcher = matchDataEx()
            .typeId(0)
            .deliveryTag("00")
            .messageFormat(1)
            .flags("SETTLED")
            .bodyKind("VALUE")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new AmqpDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .deliveryTag(b -> b.bytes(b2 -> b2.set("00".getBytes())))
            .messageFormat(0)
            .flags(1)
            .bodyKind(b -> b.set(VALUE))
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchAmqpDataExtensionFlags() throws Exception
    {
        BytesMatcher matcher = matchDataEx()
            .typeId(0)
            .deliveryTag("00")
            .messageFormat(0)
            .flags("SETTLED")
            .bodyKind("VALUE")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new AmqpDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .deliveryTag(b -> b.bytes(b2 -> b2.set("00".getBytes())))
            .messageFormat(0)
            .flags(15)
            .bodyKind(b -> b.set(VALUE))
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchAmqpDataExtensionAnnotations() throws Exception
    {
        BytesMatcher matcher = matchDataEx()
            .typeId(0)
            .annotation("annotation2", "2".getBytes(UTF_8))
            .annotation(1L, "0".getBytes(UTF_8))
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new AmqpDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .deliveryTag(b -> b.bytes(b2 -> b2.set("00".getBytes())))
            .messageFormat(0)
            .flags(1)
            .annotations(b -> b.item(i -> i.key(k -> k.name("annotation1"))
                                           .value(v -> v.bytes(b2 -> b2.set("1".getBytes()))))
                               .item(i -> i.key(k2 -> k2.id(1L))
                                           .value(v -> v.bytes(b2 -> b2.set("0".getBytes())))))
            .bodyKind(b -> b.set(VALUE))
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchAmqpDataExtensionProperties() throws Exception
    {
        BytesMatcher matcher = matchDataEx()
            .typeId(0)
            .messageId("message2")
            .userId("user1")
            .to("clients")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new AmqpDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .deliveryTag(b -> b.bytes(b2 -> b2.set("00".getBytes())))
            .messageFormat(0)
            .flags(1)
            .properties(p -> p.messageId(m -> m.stringtype("message1"))
                              .userId(u -> u.bytes(b2 -> b2.set("user1".getBytes())))
                              .to("clients"))
            .bodyKind(b -> b.set(VALUE))
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchAmqpDataExtensionApplicationProperties() throws Exception
    {
        BytesMatcher matcher = matchDataEx()
            .typeId(0)
            .property("property4", "1".getBytes(UTF_8))
            .property("property3", "2".getBytes(UTF_8))
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new AmqpDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .deliveryTag(b -> b.bytes(b2 -> b2.set("00".getBytes())))
            .messageFormat(0)
            .flags(1)
            .properties(p -> p.messageId(m -> m.stringtype("message1"))
                              .userId(u -> u.bytes(b2 -> b2.set("user1".getBytes())))
                              .to("clients"))
            .applicationProperties(b -> b.item(i -> i.key("property1").value(v -> v.bytes(o -> o.set("1".getBytes(UTF_8)))))
                                         .item(i -> i.key("property2").value(v -> v.bytes(o -> o.set("2".getBytes(UTF_8))))))
            .bodyKind(b -> b.set(VALUE))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldMatchAmqpDataExtensionWithOnlyAnnotations() throws Exception
    {
        BytesMatcher matcher = matchDataEx()
            .typeId(0)
            .annotation("annotation1", "1".getBytes(UTF_8))
            .annotation(1L, "0".getBytes(UTF_8))
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new AmqpDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .deliveryTag(b -> b.bytes(b2 -> b2.set("00".getBytes())))
            .messageFormat(0)
            .flags(1)
            .annotations(b -> b.item(i -> i.key(k -> k.name("annotation1"))
                                           .value(v -> v.bytes(b2 -> b2.set("1".getBytes()))))
                               .item(i -> i.key(k2 -> k2.id(1L))
                                           .value(v -> v.bytes(b2 -> b2.set("0".getBytes())))))
            .bodyKind(b -> b.set(VALUE))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchAmqpDataExtensionWithOnlyPropertiesWithStringMessageId() throws Exception
    {
        BytesMatcher matcher = matchDataEx()
            .typeId(0)
            .messageId("message1")
            .userId("user1")
            .to("clients")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new AmqpDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .deliveryTag(b -> b.bytes(b2 -> b2.set("00".getBytes())))
            .messageFormat(0)
            .flags(1)
            .properties(p -> p.messageId(m -> m.stringtype("message1"))
                              .userId(u -> u.bytes(b2 -> b2.set("user1".getBytes())))
                              .to("clients"))
            .bodyKind(b -> b.set(VALUE))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchAmqpDataExtensionWithOnlyPropertiesWithLongMessageId() throws Exception
    {
        BytesMatcher matcher = matchDataEx()
            .typeId(0)
            .messageId(1L)
            .userId("user1")
            .to("clients")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new AmqpDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .deliveryTag(b -> b.bytes(b2 -> b2.set("00".getBytes())))
            .messageFormat(0)
            .flags(1)
            .properties(b -> b.messageId(m -> m.ulong(1L))
                              .userId(u -> u.bytes(b2 -> b2.set("user1".getBytes())))
                              .to("clients"))
            .bodyKind(b -> b.set(VALUE))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchAmqpDataExtensionWithOnlyPropertiesWithBinaryMessageId() throws Exception
    {
        BytesMatcher matcher = matchDataEx()
            .typeId(0)
            .messageId("message1".getBytes())
            .userId("user1")
            .to("clients")
            .subject("subject1")
            .replyTo("localhost")
            .correlationId("correlationId1")
            .contentType("content_type")
            .contentEncoding("content_encoding")
            .absoluteExpiryTime(12345L)
            .creationTime(12345L)
            .groupId("group_id1")
            .groupSequence(1)
            .replyToGroupId("reply_group_id")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new AmqpDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .deliveryTag(b -> b.bytes(b2 -> b2.set("00".getBytes())))
            .messageFormat(0)
            .flags(1)
            .properties(p -> p.messageId(m -> m.binary(b1 -> b1.bytes(b2 -> b2.set("message1".getBytes()))))
                              .userId(u -> u.bytes(b2 -> b2.set("user1".getBytes(UTF_8))))
                              .to("clients")
                              .subject("subject1")
                              .replyTo("localhost")
                              .correlationId(c -> c.stringtype("correlationId1"))
                              .contentType("content_type")
                              .contentEncoding("content_encoding")
                              .absoluteExpiryTime(12345L)
                              .creationTime(12345L)
                              .groupId("group_id1")
                              .groupSequence(1)
                              .replyToGroupId("reply_group_id"))
            .bodyKind(b -> b.set(VALUE))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchAmqpDataExtensionWithOnlyPropertiesWithLongCorrelationId() throws Exception
    {
        BytesMatcher matcher = matchDataEx()
            .typeId(0)
            .messageId("message1".getBytes())
            .userId("user1")
            .to("clients")
            .subject("subject1")
            .replyTo("localhost")
            .correlationId(12345L)
            .contentType("content_type")
            .contentEncoding("content_encoding")
            .absoluteExpiryTime(12345L)
            .creationTime(12345L)
            .groupId("group_id1")
            .groupSequence(1)
            .replyToGroupId("reply_group_id")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new AmqpDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .deliveryTag(b -> b.bytes(b2 -> b2.set("00".getBytes())))
            .messageFormat(0)
            .flags(1)
            .properties(p -> p.messageId(m -> m.binary(b1 -> b1.bytes(b2 -> b2.set("message1".getBytes()))))
                              .userId(u -> u.bytes(b2 -> b2.set("user1".getBytes(UTF_8))))
                              .to("clients")
                              .subject("subject1")
                              .replyTo("localhost")
                              .correlationId(c -> c.ulong(12345L))
                              .contentType("content_type")
                              .contentEncoding("content_encoding")
                              .absoluteExpiryTime(12345L)
                              .creationTime(12345L)
                              .groupId("group_id1")
                              .groupSequence(1)
                              .replyToGroupId("reply_group_id"))
            .bodyKind(b -> b.set(VALUE))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchAmqpDataExtensionWithOnlyPropertiesWithBinaryCorrelationId() throws Exception
    {
        BytesMatcher matcher = matchDataEx()
            .typeId(0)
            .messageId("message1".getBytes())
            .userId("user1")
            .to("clients")
            .subject("subject1")
            .replyTo("localhost")
            .correlationId("correlationId1".getBytes())
            .contentType("content_type")
            .contentEncoding("content_encoding")
            .absoluteExpiryTime(12345L)
            .creationTime(12345L)
            .groupId("group_id1")
            .groupSequence(1)
            .replyToGroupId("reply_group_id")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new AmqpDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .deliveryTag(b -> b.bytes(b2 -> b2.set("00".getBytes())))
            .messageFormat(0)
            .flags(1)
            .properties(p -> p.messageId(m -> m.binary(b1 -> b1.bytes(b2 -> b2.set("message1".getBytes()))))
                              .userId(u -> u.bytes(b2 -> b2.set("user1".getBytes(UTF_8))))
                              .to("clients")
                              .subject("subject1")
                              .replyTo("localhost")
                              .correlationId(c -> c.binary(b3 -> b3.bytes(b4 -> b4.set("correlationId1".getBytes()))))
                              .contentType("content_type")
                              .contentEncoding("content_encoding")
                              .absoluteExpiryTime(12345L)
                              .creationTime(12345L)
                              .groupId("group_id1")
                              .groupSequence(1)
                              .replyToGroupId("reply_group_id"))
            .bodyKind(b -> b.set(VALUE))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchAmqpDataExtensionWithOnlyApplicationProperties() throws Exception
    {
        BytesMatcher matcher = matchDataEx()
            .typeId(0)
            .property("property1", "1".getBytes(UTF_8))
            .property("property2", "2".getBytes(UTF_8))
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new AmqpDataExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .deliveryTag(b -> b.bytes(b2 -> b2.set("00".getBytes())))
            .messageFormat(0)
            .flags(1)
            .properties(p -> p.messageId(m -> m.stringtype("message1"))
                              .userId(u -> u.bytes(b2 -> b2.set("user1".getBytes())))
                              .to("clients"))
            .applicationProperties(b -> b.item(i -> i.key("property1").value(v -> v.bytes(o -> o.set("1".getBytes(UTF_8)))))
                                         .item(i -> i.key("property2").value(v -> v.bytes(o -> o.set("2".getBytes(UTF_8))))))
            .bodyKind(b -> b.set(VALUE))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldEncodeAmqpAbortExtension()
    {
        final byte[] array = abortEx()
            .typeId(0)
            .condition("amqp:link:transfer-limit-exceeded")
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        AmqpAbortExFW amqpAbortEx = new AmqpAbortExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(amqpAbortEx.condition().asString(), "amqp:link:transfer-limit-exceeded");
    }

    @Test
    public void shouldRandomizeBytes() throws Exception
    {
        final byte[] bytes = randomBytes(600);

        assertNotNull(bytes);
        assertEquals(600, bytes.length);
    }

    @Test
    public void shouldRandomizeString() throws Exception
    {
        final String string = randomString(600);

        assertNotNull(string);
        assertEquals(600, string.length());
    }

    @Test
    public void shouldCreateAmqpStringBytes() throws Exception
    {
        final byte[] string = string8("1");

        assertArrayEquals(string, new byte[] {(byte) 0xa1, 0x01, 0x31});
        assertEquals(3, string.length);
    }

    @Test
    public void shouldCreateAmqpStringBytesWithLargeString() throws Exception
    {
        final byte[] string = string8(randomString(300));
        assertEquals(302, string.length);
    }

    @Test
    public void shouldCreateAmqpNullValue() throws Exception
    {
        final byte[] value = nullValue();

        assertArrayEquals(value, new byte[] {(byte) 0x40});
        assertEquals(1, value.length);
    }

    @Test
    public void shouldCreateAmqpBooleanValue() throws Exception
    {
        final byte[] value = booleanValue(true);

        assertArrayEquals(value, new byte[] {(byte) 0x56, 0x01});
        assertEquals(2, value.length);
    }

    @Test
    public void shouldCreateAmqpTrueValue() throws Exception
    {
        final byte[] value = trueValue();

        assertArrayEquals(value, new byte[] {(byte) 0x41});
        assertEquals(1, value.length);
    }

    @Test
    public void shouldCreateAmqpFalseValue() throws Exception
    {
        final byte[] value = falseValue();

        assertArrayEquals(value, new byte[] {(byte) 0x42});
        assertEquals(1, value.length);
    }

    @Test
    public void shouldCreateAmqpUbyteValue() throws Exception
    {
        final byte[] value = ubyte(1);

        assertArrayEquals(value, new byte[] {(byte) 0x50, 0x01});
        assertEquals(2, value.length);
    }

    @Test
    public void shouldCreateAmqpUshortValue() throws Exception
    {
        final byte[] value = ushort(1);

        assertArrayEquals(value, new byte[] {(byte) 0x60, 0x00, 0x01});
        assertEquals(3, value.length);
    }

    @Test
    public void shouldCreateAmqpUintValue() throws Exception
    {
        final byte[] value = uint(1);

        assertArrayEquals(value, new byte[] {(byte) 0x70, 0x00, 0x00, 0x00, 0x01});
        assertEquals(5, value.length);
    }

    @Test
    public void shouldCreateAmqpSmalluintValue() throws Exception
    {
        final byte[] value = smalluint(1);

        assertArrayEquals(value, new byte[] {(byte) 0x52, 0x01});
        assertEquals(2, value.length);
    }

    @Test
    public void shouldCreateAmqpUint0Value() throws Exception
    {
        final byte[] value = uint0();

        assertArrayEquals(value, new byte[] {(byte) 0x43});
        assertEquals(1, value.length);
    }

    @Test
    public void shouldCreateAmqpUlongValue() throws Exception
    {
        final byte[] value = ulong(1);

        assertArrayEquals(value, new byte[] {(byte) 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01});
        assertEquals(9, value.length);
    }

    @Test
    public void shouldCreateAmqpSmallulongValue() throws Exception
    {
        final byte[] value = smallulong(1);

        assertArrayEquals(value, new byte[] {(byte) 0x53, 0x01});
        assertEquals(2, value.length);
    }

    @Test
    public void shouldCreateAmqpUlong0Value() throws Exception
    {
        final byte[] value = ulong0();

        assertArrayEquals(value, new byte[] {(byte) 0x44});
        assertEquals(1, value.length);
    }

    @Test
    public void shouldCreateAmqpByteValue() throws Exception
    {
        final byte[] value = byteValue(1);

        assertArrayEquals(value, new byte[] {(byte) 0x51, 0x01});
        assertEquals(2, value.length);
    }

    @Test
    public void shouldCreateAmqpShortValue() throws Exception
    {
        final byte[] value = shortValue(1);

        assertArrayEquals(value, new byte[] {(byte) 0x61, 0x00, 0x01});
        assertEquals(3, value.length);
    }

    @Test
    public void shouldCreateAmqpIntValue() throws Exception
    {
        final byte[] value = intValue(1);

        assertArrayEquals(value, new byte[] {(byte) 0x71, 0x00, 0x00, 0x00, 0x01});
        assertEquals(5, value.length);
    }

    @Test
    public void shouldCreateAmqpSmallintValue() throws Exception
    {
        final byte[] value = smallint(1);

        assertArrayEquals(value, new byte[] {(byte) 0x54, 0x01});
        assertEquals(2, value.length);
    }

    @Test
    public void shouldCreateAmqpLongValue() throws Exception
    {
        final byte[] value = longValue(1);

        assertArrayEquals(value, new byte[] {(byte) 0x81, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01});
        assertEquals(9, value.length);
    }

    @Test
    public void shouldCreateAmqpSmalllongValue() throws Exception
    {
        final byte[] value = smalllong(1);

        assertArrayEquals(value, new byte[] {(byte) 0x55, 0x01});
        assertEquals(2, value.length);
    }

    @Test
    public void shouldCreateAmqpCharValue() throws Exception
    {
        final byte[] oneByteLengthChar = charValue("1");

        assertArrayEquals(oneByteLengthChar, new byte[] {(byte) 0x73, 0x00, 0x00, 0x00, 0x31});
        assertEquals(5, oneByteLengthChar.length);

        final byte[] twoByteLengthChar = charValue("¢");

        assertArrayEquals(twoByteLengthChar, new byte[] {(byte) 0x73, 0x00, 0x00, 0x00, (byte) 0xa2});
        assertEquals(5, twoByteLengthChar.length);

        final byte[] threeByteLengthChar = charValue("€");

        assertArrayEquals(threeByteLengthChar, new byte[] {(byte) 0x73, 0x00, 0x00, 0x20, (byte) 0xac});
        assertEquals(5, threeByteLengthChar.length);

        final byte[] fourByteLengthChar = charValue("\uD800\uDF48");

        assertArrayEquals(fourByteLengthChar, new byte[] {(byte) 0x73, 0x00, 0x01, 0x03, (byte) 0x48});
        assertEquals(5, fourByteLengthChar.length);
    }

    @Test
    public void shouldCreateAmqpTimestampValue() throws Exception
    {
        final byte[] value = timestamp(1);

        assertArrayEquals(value, new byte[] {(byte) 0x83, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01});
        assertEquals(9, value.length);
    }

    @Test
    public void shouldCreateAmqpString32Bytes() throws Exception
    {
        final byte[] string = string32("value");

        assertArrayEquals(string, new byte[] {(byte) 0xb1, 0x00, 0x00, 0x00, 0x05, 0x76, 0x61, 0x6C, 0x75, 0x65});
        assertEquals(10, string.length);
    }

    @Test
    public void shouldCreateAmqpBinary8Bytes() throws Exception
    {
        final byte[] binary = binary8("value");

        assertArrayEquals(binary, new byte[] {(byte) 0xa0, 0x05, 0x76, 0x61, 0x6C, 0x75, 0x65});
        assertEquals(7, binary.length);
    }

    @Test
    public void shouldCreateAmqpBinary32Bytes() throws Exception
    {
        final byte[] binary = binary32("value");

        assertArrayEquals(binary, new byte[] {(byte) 0xb0, 0x00, 0x00, 0x00, 0x05, 0x76, 0x61, 0x6C, 0x75, 0x65});
        assertEquals(10, binary.length);
    }

    @Test
    public void shouldCreateAmqpSymbol8Bytes() throws Exception
    {
        final byte[] symbol = symbol8("value");

        assertArrayEquals(symbol, new byte[] {(byte) 0xa3, 0x05, 0x76, 0x61, 0x6C, 0x75, 0x65});
        assertEquals(7, symbol.length);
    }

    @Test
    public void shouldCreateAmqpSymbol32Bytes() throws Exception
    {
        final byte[] symbol = symbol32("value");

        assertArrayEquals(symbol, new byte[] {(byte) 0xb3, 0x00, 0x00, 0x00, 0x05, 0x76, 0x61, 0x6C, 0x75, 0x65});
        assertEquals(10, symbol.length);
    }

    @Test
    public void shouldCreatePropertyTypesBytes() throws Exception
    {
        final byte[] types = propertyTypes("null", "boolean", "true", "false", "ubyte", "ushort", "uint", "smalluint",
            "uint0", "ulong", "smallulong", "ulong0", "byte", "short", "int", "smallint", "long",
            "smalllong", "char", "timestamp", "vbin8", "vbin32", "str8-utf8", "str32-utf8", "sym8", "sym32");

        assertArrayEquals(types, new byte[] {0x40, 0x56, 0x41, 0x42, 0x50, 0x60, 0x70, 0x52, 0x43, (byte) 0x80, 0x53, 0x44,
            0x51, 0x61, 0x71, 0x54, (byte) 0x81, 0x55, 0x73, (byte) 0x83, (byte) 0xa0, (byte) 0xb0, (byte) 0xa1, (byte) 0xb1,
            (byte) 0xa3, (byte) 0xb3});
        assertEquals(26, types.length);
    }

    @Test(expected = AssertionError.class)
    public void shouldRejectAmqpDataExtensionRepeatedDeliveryTag() throws Exception
    {
        matchDataEx()
            .deliveryTag("01")
            .deliveryTag("02")
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldRejectAmqpDataExtensionRepeatedMessageFormat() throws Exception
    {
        matchDataEx()
            .messageFormat(0)
            .messageFormat(1)
            .build();
    }

    @Test(expected = AssertionError.class)
    public void shouldRejectAmqpDataExtensionRepeatedFlags() throws Exception
    {
        matchDataEx()
            .flags("SETTLED")
            .flags("BATCHABLE")
            .build();
    }
}
