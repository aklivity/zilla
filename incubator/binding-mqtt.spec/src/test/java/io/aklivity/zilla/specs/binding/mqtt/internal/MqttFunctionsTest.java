/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.specs.binding.mqtt.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.nio.ByteBuffer;
import java.util.Objects;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;
import org.kaazing.k3po.lang.el.BytesMatcher;

import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttPayloadFormat;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttSessionSignalFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttSessionSignalType;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttSessionStateFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttWillMessageFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttBeginExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttDataExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttFlushExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttResetExFW;

public class MqttFunctionsTest
{
    @Test
    public void shouldGetMapper()
    {
        MqttFunctions.Mapper mapper = new MqttFunctions.Mapper();
        assertEquals("mqtt", mapper.getPrefixName());
    }

    @Test
    public void shouldEncodePayloadFormat()
    {
        final byte[] bytes = MqttFunctions.payloadFormat("TEXT");
        final byte[] expected = new byte[] {1};
        assertArrayEquals(bytes, expected);
    }

    @Test
    public void shouldEncodeMqttSessionBeginExt()
    {
        final byte[] array = MqttFunctions.beginEx()
            .typeId(0)
                .session()
                .flags("WILL", "CLEAN_START")
                .expiry(30)
                .qosMax(1)
                .packetSizeMax(100)
                .capabilities("RETAIN", "WILDCARD", "SUBSCRIPTION_IDS")
                .clientId("client")
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttBeginExFW mqttBeginEx = new MqttBeginExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(2, mqttBeginEx.kind());
        assertEquals("client", mqttBeginEx.session().clientId().asString());
        assertEquals(30, mqttBeginEx.session().expiry());
        assertEquals(1, mqttBeginEx.session().qosMax());
        assertEquals(100, mqttBeginEx.session().packetSizeMax());
        assertEquals(7, mqttBeginEx.session().capabilities());
        assertEquals(6, mqttBeginEx.session().flags());
    }

    @Test
    public void shouldEncodeMqttSessionBeginExtWithoutWillMessage()
    {
        final byte[] array = MqttFunctions.beginEx()
            .typeId(0)
                .session()
                .expiry(30)
                .clientId("client")
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttBeginExFW mqttBeginEx = new MqttBeginExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(2, mqttBeginEx.kind());
        assertEquals("client", mqttBeginEx.session().clientId().asString());
        assertEquals(30, mqttBeginEx.session().expiry());
    }

    @Test
    public void shouldEncodeMqttSessionBeginExtWithFlagsBytesWillPayload()
    {
        final byte[] array = MqttFunctions.beginEx()
            .typeId(0)
                .session()
                .clientId("client")
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttBeginExFW mqttBeginEx = new MqttBeginExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(2, mqttBeginEx.kind());
        assertEquals("client", mqttBeginEx.session().clientId().asString());
    }

    @Test
    public void shouldEncodeMqttSubscribeBeginExt()
    {
        final byte[] array = MqttFunctions.beginEx()
            .typeId(0)
                .subscribe()
                .clientId("client")
                .filter("sensor/one", 0)
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttBeginExFW mqttBeginEx = new MqttBeginExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(1, mqttBeginEx.kind());
        assertEquals("client", mqttBeginEx.subscribe().clientId().asString());
        assertNotNull(mqttBeginEx.subscribe().filters()
            .matchFirst(f ->
                "sensor/one".equals(f.pattern().asString()) &&
                    0 == f.subscriptionId() &&
                    0 == f.qos()));
    }

    @Test
    public void shouldEncodeMqttSubscribeBeginExtNoSubscriptionId()
    {
        final byte[] array = MqttFunctions.beginEx()
            .typeId(0)
            .subscribe()
            .clientId("client")
            .filter("sensor/one")
            .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttBeginExFW mqttBeginEx = new MqttBeginExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(1, mqttBeginEx.kind());
        assertEquals("client", mqttBeginEx.subscribe().clientId().asString());
        assertNotNull(mqttBeginEx.subscribe().filters()
            .matchFirst(f ->
                "sensor/one".equals(f.pattern().asString()) &&
                    0 == f.qos()));
    }

    @Test
    public void shouldEncodeMqttSubscribeBeginExtWithFlags()
    {
        final byte[] array = MqttFunctions.beginEx()
            .typeId(0)
            .subscribe()
                .clientId("client")
                .filter("sensor/one", 1, "AT_MOST_ONCE", "SEND_RETAINED")
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttBeginExFW mqttBeginEx = new MqttBeginExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(1, mqttBeginEx.kind());
        assertEquals("client", mqttBeginEx.subscribe().clientId().asString());

        assertNotNull(mqttBeginEx.subscribe().filters()
            .matchFirst(f ->
                "sensor/one".equals(f.pattern().asString()) &&
                    1 == f.subscriptionId() &&
                    0 == f.qos() &&
                    0b0001 == f.flags()));
    }

    @Test
    public void shouldMatchPublishBeginExtension() throws Exception
    {
        BytesMatcher matcher = MqttFunctions.matchBeginEx()
            .publish()
                .clientId("client")
                .topic("sensor/one")
                .flags("RETAIN")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new MqttBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .publish(f -> f
                .clientId("client")
                .topic("sensor/one")
                .flags(1))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchSubscribeBeginExtension() throws Exception
    {
        BytesMatcher matcher = MqttFunctions.matchBeginEx()
            .subscribe()
                .clientId("client")
                .filter("sensor/one", 1, "AT_MOST_ONCE", "SEND_RETAINED")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new MqttBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .subscribe(f -> f
                .clientId("client")
                .filtersItem(p -> p.subscriptionId(1).qos(0).flags(1).pattern("sensor/one")))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchSubscribeBeginExtensionDefaults() throws Exception
    {
        BytesMatcher matcher = MqttFunctions.matchBeginEx()
            .subscribe()
            .clientId("client")
            .filter("sensor/one", 1)
            .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new MqttBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .subscribe(f -> f
                .clientId("client")
                .filtersItem(p -> p.subscriptionId(1).qos(0).flags(0).pattern("sensor/one")))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchSubscribeBeginExtensionNoSubscriptionId() throws Exception
    {
        BytesMatcher matcher = MqttFunctions.matchBeginEx()
            .subscribe()
            .clientId("client")
            .filter("sensor/one")
            .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new MqttBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .subscribe(f -> f
                .clientId("client")
                .filtersItem(p -> p.qos(0).flags(0).pattern("sensor/one")))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchSessionBeginExtension() throws Exception
    {
        BytesMatcher matcher = MqttFunctions.matchBeginEx()
            .session()
                .flags("CLEAN_START")
                .expiry(10)
                .qosMax(1)
                .packetSizeMax(100)
                .capabilities("RETAIN", "WILDCARD", "SUBSCRIPTION_IDS")
                .clientId("client")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new MqttBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x00)
            .session(s -> s
                .flags(2)
                .expiry(10)
                .qosMax(1)
                .packetSizeMax(100)
                .capabilities(7)
                .clientId("client"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchSessionBeginExtensionWithEmptyFields() throws Exception
    {
        BytesMatcher matcher = MqttFunctions.matchBeginEx()
            .session()
                .expiry(10)
                .clientId("client")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new MqttBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x00)
            .session(s -> s
                .expiry(10)
                .clientId("client"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchSessionBeginExtensionWithBytes() throws Exception
    {
        BytesMatcher matcher = MqttFunctions.matchBeginEx()
                .session()
                .expiry(10)
                .clientId("client")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new MqttBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x00)
            .session(s -> s
                .expiry(10)
                .clientId("client"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldEncodeMqttPublishBeginEx()
    {
        final byte[] array = MqttFunctions.beginEx()
            .typeId(0)
            .publish()
                .clientId("client")
                .topic("sensor/one")
                .flags("RETAIN")
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttBeginExFW mqttBeginEx = new MqttBeginExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttBeginEx.kind());
        assertEquals("client", mqttBeginEx.publish().clientId().asString());
        assertEquals("sensor/one", mqttBeginEx.publish().topic().asString());
        assertEquals(1, mqttBeginEx.publish().flags());
    }

    @Test
    public void shouldMatchSubscribeDataExtension() throws Exception
    {
        BytesMatcher matcher = MqttFunctions.matchDataEx()
            .subscribe()
                .topic("sensor/one")
                .qos("AT_MOST_ONCE")
                .flags("RETAIN")
                .subscriptionId(1)
                .subscriptionId(2)
                .expiryInterval(20)
                .contentType("message")
                .format("TEXT")
                .responseTopic("sensor/response")
                .correlation("correlationData")
                .userProperty("key1", "value1")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new MqttDataExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x00)
            .subscribe(s ->
            {
                s.topic("sensor/one");
                s.qos(0);
                s.flags(1);
                s.subscriptionIdsItem(i -> i.set(1));
                s.subscriptionIdsItem(i -> i.set(2));
                s.expiryInterval(20);
                s.contentType("message");
                s.format(f -> f.set(MqttPayloadFormat.TEXT));
                s.responseTopic("sensor/response");
                s.correlation(corr -> corr.bytes(b -> b.set("correlationData".getBytes(UTF_8))));
                s.propertiesItem(p -> p.key("key1").value("value1"));
            })
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchSubscribeDataExtensionWithBytes() throws Exception
    {
        BytesMatcher matcher = MqttFunctions.matchDataEx()
            .subscribe()
                .topic("sensor/one")
                .qos("AT_MOST_ONCE")
                .subscriptionId(1)
                .subscriptionId(2)
                .expiryInterval(20)
                .contentType("message")
                .format("TEXT")
                .responseTopic("sensor/response")
                .correlationBytes("correlationData".getBytes(UTF_8))
                .userProperty("key1", "value1")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new MqttDataExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x00)
            .subscribe(s ->
            {
                s.topic("sensor/one");
                s.qos(0);
                s.subscriptionIdsItem(i -> i.set(1));
                s.subscriptionIdsItem(i -> i.set(2));
                s.expiryInterval(20);
                s.contentType("message");
                s.format(f -> f.set(MqttPayloadFormat.TEXT));
                s.responseTopic("sensor/response");
                s.correlation(corr -> corr.bytes(b -> b.set("correlationData".getBytes(UTF_8))));
                s.propertiesItem(p -> p.key("key1").value("value1"));
            })
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchSubscribeDataExtensionWithEmptyFields() throws Exception
    {
        BytesMatcher matcher = MqttFunctions.matchDataEx()
            .subscribe()
                .topic("sensor/one")
                .qos("AT_MOST_ONCE")
                .subscriptionId(1)
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new MqttDataExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x00)
            .subscribe(s ->
            {
                s.topic("sensor/one");
                s.qos(0);
                s.subscriptionIdsItem(i -> i.set(1));
            })
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldEncodeMqttSubscribeDataEx()
    {
        final byte[] array = MqttFunctions.dataEx()
            .typeId(0)
                .subscribe()
                .topic("sensor/one")
                .subscriptionId(1)
                .subscriptionId(2)
                .expiryInterval(15)
                .contentType("message")
                .format("TEXT")
                .responseTopic("sensor/one")
                .correlation("request-id-1")
                .userProperty("name", "value")
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals("sensor/one", mqttDataEx.subscribe().topic().asString());
        assertNotNull(mqttDataEx.subscribe().subscriptionIds().matchFirst(s -> s.value() == 1));
        assertNotNull(mqttDataEx.subscribe().subscriptionIds().matchFirst(s -> s.value() == 2));
        assertEquals(15, mqttDataEx.subscribe().expiryInterval());
        assertEquals("message", mqttDataEx.subscribe().contentType().asString());
        assertEquals("TEXT", mqttDataEx.subscribe().format().toString());
        assertEquals("sensor/one", mqttDataEx.subscribe().responseTopic().asString());
        assertEquals("request-id-1", mqttDataEx.subscribe().correlation()
            .bytes().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));
        assertNotNull(mqttDataEx.subscribe().properties()
            .matchFirst(h ->
                "name".equals(h.key().asString()) &&
                    "value".equals(h.value().asString())));
    }

    @Test
    public void shouldEncodeMqttSubscribeDataExWithUserProperty()
    {
        final byte[] array = MqttFunctions.dataEx()
            .typeId(0)
            .subscribe()
            .topic("sensor/one")
            .userProperty("name", "value")
            .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals("sensor/one", mqttDataEx.subscribe().topic().asString());
        assertNotNull(mqttDataEx.subscribe().properties()
            .matchFirst(h ->
                "name".equals(h.key().asString()) &&
                    "value".equals(h.value().asString())));
    }

    @Test
    public void shouldEncodeMqttSubscribeDataExWithFlags()
    {
        final byte[] array = MqttFunctions.dataEx()
            .typeId(0)
            .subscribe()
            .topic("sensor/one")
            .qos("AT_MOST_ONCE")
            .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals("sensor/one", mqttDataEx.subscribe().topic().asString());
        assertEquals(0, mqttDataEx.subscribe().qos());
    }

    @Test
    public void shouldEncodeMqttSubscribeDataExWithUserPropertyNoTopic()
    {
        final byte[] array = MqttFunctions.dataEx()
            .typeId(0)
            .subscribe()
            .userProperty("name", "value")
            .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertNotNull(mqttDataEx.subscribe().properties()
            .matchFirst(h ->
                "name".equals(h.key().asString()) &&
                    "value".equals(h.value().asString())));
    }

    @Test
    public void shouldEncodeMqttSubscribeDataExWithUserProperties()
    {
        final byte[] array = MqttFunctions.dataEx()
            .typeId(0)
            .subscribe()
            .topic("sensor/one")
            .userProperty("name1", "value1")
            .userProperty("name2", "value2")
            .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals("sensor/one", mqttDataEx.subscribe().topic().asString());
        assertNotNull(mqttDataEx.subscribe().properties()
            .matchFirst(h ->
                "name1".equals(h.key().asString()) &&
                    "value1".equals(h.value().asString())));
        assertNotNull(mqttDataEx.subscribe().properties()
            .matchFirst(h ->
                "name2".equals(h.key().asString()) &&
                    "value2".equals(h.value().asString())));
    }

    @Test
    public void shouldEncodeMqttSubscribeDataExWithoutTopic()
    {
        final byte[] array = MqttFunctions.dataEx()
            .typeId(0)
            .subscribe()
            .expiryInterval(15)
            .contentType("message")
            .format("TEXT")
            .responseTopic("sensor/one")
            .correlation("request-id-1")
            .userProperty("name", "value")
            .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertNull(mqttDataEx.subscribe().topic().asString());
        assertEquals(15, mqttDataEx.subscribe().expiryInterval());
        assertEquals("message", mqttDataEx.subscribe().contentType().asString());
        assertEquals("TEXT", mqttDataEx.subscribe().format().toString());
        assertEquals("sensor/one", mqttDataEx.subscribe().responseTopic().asString());
        assertEquals("request-id-1", mqttDataEx.subscribe().correlation()
            .bytes().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));
        assertNotNull(mqttDataEx.subscribe().properties()
            .matchFirst(h ->
                "name".equals(h.key().asString()) &&
                    "value".equals(h.value().asString())));
    }

    @Test
    public void shouldEncodeMqttSubscribeDataExWithoutResponseTopic()
    {
        final byte[] array = MqttFunctions.dataEx()
            .typeId(0)
            .subscribe()
            .topic("sensor/one")
            .expiryInterval(15)
            .contentType("message")
            .format("TEXT")
            .correlation("request-id-1")
            .userProperty("name", "value")
            .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals("sensor/one", mqttDataEx.subscribe().topic().asString());
        assertEquals(15, mqttDataEx.subscribe().expiryInterval());
        assertEquals("message", mqttDataEx.subscribe().contentType().asString());
        assertEquals("TEXT", mqttDataEx.subscribe().format().toString());
        assertNull(mqttDataEx.subscribe().responseTopic().asString());
        assertEquals("request-id-1", mqttDataEx.subscribe().correlation()
            .bytes().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));
        assertNotNull(mqttDataEx.subscribe().properties()
            .matchFirst(h ->
                "name".equals(h.key().asString()) &&
                    "value".equals(h.value().asString())));
    }

    @Test
    public void shouldEncodeMqttSubscribeDataExWithNullDefaults()
    {
        final byte[] array = MqttFunctions.dataEx()
            .typeId(0)
            .subscribe()
            .expiryInterval(15)
            .format("TEXT")
            .correlation("request-id-1")
            .userProperty("name", "value")
            .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertNull(mqttDataEx.subscribe().topic().asString());
        assertEquals(15, mqttDataEx.subscribe().expiryInterval());
        assertNull(mqttDataEx.subscribe().contentType().asString());
        assertEquals("TEXT", mqttDataEx.subscribe().format().toString());
        assertNull(mqttDataEx.subscribe().responseTopic().asString());
        assertEquals("request-id-1", mqttDataEx.subscribe().correlation()
            .bytes().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));
        assertNotNull(mqttDataEx.subscribe().properties()
            .matchFirst(h ->
                "name".equals(h.key().asString()) &&
                    "value".equals(h.value().asString())));
    }

    @Test
    public void shouldEncodeMqttSubscribeDataExWithBytes()
    {
        final byte[] array = MqttFunctions.dataEx()
            .typeId(0)
            .subscribe()
            .topic("sensor/one")
            .expiryInterval(15)
            .contentType("message")
            .format("TEXT")
            .responseTopic("sensor/one")
            .correlationBytes("request-id-1".getBytes(UTF_8))
            .userProperty("name", "value")
            .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals("sensor/one", mqttDataEx.subscribe().topic().asString());
        assertEquals(15, mqttDataEx.subscribe().expiryInterval());
        assertEquals("message", mqttDataEx.subscribe().contentType().asString());
        assertEquals("TEXT", mqttDataEx.subscribe().format().toString());
        assertEquals("sensor/one", mqttDataEx.subscribe().responseTopic().asString());
        assertEquals("request-id-1", mqttDataEx.subscribe().correlation()
            .bytes().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));
        assertNotNull(mqttDataEx.subscribe().properties()
            .matchFirst(h ->
                "name".equals(h.key().asString()) &&
                    "value".equals(h.value().asString())));
    }

    @Test
    public void shouldEncodeMqttSubscribeDataExWithNullUserPropertyValue()
    {
        final byte[] array = MqttFunctions.dataEx()
            .typeId(0)
            .subscribe()
            .topic("sensor/one")
            .expiryInterval(15)
            .contentType("message")
            .format("TEXT")
            .responseTopic("sensor/one")
            .correlation("request-id-1")
            .userProperty("name", null)
            .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals("sensor/one", mqttDataEx.subscribe().topic().asString());
        assertEquals(15, mqttDataEx.subscribe().expiryInterval());
        assertEquals("message", mqttDataEx.subscribe().contentType().asString());
        assertEquals("TEXT", mqttDataEx.subscribe().format().toString());
        assertEquals("sensor/one", mqttDataEx.subscribe().responseTopic().asString());
        assertEquals("request-id-1", mqttDataEx.subscribe().correlation()
            .bytes().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));
        assertNotNull(mqttDataEx.subscribe().properties()
            .matchFirst(h ->
                "name".equals(h.key().asString()) &&
                    Objects.isNull(h.value().asString())));
    }

    @Test
    public void shouldMatchPublishDataExtension() throws Exception
    {
        BytesMatcher matcher = MqttFunctions.matchDataEx()
            .publish()
                .qos("AT_MOST_ONCE")
                .flags("RETAIN")
                .expiryInterval(20)
                .contentType("message")
                .format("TEXT")
                .responseTopic("sensor/response")
                .correlation("correlationData")
                .userProperty("key1", "value1")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new MqttDataExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x00)
            .publish(p ->
            {
                p.qos(0);
                p.flags(1);
                p.expiryInterval(20);
                p.contentType("message");
                p.format(f -> f.set(MqttPayloadFormat.TEXT));
                p.responseTopic("sensor/response");
                p.correlation(corr -> corr.bytes(b -> b.set("correlationData".getBytes(UTF_8))));
                p.propertiesItem(pi -> pi.key("key1").value("value1"));
            })
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchPublishDataExtensionWithBytes() throws Exception
    {
        BytesMatcher matcher = MqttFunctions.matchDataEx()
            .publish()
                .qos("AT_MOST_ONCE")
                .expiryInterval(20)
                .contentType("message")
                .format("TEXT")
                .responseTopic("sensor/response")
                .correlationBytes("correlationData".getBytes(UTF_8))
                .userProperty("key1", "value1")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new MqttDataExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x00)
            .publish(p ->
            {
                p.qos(0);
                p.expiryInterval(20);
                p.contentType("message");
                p.format(f -> f.set(MqttPayloadFormat.TEXT));
                p.responseTopic("sensor/response");
                p.correlation(corr -> corr.bytes(b -> b.set("correlationData".getBytes(UTF_8))));
                p.propertiesItem(pi -> pi.key("key1").value("value1"));
            })
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchPublishDataExtensionWithEmptyFields() throws Exception
    {
        BytesMatcher matcher = MqttFunctions.matchDataEx()
            .publish()
                .qos("AT_MOST_ONCE")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new MqttDataExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x00)
            .publish(p ->
            {
                p.flags(0);
            })
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldEncodeMqttPublishDataEx()
    {
        final byte[] array = MqttFunctions.dataEx()
            .typeId(0)
            .publish()
            .expiryInterval(15)
            .contentType("message")
            .format("TEXT")
            .responseTopic("sensor/one")
            .correlation("request-id-1")
            .userProperty("name", "value")
            .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttPublishDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttPublishDataEx.typeId());
        assertEquals(15, mqttPublishDataEx.publish().expiryInterval());
        assertEquals("message", mqttPublishDataEx.publish().contentType().asString());
        assertEquals("TEXT", mqttPublishDataEx.publish().format().toString());
        assertEquals("sensor/one", mqttPublishDataEx.publish().responseTopic().asString());
        assertEquals("request-id-1", mqttPublishDataEx.publish().correlation()
            .bytes().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));
        assertNotNull(mqttPublishDataEx.publish().properties()
            .matchFirst(h ->
                "name".equals(h.key().asString()) &&
                    "value".equals(h.value().asString())));
    }

    @Test
    public void shouldEncodeMqttPublishDataExWithUserProperty()
    {
        final byte[] array = MqttFunctions.dataEx()
            .typeId(0)
            .publish()
            .userProperty("name", "value")
            .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertNotNull(mqttDataEx.publish().properties()
            .matchFirst(h ->
                "name".equals(h.key().asString()) &&
                    "value".equals(h.value().asString())));
    }

    @Test
    public void shouldEncodeMqttPublishDataExWithFlags()
    {
        final byte[] array = MqttFunctions.dataEx()
            .typeId(0)
                .publish()
                .qos("EXACTLY_ONCE")
                .flags("RETAIN")
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals(2, mqttDataEx.publish().qos());
        assertEquals(0b0001, mqttDataEx.publish().flags());
    }

    @Test
    public void shouldEncodeMqttPublishDataExWithUserPropertyNoTopic()
    {
        final byte[] array = MqttFunctions.dataEx()
            .typeId(0)
            .publish()
            .userProperty("name", "value")
            .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertNotNull(mqttDataEx.publish().properties()
            .matchFirst(h ->
                "name".equals(h.key().asString()) &&
                    "value".equals(h.value().asString())));
    }

    @Test
    public void shouldEncodeMqttPublishDataExWithUserProperties()
    {
        final byte[] array = MqttFunctions.dataEx()
            .typeId(0)
            .publish()
            .userProperty("name1", "value1")
            .userProperty("name2", "value2")
            .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertNotNull(mqttDataEx.publish().properties()
            .matchFirst(h ->
                "name1".equals(h.key().asString()) &&
                    "value1".equals(h.value().asString())));
        assertNotNull(mqttDataEx.publish().properties()
            .matchFirst(h ->
                "name2".equals(h.key().asString()) &&
                    "value2".equals(h.value().asString())));
    }

    @Test
    public void shouldEncodeMqttPublishDataExWithoutTopic()
    {
        final byte[] array = MqttFunctions.dataEx()
            .typeId(0)
            .publish()
            .expiryInterval(15)
            .contentType("message")
            .format("TEXT")
            .responseTopic("sensor/one")
            .correlation("request-id-1")
            .userProperty("name", "value")
            .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals(15, mqttDataEx.publish().expiryInterval());
        assertEquals("message", mqttDataEx.publish().contentType().asString());
        assertEquals("TEXT", mqttDataEx.publish().format().toString());
        assertEquals("sensor/one", mqttDataEx.publish().responseTopic().asString());
        assertEquals("request-id-1", mqttDataEx.publish().correlation()
            .bytes().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));
        assertNotNull(mqttDataEx.publish().properties()
            .matchFirst(h ->
                "name".equals(h.key().asString()) &&
                    "value".equals(h.value().asString())));
    }

    @Test
    public void shouldEncodeMqttPublishDataExWithoutResponseTopic()
    {
        final byte[] array = MqttFunctions.dataEx()
            .typeId(0)
            .publish()
            .expiryInterval(15)
            .contentType("message")
            .format("TEXT")
            .correlation("request-id-1")
            .userProperty("name", "value")
            .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals(15, mqttDataEx.publish().expiryInterval());
        assertEquals("message", mqttDataEx.publish().contentType().asString());
        assertEquals("TEXT", mqttDataEx.publish().format().toString());
        assertNull(mqttDataEx.publish().responseTopic().asString());
        assertEquals("request-id-1", mqttDataEx.publish().correlation()
            .bytes().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));
        assertNotNull(mqttDataEx.publish().properties()
            .matchFirst(h ->
                "name".equals(h.key().asString()) &&
                    "value".equals(h.value().asString())));
    }

    @Test
    public void shouldEncodeMqttPublishDataExWithNullDefaults()
    {
        final byte[] array = MqttFunctions.dataEx()
            .typeId(0)
            .publish()
            .expiryInterval(15)
            .format("TEXT")
            .correlation("request-id-1")
            .userProperty("name", "value")
            .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals(15, mqttDataEx.publish().expiryInterval());
        assertNull(mqttDataEx.publish().contentType().asString());
        assertEquals("TEXT", mqttDataEx.publish().format().toString());
        assertNull(mqttDataEx.publish().responseTopic().asString());
        assertEquals("request-id-1", mqttDataEx.publish().correlation()
            .bytes().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));
        assertNotNull(mqttDataEx.publish().properties()
            .matchFirst(h ->
                "name".equals(h.key().asString()) &&
                    "value".equals(h.value().asString())));
    }

    @Test
    public void shouldEncodeMqttPublishDataExWithBytes()
    {
        final byte[] array = MqttFunctions.dataEx()
            .typeId(0)
            .publish()
            .expiryInterval(15)
            .contentType("message")
            .format("TEXT")
            .responseTopic("sensor/one")
            .correlationBytes("request-id-1".getBytes(UTF_8))
            .userProperty("name", "value")
            .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals(15, mqttDataEx.publish().expiryInterval());
        assertEquals("message", mqttDataEx.publish().contentType().asString());
        assertEquals("TEXT", mqttDataEx.publish().format().toString());
        assertEquals("sensor/one", mqttDataEx.publish().responseTopic().asString());
        assertEquals("request-id-1", mqttDataEx.publish().correlation()
            .bytes().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));
        assertNotNull(mqttDataEx.publish().properties()
            .matchFirst(h ->
                "name".equals(h.key().asString()) &&
                    "value".equals(h.value().asString())));
    }

    @Test
    public void shouldEncodeMqttPublishDataExWithNullUserPropertyValue()
    {
        final byte[] array = MqttFunctions.dataEx()
            .typeId(0)
            .publish()
            .expiryInterval(15)
            .contentType("message")
            .format("TEXT")
            .responseTopic("sensor/one")
            .correlation("request-id-1")
            .userProperty("name", null)
            .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals(15, mqttDataEx.publish().expiryInterval());
        assertEquals("message", mqttDataEx.publish().contentType().asString());
        assertEquals("TEXT", mqttDataEx.publish().format().toString());
        assertEquals("sensor/one", mqttDataEx.publish().responseTopic().asString());
        assertEquals("request-id-1", mqttDataEx.publish().correlation()
            .bytes().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));
        assertNotNull(mqttDataEx.publish().properties()
            .matchFirst(h ->
                "name".equals(h.key().asString()) &&
                    Objects.isNull(h.value().asString())));
    }

    @Test
    public void shouldEncodeMqttSessionDataEx()
    {
        final byte[] array = MqttFunctions.dataEx()
            .typeId(0)
            .session()
                .kind("WILL")
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttPublishDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttPublishDataEx.typeId());
        assertEquals("WILL", mqttPublishDataEx.session().kind().toString());
    }

    @Test
    public void shouldEncodeMqttSubscribeFlushEx()
    {
        final byte[] array = MqttFunctions.flushEx()
            .typeId(0)
            .subscribe()
                .filter("sensor/one", 1, "AT_MOST_ONCE", "SEND_RETAINED")
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttFlushExFW mqttFlushEx = new MqttFlushExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttFlushEx.typeId());
        assertNotNull(mqttFlushEx.subscribe().filters()
            .matchFirst(f ->
                "sensor/one".equals(f.pattern().asString()) &&
                    1 == f.subscriptionId() &&
                    0 == f.qos() &&
                    0b0001 == f.flags()));
    }

    @Test
    public void shouldEncodeMqttResetEx()
    {
        final byte[] array = MqttFunctions.resetEx()
            .typeId(0)
            .serverRef("mqtt-1.example.com:1883")
            .reasonCode(0)
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttResetExFW mqttResetEx = new MqttResetExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0, mqttResetEx.typeId());
        assertEquals("mqtt-1.example.com:1883", mqttResetEx.serverRef().asString());
        assertEquals(0, mqttResetEx.reasonCode());
    }

    @Test
    public void shouldEncodeMqttSessionState()
    {
        final byte[] array = MqttFunctions.session()
            .subscription("sensor/one", 1, "AT_MOST_ONCE", "SEND_RETAINED")
            .subscription("sensor/two", 1, 0)
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttSessionStateFW sessionState = new MqttSessionStateFW().wrap(buffer, 0, buffer.capacity());

        assertNotNull(sessionState.subscriptions()
            .matchFirst(f ->
                "sensor/one".equals(f.pattern().asString()) &&
                    1 == f.subscriptionId() &&
                    0 == f.qos() &&
                    0b0001 == f.flags()));

        assertNotNull(sessionState.subscriptions()
            .matchFirst(f ->
                "sensor/two".equals(f.pattern().asString()) &&
                    1 == f.subscriptionId() &&
                    0 == f.qos() &&
                    0 == f.reasonCode() &&
                    0b0000 == f.flags()));
    }

    @Test
    public void shouldEncodeWillMessage()
    {
        final byte[] array = MqttFunctions.will()
                .topic("will.client")
                .delay(20)
                .expiryInterval(15)
                .contentType("message")
                .format("TEXT")
                .responseTopic("will.client.response")
                .lifetimeId("1")
                .willId("2")
                .correlation("request-id-1")
                .userProperty("name", "value")
                .payload("client failed")
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttWillMessageFW willMessage = new MqttWillMessageFW().wrap(buffer, 0, buffer.capacity());

        assertEquals("will.client", willMessage.topic().asString());
        assertEquals(20, willMessage.delay());
        assertEquals(15, willMessage.expiryInterval());
        assertEquals("message", willMessage.contentType().asString());
        assertEquals("TEXT", willMessage.format().toString());
        assertEquals("will.client.response", willMessage.responseTopic().asString());
        assertEquals("1", willMessage.lifetimeId().asString());
        assertEquals("2", willMessage.willId().asString());
        assertEquals("request-id-1", willMessage.correlation()
            .bytes().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));
        assertNotNull(willMessage.properties()
            .matchFirst(h ->
                "name".equals(h.key().asString()) &&
                    "value".equals(h.value().asString())));
        assertEquals("client failed", willMessage.payload()
            .bytes().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));
    }

    @Test
    public void shouldEncodeWillMessageBytesPayload()
    {
        final byte[] array = MqttFunctions.will()
            .topic("will.client")
            .qos("AT_LEAST_ONCE")
            .flags("RETAIN")
            .responseTopic("response_topic")
            .correlationBytes("request-id-1".getBytes(UTF_8))
            .payloadBytes(new byte[] {0, 1, 2, 3, 4, 5})
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttWillMessageFW willMessage = new MqttWillMessageFW().wrap(buffer, 0, buffer.capacity());

        assertEquals("will.client", willMessage.topic().asString());
        assertEquals(1, willMessage.flags());
        assertEquals(0b0001, willMessage.flags());
        assertEquals("NONE", willMessage.format().toString());
        assertEquals("response_topic", willMessage.responseTopic().asString());
        assertEquals("request-id-1", willMessage.correlation()
            .bytes().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));
        assertArrayEquals(new byte[] {0, 1, 2, 3, 4, 5}, willMessage.payload()
            .bytes().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)).getBytes());
    }

    @Test
    public void shouldEncodeWillSignal()
    {
        final byte[] array = MqttFunctions.sessionSignal()
                .will()
                    .instanceId("zilla-1")
                    .clientId("client-1")
                    .delay(20)
                    .deliverAt(100000)
                    .lifetimeId("1")
                    .willId("2")
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttSessionSignalFW signal = new MqttSessionSignalFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(MqttSessionSignalType.WILL.value(), signal.kind());
        assertEquals("client-1", signal.will().clientId().asString());
        assertEquals(20, signal.will().delay());
        assertEquals(100000, signal.will().deliverAt());
        assertEquals("1", signal.will().lifetimeId().asString());
        assertEquals("2", signal.will().willId().asString());
        assertEquals("zilla-1", signal.will().instanceId().asString());
    }

    @Test
    public void shouldEncodeWillSignalUnknownDeliverAt()
    {
        final byte[] array = MqttFunctions.sessionSignal()
            .will()
                .instanceId("zilla-1")
                .clientId("client-1")
                .delay(20)
                .lifetimeId("1")
                .willId("2")
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttSessionSignalFW signal = new MqttSessionSignalFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(MqttSessionSignalType.WILL.value(), signal.kind());
        assertEquals("client-1", signal.will().clientId().asString());
        assertEquals(20, signal.will().delay());
        assertEquals(-1, signal.will().deliverAt());
        assertEquals("1", signal.will().lifetimeId().asString());
        assertEquals("2", signal.will().willId().asString());
        assertEquals("zilla-1", signal.will().instanceId().asString());
    }

    @Test
    public void shouldEncodeExpirySignal()
    {
        final byte[] array = MqttFunctions.sessionSignal()
            .expiry()
                .instanceId("zilla-1")
                .clientId("client-1")
                .delay(20)
                .expireAt(100000)
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttSessionSignalFW signal = new MqttSessionSignalFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(MqttSessionSignalType.EXPIRY.value(), signal.kind());
        assertEquals("client-1", signal.expiry().clientId().asString());
        assertEquals(20, signal.expiry().delay());
        assertEquals(100000, signal.expiry().expireAt());
        assertEquals("zilla-1", signal.expiry().instanceId().asString());
    }

    @Test
    public void shouldEncodeExpirySignalUnknownExpiry()
    {
        final byte[] array = MqttFunctions.sessionSignal()
            .expiry()
                .instanceId("zilla-1")
                .clientId("client-1")
                .delay(20)
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttSessionSignalFW signal = new MqttSessionSignalFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(MqttSessionSignalType.EXPIRY.value(), signal.kind());
        assertEquals("client-1", signal.expiry().clientId().asString());
        assertEquals("zilla-1", signal.expiry().instanceId().asString());
        assertEquals(20, signal.expiry().delay());
        assertEquals(-1, signal.expiry().expireAt());
    }
}
