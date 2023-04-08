package io.aklivity.zilla.runtime.binding.kafka.grpc.internal.config;

import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.String16FW;

public class KafkaGrpcCorrelationConfig
{
    public final String16FW correlationId;
    public final String16FW service;
    public final String16FW method;
    public final String16FW request;
    public final String16FW response;

    public KafkaGrpcCorrelationConfig(
        String16FW correlationId,
        String16FW service,
        String16FW method,
        String16FW request,
        String16FW response)
    {
        this.correlationId = correlationId;
        this.service = service;
        this.method = method;
        this.request = request;
        this.response = response;
    }
}
