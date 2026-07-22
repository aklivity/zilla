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
package io.aklivity.zilla.config.binding.sse;

import java.net.URL;

import jakarta.json.JsonObject;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.binding.sse.internal.SseConditionConfigAdapter;
import io.aklivity.zilla.config.binding.sse.internal.SseOptionsConfigAdapter;
import io.aklivity.zilla.config.engine.BindingInfo;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class SseBindingInfo implements BindingInfo
{
    public static final String TYPE = "sse";

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public URL schema()
    {
        return getClass().getResource("schema/sse.schema.patch.json");
    }

    @Override
    public JsonbAdapter<OptionsConfig, JsonObject> options()
    {
        return new SseOptionsConfigAdapter();
    }

    @Override
    public JsonbAdapter<ConditionConfig, JsonObject> condition()
    {
        return new SseConditionConfigAdapter();
    }
}
