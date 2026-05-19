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

import java.security.KeyStore;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * Provides access to TLS cryptographic material from an attached vault.
 * <p>
 * Obtained from {@link VaultContext#attach(VaultConfig)}, a {@code VaultHandler} resolves
 * named key and certificate references from the vault's backing store (e.g., a PKCS#12 file
 * or PEM directory) into {@link javax.net.ssl.KeyManagerFactory} and
 * {@link javax.net.ssl.TrustManagerFactory} instances ready for use in TLS contexts.
 * </p>
 *
 * @see VaultContext
 */
public interface VaultHandler
{
    /**
     * Initializes a {@link KeyManagerFactory} from the named private key entries in the vault.
     * <p>
     * Used to supply the local TLS identity (certificate + private key) presented during
     * a TLS handshake.
     * </p>
     *
     * @param keyRefs  list of vault entry names identifying the private keys to include
     * @return an initialized {@link KeyManagerFactory}, or {@code null} if none of the
     *         referenced keys could be resolved
     */
    KeyManagerFactory initKeys(
        List<String> keyRefs);

    /**
     * Initializes a {@link KeyManagerFactory} from the named signing certificate entries
     * in the vault.
     * <p>
     * Used for mutual TLS scenarios where the engine acts as a signer rather than presenting
     * a full key pair.
     * </p>
     *
     * @param signerRefs  list of vault entry names identifying the signing certificates
     * @return an initialized {@link KeyManagerFactory}, or {@code null} if none of the
     *         referenced signers could be resolved
     */
    KeyManagerFactory initSigners(
        List<String> signerRefs);

    /**
     * Initializes a {@link TrustManagerFactory} from the named trusted certificate entries
     * in the vault, merged with the provided system CA certificates.
     * <p>
     * Used to build the trust anchors for verifying peer TLS certificates.
     * </p>
     *
     * @param certRefs  list of vault entry names identifying the trusted certificates to include
     * @param cacerts   the JVM default trust store to merge with, or {@code null} to use only
     *                  the vault-provided certificates
     * @return an initialized {@link TrustManagerFactory}
     */
    TrustManagerFactory initTrust(
        List<String> certRefs,
        KeyStore cacerts);
}
