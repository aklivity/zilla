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

import jakarta.json.stream.JsonParser;

import org.agrona.DirectBuffer;

/**
 * A {@link JsonParser} extended with the streaming-over-buffers surface the standard
 * {@code jakarta.json.stream} contract lacks. This is the {@code *Ex} pattern for going beyond JSON-P:
 * a sub-interface of the standard type that drives a {@link JsonStream} pipeline.
 * <p>
 * Obtain an instance from {@link JsonEx#createParser()} (the implementation is internal). Reuse a
 * single instance per worker thread, calling {@link #wrap(DirectBuffer, int, int)} to borrow each frame's
 * buffer before pumping.
 */
public interface JsonParserEx extends JsonParser
{
    /**
     * Config key whose value is a {@code List<String>} of JSON Pointer (RFC 6901) syntax
     * identifying document paths whose values must be readable via {@link JsonParser#getString()}
     * after the corresponding event. The convention {@code -} as an array-index segment is treated
     * as a wildcard matching any index (parser-internal extension to RFC 6901).
     * <p>
     * Defaults to "every path included" when absent. When specified, only listed paths
     * (minus any matched by {@link #PATH_EXCLUDES}) are readable; values at all other
     * paths are scanned and discarded, and {@code getString()} on those throws.
     */
    String PATH_INCLUDES = "io.aklivity.zilla.runtime.common.json.path.includes";

    /**
     * Config key whose value is a {@code List<String>} of JSON Pointer (RFC 6901) syntax
     * identifying document paths whose values are NOT required to be readable, even if
     * matched by {@link #PATH_INCLUDES}. Excludes have final veto.
     */
    String PATH_EXCLUDES = "io.aklivity.zilla.runtime.common.json.path.excludes";

    /**
     * Config key whose value is an {@code Integer} bounding the number of bytes the parser
     * will scan for a single included value before throwing {@link
     * jakarta.json.stream.JsonParsingException}. Set to the caller's slot capacity to fail
     * fast on values that cannot make progress under reset semantics.
     * <p>
     * Defaults to unbounded (no enforcement) when absent.
     */
    String TOKEN_MAX_BYTES = "io.aklivity.zilla.runtime.common.json.token.max.bytes";

    /**
     * Borrows {@code buffer} as the input for the next pump, starting at {@code offset} for {@code length}
     * bytes. The buffer is read in place for the duration of the pump; resume state carried in the parser
     * bridges values that span frames.
     */
    JsonParserEx wrap(
        DirectBuffer buffer,
        int offset,
        int length);
}
