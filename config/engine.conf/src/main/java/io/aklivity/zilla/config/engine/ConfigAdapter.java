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

import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

public abstract class ConfigAdapter<T extends Config, A> implements JsonbAdapter<T, A>
{
    public abstract static class Extensible<T extends Config.Extensible, A> extends ConfigAdapter<T, A>
    {
        private final List<ConfigExtAdapter<T>> extensions;

        protected Extensible(
            List<ConfigExtAdapter<T>> extensions)
        {
            this.extensions = extensions;
        }

        protected final void adaptExtensionsToJson(
            T config,
            JsonObjectBuilder builder)
        {
            extensions.forEach(extension -> extension.adaptToJson(config, builder));
        }

        protected final <B extends ConfigBuilder.Extensible<?, B>> B adaptExtensionsFromJson(
            JsonObject object,
            B builder)
        {
            for (ConfigExtAdapter<T> extension : extensions)
            {
                builder = extension.adaptFromJson(object, builder);
            }
            return builder;
        }
    }
}
