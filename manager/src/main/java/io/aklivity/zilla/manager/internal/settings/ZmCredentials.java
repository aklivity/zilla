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
package io.aklivity.zilla.manager.internal.settings;

import java.util.Objects;

public final class ZmCredentials
{
    public String realm;
    public String host;
    public String username;
    public String password;

    public ZmCredentials()
    {
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(realm, host, username, password);
    }

    @Override
    public boolean equals(
        Object obj)
    {
        if (obj == this)
        {
            return true;
        }

        if (!(obj instanceof ZmCredentials))
        {
            return false;
        }

        ZmCredentials that = (ZmCredentials) obj;
        return Objects.equals(this.realm, that.realm) &&
                Objects.equals(this.host, that.host) &&
                Objects.equals(this.username, that.username) &&
                Objects.equals(this.password, that.password);
    }

    @Override
    public String toString()
    {
        return String.format("%s:%s:%s:%s", realm, host, username, password != null ? "****" : null);
    }

    public static ZmCredentials of(
        String realm,
        String host,
        String username,
        String password)
    {
        return new ZmCredentials(realm, host, username, password);
    }

    ZmCredentials(
        String realm,
        String host,
        String username,
        String password)
    {
        this.realm = realm;
        this.host = host;
        this.username = username;
        this.password = password;
    }
}
