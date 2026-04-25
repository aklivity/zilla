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
import java.util.Map;

import jakarta.json.stream.JsonParser;

import io.aklivity.zilla.runtime.common.json.internal.StreamingJsonParser;

public final class StreamingJson
{
    /**
     * Config key whose value is a {@code List<JsonPointer>} identifying the document paths
     * whose values must be readable via {@link JsonParser#getString()} after the corresponding
     * {@code VALUE_STRING} event. The convention {@code -} as an array-index segment is treated
     * as a wildcard matching any index (parser-internal extension to RFC 6901).
     * <p>
     * Values at non-readable paths are scanned and discarded; calling
     * {@link JsonParser#getString()} on such a value throws {@link IllegalStateException}.
     * <p>
     * Absent or empty: every value is readable (legacy behavior).
     */
    public static final String READABLE_PATHS = "io.aklivity.zilla.runtime.common.json.readable.paths";

    private StreamingJson()
    {
    }

    public static JsonParser createParser(
        InputStream in)
    {
        return new StreamingJsonParser(in, Map.of());
    }

    public static JsonParser createParser(
        InputStream in,
        Map<String, ?> config)
    {
        return new StreamingJsonParser(in, config);
    }
}
