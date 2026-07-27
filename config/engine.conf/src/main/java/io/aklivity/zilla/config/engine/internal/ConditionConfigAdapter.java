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
package io.aklivity.zilla.config.engine.internal;

import jakarta.json.JsonObject;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.engine.BindingInfo;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.EngineInfo;

public class ConditionConfigAdapter implements JsonbAdapter<ConditionConfig, JsonObject>
{
    private final EngineInfo info;

    private JsonbAdapter<ConditionConfig, JsonObject> delegate;

    public ConditionConfigAdapter()
    {
        this(null);
    }

    public ConditionConfigAdapter(
        EngineInfo info)
    {
        this.info = info;
    }

    public void adaptType(
        String type)
    {
        BindingInfo binding = info != null && type != null ? info.binding(type) : null;
        delegate = binding != null ? binding.condition() : null;
    }

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition) throws Exception
    {
        return delegate != null ? delegate.adaptToJson(condition) : null;
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object) throws Exception
    {
        return delegate != null ? delegate.adaptFromJson(object) : null;
    }
}
