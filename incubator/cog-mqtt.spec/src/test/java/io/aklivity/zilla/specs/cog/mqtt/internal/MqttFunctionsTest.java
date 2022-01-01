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
package io.aklivity.zilla.specs.cog.mqtt.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Objects;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.specs.cog.mqtt.internal.types.stream.MqttAbortExFW;
import io.aklivity.zilla.specs.cog.mqtt.internal.types.stream.MqttBeginExFW;
import io.aklivity.zilla.specs.cog.mqtt.internal.types.stream.MqttDataExFW;
import io.aklivity.zilla.specs.cog.mqtt.internal.types.stream.MqttFlushExFW;

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
        final byte[] expected = new byte[]{1};
        assertArrayEquals(bytes, expected);
    }

    @Test
    public void shouldEncodeMqttBeginExtAsSubscribe()
    {
        final byte[] array = MqttFunctions.beginEx()
                .typeId(0)
                .capabilities("SUBSCRIBE_ONLY")
                .clientId("client")
                .topic("sensor/one")
                .subscriptionId(1)
                .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttBeginExFW mqttBeginEx = new MqttBeginExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals("SUBSCRIBE_ONLY", mqttBeginEx.capabilities().toString());
        assertEquals("client", mqttBeginEx.clientId().asString());
        assertEquals("sensor/one", mqttBeginEx.topic().asString());
        assertEquals(1, mqttBeginEx.subscriptionId());
    }

    @Test
    public void shouldEncodeMqttBeginExtAsSubscribeWithFlags()
    {
        final byte[] array = MqttFunctions.beginEx()
                                          .typeId(0)
                                          .capabilities("SUBSCRIBE_ONLY")
                                          .clientId("client")
                                          .topic("sensor/one")
                                          .flags("SEND_RETAINED")
                                          .subscriptionId(1)
                                          .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttBeginExFW mqttBeginEx = new MqttBeginExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals("SUBSCRIBE_ONLY", mqttBeginEx.capabilities().toString());
        assertEquals("client", mqttBeginEx.clientId().asString());
        assertEquals("sensor/one", mqttBeginEx.topic().asString());
        assertEquals(0b01, mqttBeginEx.flags());
        assertEquals(1, mqttBeginEx.subscriptionId());
    }

    @Test
    public void shouldEncodeMqttBeginExAsSuback()
    {
        final byte[] array = MqttFunctions.beginEx()
                .typeId(0)
                .capabilities("PUBLISH_ONLY")
                .clientId("client")
                .topic("sensor/one")
                .subscriptionId(1)
                .userProperty("name", "value")
                .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttBeginExFW mqttBeginEx = new MqttBeginExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals("PUBLISH_ONLY", mqttBeginEx.capabilities().toString());
        assertEquals("client", mqttBeginEx.clientId().asString());
        assertEquals("sensor/one", mqttBeginEx.topic().asString());
        assertEquals(1, mqttBeginEx.subscriptionId());
        assertNotNull(mqttBeginEx.properties()
                                .matchFirst(h ->
                                                "name".equals(h.key().asString()) &&
                                                    "value".equals(h.value().asString())) != null);
    }

    @Test
    public void shouldEncodeMqttBeginExAsSubackWithNullUserPropertyValue()
    {
        final byte[] array = MqttFunctions.beginEx()
                .typeId(0)
                .capabilities("PUBLISH_ONLY")
                .clientId("client")
                .topic("sensor/one")
                .subscriptionId(1)
                .userProperty("name", null)
                .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttBeginExFW mqttBeginEx = new MqttBeginExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals("PUBLISH_ONLY", mqttBeginEx.capabilities().toString());
        assertEquals("client", mqttBeginEx.clientId().asString());
        assertEquals("sensor/one", mqttBeginEx.topic().asString());
        assertEquals(1, mqttBeginEx.subscriptionId());
        assertNotNull(mqttBeginEx.properties()
                                 .matchFirst(h ->
                                                 "name".equals(h.key().asString()) &&
                                                     Objects.isNull(h.value())) != null);
    }

    @Test
    public void shouldEncodeMqttDataEx()
    {
        final byte[] array = MqttFunctions.dataEx()
                .typeId(0)
                .topic("sensor/one")
                .expiryInterval(15)
                .contentType("message")
                .format("TEXT")
                .responseTopic("sensor/one")
                .correlation("request-id-1")
                .userProperty("name", "value")
                .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals("sensor/one", mqttDataEx.topic().asString());
        assertEquals(15, mqttDataEx.expiryInterval());
        assertEquals("message", mqttDataEx.contentType().asString());
        assertEquals("TEXT", mqttDataEx.format().toString());
        assertEquals("sensor/one",  mqttDataEx.responseTopic().asString());
        assertEquals("MQTT_BINARY [length=12, bytes=octets[12]]",  mqttDataEx.correlation().toString());
        assertNotNull(mqttDataEx.properties()
                                .matchFirst(h ->
                                                "name".equals(h.key().asString()) &&
                                                    "value".equals(h.value().asString())) != null);
    }

    @Test
    public void shouldEncodeMqttFlushEx()
    {
        final byte[] array = MqttFunctions.flushEx()
                .typeId(0)
                .flags("SEND_RETAINED")
                .capabilities("SUBSCRIBE_ONLY")
                .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttFlushExFW mqttFlushEx = new MqttFlushExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttFlushEx.typeId());
        assertEquals(0b01, mqttFlushEx.flags());
        assertEquals("SUBSCRIBE_ONLY", mqttFlushEx.capabilities().toString());
    }

    @Test
    public void shouldEncodeMqttDataExWithUserProperty()
    {
        final byte[] array = MqttFunctions.dataEx()
                                          .typeId(0)
                                          .topic("sensor/one")
                                          .userProperty("name", "value")
                                          .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals("sensor/one", mqttDataEx.topic().asString());
        assertNotNull(mqttDataEx.properties()
                                .matchFirst(h ->
                                                "name".equals(h.key().asString()) &&
                                                    "value".equals(h.value().asString())) != null);
    }

    @Test
    public void shouldEncodeMqttDataExWithFlags()
    {
        final byte[] array = MqttFunctions.dataEx()
                                          .typeId(0)
                                          .topic("sensor/one")
                                          .flags("RETAIN")
                                          .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals("sensor/one", mqttDataEx.topic().asString());
        assertEquals(0b01, mqttDataEx.flags());
    }

    @Test
    public void shouldEncodeMqttDataExWithUserPropertyNoTopic()
    {
        final byte[] array = MqttFunctions.dataEx()
                                          .typeId(0)
                                          .userProperty("name", "value")
                                          .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertNotNull(mqttDataEx.properties()
                                .matchFirst(h ->
                                                "name".equals(h.key().asString()) &&
                                                    "value".equals(h.value().asString())) != null);
    }

    @Test
    public void shouldEncodeMqttDataExWithUserProperties()
    {
        final byte[] array = MqttFunctions.dataEx()
                                          .typeId(0)
                                          .topic("sensor/one")
                                          .userProperty("name1", "value1")
                                          .userProperty("name2", "value2")
                                          .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals("sensor/one", mqttDataEx.topic().asString());
        assertNotNull(mqttDataEx.properties()
                                .matchFirst(h ->
                                                "name1".equals(h.key().asString()) &&
                                                    "value1".equals(h.value().asString())) != null);
        assertNotNull(mqttDataEx.properties()
                                .matchFirst(h ->
                                                "name2".equals(h.key().asString()) &&
                                                    "value2".equals(h.value().asString())) != null);
    }

    @Test
    public void shouldEncodeMqttDataExWithoutTopic()
    {
        final byte[] array = MqttFunctions.dataEx()
                                          .typeId(0)
                                          .expiryInterval(15)
                                          .contentType("message")
                                          .format("TEXT")
                                          .responseTopic("sensor/one")
                                          .correlation("request-id-1")
                                          .userProperty("name", "value")
                                          .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertNull(mqttDataEx.topic().asString());
        assertEquals(15, mqttDataEx.expiryInterval());
        assertEquals("message", mqttDataEx.contentType().asString());
        assertEquals("TEXT", mqttDataEx.format().toString());
        assertEquals("sensor/one",  mqttDataEx.responseTopic().asString());
        assertEquals("MQTT_BINARY [length=12, bytes=octets[12]]",  mqttDataEx.correlation().toString());
        assertNotNull(mqttDataEx.properties()
                                .matchFirst(h ->
                                                "name".equals(h.key().asString()) &&
                                                    "value".equals(h.value().asString())) != null);
    }

    @Test
    public void shouldEncodeMqttDataExWithoutResponseTopic()
    {
        final byte[] array = MqttFunctions.dataEx()
                                          .typeId(0)
                                          .topic("sensor/one")
                                          .expiryInterval(15)
                                          .contentType("message")
                                          .format("TEXT")
                                          .correlation("request-id-1")
                                          .userProperty("name", "value")
                                          .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals("sensor/one", mqttDataEx.topic().asString());
        assertEquals(15, mqttDataEx.expiryInterval());
        assertEquals("message", mqttDataEx.contentType().asString());
        assertEquals("TEXT", mqttDataEx.format().toString());
        assertNull(mqttDataEx.responseTopic().asString());
        assertEquals("MQTT_BINARY [length=12, bytes=octets[12]]",  mqttDataEx.correlation().toString());
        assertNotNull(mqttDataEx.properties()
                                .matchFirst(h ->
                                                "name".equals(h.key().asString()) &&
                                                    "value".equals(h.value().asString())) != null);
    }

    @Test
    public void shouldEncodeMqttDataExWithNullDefaults()
    {
        final byte[] array = MqttFunctions.dataEx()
                                          .typeId(0)
                                          .expiryInterval(15)
                                          .format("TEXT")
                                          .correlation("request-id-1")
                                          .userProperty("name", "value")
                                          .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertNull(mqttDataEx.topic().asString());
        assertEquals(15, mqttDataEx.expiryInterval());
        assertNull(mqttDataEx.contentType().asString());
        assertEquals("TEXT", mqttDataEx.format().toString());
        assertNull(mqttDataEx.responseTopic().asString());
        assertEquals("MQTT_BINARY [length=12, bytes=octets[12]]",  mqttDataEx.correlation().toString());
        assertNotNull(mqttDataEx.properties()
                                .matchFirst(h ->
                                                "name".equals(h.key().asString()) &&
                                                    "value".equals(h.value().asString())) != null);
    }

    @Test
    public void shouldEncodeMqttDataExWithBytes()
    {
        final byte[] array = MqttFunctions.dataEx()
                                          .typeId(0)
                                          .topic("sensor/one")
                                          .expiryInterval(15)
                                          .contentType("message")
                                          .format("TEXT")
                                          .responseTopic("sensor/one")
                                          .correlationBytes("request-id-1".getBytes(UTF_8))
                                          .userProperty("name", "value")
                                          .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals("sensor/one", mqttDataEx.topic().asString());
        assertEquals(15, mqttDataEx.expiryInterval());
        assertEquals("message", mqttDataEx.contentType().asString());
        assertEquals("TEXT", mqttDataEx.format().toString());
        assertEquals("sensor/one",  mqttDataEx.responseTopic().asString());
        assertEquals("MQTT_BINARY [length=12, bytes=octets[12]]",  mqttDataEx.correlation().toString());
        assertNotNull(mqttDataEx.properties()
                                .matchFirst(h ->
                                                "name".equals(h.key().asString()) &&
                                                    "value".equals(h.value().asString())) != null);
    }

    @Test
    public void shouldEncodeMqttDataExWithNullUserPropertyValue()
    {
        final byte[] array = MqttFunctions.dataEx()
                                          .typeId(0)
                                          .topic("sensor/one")
                                          .expiryInterval(15)
                                          .contentType("message")
                                          .format("TEXT")
                                          .responseTopic("sensor/one")
                                          .correlation("request-id-1")
                                          .userProperty("name", null)
                                          .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals("sensor/one", mqttDataEx.topic().asString());
        assertEquals(15, mqttDataEx.expiryInterval());
        assertEquals("message", mqttDataEx.contentType().asString());
        assertEquals("TEXT", mqttDataEx.format().toString());
        assertEquals("sensor/one",  mqttDataEx.responseTopic().asString());
        assertEquals("MQTT_BINARY [length=12, bytes=octets[12]]",  mqttDataEx.correlation().toString());
        assertNotNull(mqttDataEx.properties()
                                .matchFirst(h ->
                                                "name".equals(h.key().asString()) &&
                                                    Objects.isNull(h.value())) != null);
    }

    @Test
    public void shouldEncodeMqttAbortExAsUnsubscribe()
    {
        final byte[] array = MqttFunctions.abortEx()
                .typeId(0)
                .reason(0xf9)
                .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttAbortExFW mqttAbortEx = new MqttAbortExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0, mqttAbortEx.typeId());
        assertEquals(0xf9, mqttAbortEx.reason());
    }
}
