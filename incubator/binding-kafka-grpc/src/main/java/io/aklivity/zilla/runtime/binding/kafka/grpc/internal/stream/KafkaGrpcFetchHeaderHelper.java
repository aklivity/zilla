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

import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.config.KafkaGrpcCorrelationConfig;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.KafkaMergedDataExFW;

public final class KafkaGrpcFetchHeaderHelper
{
    private final Map<OctetsFW, Consumer<OctetsFW>> visitors;
    private final OctetsFW serviceRO = new OctetsFW();
    private final OctetsFW methodRO = new OctetsFW();
    private final OctetsFW correlatedIdRO = new OctetsFW();

    public OctetsFW service;
    public OctetsFW method;
    public OctetsFW correlationId;

    public KafkaGrpcFetchHeaderHelper(
        KafkaGrpcCorrelationConfig correlation)
    {
        Map<OctetsFW, Consumer<OctetsFW>> visitors = new HashMap<>();
        visitors.put(new OctetsFW().wrap(correlation.service.value(),
            0, correlation.service.length()), this::visitService);
        visitors.put(new OctetsFW().wrap(correlation.method.value(),
            0, correlation.method.length()), this::visitMethod);
        visitors.put(new OctetsFW().wrap(correlation.correlationId.value(),
            0, correlation.correlationId.length()), this::visitCorrelationId);
        this.visitors = visitors;
    }

    public void visit(
        KafkaDataExFW dataEx)
    {
        service = null;
        method = null;
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
        final Consumer<OctetsFW> visitor = visitors.get(name);
        if (visitor != null)
        {
            visitor.accept(header.value());
        }

        return service != null &&
            method != null &&
            correlationId != null;
    }

    private void visitService(
        OctetsFW value)
    {
        service = serviceRO.wrap(value.buffer(), value.offset(), value.limit());
    }

    private void visitMethod(
        OctetsFW value)
    {
        method = methodRO.wrap(value.buffer(), value.offset(), value.limit());
    }

    private void visitCorrelationId(
        OctetsFW value)
    {
        correlationId = correlatedIdRO.wrap(value.buffer(), value.offset(), value.limit());
    }
}
