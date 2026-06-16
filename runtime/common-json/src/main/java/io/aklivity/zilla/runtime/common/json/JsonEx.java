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
package io.aklivity.zilla.runtime.common.json;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import jakarta.json.stream.JsonParserFactory;

import io.aklivity.zilla.runtime.common.json.internal.JsonGeneratorImpl;
import io.aklivity.zilla.runtime.common.json.internal.JsonParserFactoryImpl;
import io.aklivity.zilla.runtime.common.json.internal.JsonParserImpl;
import io.aklivity.zilla.runtime.common.json.internal.JsonProjectorImpl;
import io.aklivity.zilla.runtime.common.json.internal.JsonSinkImpl;
import io.aklivity.zilla.runtime.common.json.internal.JsonStreamImpl;

/**
 * Entry point for {@code common-json}'s streaming JSON parsing over Agrona buffers.
 * <p>
 * {@link #createParser(InputStream)} returns a standard {@link jakarta.json.stream.JsonParser},
 * so it can be used anywhere a streaming pull parser is expected — including one-shot,
 * complete-buffer cases that do not need the resumable, slot-fragmented chunked streaming this
 * parser also supports. It requires no {@code jakarta.json} provider on the classpath;
 * {@code common-json} ships the implementation.
 */
public final class JsonEx
{
    private JsonEx()
    {
    }

    public static JsonParserEx createParser(
        InputStream in)
    {
        return new JsonParserImpl(in, Map.of());
    }

    /**
     * Returns an empty {@link JsonParserEx} to be fed via {@link JsonParserEx#wrap(
     * org.agrona.DirectBuffer, int, int)} (or {@link JsonPipeline#feed(org.agrona.DirectBuffer,
     * int, int)} when driving a pipeline). Reuse a single instance per worker thread.
     */
    public static JsonParserEx createParser()
    {
        return new JsonParserImpl(Map.of());
    }

    /**
     * Variant of {@link #createParser()} taking parser config; the returned parser starts empty and is
     * fed via {@link JsonParserEx#wrap(org.agrona.DirectBuffer, int, int)}.
     */
    public static JsonParserEx createParser(
        Map<String, ?> config)
    {
        return new JsonParserImpl(config);
    }

    /**
     * Mirrors {@link jakarta.json.Json#createParserFactory(Map)}. Construct once per
     * factory class and reuse for the lifetime of the binding to avoid repeating config
     * resolution on every stream.
     */
    public static JsonParserFactory createParserFactory(
        Map<String, ?> config)
    {
        return new JsonParserFactoryImpl(config);
    }

    /**
     * Returns a buffer-backed {@link JsonGeneratorEx} — a standard {@link
     * jakarta.json.stream.JsonGenerator} extended with the streaming-to-buffer methods. Reuse a
     * single instance per worker thread, calling {@link JsonGeneratorEx#wrap(
     * org.agrona.MutableDirectBuffer, int)} before each value. Requires no {@code jakarta.json}
     * provider on the classpath; {@code common-json} ships the implementation.
     */
    public static JsonGeneratorEx createGenerator()
    {
        return new JsonGeneratorImpl();
    }

    /**
     * Variant of {@link #createGenerator()} taking generator config (e.g.
     * {@link JsonGeneratorEx#GENERATE_ESCAPED}). Reuse a single instance per worker thread, calling
     * {@link JsonGeneratorEx#wrap(org.agrona.MutableDirectBuffer, int, int)} before each value.
     */
    public static JsonGeneratorEx createGenerator(
        Map<String, ?> config)
    {
        return new JsonGeneratorImpl(config);
    }

    /**
     * Returns a terminal {@link JsonSink} that materializes each fed event into the corresponding
     * {@code writeXxx} call on {@code generator} (structured delivery). The supplied generator must
     * already be wrapped over its target buffer; reuse a single instance per worker thread.
     */
    public static JsonSink createSink(
        JsonGeneratorEx generator)
    {
        return new JsonSinkImpl(generator);
    }

    /**
     * Variant of {@link #createSink(JsonGeneratorEx)} taking sink config; the {@link JsonSink#DELIVERY}
     * key selects the {@link JsonSink.Delivery} mode (absent ⇒ {@link JsonSink.Delivery#STRUCTURED}).
     */
    public static JsonSink createSink(
        JsonGeneratorEx generator,
        Map<String, ?> config)
    {
        final Object delivery = config.get(JsonSink.DELIVERY);
        return new JsonSinkImpl(generator,
            delivery instanceof JsonSink.Delivery mode ? mode : JsonSink.Delivery.STRUCTURED);
    }

    /**
     * Begins a push pipeline pumped by {@code parser}: append stages with {@link JsonStream#transform}
     * and terminate with {@link JsonStream#into}. The {@code parser} (from {@link #createParser()} or
     * {@link #createParser(Map)}) supplies the events; stages see a non-advancing {@link JsonSource}
     * view of each.
     */
    public static JsonStream stream(
        JsonParserEx parser)
    {
        return new JsonStreamImpl(parser);
    }

    /**
     * Returns a {@link JsonTransform} that prunes a document to the given retained RFC 6901
     * pointers, forwarding each kept event to the downstream sink supplied at assembly. Add it to a
     * pipeline via {@link JsonStream#transform(JsonTransform)}. Reuse a single instance per worker
     * thread; it resets per top-level value.
     */
    public static JsonTransform projector(
        List<String> pointers)
    {
        return new JsonProjectorImpl(pointers);
    }

    /**
     * Returns a {@link JsonTransform} pruning a document to the paths retained by {@code schema}
     * (see {@link JsonSchema#retainedPaths()}).
     */
    public static JsonTransform projector(
        JsonSchema schema)
    {
        return new JsonProjectorImpl(schema.retainedPaths());
    }
}
