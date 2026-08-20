/*
 * Copyright 2021-2026 Aklivity Inc.
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

/**
 * Produces a {@link SecretKeyManager} backed by the named secret key entries an
 * {@link VaultHandler#initSecretKeys} call resolved.
 * <p>
 * Mirrors {@link javax.net.ssl.KeyManagerFactory}'s own shape: initialization (including any
 * remote retrieval a vault implementation needs) happens once, when
 * {@link VaultHandler#initSecretKeys} is called; {@link #getSecretKeyManager()} then hands
 * back a manager whose operations never block on that retrieval again.
 * </p>
 *
 * @see VaultHandler#initSecretKeys
 */
public interface SecretKeyManagerFactory
{
    /**
     * @return a {@link SecretKeyManager} over the secret keys this factory resolved
     */
    SecretKeyManager getSecretKeyManager();
}
