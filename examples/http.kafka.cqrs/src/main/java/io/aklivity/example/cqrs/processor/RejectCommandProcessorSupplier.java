/*
 * Copyright 2021-2022 Aklivity. All rights reserved.
 */
package io.aklivity.example.cqrs.processor;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.processor.api.Record;

import io.aklivity.example.cqrs.model.Command;

public class RejectCommandProcessorSupplier implements ProcessorSupplier<String, Command, String, Object>
{
    private final String replyTo;

    public RejectCommandProcessorSupplier(String replyTo)
    {
        this.replyTo = replyTo;
    }

    @Override
    public Processor<String, Command, String, Object> get()
    {
        return new Processor<>()
        {
            ProcessorContext context;

            @Override
            public void init(final ProcessorContext context)
            {
                this.context = context;
            }

            @Override
            public void process(Record<String, Command> record)
            {
                final Headers headers = record.headers();
                final Headers newHeaders = new RecordHeaders();
                headers.forEach(newHeaders::add);
                newHeaders.add(":status", "412".getBytes());
                final Record newRecord = record.withHeaders(newHeaders).withValue("");
                context.forward(newRecord, replyTo);
            }
        };
    }
}
