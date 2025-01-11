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
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public abstract class AbstractSchemaRegistryOptionsConfigBuilder<T, B extends AbstractSchemaRegistryOptionsConfigBuilder<T, B>>
    extends ConfigBuilder<T, B>
{
    private static final Duration MAX_AGE_DEFAULT = Duration.ofSeconds(300);

    private final Function<OptionsConfig, T> mapper;

    private String url;
    private String context;
    private Duration maxAge;
    private String key;
    private String secret;

    public B url(
        String url)
    {
        this.url = url;
        return thisType().cast(this);
    }

    public B context(
        String context)
    {
        this.context = context;
        return thisType().cast(this);
    }

    public B maxAge(
        Duration maxAge)
    {
        this.maxAge = maxAge;
        return thisType().cast(this);
    }

    public B key(
        String key)
    {
        this.key = key;
        return thisType().cast(this);
    }

    public B secret(
        String secret)
    {
        this.secret = secret;
        return thisType().cast(this);
    }

    @Override
    public T build()
    {
        Duration maxAge = (this.maxAge != null) ? this.maxAge : MAX_AGE_DEFAULT;
        return mapper.apply(newOptionsConfig(url, context, maxAge, key, secret));
    }

    protected AbstractSchemaRegistryOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    protected abstract AbstractSchemaRegistryOptionsConfig newOptionsConfig(
        String url,
        String context,
        Duration maxAge,
        String key,
        String secret);
}
