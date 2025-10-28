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
import java.util.List;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public abstract class AbstractSchemaRegistryOptionsConfig extends OptionsConfig
{
    public final String url;
    public final String context;
    public final Duration maxAge;
    public final List<String> keys;
    public final List<String> trust;
    public final boolean trustcacerts;
    public final String authorization;
    public final String username;
    public final String password;

    protected AbstractSchemaRegistryOptionsConfig(
            String url,
            String context,
            Duration maxAge,
            List<String> keys,
            List<String> trust,
            boolean trustcacerts,
            String authorization,
            String username,
            String password)
    {
        this.url = url;
        this.context = context;
        this.maxAge = maxAge;
        this.keys = keys;
        this.trust = trust;
        this.trustcacerts = trustcacerts;
        this.authorization = authorization;
        this.username = username;
        this.password = password;
    }
}
