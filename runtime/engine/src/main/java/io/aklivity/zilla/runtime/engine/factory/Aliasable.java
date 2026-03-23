/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.factory;

import java.util.Set;

/**
 * Mixin interface for plugin types that can be referenced under alternative type names.
 * <p>
 * Implemented by {@link Catalog} and {@link Vault} to allow a single plugin implementation
 * to respond to multiple {@code type} names in {@code zilla.yaml}. For example, a schema
 * registry catalog might register aliases for both {@code "apicurio"} and {@code "confluent"}
 * if they share the same wire protocol.
 * </p>
 * <p>
 * The primary type name is declared via {@link FactorySpi#type()}; aliases declared here
 * supplement that primary name.
 * </p>
 *
 * @see Catalog
 * @see Vault
 */
public interface Aliasable
{
    /** Default empty alias set used by implementations that declare no aliases. */
    Set<String> ALIASES_DEFAULT = Set.of();

    /**
     * Returns the set of alternative type names under which this plugin can be referenced.
     * <p>
     * Returns an empty set by default. Override to declare one or more alias names.
     * </p>
     *
     * @return an immutable set of alias type name strings
     */
    default Set<String> aliases()
    {
        return ALIASES_DEFAULT;
    }
}
