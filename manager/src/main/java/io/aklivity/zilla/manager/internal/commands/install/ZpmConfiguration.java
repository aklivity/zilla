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
package io.aklivity.zilla.manager.internal.commands.install;

import java.util.List;
import java.util.Objects;

public final class ZpmConfiguration
{
    public List<ZpmDependency> dependencies;
    public List<ZpmDependency> imports;
    public List<ZpmRepository> repositories;

    @Override
    public int hashCode()
    {
        return Objects.hash(dependencies, imports, repositories);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == this)
        {
            return true;
        }

        if (!(obj instanceof ZpmConfiguration))
        {
            return false;
        }

        ZpmConfiguration that = (ZpmConfiguration) obj;
        return Objects.deepEquals(this.dependencies, that.dependencies) &&
                Objects.deepEquals(this.imports, that.imports) &&
                Objects.deepEquals(this.repositories, that.repositories);
    }
}
