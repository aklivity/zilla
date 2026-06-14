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

/**
 * Single discovery point for the {@code common-json} config keys passed to
 * {@link JsonEx#createParser(java.util.Map)} and {@link JsonEx#createGenerator(java.util.Map)}.
 * The canonical definitions live on the type each key configures — {@link JsonParserEx} and
 * {@link JsonGeneratorEx} — and are aggregated here as aliases. This is the {@code *Ex} counterpart
 * to {@code jakarta.json.JsonConfig}.
 */
public final class JsonConfigEx
{
    public static final String PATH_INCLUDES = JsonParserEx.PATH_INCLUDES;
    public static final String PATH_EXCLUDES = JsonParserEx.PATH_EXCLUDES;
    public static final String TOKEN_MAX_BYTES = JsonParserEx.TOKEN_MAX_BYTES;
    public static final String GENERATE_ESCAPED = JsonGeneratorEx.GENERATE_ESCAPED;

    private JsonConfigEx()
    {
    }
}
