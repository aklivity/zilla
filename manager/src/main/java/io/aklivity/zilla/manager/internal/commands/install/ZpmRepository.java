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
package io.aklivity.zilla.manager.internal.commands.install;

import java.util.Objects;

import jakarta.json.bind.annotation.JsonbTypeAdapter;

import io.aklivity.zilla.manager.internal.commands.install.adapters.ZpmRepositoryAdapter;

@JsonbTypeAdapter(ZpmRepositoryAdapter.class)
public final class ZpmRepository
{
    public String location;

    public ZpmRepository()
    {
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(location);
    }

    @Override
    public boolean equals(
        Object obj)
    {
        if (obj == this)
        {
            return true;
        }

        if (!(obj instanceof ZpmRepository))
        {
            return false;
        }

        ZpmRepository that = (ZpmRepository) obj;
        return Objects.equals(this.location, that.location);
    }

    @Override
    public String toString()
    {
        return location;
    }

    ZpmRepository(
        String location)
    {
        this.location = location;
    }
}
