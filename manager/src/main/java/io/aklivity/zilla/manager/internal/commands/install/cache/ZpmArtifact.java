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
package io.aklivity.zilla.manager.internal.commands.install.cache;

import static java.util.Collections.unmodifiableSet;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

public final class ZpmArtifact
{
    public final ZpmArtifactId id;
    public final Path path;
    public final Set<ZpmArtifactId> depends;


    public ZpmArtifact(
        ZpmArtifactId id,
        Path path,
        Set<ZpmArtifactId> depends)
    {
        this.id = id;
        this.path = path;
        this.depends = unmodifiableSet(depends);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id, path, depends);
    }

    @Override
    public boolean equals(
        Object obj)
    {
        if (obj == this)
        {
            return true;
        }

        if (!(obj instanceof ZpmArtifact))
        {
            return false;
        }

        ZpmArtifact that = (ZpmArtifact) obj;
        return Objects.equals(this.id, that.id) &&
                Objects.equals(this.path, that.path) &&
                Objects.equals(this.depends, that.depends);
    }

    @Override
    public String toString()
    {
        return String.format("%s [%s] -> %s", id, path, depends);
    }
}
