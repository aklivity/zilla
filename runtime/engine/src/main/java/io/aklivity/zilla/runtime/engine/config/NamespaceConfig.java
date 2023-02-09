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
package io.aklivity.zilla.runtime.engine.config;

import static java.util.Objects.requireNonNull;

import java.net.URL;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToLongFunction;

import io.aklivity.zilla.runtime.engine.internal.config.NamespaceRef;

public class NamespaceConfig
{
    public transient int id;
    public transient ToLongFunction<String> resolveId;
    public transient Function<URL, String> readURL;

    public final String name;
    public final List<NamespaceRef> references;
    public final List<BindingConfig> bindings;
    public final List<GuardConfig> guards;
    public final List<VaultConfig> vaults;

    public NamespaceConfig(
        String name,
        List<NamespaceRef> references,
        List<BindingConfig> bindings,
        List<GuardConfig> guards,
        List<VaultConfig> vaults)
    {
        this.name = requireNonNull(name);
        this.references = requireNonNull(references);
        this.bindings = requireNonNull(bindings);
        this.guards = requireNonNull(guards);
        this.vaults = requireNonNull(vaults);
    }
}
