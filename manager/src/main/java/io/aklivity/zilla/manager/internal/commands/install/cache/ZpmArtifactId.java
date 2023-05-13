/*
 * Copyright 2021-2023 Aklivity Inc.
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

import java.util.Objects;

public final class ZpmArtifactId
{
    public final String group;
    public final String artifact;
    public final String version;

    public ZpmArtifactId(
        String groupId,
        String artifactId,
        String version)
    {
        this.group = groupId;
        this.artifact = artifactId;
        this.version = version;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(group, artifact, version);
    }

    @Override
    public boolean equals(
        Object obj)
    {
        if (obj == this)
        {
            return true;
        }

        if (!(obj instanceof ZpmArtifactId))
        {
            return false;
        }

        ZpmArtifactId that = (ZpmArtifactId) obj;
        return Objects.equals(this.group, that.group) &&
                Objects.equals(this.artifact, that.artifact) &&
                Objects.equals(this.version, that.version);
    }

    @Override
    public String toString()
    {
        return String.format("%s:%s:%s", group, artifact, version);
    }
}
