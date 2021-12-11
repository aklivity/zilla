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
package io.aklivity.zilla.runtime.engine.internal.config;

import java.util.List;

import io.aklivity.zilla.runtime.engine.config.Binding;
import io.aklivity.zilla.runtime.engine.config.Namespace;
import io.aklivity.zilla.runtime.engine.config.Vault;

public class Configuration extends Namespace
{
    public final List<NamespaceRef> namespaces;

    public Configuration(
        String name,
        List<NamespaceRef> namespaces,
        List<Vault> vaults,
        List<Binding> bindings)
    {
        super(name, vaults, bindings);
        this.namespaces = namespaces;
    }
}
