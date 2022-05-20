/*
 * Copyright 2021-2022 Aklivity. All rights reserved.
 */
package io.aklivity.zilla.example.todo.processor;

import java.util.Arrays;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

import io.aklivity.zilla.example.todo.model.Command;

public class CommandProcessorSupplier implements ProcessorSupplier<String, Command, String, Command>
{
    private final String etagStoreName;
    private final String successName;
    private final String failureName;

    public CommandProcessorSupplier(String etagStoreName, String successName, String failureName)
    {
        this.etagStoreName = etagStoreName;
        this.successName = successName;
        this.failureName = failureName;
    }

    @Override
    public Processor<String, Command, String, Command> get()
    {
        return new CommandProcessor();
    }

    class CommandProcessor implements Processor<String, Command, String, Command>
    {
        private ProcessorContext context;
        private KeyValueStore<String, String> etagStore;

        @Override
        public void init(final ProcessorContext context)
        {
            this.context = context;
            this.etagStore = (KeyValueStore) context.getStateStore(etagStoreName);
        }

        @Override
        public void process(Record<String, Command> record)
        {
            final String key = record.key();
            final Headers headers = record.headers();
            final Header correlationId = headers.lastHeader("zilla:correlation-id");
            final Header idempotencyKey = headers.lastHeader("idempotency-key");
            final Header path = headers.lastHeader(":path");
            final Header ifMatch = headers.lastHeader("if-match");
            final String etag = etagStore.get(key);

            final Headers newHeaders = new RecordHeaders();
            newHeaders.add(correlationId);
            newHeaders.add(idempotencyKey);
            newHeaders.add(path);

            final Record<String, Command> command = record.withHeaders(newHeaders);
            final String childName = ifMatch == null || etag != null && Arrays.equals(ifMatch.value(), etag.getBytes())
                    ? successName : failureName;
            context.forward(command, childName);
        }
    }
}
