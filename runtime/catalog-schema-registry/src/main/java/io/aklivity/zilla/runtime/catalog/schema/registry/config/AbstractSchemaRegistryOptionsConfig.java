/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.catalog.schema.registry.config;

import java.time.Duration;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public abstract class AbstractSchemaRegistryOptionsConfig extends OptionsConfig
{
    public final String url;
    public final String context;
    public final Duration maxAge;
    public final String key;
    public final String secret;

    protected AbstractSchemaRegistryOptionsConfig(
        String url,
        String context,
        Duration maxAge,
        String key,
        String secret)
    {
        this.url = url;
        this.context = context;
        this.maxAge = maxAge;
        this.key = key;
        this.secret = secret;
    }
}
