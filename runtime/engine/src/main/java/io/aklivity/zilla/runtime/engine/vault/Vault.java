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
package io.aklivity.zilla.runtime.engine.vault;

import java.net.URL;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.factory.Aliasable;

/**
 * Entry point for a cryptographic material vault plugin.
 * <p>
 * A {@code Vault} provides access to TLS keys, certificates, and trust anchors used by
 * bindings that require TLS (e.g., {@code binding-tls}). The canonical implementation is
 * {@code vault-filesystem}, which loads key stores from the local filesystem.
 * </p>
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} through {@link VaultFactorySpi}.
 * A vault may declare aliases via {@link Aliasable#aliases()} to support multiple configuration names.
 * </p>
 *
 * @see VaultContext
 * @see VaultHandler
 * @see VaultFactorySpi
 */
public interface Vault extends Aliasable
{
    /**
     * Returns the unique name identifying this vault type, e.g. {@code "filesystem"}.
     *
     * @return the vault type name
     */
    String name();

    /**
     * Creates a per-thread context for this vault.
     *
     * @param context  the engine context for the calling I/O thread
     * @return a new {@link VaultContext} confined to that thread
     */
    VaultContext supply(
        EngineContext context);

    /**
     * Returns a URL pointing to the JSON schema for this vault's configuration options.
     *
     * @return the configuration schema URL
     */
    URL type();
}
