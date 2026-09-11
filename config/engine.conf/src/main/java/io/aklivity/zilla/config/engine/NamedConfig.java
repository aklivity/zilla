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
 * carrying only the name at config-load time. The engine resolves {@code name} to {@code id}/{@code qname}
 * once, by the same generic walk regardless of which concrete kind of named config this is.
 *
 * @see Config#refs()
 */
public abstract class NamedConfig extends Config.Extensible implements Config.Reference
{
    public transient long id;
    public transient String qname;

    public final String name;

    protected NamedConfig(
        String name)
    {
        this(name, null);
    }

    protected NamedConfig(
        String name,
        Map<String, Config> extensions)
    {
        super(extensions);
        this.name = requireNonNull(name);
    }

    @Override
    public long id()
    {
        return id;
    }

    @Override
    public String qname()
    {
        return qname;
    }

    @Override
    public void visit(
        Config.Resolver resolver)
    {
        resolver.resolve(this);
    }

    /**
     * A named config that also carries its own named extensions/refs (a vault, a store, an embedding, ...).
     * Independent of {@link NamedConfig} (Java single inheritance won't let one extend the other while both
     * also extend their respective {@link Config}/{@link Config.Extensible} base), but both implement
     * {@link Config.Reference} so either kind can appear in the same {@link Config#refs()} list.
     */
    public abstract static class Extensible extends Config.Extensible implements Config.Reference
    {
        public transient long id;
        public transient String qname;

        public final String name;

        protected Extensible(
            String name)
        {
            this(name, null);
        }

        protected Extensible(
            String name,
            Map<String, Config> extensions)
        {
            super(extensions);
            this.name = requireNonNull(name);
        }

        @Override
        public long id()
        {
            return id;
        }

        @Override
        public String qname()
        {
            return qname;
        }

        @Override
        public void visit(
            Config.Resolver resolver)
        {
            resolver.resolve(this);
        }
    }
}
