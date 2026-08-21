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

import java.security.KeyStore;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Provides access to cryptographic material from an attached vault, for TLS contexts and
 * for wrapping and unwrapping arbitrary byte buffers under a named key.
 * <p>
 * Obtained from {@link VaultContext#attach(VaultConfig)}, a {@code VaultHandler} resolves
 * named key and certificate references from the vault's backing store (e.g., a PKCS#12 file
 * or PEM directory) into {@link javax.net.ssl.KeyManagerFactory} and
 * {@link javax.net.ssl.TrustManagerFactory} instances ready for use in TLS contexts, or into
 * a {@link SecretKeyManagerFactory} for wrapping and unwrapping arbitrary byte buffers under
 * a named key.
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
     * Initializes a {@link KeyManagerFactory} from every private key entry this vault
     * instance is configured to expose.
     * <p>
     * Used when the caller has no way to itemize specific key aliases, such as a
     * dynamically assembled binding configuration.
     * </p>
     *
     * @return an initialized {@link KeyManagerFactory}, or {@code null} if this vault
     *         instance exposes no private keys
     */
    KeyManagerFactory initKeys();

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
     * Initializes a {@link KeyManagerFactory} from every signing certificate entry this
     * vault instance is configured to expose.
     * <p>
     * Used when the caller has no way to itemize specific signer aliases, such as a
     * dynamically assembled binding configuration.
     * </p>
     *
     * @return an initialized {@link KeyManagerFactory}, or {@code null} if this vault
     *         instance exposes no signing certificates
     */
    KeyManagerFactory initSigners();

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

    /**
     * Initializes a {@link TrustManagerFactory} from every trusted certificate entry this
     * vault instance is configured to expose, merged with the provided system CA certificates.
     * <p>
     * Used when the caller has no way to itemize specific trust aliases, such as a
     * dynamically assembled binding configuration.
     * </p>
     *
     * @param cacerts   the JVM default trust store to merge with, or {@code null} to use only
     *                  the vault-provided certificates
     * @return an initialized {@link TrustManagerFactory}
     */
    TrustManagerFactory initTrust(
        KeyStore cacerts);

    /**
     * Initializes a {@link SecretKeyManagerFactory} from the named secret key entries in
     * the vault, for wrapping and unwrapping arbitrary byte buffers under those keys.
     * <p>
     * Like {@link #initKeys(List)}, any retrieval this vault implementation needs (e.g., a
     * call to a remote secret store) happens here, once; the {@link SecretKeyManager}
     * obtained from the returned factory then wraps and unwraps synchronously.
     * </p>
     *
     * @param secretKeyRefs  list of vault entry names identifying the secret keys to include
     * @return an initialized {@link SecretKeyManagerFactory}, or {@code null} if none of
     *         the referenced keys could be resolved
     */
    default SecretKeyManagerFactory initSecretKeys(
        List<String> secretKeyRefs)
    {
        return null;
    }

    /**
     * Wraps a buffer of bytes under the named key held by this vault, without the key's
     * raw material ever leaving the vault's implementation.
     * <p>
     * The result is delivered asynchronously to {@code next}, since resolving the named
     * key may require a call outside this thread. On success, {@code next} receives the
     * wrapped bytes; on failure (e.g., the named key could not be resolved), {@code next}
     * receives a {@code null} buffer and the caller treats the operation as failed. The
     * reason for a failure is reported separately, through this vault's own diagnostics.
     * </p>
     *
     * @param traceId  the trace identifier correlating this operation with its caller
     * @param key      the vault-specific name of the key to wrap under
     * @param bytes    the buffer containing the bytes to wrap
     * @param index    the index within {@code bytes} at which the bytes to wrap begin
     * @param length   the length in bytes of the data to wrap
     * @param next     invoked with the wrapped bytes on success, or a {@code null} buffer
     *                 on failure
     */
    default void wrap(
        long traceId,
        String key,
        DirectBufferEx bytes,
        int index,
        int length,
        BytesConsumer next)
    {
        next.accept(null, 0, 0);
    }

    /**
     * Unwraps a buffer of previously wrapped bytes under the key held by this vault that
     * wrapped them, resolved from the wrapped bytes themselves, without the key's raw
     * material ever leaving the vault's implementation.
     * <p>
     * The result is delivered asynchronously to {@code next}, since resolving the key may
     * require a call outside this thread. On success, {@code next} receives the unwrapped
     * bytes; on failure (e.g., no key could be resolved from the wrapped bytes), {@code next}
     * receives a {@code null} buffer and the caller treats the operation as failed. The
     * reason for a failure is reported separately, through this vault's own diagnostics.
     * </p>
     *
     * @param traceId  the trace identifier correlating this operation with its caller
     * @param bytes    the buffer containing the wrapped bytes to unwrap
     * @param index    the index within {@code bytes} at which the wrapped bytes begin
     * @param length   the length in bytes of the wrapped data
     * @param next     invoked with the unwrapped bytes on success, or a {@code null} buffer
     *                 on failure
     */
    default void unwrap(
        long traceId,
        DirectBufferEx bytes,
        int index,
        int length,
        BytesConsumer next)
    {
        next.accept(null, 0, 0);
    }

    /**
     * Receives the result of a {@link #wrap} or {@link #unwrap} operation.
     * <p>
     * A {@code null} buffer signals that the operation failed; the reason is reported
     * separately, through the vault's own diagnostics.
     * </p>
     */
    @FunctionalInterface
    interface BytesConsumer
    {
        void accept(
            DirectBufferEx buffer,
            int index,
            int length);
    }
}
