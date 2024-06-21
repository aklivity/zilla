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
package io.aklivity.zilla.runtime.model.avro.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Int2ObjectCache;
import org.agrona.io.DirectBufferInputStream;
import org.agrona.io.ExpandableDirectBufferOutputStream;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.model.avro.config.AvroModelConfig;
import io.aklivity.zilla.runtime.model.avro.internal.types.AvroBooleanFW;
import io.aklivity.zilla.runtime.model.avro.internal.types.AvroBytesFW;
import io.aklivity.zilla.runtime.model.avro.internal.types.AvroDoubleFW;
import io.aklivity.zilla.runtime.model.avro.internal.types.AvroFloatFW;
import io.aklivity.zilla.runtime.model.avro.internal.types.AvroIntFW;
import io.aklivity.zilla.runtime.model.avro.internal.types.AvroLongFW;
import io.aklivity.zilla.runtime.model.avro.internal.types.OctetsFW;

public abstract class AvroModelHandler
{
    protected static final String VIEW_JSON = "json";

    private static final InputStream EMPTY_INPUT_STREAM = new ByteArrayInputStream(new byte[0]);
    private static final OutputStream EMPTY_OUTPUT_STREAM = new ByteArrayOutputStream(0);
    private static final int JSON_FIELD_STRUCTURE_LENGTH = "\"\":\"\",".length();
    private static final int JSON_FIELD_UNION_LENGTH = "\"\":{\"DATA_TYPE\":\"\"},".length();
    private static final int COMMA_LENGTH = ",".length();
    private static final int JSON_FIELD_ARRAY_LENGTH = "\"\":[]," .length() + COMMA_LENGTH * 100;
    private static final int JSON_FIELD_MAP_LENGTH = "\"\":{},".length();

    protected final SchemaConfig catalog;
    protected final CatalogHandler handler;
    protected final DecoderFactory decoderFactory;
    protected final EncoderFactory encoderFactory;
    protected final BinaryDecoder decoder;
    protected final BinaryEncoder encoder;
    protected final String subject;
    protected final String view;
    protected final ExpandableDirectBufferOutputStream expandable;
    protected final DirectBufferInputStream in;
    protected final AvroModelEventContext event;
    protected final Map<String, AvroField> extracted;

    private final Int2ObjectCache<Schema> schemas;
    private final Int2ObjectCache<GenericDatumReader<GenericRecord>> readers;
    private final Int2ObjectCache<GenericDatumWriter<GenericRecord>> writers;
    private final Int2ObjectCache<GenericRecord> records;
    private final Int2IntHashMap paddings;
    private final AvroBytesFW bytesRO;
    private final AvroIntFW intRO;
    private final AvroLongFW longRO;
    private final AvroFloatFW floatRO;
    private final AvroDoubleFW doubleRO;

    protected int progress;

    protected AvroModelHandler(
        AvroModelConfig config,
        EngineContext context)
    {
        this.decoderFactory = DecoderFactory.get();
        this.decoder = decoderFactory.binaryDecoder(EMPTY_INPUT_STREAM, null);
        this.encoderFactory = EncoderFactory.get();
        this.encoder = encoderFactory.binaryEncoder(EMPTY_OUTPUT_STREAM, null);
        CatalogedConfig cataloged = config.cataloged.get(0);
        this.handler = context.supplyCatalog(cataloged.id);
        this.catalog = cataloged.schemas.size() != 0 ? cataloged.schemas.get(0) : null;
        this.view = config.view;
        this.subject = catalog != null && catalog.subject != null
                ? catalog.subject
                : config.subject;
        this.schemas = new Int2ObjectCache<>(1, 1024, i -> {});
        this.readers = new Int2ObjectCache<>(1, 1024, i -> {});
        this.writers = new Int2ObjectCache<>(1, 1024, i -> {});
        this.records = new Int2ObjectCache<>(1, 1024, i -> {});
        this.paddings = new Int2IntHashMap(-1);
        this.expandable = new ExpandableDirectBufferOutputStream(new ExpandableDirectByteBuffer());
        this.in = new DirectBufferInputStream();
        this.event = new AvroModelEventContext(context);
        this.extracted = new HashMap<>();
        this.bytesRO = new AvroBytesFW();
        this.intRO = new AvroIntFW();
        this.longRO = new AvroLongFW();
        this.floatRO = new AvroFloatFW();
        this.doubleRO = new AvroDoubleFW();

    }

    protected final boolean validate(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        boolean status = false;
        try
        {
            GenericRecord record = supplyRecord(schemaId);
            in.wrap(buffer, index, length);
            GenericDatumReader<GenericRecord> reader = supplyReader(schemaId);
            Schema schema = supplySchema(schemaId);
            if (reader != null)
            {
                decoderFactory.binaryDecoder(in, decoder);
                reader.read(record, decoder);
                status = true;

            }
            progress = index;
            extractFields(buffer, length, schema);
        }
        catch (IOException | AvroRuntimeException ex)
        {
            event.validationFailure(traceId, bindingId, ex.getMessage());
        }
        return status;
    }

    protected void extractFields(
        DirectBuffer buffer,
        int length,
        Schema schema)
    {
        for (Schema.Field field : schema.getFields())
        {
            extract(field.schema(), buffer, length, extracted.get(field.name()));
        }
    }

    protected final Schema supplySchema(
        int schemaId)
    {
        return schemas.computeIfAbsent(schemaId, this::resolveSchema);
    }

    protected final int supplyPadding(
        int schemaId)
    {
        return paddings.computeIfAbsent(schemaId, id -> calculatePadding(supplySchema(id)));
    }

    protected final GenericDatumReader<GenericRecord> supplyReader(
        int schemaId)
    {
        return readers.computeIfAbsent(schemaId, this::createReader);
    }

    protected final GenericDatumWriter<GenericRecord> supplyWriter(
        int schemaId)
    {
        return writers.computeIfAbsent(schemaId, this::createWriter);
    }

    protected final GenericRecord supplyRecord(
        int schemaId)
    {
        return records.computeIfAbsent(schemaId, this::createRecord);
    }

    private GenericDatumReader<GenericRecord> createReader(
        int schemaId)
    {
        Schema schema = supplySchema(schemaId);
        GenericDatumReader<GenericRecord> reader = null;
        if (schema != null)
        {
            reader = new GenericDatumReader<GenericRecord>(schema);
        }
        return reader;
    }

    private GenericDatumWriter<GenericRecord> createWriter(
        int schemaId)
    {
        Schema schema = supplySchema(schemaId);
        GenericDatumWriter<GenericRecord> writer = null;
        if (schema != null)
        {
            writer = new GenericDatumWriter<GenericRecord>(schema);
        }
        return writer;
    }

    private GenericRecord createRecord(
        int schemaId)
    {
        Schema schema = supplySchema(schemaId);
        GenericRecord record = null;
        if (schema != null)
        {
            record = new GenericData.Record(schema);
        }
        return record;
    }

    private Schema resolveSchema(
        int schemaId)
    {
        Schema schema = null;
        String schemaText = handler.resolve(schemaId);
        if (schemaText != null)
        {
            schema = new Schema.Parser().parse(schemaText);
        }
        return schema;
    }

    private int calculatePadding(
        Schema schema)
    {
        int padding = 0;

        if (schema != null)
        {
            padding = 2;
            if (schema.getType().equals(Schema.Type.RECORD))
            {
                for (Schema.Field field : schema.getFields())
                {
                    switch (field.schema().getType())
                    {
                    case RECORD:
                    {
                        padding += calculatePadding(field.schema());
                        break;
                    }
                    case UNION:
                    {
                        padding += field.name().getBytes().length + JSON_FIELD_UNION_LENGTH;
                        break;
                    }
                    case MAP:
                    {
                        padding += field.name().getBytes().length + JSON_FIELD_MAP_LENGTH +
                            calculatePadding(field.schema().getValueType());
                        break;
                    }
                    case ARRAY:
                    {
                        padding += field.name().getBytes().length + JSON_FIELD_ARRAY_LENGTH +
                            calculatePadding(field.schema().getElementType());
                        break;
                    }
                    default:
                    {
                        padding += field.name().getBytes().length + JSON_FIELD_STRUCTURE_LENGTH;
                        break;
                    }
                    }
                }
            }
        }
        return padding;
    }

    private void extract(
        Schema schema,
        DirectBuffer data,
        int limit,
        AvroField field)
    {
        switch (schema.getType())
        {
        case RECORD:
            extractFields(data, limit, schema);
            break;
        case BYTES:
        case STRING:
            AvroBytesFW bytes = bytesRO.wrap(data, progress, limit);
            OctetsFW value = bytes.value();
            progress = bytes.limit();
            if (field != null)
            {
                OctetsFW octets = field.value;
                octets.wrap(value.buffer(), value.offset(), value.limit());
            }
            break;
        case ENUM:
        case INT:
            AvroIntFW int32 = intRO.wrap(data, progress, limit);
            int intValue = int32.value();
            progress = int32.limit();
            if (field != null)
            {
                MutableDirectBuffer text = field.buffer;
                int length = text.putIntAscii(0, intValue);
                field.value.wrap(text, 0, length);
            }
            break;
        case FLOAT:
            AvroFloatFW avroFloat = floatRO.wrap(data, progress, limit);
            int len = 0;
            DirectBuffer buffer = avroFloat.value().value();
            float floatValue = Float.intBitsToFloat(decodeNumberBytes(len, buffer));
            progress = avroFloat.limit();
            if (field != null)
            {
                MutableDirectBuffer text = field.buffer;
                int length = text.putStringWithoutLengthAscii(0, String.valueOf(floatValue));
                field.value.wrap(text, 0, length);
            }
            break;
        case LONG:
            AvroLongFW avroLong = longRO.wrap(data, progress, limit);
            long longValue = avroLong.value();
            progress = avroLong.limit();
            if (field != null)
            {
                MutableDirectBuffer text = field.buffer;
                int length = text.putLongAscii(0, longValue);
                field.value.wrap(text, 0, length);
            }
            break;
        case DOUBLE:
            AvroDoubleFW avroDouble = doubleRO.wrap(data, progress, limit);
            len = 0;
            buffer = avroDouble.value().value();
            int decoded = (buffer.getByte(len++) & 0xff) |
                ((buffer.getByte(len++) & 0xff) << 8) |
                ((buffer.getByte(len++) & 0xff) << 16) |
                ((buffer.getByte(len++) & 0xff) << 24);

            double doubleValue = Double.longBitsToDouble((((long) decoded) & 0xffffffffL) |
                (((long) decodeNumberBytes(len, buffer)) << 32));
            progress = avroDouble.limit();
            if (field != null)
            {
                MutableDirectBuffer text = field.buffer;
                int length = text.putStringWithoutLengthAscii(0, String.valueOf(doubleValue));
                field.value.wrap(text, 0, length);
            }
            break;
        case BOOLEAN:
            AvroBooleanFW avroBoolean = new AvroBooleanFW().wrap(data, progress, limit);
            value = avroBoolean.value();
            progress = avroBoolean.limit();
            if (field != null)
            {
                field.value.wrap(value.buffer(), value.offset(), value.limit());
            }
            break;
        case FIXED:
            int fixedSize = schema.getFixedSize();
            if (field != null)
            {
                field.value.wrap(data, progress, progress + fixedSize);
            }
            progress += fixedSize;
            break;
        }
    }

    private static int decodeNumberBytes(
        int len,
        DirectBuffer buffer)
    {
        return (buffer.getByte(len++) & 0xff) |
            ((buffer.getByte(len++) & 0xff) << 8) |
            ((buffer.getByte(len++) & 0xff) << 16) |
            ((buffer.getByte(len) & 0xff) << 24);
    }
}
