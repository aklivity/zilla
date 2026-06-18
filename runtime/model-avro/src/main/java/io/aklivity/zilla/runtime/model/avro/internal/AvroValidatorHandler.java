/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.model.avro.internal;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectCache;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.common.avro.Avro;
import io.aklivity.zilla.runtime.common.avro.AvroGenerator;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ValidatorHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.avro.config.AvroModelConfig;

public class AvroValidatorHandler extends AvroModelHandler implements ValidatorHandler
{
    private static final String ENCODED = "encoded";
    private static final String INVALID = "Invalid Avro encoding";
    private static final int OUTPUT_CAPACITY = 8192;

    private final Int2ObjectCache<Validator> validators;
    private final ExpandableDirectByteBuffer carry;
    private final ExpandableDirectByteBuffer assembly;
    private final ExpandableDirectByteBuffer encoded;

    private Validator active;
    private int carryLength;
    private int encodedProgress;

    public AvroValidatorHandler(
        AvroModelConfiguration config,
        AvroModelConfig options,
        EngineContext context)
    {
        super(config, options, context);
        this.validators = new Int2ObjectCache<>(1, 16, v -> {});
        this.carry = new ExpandableDirectByteBuffer();
        this.assembly = new ExpandableDirectByteBuffer();
        this.encoded = new ExpandableDirectByteBuffer();
    }

    @Override
    public boolean validate(
        long traceId,
        long bindingId,
        int flags,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        return catalog != null && ENCODED.equals(catalog.strategy)
            ? validateEncoded(traceId, bindingId, flags, data, index, length, next)
            : validateStreaming(traceId, bindingId, flags, data, index, length, next);
    }

    private boolean validateStreaming(
        long traceId,
        long bindingId,
        int flags,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        boolean valid;

        if ((flags & FLAGS_INIT) != 0x00)
        {
            int schemaId = catalog != null && catalog.id > 0
                ? catalog.id
                : handler.resolve(subject, catalog.version);
            active = supplyValidator(schemaId);
            if (active != null)
            {
                active.pipeline.reset();
            }
            carryLength = 0;
        }

        boolean last = (flags & FLAGS_FIN) != 0x00;

        if (active == null)
        {
            valid = !last;
            if (last)
            {
                event.validationFailure(traceId, bindingId, INVALID);
            }
        }
        else
        {
            DirectBuffer buffer;
            int offset;
            int limit;
            if (carryLength == 0)
            {
                buffer = data;
                offset = index;
                limit = index + length;
            }
            else
            {
                assembly.putBytes(0, carry, 0, carryLength);
                assembly.putBytes(carryLength, data, index, length);
                buffer = assembly;
                offset = 0;
                limit = carryLength + length;
            }

            Status status = feed(buffer, offset, limit, last, next);

            switch (status)
            {
            case COMPLETED:
                valid = true;
                carryLength = 0;
                break;
            case STARVED:
                int remaining = active.pipeline.remaining();
                carry.putBytes(0, buffer, limit - remaining, remaining);
                carryLength = remaining;
                valid = true;
                break;
            default:
                valid = false;
                carryLength = 0;
                event.validationFailure(traceId, bindingId, INVALID);
                break;
            }
        }

        return valid;
    }

    private boolean validateEncoded(
        long traceId,
        long bindingId,
        int flags,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        boolean status = true;

        try
        {
            if ((flags & FLAGS_INIT) != 0x00)
            {
                this.encodedProgress = 0;
            }

            encoded.putBytes(encodedProgress, data, index, length);
            encodedProgress += length;

            if ((flags & FLAGS_FIN) != 0x00)
            {
                status = handler.validate(traceId, bindingId, encoded, 0, encodedProgress, next, this::validatePayload);
            }
        }
        catch (Exception ex)
        {
            status = false;
            event.validationFailure(traceId, bindingId, ex.getMessage());
        }

        return status;
    }

    private Status feed(
        DirectBuffer buffer,
        int offset,
        int limit,
        boolean last,
        ValueConsumer next)
    {
        Status status;
        do
        {
            active.generator.wrap(active.output, 0, OUTPUT_CAPACITY);
            status = active.pipeline.feed(buffer, offset, limit, last);
            int produced = active.generator.length();
            if (produced > 0 && status != Status.REJECTED)
            {
                next.accept(active.output, 0, produced);
            }
        }
        while (status == Status.SUSPENDED);
        return status;
    }

    private boolean validatePayload(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        return validate(traceId, bindingId, schemaId, data, index, length);
    }

    private Validator supplyValidator(
        int schemaId)
    {
        AvroSchema schema = supplySchema(schemaId);
        return schema != null ? validators.computeIfAbsent(schemaId, id -> new Validator(schema)) : null;
    }

    private static final class Validator
    {
        private final AvroPipeline pipeline;
        private final AvroGenerator generator;
        private final MutableDirectBuffer output;

        private Validator(
            AvroSchema schema)
        {
            this.output = new UnsafeBuffer(new byte[OUTPUT_CAPACITY]);
            this.generator = Avro.generator(schema, output, 0);
            this.pipeline = Avro.stream(Avro.parser(schema)).into(AvroSink.of(generator));
        }
    }
}
