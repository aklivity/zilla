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
package io.aklivity.zilla.manager.internal.commands.install.cache;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;

import java.lang.module.ModuleDescriptor;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class ZpmModule
{
    public static final String DELEGATE_NAME = "io.aklivity.zilla.manager.delegate";
    public static final ZpmArtifactId DELEGATE_ID = null;

    public final String name;
    public final boolean automatic;
    public final Set<Path> paths;
    public final ZpmArtifactId id;
    public final Set<ZpmArtifactId> depends;

    public boolean delegating;

    public ZpmModule()
    {
        this.name = DELEGATE_NAME;
        this.automatic = false;
        this.paths = new LinkedHashSet<>();
        this.id = DELEGATE_ID;
        this.depends = emptySet();
        this.delegating = false;
    }

    public ZpmModule(
        ZpmArtifact artifact)
    {
        this.name = null;
        this.automatic = false;
        this.paths = new LinkedHashSet<>(singleton(artifact.path));
        this.id = artifact.id;
        this.depends = artifact.depends;
        this.delegating = true;
    }

    public ZpmModule(
        ModuleDescriptor descriptor,
        ZpmArtifact artifact)
    {
        this.name = descriptor.name();
        this.automatic = descriptor.isAutomatic();
        this.paths = new LinkedHashSet<>(singleton(artifact.path));
        this.id = artifact.id;
        this.depends = artifact.depends;
        this.delegating = false;
    }

    public ZpmModule(
        ZpmModule module)
    {
        this.name = module.name;
        this.automatic = false;
        this.paths = emptySet();
        this.id = module.id;
        this.depends = emptySet();
        this.delegating = false;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name, automatic, paths, id, depends, delegating);
    }

    @Override
    public boolean equals(
        Object obj)
    {
        if (obj == this)
        {
            return true;
        }

        if (!(obj instanceof ZpmModule))
        {
            return false;
        }

        ZpmModule that = (ZpmModule) obj;
        return Objects.equals(this.name, that.name) &&
                Objects.equals(this.paths, that.paths) &&
                Objects.equals(this.id, that.id) &&
                Objects.equals(this.depends, that.depends) &&
                this.automatic == that.automatic &&
                this.delegating == that.delegating;
    }

    @Override
    public String toString()
    {
        return String.format("%s%s%s -> %s %s", name, automatic ? "@" : "", delegating ? "+" : "", depends, paths);
    }
}
