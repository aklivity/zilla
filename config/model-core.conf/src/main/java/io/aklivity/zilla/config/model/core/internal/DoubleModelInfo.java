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
package io.aklivity.zilla.config.model.core.internal;

import java.net.URL;

import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.ModelInfo;

public final class DoubleModelInfo implements ModelInfo
{
    public static final String TYPE = "double";

    @Override
    public String type()
    {
        return TYPE;
    }

    @Override
    public URL schema()
    {
        return getClass().getResource("schema/double.schema.patch.json");
    }

    @Override
    public JsonbAdapter<ModelConfig, JsonValue> adapter()
    {
        return new DoubleModelConfigAdapter();
    }
}
