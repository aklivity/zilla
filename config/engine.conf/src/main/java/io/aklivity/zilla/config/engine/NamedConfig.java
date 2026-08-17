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

import static java.util.Objects.requireNonNull;

import java.util.Map;

/**
 * A config entry that names something resolved elsewhere in the same namespace (a vault, a guard, ...),
 * carrying only the name at config-load time. The engine resolves {@code name} to {@code id} once, by
 * the same generic walk regardless of which concrete kind of named config this is.
 *
 * @see Config.Extensible#refs()
 */
public abstract class NamedConfig extends Config.Extensible
{
    public transient long id;

    public final String name;

    protected NamedConfig(
        String name,
        Map<String, Config> extensions)
    {
        super(extensions);
        this.name = requireNonNull(name);
    }
}
