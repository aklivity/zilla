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
package io.aklivity.zilla.config.guard.x509;

import static java.util.Collections.emptyMap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class X509MatchConfigBuilder<T> extends ConfigBuilder<T, X509MatchConfigBuilder<T>>
{
    private final Function<X509MatchConfig, T> mapper;

    private Map<String, String> fields;

    X509MatchConfigBuilder(
        Function<X509MatchConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<X509MatchConfigBuilder<T>> thisType()
    {
        return (Class<X509MatchConfigBuilder<T>>) getClass();
    }

    public X509MatchConfigBuilder<T> field(
        String name,
        String pattern)
    {
        if (fields == null)
        {
            fields = new LinkedHashMap<>();
        }
        fields.put(name, pattern);
        return this;
    }

    public X509MatchConfigBuilder<T> fields(
        Map<String, String> fields)
    {
        this.fields = fields;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new X509MatchConfig(fields != null ? fields : emptyMap()));
    }
}
