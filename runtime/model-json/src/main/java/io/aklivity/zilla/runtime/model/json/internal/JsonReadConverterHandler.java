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

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;
import io.aklivity.zilla.runtime.model.json.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.model.json.internal.types.String32FW;

public class JsonReadConverterHandler extends JsonModelHandler implements ConverterHandler
{
    private static final String PATH = "\\$\\.([A-Za-z_][A-Za-z0-9_]*)";
    private static final Pattern PATH_PATTERN = Pattern.compile(PATH);

    private final String32FW.Builder stringRW = new String32FW.Builder()
            .wrap(new UnsafeBuffer(new byte[256]), 0, 256);

    private final Matcher matcher;

    public JsonReadConverterHandler(
        JsonModelConfig config,
        EngineContext context)
    {
        super(config, context);
        this.matcher = PATH_PATTERN.matcher("");
    }

    @Override
    public int padding(
        DirectBuffer data,
        int index,
        int length)
    {
        return handler.decodePadding(data, index, length);
    }

    @Override
    public void extract(
        String path)
    {
        if (matcher.reset(path).matches())
        {
            extracted.put(matcher.group(1), new OctetsFW());
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
            OctetsFW extractedField = extracted.get(fieldName);
            if (extractedField == null)
            {
                return "";
            }
            DirectBuffer value = extractedField.value();
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

        if (schemaId != NO_SCHEMA_ID &&
            validate(traceId, bindingId, schemaId, data, index, length))
        {
            next.accept(data, index, length);
            valLength = length;
        }

        return valLength;
    }
}
