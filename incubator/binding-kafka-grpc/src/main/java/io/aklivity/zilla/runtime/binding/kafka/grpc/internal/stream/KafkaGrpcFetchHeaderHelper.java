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
package io.aklivity.zilla.runtime.binding.kafka.grpc.internal.stream;


import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.agrona.AsciiSequenceView;
import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.config.KafkaGrpcCorrelationConfig;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.GrpcKind;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.KafkaMergedDataExFW;

public final class KafkaGrpcFetchHeaderHelper
{

    private final Map<DirectBuffer, Consumer<DirectBuffer>> visitors;
    private final OctetsFW serviceRO = new OctetsFW();
    private final OctetsFW methodRO = new OctetsFW();
    private final AsciiSequenceView correlatedIdRO = new AsciiSequenceView();

    public OctetsFW service;
    public OctetsFW method;
    public GrpcKind request;
    public GrpcKind response;
    public CharSequence correlationId;

    public KafkaGrpcFetchHeaderHelper(
        KafkaGrpcCorrelationConfig correlation)
    {
        Map<DirectBuffer, Consumer<DirectBuffer>> visitors = new HashMap<>();
        visitors.put(correlation.service.value(), this::visitService);
        visitors.put(correlation.method.value(), this::visitMethod);
        visitors.put(correlation.correlationId.value(), this::visitCorrelationId);
        this.visitors = visitors;
    }

    public void visit(
        KafkaDataExFW dataEx)
    {
        service = null;
        method = null;
        request = null;
        response = null;
        correlationId = null;

        if (dataEx != null)
        {
            final KafkaMergedDataExFW kafkaMergedDataEx = dataEx.merged();
            final Array32FW<KafkaHeaderFW> headers = kafkaMergedDataEx.headers();
            headers.forEach(this::dispatch);
        }
    }

    public boolean resolved()
    {
        return service != null &&
            method != null &&
            correlationId != null;
    }

    private boolean dispatch(
        KafkaHeaderFW header)
    {
        final OctetsFW name = header.name();
        final Consumer<DirectBuffer> visitor = visitors.get(name.value());
        if (visitor != null)
        {
            visitor.accept(header.value().value());
        }

        return service != null &&
            method != null &&
            correlationId != null;
    }

    private void visitService(
        DirectBuffer value)
    {
        service = serviceRO.wrap(value, 0, value.capacity());
    }

    private void visitMethod(
        DirectBuffer value)
    {
        method = methodRO.wrap(value, 0, value.capacity());
    }

    private void visitCorrelationId(
        DirectBuffer value)
    {
        correlationId = correlatedIdRO.wrap(value, 0, value.capacity());
    }
}
