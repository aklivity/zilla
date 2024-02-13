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
package io.aklivity.zilla.runtime.exporter.stdout.internal.stream;

import java.io.PrintStream;
import java.util.function.LongFunction;
import java.util.function.ToIntFunction;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.util.function.EventReader;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.StringFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.HttpAuthorizationEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.HttpEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.KafkaAuthorizationEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.KafkaEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.MqttAuthorizationEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.MqttEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.Result;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.SchemaRegistryEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.SchemaRegistryRemoteAccessRejectedEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TcpEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TcpRemoteAccessFailedEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TlsEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TlsFailedEventFW;

public class StdoutEventsStream
{
    private static final String HTTP_AUTHORIZATION_FORMAT =
        "%s: HTTP Authorization %s [timestamp = %d] [traceId = 0x%016x] [binding = %s.%s] [identity = %s]%n";
    private static final String KAFKA_AUTHORIZATION_FORMAT =
        "%s: Kafka Authorization %s [timestamp = %d] [traceId = 0x%016x] [binding = %s.%s]%n";
    private static final String KAFKA_API_VERSION_REJECTED =
        "ERROR: Kafka API Version Rejected [timestamp = %d] [traceId = 0x%016x] [binding = %s.%s]%n";
    private static final String MQTT_AUTHORIZATION_FORMAT =
        "%s: MQTT Authorization %s [timestamp = %d] [traceId = 0x%016x] [binding = %s.%s] [identity = %s]%n";
    private static final String SCHEMA_REGISTRY_REMOTE_ACCESS_REJECTED =
        "ERROR: Schema Registry Remote Access Rejected [timestamp = %d] [traceId = 0x%016x] [binding = %s.%s] [url = %s]" +
        "[method = %s] [status = %d]%n";
    private static final String TCP_REMOTE_ACCESS_FAILED_FORMAT =
        "ERROR: TCP Remote Access Failed [timestamp = %d] [traceId = 0x%016x] [binding = %s.%s] [address = %s]%n";
    private static final String TLS_FAILED_FORMAT =
        "ERROR: TLS Failed [timestamp = %d] [traceId = 0x%016x] [binding = %s.%s] [error = %s]%n";

    private final HttpEventFW httpEventRO = new HttpEventFW();
    private final KafkaEventFW kafkaEventRO = new KafkaEventFW();
    private final MqttEventFW mqttEventRO = new MqttEventFW();
    private final SchemaRegistryEventFW schemaRegistryEventRO = new SchemaRegistryEventFW();
    private final TcpEventFW tcpEventRO = new TcpEventFW();
    private final TlsEventFW tlsEventRO = new TlsEventFW();

    private final EventReader readEvent;
    private final LongFunction<String> supplyNamespace;
    private final LongFunction<String> supplyLocalName;
    private final ToIntFunction<String> lookupLabelId;
    private final PrintStream out;
    private final Int2ObjectHashMap<MessageConsumer> eventHandlers;

    public StdoutEventsStream(
        EventReader readEvent,
        LongFunction<String> supplyNamespace,
        LongFunction<String> supplyLocalName,
        ToIntFunction<String> lookupLabelId,
        PrintStream out)
    {
        this.readEvent = readEvent;
        this.supplyNamespace = supplyNamespace;
        this.supplyLocalName = supplyLocalName;
        this.lookupLabelId = lookupLabelId;
        this.out = out;

        final Int2ObjectHashMap<MessageConsumer> eventHandlers = new Int2ObjectHashMap<>();
        addEventHandler(eventHandlers, "http", this::handleHttpEvent);
        addEventHandler(eventHandlers, "kafka", this::handleKafkaEvent);
        addEventHandler(eventHandlers, "mqtt", this::handleMqttEvent);
        addEventHandler(eventHandlers, "schema-registry", this::handleSchemaRegistryEvent);
        addEventHandler(eventHandlers, "tcp", this::handleTcpEvent);
        addEventHandler(eventHandlers, "tls", this::handleTlsEvent);
        this.eventHandlers = eventHandlers;
    }

    private void addEventHandler(
        Int2ObjectHashMap<MessageConsumer> eventHandlers,
        String type,
        MessageConsumer consumer)
    {
        int labelId = lookupLabelId.applyAsInt(type);
        if (labelId != 0)
        {
            eventHandlers.put(labelId, consumer);
        }
    }

    public int process()
    {
        return readEvent.applyAsInt(this::handleEvent, 1);
    }

    private void handleEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        final MessageConsumer handler = eventHandlers.get(msgTypeId);
        if (handler != null)
        {
            handler.accept(msgTypeId, buffer, index, length);
        }
    }

    private void handleHttpEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        final HttpEventFW event = httpEventRO.wrap(buffer, index, index + length);
        switch (event.kind())
        {
        case AUTHORIZATION:
            HttpAuthorizationEventFW e = event.authorization();
            String namespace = supplyNamespace.apply(e.namespacedId());
            String binding = supplyLocalName.apply(e.namespacedId());
            String level = e.result().get() == Result.SUCCESS ? "INFO" : "WARNING";
            String result = e.result().get() == Result.SUCCESS ? "Succeeded" : "Failed";
            out.printf(HTTP_AUTHORIZATION_FORMAT, level, result, e.timestamp(), e.traceId(), namespace, binding,
                asString(e.identity()));
            break;
        }
    }

    private void handleKafkaEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        final KafkaEventFW event = kafkaEventRO.wrap(buffer, index, index + length);
        switch (event.kind())
        {
        case AUTHORIZATION:
        {
            KafkaAuthorizationEventFW e = event.authorization();
            String namespace = supplyNamespace.apply(e.namespacedId());
            String binding = supplyLocalName.apply(e.namespacedId());
            String level = e.result().get() == Result.SUCCESS ? "INFO" : "WARNING";
            String result = e.result().get() == Result.SUCCESS ? "Succeeded" : "Failed";
            out.printf(KAFKA_AUTHORIZATION_FORMAT, level, result, e.timestamp(), e.traceId(), namespace, binding);
            break;
        }
        case API_VERSION_REJECTED:
        {
            EventFW e = event.apiVersionRejected();
            String namespace = supplyNamespace.apply(e.namespacedId());
            String binding = supplyLocalName.apply(e.namespacedId());
            out.printf(KAFKA_API_VERSION_REJECTED, e.timestamp(), e.traceId(), namespace, binding);
            break;
        }
        }
    }

    private void handleMqttEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        MqttEventFW event = mqttEventRO.wrap(buffer, index, index + length);
        switch (event.kind())
        {
        case AUTHORIZATION:
            MqttAuthorizationEventFW e = event.authorization();
            String namespace = supplyNamespace.apply(e.namespacedId());
            String binding = supplyLocalName.apply(e.namespacedId());
            String level = e.result().get() == Result.SUCCESS ? "INFO" : "WARNING";
            String result = e.result().get() == Result.SUCCESS ? "Succeeded" : "Failed";
            out.printf(MQTT_AUTHORIZATION_FORMAT, level, result, e.timestamp(), e.traceId(), namespace, binding,
                asString(e.identity()));
            break;
        }
    }

    private void handleSchemaRegistryEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        SchemaRegistryEventFW event = schemaRegistryEventRO.wrap(buffer, index, index + length);
        switch (event.kind())
        {
        case REMOTE_ACCESS_REJECTED:
            SchemaRegistryRemoteAccessRejectedEventFW e = event.remoteAccessRejected();
            String namespace = supplyNamespace.apply(e.namespacedId());
            String binding = supplyLocalName.apply(e.namespacedId());
            out.printf(SCHEMA_REGISTRY_REMOTE_ACCESS_REJECTED, e.timestamp(), e.traceId(), namespace, binding, asString(e.url()),
                asString(e.method()), e.status());
            break;
        }
    }

    private void handleTcpEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        final TcpEventFW event = tcpEventRO.wrap(buffer, index, index + length);
        switch (event.kind())
        {
        case REMOTE_ACCESS_FAILED:
            TcpRemoteAccessFailedEventFW e = event.remoteAccessFailed();
            String namespace = supplyNamespace.apply(e.namespacedId());
            String binding = supplyLocalName.apply(e.namespacedId());
            out.printf(TCP_REMOTE_ACCESS_FAILED_FORMAT, e.timestamp(), e.traceId(), namespace, binding, asString(e.address()));
            break;
        }
    }

    private void handleTlsEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        TlsEventFW event = tlsEventRO.wrap(buffer, index, index + length);
        switch (event.kind())
        {
        case TLS_FAILED:
            TlsFailedEventFW e = event.tlsFailed();
            String namespace = supplyNamespace.apply(e.namespacedId());
            String binding = supplyLocalName.apply(e.namespacedId());
            out.printf(TLS_FAILED_FORMAT, e.timestamp(), e.traceId(), namespace, binding, e.error().get().name());
            break;
        }
    }

    private static String asString(
        StringFW stringFW)
    {
        String s = stringFW.asString();
        return s == null ? "" : s;
    }
}
