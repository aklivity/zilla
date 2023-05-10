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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaMergedDataExFW;

public class MqttKafkaHeaderHelper
{
    public static final String KAFKA_TOPIC_HEADER_NAME = "zilla:topic";
    public static final String KAFKA_LOCAL_HEADER_NAME = "zilla:local";
    public static final String KAFKA_TIMEOUT_HEADER_NAME = "zilla:timeout-ms";
    public static final String KAFKA_CONTENT_TYPE_HEADER_NAME = "zilla:content-type";
    public static final String KAFKA_FORMAT_HEADER_NAME = "zilla:format";
    public static final String KAFKA_REPLY_TO_HEADER_NAME = "zilla:reply-to";
    public static final String KAFKA_CORRELATION_ID_HEADER_NAME = "zilla:correlation-id";
    public final OctetsFW kafkaTopicHeaderOctets;
    public final OctetsFW kafkaLocalHeaderOctets;
    public final OctetsFW kafkaTimeoutHeaderOctets;
    public final OctetsFW kafkaContentTypeHeaderOctets;
    public final OctetsFW kafkaFormatHeaderOctets;
    public final OctetsFW kafkaReplyToHeaderOctets;
    public final OctetsFW kafkaCorrelationHeaderOctets;
    private final Map<String, Consumer<OctetsFW>> visitors;
    private final MutableDirectBuffer octetsBuffer;
    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBuffer(0L, 0), 0, 0);


    public MqttKafkaHeaderHelper()
    {
        octetsBuffer = new UnsafeBuffer(new byte[8 * 1024]);
        visitors = new HashMap<>();
        visitors.put(KAFKA_TOPIC_HEADER_NAME, this::visitTopic);
        visitors.put(KAFKA_LOCAL_HEADER_NAME, this::visitLocal);
        visitors.put(KAFKA_TIMEOUT_HEADER_NAME, this::visitTimeout);
        visitors.put(KAFKA_CONTENT_TYPE_HEADER_NAME, this::visitContentType);
        visitors.put(KAFKA_FORMAT_HEADER_NAME, this::visitFormat);
        visitors.put(KAFKA_REPLY_TO_HEADER_NAME, this::visitReplyTo);
        visitors.put(KAFKA_CORRELATION_ID_HEADER_NAME, this::visitCorrelationId);

        kafkaTopicHeaderOctets = stringToOctets(KAFKA_TOPIC_HEADER_NAME);
        kafkaLocalHeaderOctets = stringToOctets(KAFKA_LOCAL_HEADER_NAME);
        kafkaTimeoutHeaderOctets = stringToOctets(KAFKA_TIMEOUT_HEADER_NAME);
        kafkaContentTypeHeaderOctets = stringToOctets(KAFKA_CONTENT_TYPE_HEADER_NAME);
        kafkaFormatHeaderOctets = stringToOctets(KAFKA_FORMAT_HEADER_NAME);
        kafkaReplyToHeaderOctets = stringToOctets(KAFKA_REPLY_TO_HEADER_NAME);
        kafkaCorrelationHeaderOctets = stringToOctets(KAFKA_CORRELATION_ID_HEADER_NAME);
    }

    public List<String> topicNames;
    public String local;
    public int timeout;
    public String contentType;
    public String format;
    public String replyTo;
    public OctetsFW correlation;
    public Map<String, List<String>> userProperties;

    private OctetsFW stringToOctets(String string0)
    {
        String16FW string = new String16FW(string0);
        return new OctetsFW().wrap(string.value(), 0, string.length());
    }

    public void visit(
        KafkaMergedDataExFW dataEx)
    {
        topicNames = new ArrayList<>();
        local = null;
        timeout = -1;
        contentType = null;
        format = null;
        replyTo = null;
        correlation = null;
        userProperties = new HashMap<>();
        if (dataEx != null)
        {
            dataEx.headers().matchFirst(this::dispatch);
        }
    }

    private boolean dispatch(
        KafkaHeaderFW header)
    {
        final String headerName = header.name().get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o));
        final Consumer<OctetsFW> visitor = visitors
            .get(headerName);
        if (visitor != null)
        {
            visitor.accept(header.value());
        }
        else
        {
            OctetsFW value = header.value();
            if (value != null)
            {
                String propertyValue = value.get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o));
                userProperties.computeIfAbsent(headerName, h -> new ArrayList<>()).add(propertyValue);
            }
        }
        return timeout != -1 && contentType != null && format != null && replyTo != null && correlation != null;
    }

    private void visitTopic(
        OctetsFW value)
    {
        topicNames.add(value.get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o)));
    }

    private void visitLocal(
        OctetsFW value)
    {
        local = value.get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o));
    }

    private void visitContentType(
        OctetsFW value)
    {
        contentType = value.get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o));
    }

    private void visitFormat(
        OctetsFW value)
    {
        format = value.get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o));
    }

    private void visitReplyTo(
        OctetsFW value)
    {
        replyTo = value.get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o));
    }

    private void visitCorrelationId(
        OctetsFW value)
    {
        correlation = value;
    }

    private void visitTimeout(
        OctetsFW value)
    {
        timeout = value.get((b, o, m) -> b.getInt(o, ByteOrder.BIG_ENDIAN));
    }
}
