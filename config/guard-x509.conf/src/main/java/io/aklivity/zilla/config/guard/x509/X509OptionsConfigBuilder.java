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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public class X509OptionsConfigBuilder<T> extends ConfigBuilder<T, X509OptionsConfigBuilder<T>>
{
    public static final String IDENTITY_DEFAULT = "subject.dn";

    private final Function<OptionsConfig, T> mapper;

    private String identity;
    private Map<String, String> attributes;
    private Map<String, List<X509MatchConfig>> roles;

    X509OptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<X509OptionsConfigBuilder<T>> thisType()
    {
        return (Class<X509OptionsConfigBuilder<T>>) getClass();
    }

    public X509OptionsConfigBuilder<T> identity(
        String identity)
    {
        this.identity = identity;
        return this;
    }

    public X509OptionsConfigBuilder<T> attribute(
        String name,
        String field)
    {
        if (attributes == null)
        {
            attributes = new LinkedHashMap<>();
        }
        attributes.put(name, field);
        return this;
    }

    public X509OptionsConfigBuilder<T> attributes(
        Map<String, String> attributes)
    {
        this.attributes = attributes;
        return this;
    }

    public X509MatchConfigBuilder<X509OptionsConfigBuilder<T>> match(
        String role)
    {
        return X509MatchConfig.builder(match -> match(role, match));
    }

    public X509OptionsConfigBuilder<T> match(
        String role,
        X509MatchConfig match)
    {
        if (roles == null)
        {
            roles = new LinkedHashMap<>();
        }
        roles.computeIfAbsent(role, name -> new LinkedList<>()).add(match);
        return this;
    }

    public X509OptionsConfigBuilder<T> roles(
        Map<String, List<X509MatchConfig>> roles)
    {
        this.roles = roles;
        return this;
    }

    @Override
    public T build()
    {
        String identity = this.identity != null ? this.identity : IDENTITY_DEFAULT;

        return mapper.apply(new X509OptionsConfig(
            identity,
            attributes != null ? attributes : emptyMap(),
            roles != null ? roles : emptyMap()));
    }
}
