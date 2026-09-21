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
package io.aklivity.zilla.config.binding.llm.internal;

import jakarta.json.JsonObject;

import io.aklivity.zilla.config.binding.llm.LlmSignInfo;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class LlmTestSignInfo implements LlmSignInfo
{
    @Override
    public String type()
    {
        return "test-options";
    }

    @Override
    public ConfigAdapter<OptionsConfig, JsonObject> options()
    {
        return new LlmTestSignOptionsConfigAdapter();
    }
}
