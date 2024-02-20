/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.exporter.stdout.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.ExporterConfig;
import io.aklivity.zilla.runtime.engine.internal.layouts.EventsLayout;
import io.aklivity.zilla.runtime.exporter.stdout.internal.config.StdoutExporterConfig;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.HttpEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.KafkaEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.MqttEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.Result;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.SchemaRegistryEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TcpEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TlsError;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TlsEventFW;

public class StdoutExporterHandlerTest
{
    private static final Path ENGINE_PATH = Paths.get("target/zilla-itests");
    private static final Path EVENTS_PATH = ENGINE_PATH.resolve("events");
    private static final int CAPACITY = 1024;
    private static final int HTTP_TYPE_ID = 1;
    private static final int KAFKA_TYPE_ID = 2;
    private static final int MQTT_TYPE_ID = 3;
    private static final int SCHEMA_REGISTRY_TYPE_ID = 4;
    private static final int TCP_TYPE_ID = 5;
    private static final int TLS_TYPE_ID = 6;
    private static final String EXPECTED_OUTPUT =
        "HTTP Authorization Failed [timestamp = 77] [traceId = 0x0000000000000042] [binding = ns.binding] " +
            "[identity = identity]\n" +
        "HTTP Request [timestamp = 77] [traceId = 0x0000000000000042] [binding = ns.binding] " +
            "[name1 = value1] [name2 = value2] \n" +
        "HTTP Response [timestamp = 77] [traceId = 0x0000000000000042] [binding = ns.binding] " +
            "[name1 = value1] [name2 = value2] \n" +
        "Kafka Authorization Failed [timestamp = 77] [traceId = 0x0000000000000042] [binding = ns.binding]\n" +
        "Kafka API Version Rejected [timestamp = 77] [traceId = 0x0000000000000042] [binding = ns.binding]\n" +
        "MQTT Authorization Failed [timestamp = 77] [traceId = 0x0000000000000042] [binding = ns.binding] " +
            "[identity = identity]\n" +
        "Schema Registry Remote Access Rejected [timestamp = 77] [traceId = 0x0000000000000042] [binding = ns.binding] " +
            "[url = url] [method = method] [status = 42]\n" +
        "TCP DNS Resolution Failed [timestamp = 77] [traceId = 0x0000000000000042] [binding = ns.binding] " +
            "[address = address]\n" +
        "TLS Failed [timestamp = 77] [traceId = 0x0000000000000042] [binding = ns.binding] [error = HANDSHAKE_ERROR]\n";

    @Test
    public void shouldStart()
    {
        // GIVEN
        EventsLayout layout = new EventsLayout.Builder()
            .path(EVENTS_PATH)
            .capacity(CAPACITY)
            .build();
        MutableDirectBuffer eventBuffer = new UnsafeBuffer(new byte[64]);
        HttpEventFW httpAuthorizationEvent = new HttpEventFW.Builder()
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .authorization(e -> e.timestamp(77)
                .traceId(0x0000000000000042L)
                .namespacedId(0x0000000200000007L)
                .result(r -> r.set(Result.FAILURE))
                .identity("identity")
            ).build();
        layout.writeEvent(HTTP_TYPE_ID, httpAuthorizationEvent.buffer(), 0, httpAuthorizationEvent.sizeof());
        HttpEventFW httpRequestEvent = new HttpEventFW.Builder()
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .request(e -> e.timestamp(77)
                .traceId(0x0000000000000042L)
                .namespacedId(0x0000000200000007L)
                .headersItem(h -> h.name("name1").value("value1"))
                .headersItem(h -> h.name("name2").value("value2"))
            ).build();
        layout.writeEvent(HTTP_TYPE_ID, httpRequestEvent.buffer(), 0, httpRequestEvent.sizeof());
        HttpEventFW httpResponseEvent = new HttpEventFW.Builder()
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .response(e -> e.timestamp(77)
                .traceId(0x0000000000000042L)
                .namespacedId(0x0000000200000007L)
                .headersItem(h -> h.name("name1").value("value1"))
                .headersItem(h -> h.name("name2").value("value2"))
            ).build();
        layout.writeEvent(HTTP_TYPE_ID, httpResponseEvent.buffer(), 0, httpResponseEvent.sizeof());
        KafkaEventFW kafkaAuthorizationEvent = new KafkaEventFW.Builder()
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .authorization(e -> e.timestamp(77)
                .traceId(0x0000000000000042L)
                .namespacedId(0x0000000200000007L)
                .result(r -> r.set(Result.FAILURE))
            ).build();
        layout.writeEvent(KAFKA_TYPE_ID, kafkaAuthorizationEvent.buffer(), 0, kafkaAuthorizationEvent.sizeof());
        KafkaEventFW kafkaApiVersionRejectedEvent = new KafkaEventFW.Builder()
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .apiVersionRejected(e -> e.timestamp(77)
                .traceId(0x0000000000000042L)
                .namespacedId(0x0000000200000007L)
            ).build();
        layout.writeEvent(KAFKA_TYPE_ID, kafkaApiVersionRejectedEvent.buffer(), 0, kafkaApiVersionRejectedEvent.sizeof());
        MqttEventFW mqttAuthorizationEvent = new MqttEventFW.Builder()
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .authorization(e -> e.timestamp(77)
                .traceId(0x0000000000000042L)
                .namespacedId(0x0000000200000007L)
                .result(r -> r.set(Result.FAILURE))
                .identity("identity")
            ).build();
        layout.writeEvent(MQTT_TYPE_ID, mqttAuthorizationEvent.buffer(), 0, mqttAuthorizationEvent.sizeof());
        SchemaRegistryEventFW schemaRegistryAuthorizationEvent = new SchemaRegistryEventFW.Builder()
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .remoteAccessRejected(e -> e.timestamp(77)
                .traceId(0x0000000000000042L)
                .namespacedId(0x0000000200000007L)
                .url("url")
                .method("method")
                .status((short) 42)
            ).build();
        layout.writeEvent(SCHEMA_REGISTRY_TYPE_ID, schemaRegistryAuthorizationEvent.buffer(), 0,
            schemaRegistryAuthorizationEvent.sizeof());
        TcpEventFW tcpDnsResolutionFailedEvent = new TcpEventFW.Builder()
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .dnsResolutionFailed(e -> e.timestamp(77)
                .traceId(0x0000000000000042L)
                .namespacedId(0x0000000200000007L)
                .address("address")
            ).build();
        layout.writeEvent(TCP_TYPE_ID, tcpDnsResolutionFailedEvent.buffer(), 0, tcpDnsResolutionFailedEvent.sizeof());
        TlsEventFW tlsFailedEvent = new TlsEventFW.Builder()
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .tlsFailed(e -> e.timestamp(77)
                .traceId(0x0000000000000042L)
                .namespacedId(0x0000000200000007L)
                .error(t -> t.set(TlsError.HANDSHAKE_ERROR))
            ).build();
        layout.writeEvent(TLS_TYPE_ID, tlsFailedEvent.buffer(), 0, tlsFailedEvent.sizeof());

        EngineConfiguration config = mock(EngineConfiguration.class);
        EngineContext context = mock(EngineContext.class);
        StdoutExporterConfig exporter = new StdoutExporterConfig(mock(ExporterConfig.class));
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(os);
        when(context.lookupLabelId("http")).thenReturn(HTTP_TYPE_ID);
        when(context.lookupLabelId("kafka")).thenReturn(KAFKA_TYPE_ID);
        when(context.lookupLabelId("mqtt")).thenReturn(MQTT_TYPE_ID);
        when(context.lookupLabelId("schema-registry")).thenReturn(SCHEMA_REGISTRY_TYPE_ID);
        when(context.lookupLabelId("tcp")).thenReturn(TCP_TYPE_ID);
        when(context.lookupLabelId("tls")).thenReturn(TLS_TYPE_ID);
        when(context.supplyNamespace(0x0000000200000007L)).thenReturn("ns");
        when(context.supplyLocalName(0x0000000200000007L)).thenReturn("binding");
        when(context.supplyEventReader()).thenReturn(layout::readEvent);
        StdoutExporterHandler handler = new StdoutExporterHandler(config, context, exporter, ps);

        // WHEN
        handler.start();
        for (int i = 0; i < 42; i++)
        {
            handler.export();
        }
        handler.stop();

        // THEN
        assertThat(os.toString(), equalTo(EXPECTED_OUTPUT));
    }
}
