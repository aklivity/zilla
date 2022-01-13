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
package io.aklivity.zilla.runtime.cog.tls.internal;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.cog.Cog;
import io.aklivity.zilla.runtime.engine.cog.CogFactorySpi;

public final class TlsCogFactorySpi implements CogFactorySpi
{
    @Override
    public String name()
    {
        return TlsCog.NAME;
    }

    @Override
    public Cog create(
        Configuration config)
    {
        return new TlsCog(new TlsConfiguration(config));
    }
}
