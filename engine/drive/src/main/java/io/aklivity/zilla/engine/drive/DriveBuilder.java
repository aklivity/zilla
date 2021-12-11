/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.engine.drive;

import static java.util.Objects.requireNonNull;

import java.net.URL;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.agrona.ErrorHandler;

import io.aklivity.zilla.engine.drive.cog.Cog;
import io.aklivity.zilla.engine.drive.cog.CogFactory;
import io.aklivity.zilla.engine.drive.cog.Configuration;

public class DriveBuilder
{
    private Configuration config;
    private ErrorHandler errorHandler;

    private int threads = 1;
    private URL configURL;
    private Collection<DriveAffinity> affinities;

    DriveBuilder()
    {
        this.affinities = new LinkedHashSet<>();
    }

    public DriveBuilder config(
        Configuration config)
    {
        this.config = requireNonNull(config);
        return this;
    }

    public DriveBuilder configURL(
        URL configURL)
    {
        this.configURL = configURL;
        return this;
    }

    public DriveBuilder threads(
        int threads)
    {
        this.threads = threads;
        return this;
    }

    public DriveBuilder affinity(
        String namespace,
        String binding,
        long mask)
    {
        affinities.add(new DriveAffinity(namespace, binding, mask));
        return this;
    }

    public DriveBuilder errorHandler(
        ErrorHandler errorHandler)
    {
        this.errorHandler = requireNonNull(errorHandler);
        return this;
    }

    public Drive build()
    {
        final DriveConfiguration config = new DriveConfiguration(this.config != null ? this.config : new Configuration());

        final Set<Cog> cogs = new LinkedHashSet<>();
        final CogFactory factory = CogFactory.instantiate();
        for (String name : factory.names())
        {
            Cog cog = factory.create(name, config);
            cogs.add(cog);
        }

        final ErrorHandler errorHandler = requireNonNull(this.errorHandler, "errorHandler");

        return new Drive(config, cogs, errorHandler, configURL, threads, affinities);
    }
}
