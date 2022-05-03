/*
 * Copyright 2021-2022 Aklivity. All rights reserved.
 */
package io.aklivity.example.cqrs.processor;

import java.util.UUID;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

import io.aklivity.example.cqrs.model.Command;
import io.aklivity.example.cqrs.model.CreateTaskCommand;
import io.aklivity.example.cqrs.model.DeleteTaskCommand;
import io.aklivity.example.cqrs.model.Task;
import io.aklivity.example.cqrs.model.UpdateTaskCommand;

public class AcceptCommandProcessorSupplier implements ProcessorSupplier<String, Command, String, Object>
{
    private final String etagStoreName;
    private final String snapshot;
    private final String replyTo;

    private KeyValueStore<String, byte[]> etagStore;

    public AcceptCommandProcessorSupplier(String etagStoreName, String snapshot, String replyTo)
    {
        this.etagStoreName = etagStoreName;
        this.snapshot = snapshot;
        this.replyTo = replyTo;
    }

    @Override
    public Processor<String, Command, String, Object> get()
    {
        return new AcceptCommandProcessor();
    }

    class AcceptCommandProcessor implements Processor<String, Command, String, Object>
    {
        ProcessorContext context;

        @Override
        public void init(final ProcessorContext context)
        {
            this.context = context;
            etagStore = (KeyValueStore) context.getStateStore(etagStoreName);
        }

        @Override
        public void process(Record<String, Command> newCommand)
        {
            final String key = newCommand.key();
            final Command command = newCommand.value();
            final Headers headers = newCommand.headers();
            final Header idempotencyKey = headers.lastHeader("idempotency-key");
            final Header correlationId = headers.lastHeader("zilla:correlation-id");
            final Headers newResponseHeaders = new RecordHeaders();
            newResponseHeaders.add(correlationId);
            final Headers newSnapshotHeaders = new RecordHeaders();
            final byte[] etag = UUID.randomUUID().toString().getBytes();
            final Header contentType = new RecordHeader("content-type", "application/json".getBytes());

            if (command instanceof CreateTaskCommand)
            {
                newSnapshotHeaders.add("etag", etag);
                newSnapshotHeaders.add(contentType);
                final Record newSnapshot = newCommand
                        .withHeaders(newSnapshotHeaders)
                        .withValue(Task.builder()
                                .description(((CreateTaskCommand) command).getDescription())
                                .build());
                context.forward(newSnapshot, snapshot);
                etagStore.putIfAbsent(key, etag);

                final Header path = headers.lastHeader(":path");
                newResponseHeaders.add(":status", "201".getBytes());
                newResponseHeaders.add("content-length", "0".getBytes());
                newResponseHeaders.add("location", String.format("%s/%s", new String(path.value()),
                        new String(idempotencyKey.value())).getBytes());
                final Record reply = newCommand.withHeaders(newResponseHeaders).withValue("");
                context.forward(reply, replyTo);
            }
            else if (command instanceof UpdateTaskCommand)
            {
                newSnapshotHeaders.add("etag", etag);
                newSnapshotHeaders.add(contentType);
                final Record newSnapshot = newCommand
                        .withHeaders(newSnapshotHeaders)
                        .withValue(Task.builder()
                                .description(((UpdateTaskCommand) command).getDescription())
                                .build());
                context.forward(newSnapshot, snapshot);
                etagStore.put(key, etag);

                newResponseHeaders.add(":status", "200".getBytes());
                newResponseHeaders.add("content-length", "0".getBytes());
                final Record reply = newCommand.withHeaders(newResponseHeaders).withValue("");
                context.forward(reply, replyTo);
            }
            else if (command instanceof DeleteTaskCommand)
            {
                final Record newSnapshot = newCommand.withHeaders(newResponseHeaders).withValue(null);
                context.forward(newSnapshot, snapshot);

                newResponseHeaders.add(":status", "204".getBytes());
                newResponseHeaders.add("content-length", "0".getBytes());
                final Record reply = newCommand.withHeaders(newResponseHeaders).withValue("");
                context.forward(reply, replyTo);
            }
            else
            {
                newResponseHeaders.add(":status", "400".getBytes());
                final Record reply = newCommand.withHeaders(newResponseHeaders).withValue("Unsupported command");
                context.forward(reply, replyTo);
            }
        }
    }
}
