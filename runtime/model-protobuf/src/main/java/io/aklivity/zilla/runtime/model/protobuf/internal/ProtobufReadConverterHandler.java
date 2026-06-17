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
package io.aklivity.zilla.runtime.model.protobuf.internal;

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.json.ProtobufJson;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.protobuf.config.ProtobufModelConfig;
import io.aklivity.zilla.runtime.model.protobuf.internal.types.OctetsFW;

public class ProtobufReadConverterHandler extends ProtobufModelHandler implements ConverterHandler
{
    private static final int TAG_TYPE_BITS = 3;
    private static final String PATH = "^\\$\\.([A-Za-z_][A-Za-z0-9_]*)$";
    private static final Pattern PATH_PATTERN = Pattern.compile(PATH);
    private static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer();

    private final Matcher matcher;
    private final Map<String, ProtobufFieldValue> extracted;
    private final Map<String, JsonState> jsonStates;
    private final Map<String, ProtobufPipeline> validators;

    private int progress;

    public ProtobufReadConverterHandler(
        ProtobufModelConfig config,
        EngineContext context)
    {
        super(config, context);
        this.matcher = PATH_PATTERN.matcher("");
        this.extracted = new HashMap<>();
        this.jsonStates = new HashMap<>();
        this.validators = new HashMap<>();
    }

    @Override
    public int padding(
        DirectBuffer data,
        int index,
        int length)
    {
        int padding = handler.decodePadding(data, index, length);
        if (VIEW_JSON.equals(view))
        {
            int schemaId = handler.resolve(data, index, length);

            if (schemaId == NO_SCHEMA_ID)
            {
                schemaId = catalog.id != NO_SCHEMA_ID
                    ? catalog.id
                    : handler.resolve(subject, catalog.version);
            }
            padding += supplyJsonFormatPadding(schemaId);
        }
        return padding;
    }

    @Override
    public void extract(
        String path)
    {
        if (matcher.reset(path).matches())
        {
            extracted.put(matcher.group(1), new ProtobufFieldValue());
        }
    }

    @Override
    public int convert(
        long traceId,
        long bindingId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        for (ProtobufFieldValue field : extracted.values())
        {
            field.value.wrap(EMPTY_BUFFER, 0, 0);
        }
        return handler.decode(traceId, bindingId, data, index, length, next, this::decodePayload);
    }

    @Override
    public int extractedLength(
        String path)
    {
        OctetsFW value = null;
        if (matcher.reset(path).matches())
        {
            value = extracted.get(matcher.group(1)).value;
        }
        return value != null ? value.sizeof() : 0;
    }

    @Override
    public void extracted(
        String path,
        FieldVisitor visitor)
    {
        if (matcher.reset(path).matches())
        {
            OctetsFW value = extracted.get(matcher.group(1)).value;
            if (value != null && value.sizeof() != 0)
            {
                visitor.visit(value.buffer(), value.offset(), value.sizeof());
            }
        }
    }

    private int decodePayload(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        if (schemaId == NO_SCHEMA_ID)
        {
            if (catalog.id != NO_SCHEMA_ID)
            {
                schemaId = catalog.id;
            }
            else
            {
                schemaId = handler.resolve(subject, catalog.version);
            }
        }

        int progress = decodeIndexes(data, index, length);

        return validate(traceId, bindingId, schemaId, data, index + progress, length - progress, next);
    }

    private int validate(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        int valLength = -1;
        ProtobufSchema schema = supplySchema(schemaId);
        if (schema != null)
        {
            ProtobufMessage message = schema.messageByIndexes(decodedPath());
            if (message != null)
            {
                String messageName = message.name();
                ProtobufPipeline validator = validators.computeIfAbsent(messageName, name -> schema.validatorPipeline(name));
                validator.reset();
                if (validator.feed(data, index, length) == ProtobufPipeline.Status.COMPLETED)
                {
                    progress = index;
                    extractFields(data, index + length, message);

                    if (VIEW_JSON.equals(view))
                    {
                        valLength = toJson(schemaId, schema, messageName, data, index, length, next);
                    }
                    else
                    {
                        next.accept(data, index, length);
                        valLength = length;
                    }
                }
                else
                {
                    String reason = validator.reason();
                    event.validationFailure(traceId, bindingId, reason != null ? reason : "Invalid Protobuf event");
                }
            }
        }
        return valLength;
    }

    private int toJson(
        int schemaId,
        ProtobufSchema schema,
        String messageName,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        int valLength = -1;
        JsonState state = jsonStates.computeIfAbsent(messageName, name -> new JsonState(schema, name));
        out.wrap(out.buffer());
        state.generator.wrap(out.buffer(), 0, out.buffer().capacity());
        state.pipeline.reset();
        if (state.pipeline.feed(data, index, length) == ProtobufPipeline.Status.COMPLETED)
        {
            state.generator.flush();
            valLength = state.generator.length();
            next.accept(out.buffer(), 0, valLength);
        }
        return valLength;
    }

    private void extractFields(
        DirectBuffer data,
        int length,
        ProtobufMessage message)
    {
        while (progress < length)
        {
            int lastTag = decodeVarint32(data, length) >>> TAG_TYPE_BITS;
            ProtobufField field = message.field(lastTag);
            if (field != null)
            {
                extract(field, data, length, extracted.get(field.name()));
            }
            else
            {
                progress = length;
            }
        }
    }

    private void extract(
        ProtobufField descriptor,
        DirectBuffer data,
        int limit,
        ProtobufFieldValue field)
    {
        switch (descriptor.type())
        {
        case MESSAGE:
            extractFields(data, limit, descriptor.message());
            break;
        case BYTES:
        case STRING:
            int length = decodeVarint32(data, limit);
            if (field != null)
            {
                field.value.wrap(data, progress, progress + length);
            }
            progress += length;
            break;
        case ENUM:
        case UINT32:
        case INT32:
            int intValue = decodeVarint32(data, limit);
            if (field != null)
            {
                MutableDirectBuffer text = field.buffer;
                length = text.putIntAscii(0, intValue);
                field.value.wrap(text, 0, length);
            }
            break;
        case UINT64:
        case INT64:
            long longValue = decodeVarint64(data, limit);
            if (field != null)
            {
                MutableDirectBuffer text = field.buffer;
                length = text.putLongAscii(0, longValue);
                field.value.wrap(text, 0, length);
            }
            break;
        case FLOAT:
            float floatValue = Float.intBitsToFloat(decodeLittleEndian32(data));
            if (field != null)
            {
                MutableDirectBuffer text = field.buffer;
                length = text.putStringWithoutLengthAscii(0, String.valueOf(floatValue));
                field.value.wrap(text, 0, length);
            }
            break;
        case DOUBLE:
            double doubleValue = Double.longBitsToDouble(decodeLittleEndian64(data));
            if (field != null)
            {
                MutableDirectBuffer text = field.buffer;
                length = text.putStringWithoutLengthAscii(0, String.valueOf(doubleValue));
                field.value.wrap(text, 0, length);
            }
            break;
        case SFIXED32:
        case FIXED32:
            int fixed32Value = decodeLittleEndian32(data);
            if (field != null)
            {
                MutableDirectBuffer text = field.buffer;
                length = text.putIntAscii(0, fixed32Value);
                field.value.wrap(text, 0, length);
            }
            break;
        case SFIXED64:
        case FIXED64:
            long fixed64Value = decodeLittleEndian64(data);
            if (field != null)
            {
                MutableDirectBuffer text = field.buffer;
                length = text.putLongAscii(0, fixed64Value);
                field.value.wrap(text, 0, length);
            }
            break;
        case SINT32:
            int sintValue = decodeVarint32(data, limit);
            if (field != null)
            {
                MutableDirectBuffer text = field.buffer;
                length = text.putIntAscii(0, (sintValue >>> 1) ^ -(sintValue & 1));
                field.value.wrap(text, 0, length);
            }
            break;
        case SINT64:
            long sint64Value = decodeVarint64(data, limit);
            if (field != null)
            {
                MutableDirectBuffer text = field.buffer;
                length = text.putLongAscii(0, (sint64Value >>> 1) ^ -(sint64Value & 1));
                field.value.wrap(text, 0, length);
            }
            break;
        default:
            break;
        }
    }

    public int decodeVarint32(
        DirectBuffer data,
        int limit)
    {
        int value;
        boolean slow = false;
        fastpath:
        {
            int tmpProgress = progress;
            if ((value = data.getByte(tmpProgress++)) >= 0)
            {
            }
            else if (limit - tmpProgress < 9)
            {
                slow = true;
                break fastpath;
            }
            else if ((value ^= data.getByte(tmpProgress++) << 7) < 0)
            {
                value ^= ~0 << 7;
            }
            else if ((value ^= data.getByte(tmpProgress++) << 14) >= 0)
            {
                value ^= (~0 << 7) ^ (~0 << 14);
            }
            else if ((value ^= data.getByte(tmpProgress++) << 21) < 0)
            {
                value ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
            }
            else
            {
                int y = data.getByte(tmpProgress++);
                value ^= y << 28;
                value ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
                if (y < 0 && data.getByte(tmpProgress++) < 0 &&
                    data.getByte(tmpProgress++) < 0 && data.getByte(tmpProgress++) < 0 &&
                    data.getByte(tmpProgress++) < 0 && data.getByte(tmpProgress++) < 0)
                {
                    slow = true;
                    break fastpath;
                }
            }
            progress = tmpProgress;
        }

        if (slow)
        {
            value = (int) decodeVarint64SlowPath(data);
        }

        return value;
    }

    long decodeVarint64(
        DirectBuffer data,
        int limit)
    {
        long value;
        boolean slow = false;
        fastpath:
        {
            int tmpProgress = progress;
            if ((value = data.getByte(tmpProgress++)) >= 0)
            {
            }
            else if (limit - tmpProgress < 9)
            {
                slow = true;
                break fastpath;
            }
            else if ((value ^= data.getByte(tmpProgress++) << 7) < 0)
            {
                value = value ^ (~0 << 7);
            }
            else if ((value ^= data.getByte(tmpProgress++) << 14) >= 0)
            {
                value = value ^ ((~0 << 7) ^ (~0 << 14));
            }
            else if ((value ^= data.getByte(tmpProgress++) << 21) < 0)
            {
                value = value ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
            }
            else if ((value = value ^ data.getByte(tmpProgress++) << 28) >= 0L)
            {
                value ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
            }
            else if ((value ^= data.getByte(tmpProgress++) << 35) < 0L)
            {
                value ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
            }
            else if ((value ^= data.getByte(tmpProgress++) << 42) >= 0L)
            {
                value ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
            }
            else if ((value ^= data.getByte(tmpProgress++) << 49) < 0L)
            {
                value ^=
                    (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42) ^ (~0L << 49);
            }
            else
            {
                value ^= data.getByte(tmpProgress++) << 56;
                value ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35)
                    ^ (~0L << 42) ^ (~0L << 49) ^ (~0L << 56);
                if (value < 0L)
                {
                    if (data.getByte(tmpProgress++) < 0L)
                    {
                        slow = true;
                        break fastpath;
                    }
                }
            }
            progress = tmpProgress;
        }

        if (slow)
        {
            value = decodeVarint64SlowPath(data);
        }

        return value;
    }

    private long decodeVarint64SlowPath(
        DirectBuffer data)
    {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7)
        {
            final byte b = data.getByte(progress++);
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0)
            {
                break;
            }
        }
        return result;
    }

    private int decodeLittleEndian32(
        DirectBuffer data)
    {
        return (data.getByte(progress++) & 0xff) |
            ((data.getByte(progress++) & 0xff) << 8) |
            ((data.getByte(progress++) & 0xff) << 16) |
            ((data.getByte(progress++) & 0xff) << 24);
    }

    long decodeLittleEndian64(
        DirectBuffer data)
    {
        return (data.getByte(progress++) & 0xffL) |
            ((data.getByte(progress++) & 0xffL) << 8) |
            ((data.getByte(progress++) & 0xffL) << 16) |
            ((data.getByte(progress++) & 0xffL) << 24) |
            ((data.getByte(progress++) & 0xffL) << 32) |
            ((data.getByte(progress++) & 0xffL) << 40) |
            ((data.getByte(progress++) & 0xffL) << 48) |
            ((data.getByte(progress++) & 0xffL) << 56);
    }

    private final class JsonState
    {
        private final ProtobufGenerator generator;
        private final ProtobufPipeline pipeline;

        private JsonState(
            ProtobufSchema schema,
            String messageName)
        {
            Map<String, Object> config = new HashMap<>();
            config.put(ProtobufJson.FIELD_NAMES, ProtobufJson.FieldNames.PROTO);
            config.put(ProtobufJson.INCLUDE_DEFAULTS, Boolean.TRUE);
            JsonGeneratorEx jsonGenerator = JsonEx.createGenerator();
            this.generator = ProtobufJson.generator(jsonGenerator, schema, messageName, config);
            this.pipeline = Protobuf.stream(Protobuf.parser(schema, messageName))
                .into(ProtobufSink.of(generator, schema, messageName));
        }
    }
}
