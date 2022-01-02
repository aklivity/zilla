/*
 * Copyright 2021-2022 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine;

import static java.util.Objects.requireNonNull;

import java.net.URL;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.agrona.ErrorHandler;

import io.aklivity.zilla.runtime.engine.cog.Cog;
import io.aklivity.zilla.runtime.engine.cog.CogFactory;
import io.aklivity.zilla.runtime.engine.cog.Configuration;

public class EngineBuilder
{
    private Configuration config;
    private ErrorHandler errorHandler;

    private URL configURL;
    private Collection<EngineAffinity> affinities;

    EngineBuilder()
    {
        this.affinities = new LinkedHashSet<>();
    }

    public EngineBuilder config(
        Configuration config)
    {
        this.config = requireNonNull(config);
        return this;
    }

    public EngineBuilder configURL(
        URL configURL)
    {
        this.configURL = configURL;
        return this;
    }

    public EngineBuilder affinity(
        String namespace,
        String binding,
        long mask)
    {
        affinities.add(new EngineAffinity(namespace, binding, mask));
        return this;
    }

    public EngineBuilder errorHandler(
        ErrorHandler errorHandler)
    {
        this.errorHandler = requireNonNull(errorHandler);
        return this;
    }

    public Engine build()
    {
        final EngineConfiguration config = new EngineConfiguration(this.config != null ? this.config : new Configuration());

        final Set<Cog> cogs = new LinkedHashSet<>();
        final CogFactory factory = CogFactory.instantiate();
        for (String name : factory.names())
        {
            Cog cog = factory.create(name, config);
            cogs.add(cog);
        }

        final ErrorHandler errorHandler = requireNonNull(this.errorHandler, "errorHandler");

        return new Engine(config, cogs, errorHandler, configURL, affinities);
    }
}
