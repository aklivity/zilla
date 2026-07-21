/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.json.internal.json;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;

import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonMergePatch;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonPatch;
import jakarta.json.JsonPatchBuilder;
import jakarta.json.JsonPointer;
import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonGeneratorFactory;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;

import io.aklivity.zilla.runtime.common.json.internal.JsonParserFactoryImpl;
import io.aklivity.zilla.runtime.common.json.internal.JsonParserImpl;

public final class JsonProviderImpl extends JsonProvider
{
    private final Map<String, ?> config;

    public JsonProviderImpl()
    {
        this(Map.of());
    }

    public JsonProviderImpl(
        Map<String, ?> config)
    {
        this.config = config == null ? Map.of() : Map.copyOf(config);
    }

    @Override
    public JsonParser createParser(
        Reader reader)
    {
        return new JsonParserImpl(ReaderInputStream.from(reader), config);
    }

    @Override
    public JsonParser createParser(
        InputStream in)
    {
        return new JsonParserImpl(marked(in), config);
    }

    @Override
    public JsonParserFactory createParserFactory(
        Map<String, ?> config)
    {
        return new JsonParserFactoryImpl(config == null ? Map.of() : Map.copyOf(config));
    }

    @Override
    public JsonGenerator createGenerator(
        Writer writer)
    {
        return new JsonTextGeneratorImpl(writer);
    }

    @Override
    public JsonGenerator createGenerator(
        OutputStream out)
    {
        return new JsonTextGeneratorImpl(out);
    }

    @Override
    public JsonGeneratorFactory createGeneratorFactory(
        Map<String, ?> config)
    {
        return new JsonGeneratorFactoryImpl(config);
    }

    @Override
    public JsonReader createReader(
        Reader reader)
    {
        return new JsonReaderImpl(createParser(reader));
    }

    @Override
    public JsonReader createReader(
        InputStream in)
    {
        return new JsonReaderImpl(createParser(in));
    }

    @Override
    public JsonWriter createWriter(
        Writer writer)
    {
        return new JsonWriterImpl(createGenerator(writer));
    }

    @Override
    public JsonWriter createWriter(
        OutputStream out)
    {
        return new JsonWriterImpl(createGenerator(out));
    }

    @Override
    public JsonWriterFactory createWriterFactory(
        Map<String, ?> config)
    {
        return new JsonWriterFactoryImpl(config);
    }

    @Override
    public JsonReaderFactory createReaderFactory(
        Map<String, ?> config)
    {
        return new JsonReaderFactoryImpl(config);
    }

    @Override
    public JsonObjectBuilder createObjectBuilder()
    {
        return JsonValues.objectBuilder();
    }

    @Override
    public JsonObjectBuilder createObjectBuilder(
        JsonObject object)
    {
        return JsonValues.objectBuilder(object);
    }

    @Override
    public JsonObjectBuilder createObjectBuilder(
        Map<String, ?> map)
    {
        return JsonValues.objectBuilder(map);
    }

    @Override
    public JsonArrayBuilder createArrayBuilder()
    {
        return JsonValues.arrayBuilder();
    }

    @Override
    public JsonArrayBuilder createArrayBuilder(
        JsonArray array)
    {
        return JsonValues.arrayBuilder(array);
    }

    @Override
    public JsonArrayBuilder createArrayBuilder(
        Collection<?> collection)
    {
        return JsonValues.arrayBuilder(collection);
    }

    @Override
    public JsonBuilderFactory createBuilderFactory(
        Map<String, ?> config)
    {
        return JsonValues.builderFactory(config);
    }

    @Override
    public JsonPointer createPointer(
        String jsonPointer)
    {
        return JsonValues.pointer(jsonPointer);
    }

    @Override
    public JsonPatchBuilder createPatchBuilder()
    {
        return new JsonValues.PatchBuilder();
    }

    @Override
    public JsonPatchBuilder createPatchBuilder(
        JsonArray array)
    {
        return JsonValues.patchBuilder(array);
    }

    @Override
    public JsonPatch createPatch(
        JsonArray array)
    {
        return JsonValues.patch(array);
    }

    @Override
    public JsonPatch createDiff(
        JsonStructure source,
        JsonStructure target)
    {
        return JsonValues.diff(source, target);
    }

    @Override
    public JsonMergePatch createMergePatch(
        JsonValue value)
    {
        return JsonValues.mergePatch(value);
    }

    @Override
    public JsonMergePatch createMergeDiff(
        JsonValue source,
        JsonValue target)
    {
        return JsonValues.mergeDiff(source, target);
    }

    @Override
    public JsonString createValue(
        String value)
    {
        return JsonValues.string(value);
    }

    @Override
    public JsonNumber createValue(
        int value)
    {
        return JsonValues.number(value);
    }

    @Override
    public JsonNumber createValue(
        long value)
    {
        return JsonValues.number(value);
    }

    @Override
    public JsonNumber createValue(
        double value)
    {
        return JsonValues.number(value);
    }

    @Override
    public JsonNumber createValue(
        BigDecimal value)
    {
        return JsonValues.number(value);
    }

    @Override
    public JsonNumber createValue(
        BigInteger value)
    {
        return JsonValues.number(value);
    }

    @Override
    public JsonNumber createValue(
        Number value)
    {
        return JsonValues.number(value);
    }

    private static InputStream marked(
        InputStream in)
    {
        return in.markSupported() ? in : new BufferedInputStream(in);
    }
}
