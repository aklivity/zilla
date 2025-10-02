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

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.CanonicalJsonEncoder;
import org.apache.avro.io.JsonEncoder;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.avro.config.AvroModelConfig;
import io.aklivity.zilla.runtime.model.avro.internal.types.String32FW;


public class AvroReadConverterHandler extends AvroModelHandler implements ConverterHandler
{
    private static final String PATH = "\\$\\.([A-Za-z_][A-Za-z0-9_]*)";
    private static final Pattern PATH_PATTERN = Pattern.compile(PATH);
    private static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer();

    private final String32FW.Builder stringRW = new String32FW.Builder()
            .wrap(new UnsafeBuffer(new byte[256]), 0, 256);

    private final Matcher matcher;

    public AvroReadConverterHandler(
        AvroModelConfiguration config,
        AvroModelConfig options,
        EngineContext context)
    {
        super(config, options, context);
        this.matcher = PATH_PATTERN.matcher("");
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
                if (catalog.id != NO_SCHEMA_ID)
                {
                    schemaId = catalog.id;
                }
                else
                {
                    schemaId = handler.resolve(subject, catalog.version);
                }
            }
            padding += supplyPadding(schemaId);
        }
        return padding;
    }

    @Override
    public void extract(
        String path)
    {
        matcher.reset(path);
        while (matcher.find())
        {
            extracted.put(matcher.group(1), new AvroField());
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
        for (AvroField field: extracted.values())
        {
            field.value.wrap(EMPTY_BUFFER, 0, 0);
        }
        return handler.decode(traceId, bindingId, data, index, length, next, this::decodePayload);
    }

    @Override
    public int extractedLength(
        String path)
    {
        return findAndReplaceExtracted(path).length();
    }

    @Override
    public void extracted(
        String path,
        FieldVisitor visitor)
    {
        DirectBuffer value = findAndReplaceExtracted(path).value();
        visitor.visit(value, 0, value.capacity());
    }

    private String32FW findAndReplaceExtracted(String path)
    {
        matcher.reset(path);
        String newValue = matcher.replaceAll(r ->
        {
            String fieldName = r.group(1);
            AvroField extractedField = extracted.get(fieldName);
            if (extractedField == null)
            {
                return "";
            }
            DirectBuffer value = extractedField.value.value();
            return stringRW.set(value, 0, value.capacity()).build().asString();
        });
        return stringRW.set(newValue, StandardCharsets.UTF_8).build();
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
        int valLength = -1;

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

        if (VIEW_JSON.equals(view))
        {
            deserializeRecord(traceId, bindingId, schemaId, data, index, length);
            int recordLength = expandable.position();
            if (recordLength > 0)
            {
                next.accept(expandable.buffer(), 0, recordLength);
                valLength = recordLength;
            }
        }
        else if (validate(traceId, bindingId, schemaId, data, index, length))
        {
            next.accept(data, index, length);
            valLength = length;
        }
        return valLength;
    }

    private void deserializeRecord(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        try
        {
            GenericDatumReader<GenericRecord> reader = supplyReader(schemaId);
            GenericDatumWriter<GenericRecord> writer = supplyWriter(schemaId);
            if (reader != null)
            {
                GenericRecord record = supplyRecord(schemaId);
                in.wrap(buffer, index, length);
                expandable.wrap(expandable.buffer());
                record = reader.read(record, decoderFactory.binaryDecoder(in, decoder));
                Schema schema = record.getSchema();
                JsonEncoder out = new CanonicalJsonEncoder(schema, expandable);
                writer.write(record, out);
                out.flush();

                progress = index;
                extractFields(buffer, index + length, schema);
            }
        }
        catch (IOException | AvroRuntimeException ex)
        {
            event.validationFailure(traceId, bindingId, ex.getMessage());
        }
    }
}
