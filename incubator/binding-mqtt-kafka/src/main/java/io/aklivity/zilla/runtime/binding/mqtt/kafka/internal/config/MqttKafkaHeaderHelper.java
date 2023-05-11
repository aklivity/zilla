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
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.agrona.collections.IntArrayList;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaMergedDataExFW;

public class MqttKafkaHeaderHelper
{
    private static final String KAFKA_TOPIC_HEADER_NAME = "zilla:topic";

    private static final String KAFKA_LOCAL_HEADER_NAME = "zilla:local";

    private static final String KAFKA_TIMEOUT_HEADER_NAME = "zilla:timeout-ms";

    private static final String KAFKA_CONTENT_TYPE_HEADER_NAME = "zilla:content-type";

    private static final String KAFKA_FORMAT_HEADER_NAME = "zilla:format";

    private static final String KAFKA_REPLY_TO_HEADER_NAME = "zilla:reply-to";

    private static final String KAFKA_CORRELATION_ID_HEADER_NAME = "zilla:correlation-id";

    public final OctetsFW kafkaTopicHeaderName;
    public final OctetsFW kafkaLocalHeaderName;
    public final OctetsFW kafkaTimeoutHeaderName;
    public final OctetsFW kafkaContentTypeHeaderName;
    public final OctetsFW kafkaFormatHeaderName;
    public final OctetsFW kafkaReplyToHeaderName;
    public final OctetsFW kafkaCorrelationHeaderName;
    private final Map<OctetsFW, Consumer<OctetsFW>> visitors;
    public final OctetsFW contentTypeRO = new OctetsFW();
    public final OctetsFW replyToRO = new OctetsFW();

    public int timeout;
    public OctetsFW contentType;
    public String format;
    public OctetsFW replyTo;
    public OctetsFW correlation;
    public IntArrayList userPropertiesOffsets;

    public MqttKafkaHeaderHelper()
    {
        kafkaTopicHeaderName = stringToOctets(KAFKA_TOPIC_HEADER_NAME);
        kafkaLocalHeaderName = stringToOctets(KAFKA_LOCAL_HEADER_NAME);
        kafkaTimeoutHeaderName = stringToOctets(KAFKA_TIMEOUT_HEADER_NAME);
        kafkaContentTypeHeaderName = stringToOctets(KAFKA_CONTENT_TYPE_HEADER_NAME);
        kafkaFormatHeaderName = stringToOctets(KAFKA_FORMAT_HEADER_NAME);
        kafkaReplyToHeaderName = stringToOctets(KAFKA_REPLY_TO_HEADER_NAME);
        kafkaCorrelationHeaderName = stringToOctets(KAFKA_CORRELATION_ID_HEADER_NAME);

        visitors = new HashMap<>();
        visitors.put(kafkaTopicHeaderName, this::skip);
        visitors.put(kafkaLocalHeaderName, this::skip);
        visitors.put(kafkaTimeoutHeaderName, this::visitTimeout);
        visitors.put(kafkaContentTypeHeaderName, this::visitContentType);
        visitors.put(kafkaFormatHeaderName, this::visitFormat);
        visitors.put(kafkaReplyToHeaderName, this::visitReplyTo);
        visitors.put(kafkaCorrelationHeaderName, this::visitCorrelationId);
    }

    public void visit(
        KafkaMergedDataExFW dataEx)
    {
        this.timeout = -1;
        this.contentType = null;
        this.format = null;
        this.replyTo = null;
        this.correlation = null;
        this.userPropertiesOffsets = new IntArrayList();
        if (dataEx != null)
        {
            dataEx.headers().matchFirst(this::dispatch);
        }
    }

    private boolean dispatch(
        KafkaHeaderFW header)
    {
        final Consumer<OctetsFW> visitor = visitors.get(header.name());
        if (visitor != null)
        {
            visitor.accept(header.value());
        }
        else
        {
            userPropertiesOffsets.add(header.offset());
        }
        return timeout != -1 && contentType != null && format != null && replyTo != null && correlation != null;
    }

    private void skip(
        OctetsFW value)
    {
    }

    private void visitContentType(
        OctetsFW value)
    {
        contentType = contentTypeRO.wrap(value.buffer(), value.offset(), value.limit());
    }

    private void visitFormat(
        OctetsFW value)
    {
        format = value.get((b, o, m) -> b.getStringWithoutLengthUtf8(o, m - o));
    }

    private void visitReplyTo(
        OctetsFW value)
    {
        replyTo = replyToRO.wrap(value.buffer(), value.offset(), value.limit());
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

    private static OctetsFW stringToOctets(
        String input)
    {
        String16FW inputFW = new String16FW(input);
        return new OctetsFW().wrap(inputFW.value(), 0, inputFW.length());
    }
}
