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
package io.aklivity.zilla.runtime.engine.test.internal.vault;

import java.net.URL;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.vault.Vault;
import io.aklivity.zilla.runtime.engine.vault.VaultContext;

public final class TestVault implements Vault
{
    public static final String NAME = "test";

    public TestVault(
        Configuration config)
    {
    }

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public URL type()
    {
        return getClass().getResource("test.schema.patch.json");
    }

    @Override
    public VaultContext supply(
        EngineContext context)
    {
        return new TestVaultContext(context);
    }
}
