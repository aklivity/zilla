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

import io.aklivity.zilla.runtime.engine.config.VaultConfig;

/**
 * Per-thread context for a cryptographic material vault.
 * <p>
 * Created once per I/O thread by {@link Vault#supply(EngineContext)}. Manages the lifecycle
 * of {@link VaultHandler} instances for vault configurations active on this thread.
 * </p>
 *
 * @see Vault
 * @see VaultHandler
 */
public interface VaultContext
{
    /**
     * Attaches a vault configuration to this thread's context and returns a handler
     * for accessing its cryptographic material.
     *
     * @param vault  the vault configuration to activate
     * @return a {@link VaultHandler} for retrieving keys and certificates
     */
    VaultHandler attach(
        VaultConfig vault);

    /**
     * Detaches a previously attached vault configuration, releasing associated resources.
     *
     * @param vault  the vault configuration to deactivate
     */
    void detach(
        VaultConfig vault);
}
