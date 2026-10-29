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
package io.aklivity.zilla.config.binding.a2a;

import java.util.List;

import io.aklivity.zilla.config.engine.Config;

public abstract class A2aSearchIndexConfig extends Config
{
    public final String type;

    protected A2aSearchIndexConfig(
        String type)
    {
        this.type = type;
    }

    public List<Config.Reference> refs()
    {
        return List.of();
    }
}
