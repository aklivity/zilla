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
     * Config key whose value is a {@code List<JsonPointer>} identifying document paths
     * whose values must be readable via {@link JsonParser#getString()} after the
     * corresponding event. The convention {@code -} as an array-index segment is treated
     * as a wildcard matching any index (parser-internal extension to RFC 6901).
     * <p>
     * Defaults to "every path included" when absent. When specified, only listed paths
     * (minus any matched by {@link #PATHS_EXCLUDED}) are readable; values at all other
     * paths are scanned and discarded, and {@code getString()} on those throws.
     */
    public static final String PATHS_INCLUDED = "io.aklivity.zilla.runtime.common.json.paths.included";

    /**
     * Config key whose value is a {@code List<JsonPointer>} identifying document paths
     * whose values are NOT required to be readable, even if matched by
     * {@link #PATHS_INCLUDED}. Excludes have final veto.
     */
    public static final String PATHS_EXCLUDED = "io.aklivity.zilla.runtime.common.json.paths.excluded";

    /**
     * Config key whose value is an {@code Integer} bounding the number of bytes the parser
     * will scan for a single included value before throwing {@link
     * jakarta.json.stream.JsonParsingException}. Set to the caller's slot capacity to fail
     * fast on values that cannot make progress under reset semantics.
     * <p>
     * Defaults to unbounded (no enforcement) when absent.
     */
    public static final String MAX_TOKEN_BYTES = "io.aklivity.zilla.runtime.common.json.max.token.bytes";

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
