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
package io.aklivity.zilla.manager.internal.commands.install;

import java.util.Objects;

import jakarta.json.bind.annotation.JsonbTypeAdapter;

import io.aklivity.zilla.manager.internal.commands.install.adapters.ZpmDependencyAdapter;

@JsonbTypeAdapter(ZpmDependencyAdapter.class)
public final class ZpmDependency
{
    public String groupId;
    public String artifactId;
    public String version;

    public ZpmDependency()
    {
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(groupId, artifactId, version);
    }

    @Override
    public boolean equals(
        Object obj)
    {
        if (obj == this)
        {
            return true;
        }

        if (!(obj instanceof ZpmDependency))
        {
            return false;
        }

        ZpmDependency that = (ZpmDependency) obj;
        return Objects.equals(this.groupId, that.groupId) &&
                Objects.equals(this.artifactId, that.artifactId) &&
                Objects.equals(this.version, that.version);
    }

    @Override
    public String toString()
    {
        return String.format("%s:%s:%s", groupId, artifactId, version);
    }

    public static ZpmDependency of(
        String groupId,
        String artifactId,
        String version)
    {
        return new ZpmDependency(groupId, artifactId, version);
    }

    ZpmDependency(
        String groupId,
        String artifactId,
        String version)
    {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }
}
