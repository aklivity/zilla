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
package io.aklivity.zilla.runtime.common.yaml.internal;

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

public final class YamlProvider extends JsonProvider
{
    private JsonProvider delegate;

    @Override
    public JsonParser createParser(
        Reader reader)
    {
        return new YamlParser(reader);
    }

    @Override
    public JsonParser createParser(
        InputStream in)
    {
        return new YamlParser(in);
    }

    @Override
    public JsonParserFactory createParserFactory(
        Map<String, ?> config)
    {
        return new YamlParserFactory(config);
    }

    @Override
    public JsonGenerator createGenerator(
        Writer writer)
    {
        return new YamlGenerator(writer);
    }

    @Override
    public JsonGenerator createGenerator(
        OutputStream out)
    {
        return new YamlGenerator(out);
    }

    @Override
    public JsonGeneratorFactory createGeneratorFactory(
        Map<String, ?> config)
    {
        return new YamlGeneratorFactory(config);
    }

    @Override
    public JsonReader createReader(
        Reader reader)
    {
        return new YamlReader(createParser(reader));
    }

    @Override
    public JsonReader createReader(
        InputStream in)
    {
        return new YamlReader(createParser(in));
    }

    @Override
    public JsonWriter createWriter(
        Writer writer)
    {
        return new YamlWriter(createGenerator(writer));
    }

    @Override
    public JsonWriter createWriter(
        OutputStream out)
    {
        return new YamlWriter(createGenerator(out));
    }

    @Override
    public JsonWriterFactory createWriterFactory(
        Map<String, ?> config)
    {
        return new YamlWriterFactory(config);
    }

    @Override
    public JsonReaderFactory createReaderFactory(
        Map<String, ?> config)
    {
        return new YamlReaderFactory(config);
    }

    @Override
    public JsonObjectBuilder createObjectBuilder()
    {
        return delegate().createObjectBuilder();
    }

    @Override
    public JsonObjectBuilder createObjectBuilder(
        JsonObject object)
    {
        return delegate().createObjectBuilder(object);
    }

    @Override
    public JsonObjectBuilder createObjectBuilder(
        Map<String, ?> map)
    {
        return delegate().createObjectBuilder(map);
    }

    @Override
    public JsonArrayBuilder createArrayBuilder()
    {
        return delegate().createArrayBuilder();
    }

    @Override
    public JsonArrayBuilder createArrayBuilder(
        JsonArray array)
    {
        return delegate().createArrayBuilder(array);
    }

    @Override
    public JsonArrayBuilder createArrayBuilder(
        Collection<?> collection)
    {
        return delegate().createArrayBuilder(collection);
    }

    @Override
    public JsonBuilderFactory createBuilderFactory(
        Map<String, ?> config)
    {
        return delegate().createBuilderFactory(config);
    }

    @Override
    public JsonPointer createPointer(
        String jsonPointer)
    {
        return delegate().createPointer(jsonPointer);
    }

    @Override
    public JsonPatchBuilder createPatchBuilder()
    {
        return delegate().createPatchBuilder();
    }

    @Override
    public JsonPatchBuilder createPatchBuilder(
        JsonArray array)
    {
        return delegate().createPatchBuilder(array);
    }

    @Override
    public JsonPatch createPatch(
        JsonArray array)
    {
        return delegate().createPatch(array);
    }

    @Override
    public JsonPatch createDiff(
        JsonStructure source,
        JsonStructure target)
    {
        return delegate().createDiff(source, target);
    }

    @Override
    public JsonMergePatch createMergePatch(
        JsonValue value)
    {
        return delegate().createMergePatch(value);
    }

    @Override
    public JsonMergePatch createMergeDiff(
        JsonValue source,
        JsonValue target)
    {
        return delegate().createMergeDiff(source, target);
    }

    @Override
    public JsonString createValue(
        String value)
    {
        return delegate().createValue(value);
    }

    @Override
    public JsonNumber createValue(
        int value)
    {
        return delegate().createValue(value);
    }

    @Override
    public JsonNumber createValue(
        long value)
    {
        return delegate().createValue(value);
    }

    @Override
    public JsonNumber createValue(
        double value)
    {
        return delegate().createValue(value);
    }

    @Override
    public JsonNumber createValue(
        BigDecimal value)
    {
        return delegate().createValue(value);
    }

    @Override
    public JsonNumber createValue(
        BigInteger value)
    {
        return delegate().createValue(value);
    }

    @Override
    public JsonNumber createValue(
        Number value)
    {
        return delegate().createValue(value);
    }

    private JsonProvider delegate()
    {
        if (delegate == null)
        {
            delegate = JsonProvider.provider();
        }
        return delegate;
    }
}
