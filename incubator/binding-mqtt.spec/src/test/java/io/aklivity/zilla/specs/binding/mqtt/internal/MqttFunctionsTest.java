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
package io.aklivity.zilla.specs.binding.mqtt.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Objects;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.specs.binding.mqtt.internal.types.MqttEndReasonCode;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.SessionStateFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttBeginExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttDataExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttEndExFW;
import io.aklivity.zilla.specs.binding.mqtt.internal.types.stream.MqttFlushExFW;

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
    public void shouldEncodeMqttSessionBeginExt()
    {
        final byte[] array = MqttFunctions.beginEx()
            .typeId(0)
            .session()
                .clientId("client")
                .expiry(30)
                .willTopic("will.client")
                .willDelay(20)
                .willExpiryInterval(15)
                .willContentType("message")
                .willFormat("TEXT")
                .willResponseTopic("will.client.response")
                .willCorrelation("request-id-1")
                .willUserProperty("name", "value")
                .willPayload("client failed")
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttBeginExFW mqttBeginEx = new MqttBeginExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(2, mqttBeginEx.kind());
        assertEquals("client", mqttBeginEx.session().clientId().asString());
        assertEquals("will.client", mqttBeginEx.session().willTopic().asString());
        assertEquals(30, mqttBeginEx.session().expiry());
        assertEquals(20, mqttBeginEx.session().willDelay());
        assertEquals(15, mqttBeginEx.session().willExpiryInterval());
        assertEquals("message", mqttBeginEx.session().willContentType().asString());
        assertEquals("TEXT", mqttBeginEx.session().willFormat().toString());
        assertEquals("will.client.response", mqttBeginEx.session().willResponseTopic().asString());
        assertEquals("request-id-1", mqttBeginEx.session().willCorrelation()
            .bytes().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));
        assertNotNull(mqttBeginEx.session().willUserProperties()
            .matchFirst(h ->
                "name".equals(h.key().asString()) &&
                    "value".equals(h.value().asString())));
        assertEquals("client failed", mqttBeginEx.session().willPayload()
            .bytes().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));
    }

    @Test
    public void shouldEncodeMqttSessionBeginExtWithFlagsBytesWillPayload()
    {
        final byte[] array = MqttFunctions.beginEx()
            .typeId(0)
            .session()
                .clientId("client")
                .willTopic("will.client")
                .willFlags("RETAIN", "QOS1")
                .willCorrelationBytes("request-id-1".getBytes(UTF_8))
                .willPayloadBytes(new byte[] {0, 1, 2, 3, 4, 5})
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttBeginExFW mqttBeginEx = new MqttBeginExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(2, mqttBeginEx.kind());
        assertEquals("client", mqttBeginEx.session().clientId().asString());
        assertEquals("will.client", mqttBeginEx.session().willTopic().asString());
        assertEquals(0b1010, mqttBeginEx.session().willFlags());
        assertEquals("BINARY", mqttBeginEx.session().willFormat().toString());
        assertEquals("request-id-1", mqttBeginEx.session().willCorrelation()
            .bytes().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));
        assertArrayEquals(new byte[] {0, 1, 2, 3, 4, 5}, mqttBeginEx.session().willPayload()
            .bytes().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)).getBytes());
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
                    0 == f.subscriptionId()));
    }

    @Test
    public void shouldEncodeMqttSubscribeBeginExtWithFlags()
    {
        final byte[] array = MqttFunctions.beginEx()
                                          .typeId(0)
                                          .subscribe()
                                              .clientId("client")
                                              .filter("sensor/one", 1, "SEND_RETAINED")
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
                    0b1000 == f.flags()));
    }

    @Test
    public void shouldEncodeMqttProduceBeginEx()
    {
        final byte[] array = MqttFunctions.beginEx()
                .typeId(0)
                .publish()
                    .clientId("client")
                    .topic("sensor/one")
                    .build()
                .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttBeginExFW mqttBeginEx = new MqttBeginExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttBeginEx.kind());
        assertEquals("client", mqttBeginEx.publish().clientId().asString());
        assertEquals("sensor/one", mqttBeginEx.publish().topic().asString());
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
        assertEquals("sensor/one",  mqttDataEx.subscribe().responseTopic().asString());
        assertEquals("request-id-1",  mqttDataEx.subscribe().correlation()
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
                                              .flags("SEND_RETAINED", "NO_LOCAL")
                                              .build()
                                          .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals("sensor/one", mqttDataEx.subscribe().topic().asString());
        assertEquals(0b101000, mqttDataEx.subscribe().flags());
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
        assertEquals("sensor/one",  mqttDataEx.subscribe().responseTopic().asString());
        assertEquals("request-id-1",  mqttDataEx.subscribe().correlation()
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
        assertEquals("request-id-1",  mqttDataEx.subscribe().correlation()
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
        assertEquals("request-id-1",  mqttDataEx.subscribe().correlation()
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
        assertEquals("sensor/one",  mqttDataEx.subscribe().responseTopic().asString());
        assertEquals("request-id-1",  mqttDataEx.subscribe().correlation()
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
        assertEquals("sensor/one",  mqttDataEx.subscribe().responseTopic().asString());
        assertEquals("request-id-1",  mqttDataEx.subscribe().correlation()
            .bytes().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));
        assertNotNull(mqttDataEx.subscribe().properties()
                                .matchFirst(h ->
                                                "name".equals(h.key().asString()) &&
                                                    Objects.isNull(h.value().asString())));
    }

    @Test
    public void shouldEncodeMqttPublishDataEx()
    {
        final byte[] array = MqttFunctions.dataEx()
            .typeId(0)
            .publish()
                .topic("sensor/one")
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
        assertEquals("sensor/one", mqttPublishDataEx.publish().topic().asString());
        assertEquals(15, mqttPublishDataEx.publish().expiryInterval());
        assertEquals("message", mqttPublishDataEx.publish().contentType().asString());
        assertEquals("TEXT", mqttPublishDataEx.publish().format().toString());
        assertEquals("sensor/one",  mqttPublishDataEx.publish().responseTopic().asString());
        assertEquals("request-id-1",  mqttPublishDataEx.publish().correlation()
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
                .topic("sensor/one")
                .userProperty("name", "value")
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals("sensor/one", mqttDataEx.publish().topic().asString());
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
                .topic("sensor/one")
                .flags("RETAIN")
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals("sensor/one", mqttDataEx.publish().topic().asString());
        assertEquals(0b1000, mqttDataEx.publish().flags());
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
                .topic("sensor/one")
                .userProperty("name1", "value1")
                .userProperty("name2", "value2")
                .build()
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttDataExFW mqttDataEx = new MqttDataExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttDataEx.typeId());
        assertEquals("sensor/one", mqttDataEx.publish().topic().asString());
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
        assertNull(mqttDataEx.publish().topic().asString());
        assertEquals(15, mqttDataEx.publish().expiryInterval());
        assertEquals("message", mqttDataEx.publish().contentType().asString());
        assertEquals("TEXT", mqttDataEx.publish().format().toString());
        assertEquals("sensor/one",  mqttDataEx.publish().responseTopic().asString());
        assertEquals("request-id-1",  mqttDataEx.publish().correlation()
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
        assertEquals("sensor/one", mqttDataEx.publish().topic().asString());
        assertEquals(15, mqttDataEx.publish().expiryInterval());
        assertEquals("message", mqttDataEx.publish().contentType().asString());
        assertEquals("TEXT", mqttDataEx.publish().format().toString());
        assertNull(mqttDataEx.publish().responseTopic().asString());
        assertEquals("request-id-1",  mqttDataEx.publish().correlation()
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
        assertNull(mqttDataEx.publish().topic().asString());
        assertEquals(15, mqttDataEx.publish().expiryInterval());
        assertNull(mqttDataEx.publish().contentType().asString());
        assertEquals("TEXT", mqttDataEx.publish().format().toString());
        assertNull(mqttDataEx.publish().responseTopic().asString());
        assertEquals("request-id-1",  mqttDataEx.publish().correlation()
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
        assertEquals("sensor/one", mqttDataEx.publish().topic().asString());
        assertEquals(15, mqttDataEx.publish().expiryInterval());
        assertEquals("message", mqttDataEx.publish().contentType().asString());
        assertEquals("TEXT", mqttDataEx.publish().format().toString());
        assertEquals("sensor/one",  mqttDataEx.publish().responseTopic().asString());
        assertEquals("request-id-1",  mqttDataEx.publish().correlation()
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
        assertEquals("sensor/one", mqttDataEx.publish().topic().asString());
        assertEquals(15, mqttDataEx.publish().expiryInterval());
        assertEquals("message", mqttDataEx.publish().contentType().asString());
        assertEquals("TEXT", mqttDataEx.publish().format().toString());
        assertEquals("sensor/one",  mqttDataEx.publish().responseTopic().asString());
        assertEquals("request-id-1",  mqttDataEx.publish().correlation()
            .bytes().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));
        assertNotNull(mqttDataEx.publish().properties()
            .matchFirst(h ->
                "name".equals(h.key().asString()) &&
                    Objects.isNull(h.value().asString())));
    }

    @Test
    public void shouldEncodeMqttFlushEx()
    {
        final byte[] array = MqttFunctions.flushEx()
            .typeId(0)
            .filter("sensor/one", 1, "SEND_RETAINED")
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttFlushExFW mqttFlushEx = new MqttFlushExFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(0, mqttFlushEx.typeId());
        assertNotNull(mqttFlushEx.filters()
            .matchFirst(f ->
                "sensor/one".equals(f.pattern().asString()) &&
                    1 == f.subscriptionId() &&
                    0b1000 == f.flags()));
    }

    @Test
    public void shouldEncodeMqttAbortExAsUnsubscribe()
    {
        final byte[] array = MqttFunctions.endEx()
                .typeId(0)
                .reason("KEEP_ALIVE_EXPIRY")
                .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        MqttEndExFW mqttEndEx = new MqttEndExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0, mqttEndEx.typeId());
        assertEquals(MqttEndReasonCode.KEEP_ALIVE_EXPIRY, mqttEndEx.reasonCode().get());
    }

    @Test
    public void shouldEncodeMqttSessionState()
    {
        final byte[] array = MqttFunctions.sessionState()
            .clientId("client")
            .subscription("sensor/one", 1, "SEND_RETAINED")
            .subscription("sensor/two", 2, "SEND_RETAINED", "QOS1")
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        SessionStateFW sessionState = new SessionStateFW().wrap(buffer, 0, buffer.capacity());

        assertEquals("client", sessionState.clientId().asString());
        assertNotNull(sessionState.subscriptions()
            .matchFirst(f ->
                "sensor/one".equals(f.pattern().asString()) &&
                    1 == f.subscriptionId() &&
                    0b1000 == f.flags()));

        assertNotNull(sessionState.subscriptions()
            .matchFirst(f ->
                "sensor/two".equals(f.pattern().asString()) &&
                    2 == f.subscriptionId() &&
                    0b1010 == f.flags()));
    }
}
