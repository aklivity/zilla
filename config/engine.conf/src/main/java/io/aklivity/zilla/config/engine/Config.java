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

import java.util.List;
import java.util.Map;

public abstract class Config
{
    protected Config()
    {
    }

    public abstract static class Extensible extends Config
    {
        private final Map<String, Config> extensions;
        private final List<NamedConfig> refs;

        protected Extensible()
        {
            this(null, null);
        }

        protected Extensible(
            Map<String, Config> extensions)
        {
            this(extensions, null);
        }

        protected Extensible(
            Map<String, Config> extensions,
            List<NamedConfig> refs)
        {
            this.extensions = extensions;
            this.refs = refs != null ? refs : List.of();
        }

        public final <T extends Config> T ext(
            String name,
            Class<T> type)
        {
            return extensions != null ? type.cast(extensions.get(name)) : null;
        }

        /**
         * Named configs (vaults, guards, ...) contributed by this config or any of its own extensions,
         * each carrying only a name until the engine resolves it to an id once, generically, regardless of
         * which concrete kind of named config it is.
         */
        public final List<NamedConfig> refs()
        {
            return refs;
        }
    }
}
