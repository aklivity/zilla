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
package io.aklivity.zilla.config.binding.a2a.internal;

import jakarta.json.JsonException;
import jakarta.json.JsonObject;

import io.aklivity.zilla.config.binding.a2a.A2aKeywordSearchIndexConfig;
import io.aklivity.zilla.config.binding.a2a.A2aSearchIndexConfig;
import io.aklivity.zilla.config.engine.ConfigAdapter;

public final class A2aSearchIndexConfigAdapter extends ConfigAdapter<A2aSearchIndexConfig, JsonObject>
{
    private static final String TYPE_NAME = "type";

    private final A2aKeywordSearchIndexConfigAdapter keyword = new A2aKeywordSearchIndexConfigAdapter();

    @Override
    public JsonObject adaptToJson(
        A2aSearchIndexConfig index)
    {
        return keyword.adaptToJson(index);
    }

    @Override
    public A2aSearchIndexConfig adaptFromJson(
        JsonObject object)
    {
        String type = object.getString(TYPE_NAME, null);

        if (!A2aKeywordSearchIndexConfig.NAME.equals(type))
        {
            throw new JsonException(String.format("Unrecognized agent search index type: %s", type));
        }

        return keyword.adaptFromJson(object);
    }
}
