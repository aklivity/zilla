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
package io.aklivity.zilla.runtime.model.json.internal;

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectCache;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.common.json.JsonDiagnostic;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonReporter;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ValidatorHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public class JsonValidatorHandler extends JsonModelHandler implements ValidatorHandler
{
    private static final String ENCODED = "encoded";
    private static final int OUTPUT_CAPACITY = 8192;

    private final Int2ObjectCache<Validator> validators;
    private final ExpandableDirectByteBuffer carry;
    private final ExpandableDirectByteBuffer assembly;

    private Validator active;
    private int carryLength;
    private int encodedSchemaId;
    private int encodedIndex;
    private int encodedLength;
    private String diagnostic;

    public JsonValidatorHandler(
        JsonModelConfig config,
        EngineContext context)
    {
        super(config, context);
        this.validators = new Int2ObjectCache<>(1, 16, v -> {});
        this.carry = new ExpandableDirectByteBuffer();
        this.assembly = new ExpandableDirectByteBuffer();
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
        boolean valid = catalog != null && ENCODED.equals(catalog.strategy)
            ? validateEncoded(traceId, bindingId, flags, data, index, length, next)
            : validateStreaming(traceId, bindingId, flags, data, index, length, next);
        return valid;
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
                event.validationFailure(traceId, bindingId, JsonModel.NAME);
            }
        }
        else
        {
            valid = stream(traceId, bindingId, last, data, index, length, next);
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
        boolean valid;

        int payloadIndex = index;
        int payloadLength = length;
        if ((flags & FLAGS_INIT) != 0x00)
        {
            // the schema framing prefix appears only on the first fragment; defer to the catalog's decode to
            // skip it in a catalog-specific way and hand back any embedded schema id plus the post-prefix
            // payload, which then streams through the pipeline like any other fragment — no whole-payload
            // buffering. Resolve the schema id the same way the read converter does when the catalog embeds none.
            encodedSchemaId = NO_SCHEMA_ID;
            encodedIndex = index;
            encodedLength = length;
            handler.decode(traceId, bindingId, data, index, length, next, this::decodeEncoded);
            int schemaId = encodedSchemaId != NO_SCHEMA_ID
                ? encodedSchemaId
                : catalog.id != NO_SCHEMA_ID
                    ? catalog.id
                    : handler.resolve(subject, catalog.version);
            active = supplyValidator(schemaId);
            if (active != null)
            {
                active.pipeline.reset();
            }
            carryLength = 0;
            payloadIndex = encodedIndex;
            payloadLength = encodedLength;
        }

        boolean last = (flags & FLAGS_FIN) != 0x00;

        if (active == null)
        {
            valid = !last;
            if (last)
            {
                event.validationFailure(traceId, bindingId, JsonModel.NAME);
            }
        }
        else
        {
            valid = stream(traceId, bindingId, last, data, payloadIndex, payloadLength, next);
        }

        return valid;
    }

    // CatalogHandler.Decoder callback: captures any embedded schema id and the post-prefix payload region
    // the catalog unwrapped, so the encoded fragment can stream rather than buffer whole.
    private int decodeEncoded(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        encodedSchemaId = schemaId;
        encodedIndex = index;
        encodedLength = length;
        return length;
    }

    private boolean stream(
        long traceId,
        long bindingId,
        boolean last,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        boolean valid;

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
            event.validationFailure(traceId, bindingId, diagnostic != null ? diagnostic : JsonModel.NAME);
            break;
        }

        return valid;
    }

    private Status feed(
        DirectBuffer buffer,
        int offset,
        int limit,
        boolean last,
        ValueConsumer next)
    {
        diagnostic = null;
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

    private void onRejected(
        JsonDiagnostic diagnostic)
    {
        this.diagnostic = diagnostic.message();
    }

    private Validator supplyValidator(
        int schemaId)
    {
        JsonSchema schema = supplySchema(schemaId);
        return schema != null ? validators.computeIfAbsent(schemaId, id -> new Validator(schema, this::onRejected)) : null;
    }

    private static final class Validator
    {
        private final JsonPipeline pipeline;
        private final JsonGeneratorEx generator;
        private final MutableDirectBuffer output;

        private Validator(
            JsonSchema schema,
            JsonReporter reporter)
        {
            this.output = new UnsafeBuffer(new byte[OUTPUT_CAPACITY]);
            this.generator = JsonEx.createGenerator();
            this.pipeline = JsonEx.stream(JsonEx.createParser())
                .transform(schema.validator())
                .reporting(reporter)
                .into(JsonEx.createSink(generator));
        }
    }
}
