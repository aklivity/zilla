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
package io.aklivity.zilla.config.engine;

import java.util.Map;

public abstract class Config
{
    private final Map<String, Config> extensions;

    protected Config()
    {
        this(null);
    }

    protected Config(
        Map<String, Config> extensions)
    {
        this.extensions = extensions;
    }

    public final <T extends Config> T ext(
        String name,
        Class<T> type)
    {
        return extensions != null ? type.cast(extensions.get(name)) : null;
    }
}
