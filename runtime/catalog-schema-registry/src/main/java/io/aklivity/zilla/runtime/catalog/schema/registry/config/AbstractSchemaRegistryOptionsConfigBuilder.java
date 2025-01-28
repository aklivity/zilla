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
    private List<String> keys;
    private List<String> trust;
    private Boolean trustcacerts;
    private String authorization;

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

    public B keys(
        List<String> keys)
    {
        this.keys = keys;
        return thisType().cast(this);
    }

    public B trust(
        List<String> trust)
    {
        this.trust = trust;
        return thisType().cast(this);
    }

    public B trustcacerts(
        boolean trustcacerts)
    {
        this.trustcacerts = trustcacerts;
        return thisType().cast(this);
    }

    public B authorization(
        String authorization)
    {
        this.authorization = authorization;
        return thisType().cast(this);
    }

    @Override
    public T build()
    {
        Duration maxAge = (this.maxAge != null) ? this.maxAge : MAX_AGE_DEFAULT;
        final boolean trustcacerts = this.trustcacerts == null ? this.trust == null : this.trustcacerts;
        return mapper.apply(newOptionsConfig(url, context, maxAge, keys, trust, trustcacerts, authorization));
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
        List<String> keys,
        List<String> trust,
        boolean trustcacerts,
        String authorization);
}
