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

import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;

import io.aklivity.zilla.runtime.common.json.internal.StreamingJsonGenerator;
import io.aklivity.zilla.runtime.common.json.internal.StreamingJsonParser;
import io.aklivity.zilla.runtime.common.json.internal.StreamingJsonParserFactory;
import io.aklivity.zilla.runtime.common.json.internal.StreamingJsonProjector;

/**
 * Entry point for {@code common-json}'s streaming JSON parsing over Agrona buffers.
 * <p>
 * {@link #createParser(InputStream)} returns a standard {@link jakarta.json.stream.JsonParser},
 * so it can be used anywhere a streaming pull parser is expected — including one-shot,
 * complete-buffer cases that do not need the resumable, slot-fragmented chunked streaming this
 * parser also supports. It requires no {@code jakarta.json} provider on the classpath;
 * {@code common-json} ships the implementation.
 */
public final class StreamingJson
{
    /**
     * Config key whose value is a {@code List<String>} of JSON Pointer (RFC 6901) syntax
     * identifying document paths whose values must be readable via {@link
     * JsonParser#getString()} after the corresponding event. The convention {@code -} as an
     * array-index segment is treated as a wildcard matching any index (parser-internal
     * extension to RFC 6901).
     * <p>
     * Defaults to "every path included" when absent. When specified, only listed paths
     * (minus any matched by {@link #PATH_EXCLUDES}) are readable; values at all other
     * paths are scanned and discarded, and {@code getString()} on those throws.
     */
    public static final String PATH_INCLUDES = "io.aklivity.zilla.runtime.common.json.path.includes";

    /**
     * Config key whose value is a {@code List<String>} of JSON Pointer (RFC 6901) syntax
     * identifying document paths whose values are NOT required to be readable, even if
     * matched by {@link #PATH_INCLUDES}. Excludes have final veto.
     */
    public static final String PATH_EXCLUDES = "io.aklivity.zilla.runtime.common.json.path.excludes";

    /**
     * Config key whose value is an {@code Integer} bounding the number of bytes the parser
     * will scan for a single included value before throwing {@link
     * jakarta.json.stream.JsonParsingException}. Set to the caller's slot capacity to fail
     * fast on values that cannot make progress under reset semantics.
     * <p>
     * Defaults to unbounded (no enforcement) when absent.
     */
    public static final String TOKEN_MAX_BYTES = "io.aklivity.zilla.runtime.common.json.token.max.bytes";

    private StreamingJson()
    {
    }

    public static JsonParser createParser(
        InputStream in)
    {
        return new StreamingJsonParser(in, Map.of());
    }

    /**
     * Mirrors {@link jakarta.json.Json#createParserFactory(Map)}. Construct once per
     * factory class and reuse for the lifetime of the binding to avoid repeating config
     * resolution on every stream.
     */
    public static JsonParserFactory createParserFactory(
        Map<String, ?> config)
    {
        return new StreamingJsonParserFactory(config);
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
        return new StreamingJsonGenerator();
    }

    /**
     * Returns a {@link JsonProjector} that prunes a document to the given retained RFC 6901
     * pointers, forwarding each kept event to {@code sink} as a stage in a processing chain (e.g.
     * {@code parser → projector → generator-sink}, where {@code sink} is {@link
     * JsonEventConsumer#of(JsonGeneratorEx)}). Reuse a single instance per worker thread; it
     * resets per top-level value.
     */
    public static JsonProjector createProjector(
        List<String> pointers,
        JsonEventConsumer sink)
    {
        return new StreamingJsonProjector(pointers, sink);
    }
}
