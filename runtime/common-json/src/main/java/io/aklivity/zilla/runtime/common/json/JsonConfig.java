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
package io.aklivity.zilla.runtime.common.json;

public final class JsonConfig
{
    /**
     * Reader config key whose {@link Boolean} value, when {@code true}, makes a {@link jakarta.json.JsonReader}
     * reject an object containing the same name more than once, at any nesting depth, with a
     * {@link jakarta.json.JsonException}. Absent or {@code false} ⇒ the last value for a repeated name wins.
     */
    public static final String FEATURE_UNIQUE_KEYS = "io.aklivity.zilla.json.feature.unique.keys";

    private JsonConfig()
    {
    }
}
