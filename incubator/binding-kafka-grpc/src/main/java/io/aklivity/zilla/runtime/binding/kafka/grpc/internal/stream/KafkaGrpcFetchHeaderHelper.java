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

import static io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.GrpcKind.STREAM;
import static io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.GrpcKind.UNARY;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.agrona.AsciiSequenceView;
import org.agrona.DirectBuffer;
import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.GrpcKind;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.stream.KafkaMergedDataExFW;

public final class KafkaGrpcFetchHeaderHelper
{
    private static final OctetsFW HEADER_NAME_SERVICE = new OctetsFW()
        .wrap(new String8FW("zilla:service").value(), 0, 13);
    private static final OctetsFW HEADER_NAME_METHOD = new OctetsFW()
        .wrap(new String8FW("zilla:method").value(), 0, 12);
    private static final OctetsFW HEADER_NAME_REQUEST = new OctetsFW()
        .wrap(new String8FW("zilla:request").value(), 0, 13);
    private static final OctetsFW HEADER_NAME_RESPONSE = new OctetsFW()
        .wrap(new String8FW("zilla:response").value(), 0, 14);
    private static final OctetsFW HEADER_NAME_CORRELATION_ID = new OctetsFW()
        .wrap(new String8FW("zilla:correlation-id").value(), 0, 20);

    private static final Map<DirectBuffer, GrpcKind> GRPC_KINDS;
    static
    {
        DirectBuffer unary = new OctetsFW().wrap(new String16FW("UNARY").value(), 0, 5).value();
        DirectBuffer stream = new OctetsFW().wrap(new String16FW("STREAM").value(), 0, 6).value();

        Map<DirectBuffer, GrpcKind> kinds = new Object2ObjectHashMap<>();
        kinds.put(unary, UNARY);
        kinds.put(stream, STREAM);
        GRPC_KINDS = kinds;
    }

    private final Map<OctetsFW, Consumer<OctetsFW>> visitors;
    {
        Map<OctetsFW, Consumer<OctetsFW>> visitors = new HashMap<>();
        visitors.put(HEADER_NAME_SERVICE, this::visitService);
        visitors.put(HEADER_NAME_METHOD, this::visitMethod);
        visitors.put(HEADER_NAME_REQUEST, this::visitRequest);
        visitors.put(HEADER_NAME_RESPONSE, this::visitResponse);
        visitors.put(HEADER_NAME_CORRELATION_ID, this::visitCorrelationId);
        this.visitors = visitors;
    }
    private final OctetsFW serviceRO = new OctetsFW();
    private final OctetsFW methodRO = new OctetsFW();
    private final AsciiSequenceView requestRO = new AsciiSequenceView();
    private final AsciiSequenceView responseRO = new AsciiSequenceView();
    private final AsciiSequenceView correlatedIdRO = new AsciiSequenceView();

    public OctetsFW service;
    public OctetsFW method;
    public GrpcKind request;
    public GrpcKind response;
    public CharSequence correlationId;

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
            request != null &&
            response != null &&
            correlationId != null;
    }

    private void visitService(
        OctetsFW value)
    {
        service = serviceRO.wrap(value.value(), 0, value.sizeof());
    }

    private void visitMethod(
        OctetsFW value)
    {
        method = methodRO.wrap(value.value(), 0, value.sizeof());
    }

    private void visitRequest(
        OctetsFW value)
    {
        request = GRPC_KINDS.get(value.value());
    }

    private void visitResponse(
        OctetsFW value)
    {
        response = GRPC_KINDS.get(value.value());
    }

    private void visitCorrelationId(
        OctetsFW value)
    {
        correlationId = correlatedIdRO.wrap(value.value(), 0, value.sizeof());
    }
}
