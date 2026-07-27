/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.http.internal.config;

import java.util.LinkedHashMap;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.http.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;

public final class HttpOverridesResolver
{
    public final Map<String8FW, String16FW> overrides;

    public HttpOverridesResolver(
        Map<String, String> overrides)
    {
        this.overrides = asOverrides(overrides);
    }

    private static Map<String8FW, String16FW> asOverrides(
        Map<String, String> overrides)
    {
        Map<String8FW, String16FW> resolved = null;
        if (overrides != null)
        {
            resolved = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : overrides.entrySet())
            {
                resolved.put(new String8FW(entry.getKey()), new String16FW(entry.getValue()));
            }
        }
        return resolved;
    }
}
