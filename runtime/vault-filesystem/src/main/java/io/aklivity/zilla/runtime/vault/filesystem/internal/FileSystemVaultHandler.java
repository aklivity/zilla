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
package io.aklivity.zilla.runtime.vault.filesystem.internal;

import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStore.Entry;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStore.SecretKeyEntry;
import java.security.KeyStore.TrustedCertificateEntry;
import java.security.SecureRandom;
import java.security.cert.CertPathValidator;
import java.security.cert.Certificate;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.x500.X500Principal;

import org.agrona.LangUtil;

import io.aklivity.zilla.config.vault.filesystem.FileSystemOptionsConfig;
import io.aklivity.zilla.config.vault.filesystem.FileSystemSecretEntryConfig;
import io.aklivity.zilla.config.vault.filesystem.FileSystemSecretsConfig;
import io.aklivity.zilla.config.vault.filesystem.FileSystemStoreConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.security.RevocationStrategy;
import io.aklivity.zilla.runtime.engine.vault.SecretKeyManager;
import io.aklivity.zilla.runtime.engine.vault.SecretKeyManagerFactory;
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;

public class FileSystemVaultHandler implements VaultHandler
{
    private static final String STORE_TYPE_DEFAULT = "pkcs12";
    private static final String PKIX_ALGORITHM = "PKIX";
    private static final String AES_GCM_TRANSFORM = "AES/GCM/NoPadding";
    private static final String ALGORITHM_AES128_GCM = "AES128_GCM";
    private static final String ALGORITHM_AES256_GCM = "AES256_GCM";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int PREFIX_LENGTH = Integer.BYTES + GCM_IV_LENGTH;
    private static final int CHUNK_CAPACITY = 128;
    private static final int OUTPUT_CAPACITY_DEFAULT = 192;

    private final Function<List<String>, KeyManagerFactory> supplyKeys;
    private final Function<List<String>, KeyManagerFactory> supplySigners;
    private final BiFunction<List<String>, KeyStore, TrustManagerFactory> supplyTrust;
    private final RevocationStrategy revocation;
    private final FileSystemStoreInfo keys;
    private final FileSystemStoreInfo signers;
    private final FileSystemStoreInfo trust;
    private final FileSystemSecretsInfo secrets;
    private final SecureRandom random;
    private final Cipher cipher;
    private final byte[] iv;
    private final byte[] chunk;
    private final UnsafeBufferEx scratchBuffer;
    private byte[] output;

    public FileSystemVaultHandler(
        FileSystemOptionsConfig options,
        Function<String, Path> resolvePath)
    {
        this(options, resolvePath, RevocationStrategy.NONE);
    }

    public FileSystemVaultHandler(
        FileSystemOptionsConfig options,
        Function<String, Path> resolvePath,
        RevocationStrategy revocation)
    {
        this.keys = supplyStoreInfo(resolvePath, options.keys);
        supplyKeys = keys != null
            ? keys::newKeysFactory
            : aliases -> null;

        this.signers = supplyStoreInfo(resolvePath, options.signers);
        supplySigners = signers != null && keys != null
            ? aliases -> newSignersFactory(aliases, signers, keys)
            : aliases -> null;

        this.revocation = options.revocation != null
            ? RevocationStrategy.valueOf(options.revocation.toUpperCase())
            : revocation;
        this.trust = supplyStoreInfo(resolvePath, options.trust);
        supplyTrust = (aliases, cacerts) -> newTrustFactory(trust, aliases, cacerts);

        this.secrets = supplySecretsInfo(resolvePath, options.secrets);
        this.random = new SecureRandom();
        this.cipher = newCipher();
        this.iv = new byte[GCM_IV_LENGTH];
        this.chunk = new byte[CHUNK_CAPACITY];
        this.scratchBuffer = new UnsafeBufferEx();
        this.output = new byte[OUTPUT_CAPACITY_DEFAULT];
    }

    @Override
    public KeyManagerFactory initKeys(
        List<String> aliases)
    {
        return supplyKeys.apply(aliases);
    }

    @Override
    public KeyManagerFactory initKeys()
    {
        return keys != null ? initKeys(keys.allKeyAliases()) : null;
    }

    @Override
    public TrustManagerFactory initTrust(
        List<String> aliases,
        KeyStore cacerts)
    {
        return supplyTrust.apply(aliases, cacerts);
    }

    @Override
    public TrustManagerFactory initTrust(
        KeyStore cacerts)
    {
        return initTrust(trust != null ? trust.allCertificateAliases() : null, cacerts);
    }

    @Override
    public KeyManagerFactory initSigners(
        List<String> aliases)
    {
        return supplySigners.apply(aliases);
    }

    @Override
    public KeyManagerFactory initSigners()
    {
        return signers != null ? initSigners(signers.allCertificateAliases()) : null;
    }

    @Override
    public void wrap(
        long traceId,
        String key,
        DirectBufferEx bytes,
        int index,
        int length,
        BytesConsumer next)
    {
        int producedLength = wrapCore(key, bytes, index, length);
        DirectBufferEx wrapped = null;

        if (producedLength >= 0)
        {
            scratchBuffer.wrap(output, 0, producedLength);
            wrapped = scratchBuffer;
        }

        next.accept(wrapped, 0, Math.max(producedLength, 0));
    }

    @Override
    public void unwrap(
        long traceId,
        DirectBufferEx bytes,
        int index,
        int length,
        BytesConsumer next)
    {
        int producedLength = unwrapCore(bytes, index, length, null);
        DirectBufferEx unwrapped = null;

        if (producedLength >= 0)
        {
            scratchBuffer.wrap(output, 0, producedLength);
            unwrapped = scratchBuffer;
        }

        next.accept(unwrapped, 0, Math.max(producedLength, 0));
    }

    @Override
    public SecretKeyManagerFactory initSecretKeys(
        List<String> aliases)
    {
        List<String> matched = matchedSecrets(aliases);
        SecretKeyManagerFactory factory = null;

        if (!matched.isEmpty())
        {
            SecretKeyManager manager = new FileSystemSecretKeyManager(new HashSet<>(matched));
            factory = () -> manager;
        }

        return factory;
    }

    private List<String> matchedSecrets(
        List<String> aliases)
    {
        List<String> matched = List.of();

        if (aliases != null && secrets != null)
        {
            matched = aliases.stream()
                .filter(name -> secrets.entry(name) != null)
                .toList();
        }

        return matched;
    }

    // returns the length of the wrapped bytes written to `output` (see #output), or -1 on failure;
    // shared by both the async #wrap and the synchronous FileSystemSecretKeyManager#wrap;
    // the wrapped bytes are self-sufficient for #unwrapCore: a length-prefixed copy of `key`
    // precedes the version + iv + ciphertext so no external name is needed to unwrap them
    private int wrapCore(
        String key,
        DirectBufferEx bytes,
        int index,
        int length)
    {
        int producedLength = -1;

        FileSystemSecretEntryInfo entry = secrets != null ? secrets.entry(key) : null;
        String alias = entry != null ? entry.activeAlias() : null;
        SecretKey secretKey = alias != null ? secrets.secretKey(alias) : null;
        String algorithm = secretKey != null ? resolveAlgorithm(entry, secretKey) : null;

        if (algorithm != null)
        {
            try
            {
                random.nextBytes(iv);
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

                byte[] nameBytes = entry.nameBytes();
                int nameHeaderLength = Integer.BYTES + nameBytes.length;
                int headerLength = nameHeaderLength + PREFIX_LENGTH;

                byte[] wire = output(headerLength + cipher.getOutputSize(length));
                putInt(wire, 0, nameBytes.length);
                System.arraycopy(nameBytes, 0, wire, Integer.BYTES, nameBytes.length);
                putInt(wire, nameHeaderLength, entry.active);
                System.arraycopy(iv, 0, wire, nameHeaderLength + Integer.BYTES, GCM_IV_LENGTH);

                int position = update(bytes, index, length, wire, headerLength);
                producedLength = position + cipher.doFinal(wire, position);
            }
            catch (GeneralSecurityException ex)
            {
                producedLength = -1;
            }
        }

        return producedLength;
    }

    // returns the length of the unwrapped bytes written to `output` (see #output), or -1 on failure;
    // shared by both the async #unwrap and the synchronous FileSystemSecretKeyManager#unwrap; the
    // key to unwrap under is recovered entirely from the length-prefixed name embedded by
    // #wrapCore, not supplied by the caller; `permitted` further restricts the recovered name
    // (e.g. to a SecretKeyManager's configured set), or is null when any resolved key is allowed
    private int unwrapCore(
        DirectBufferEx bytes,
        int index,
        int length,
        Predicate<String> permitted)
    {
        int producedLength = -1;

        int remaining = length - Integer.BYTES;
        if (secrets != null && remaining >= 0)
        {
            int nameLength = bytes.getInt(index, ByteOrder.BIG_ENDIAN);
            int nameOffset = index + Integer.BYTES;

            if (nameLength >= 0 && nameLength <= remaining - PREFIX_LENGTH)
            {
                FileSystemSecretEntryInfo entry = secrets.entry(bytes, nameOffset, nameLength);

                if (entry != null && (permitted == null || permitted.test(entry.name())))
                {
                    int versionOffset = nameOffset + nameLength;
                    int headerLength = (versionOffset - index) + PREFIX_LENGTH;
                    int version = bytes.getInt(versionOffset, ByteOrder.BIG_ENDIAN);

                    String alias = entry.alias(version);
                    SecretKey secretKey = alias != null ? secrets.secretKey(alias) : null;

                    if (secretKey != null)
                    {
                        try
                        {
                            bytes.getBytes(versionOffset + Integer.BYTES, iv, 0, GCM_IV_LENGTH);

                            // codeql[java/static-initialization-vector]: iv comes from wrap, not static
                            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

                            int cipherIndex = index + headerLength;
                            int cipherLength = length - headerLength;
                            byte[] plaintext = output(cipher.getOutputSize(cipherLength));
                            int position = update(bytes, cipherIndex, cipherLength, plaintext, 0);
                            producedLength = position + cipher.doFinal(plaintext, position);
                        }
                        catch (GeneralSecurityException ex)
                        {
                            producedLength = -1;
                        }
                    }
                }
            }
        }

        return producedLength;
    }

    private static Cipher newCipher()
    {
        Cipher cipher = null;

        try
        {
            cipher = Cipher.getInstance(AES_GCM_TRANSFORM);
        }
        catch (GeneralSecurityException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return cipher;
    }

    private String resolveAlgorithm(
        FileSystemSecretEntryInfo entry,
        SecretKey secretKey)
    {
        return entry.algorithm != null ? entry.algorithm : inferAlgorithm(secretKey.getEncoded().length);
    }

    private static String inferAlgorithm(
        int keyLength)
    {
        String algorithm = null;

        switch (keyLength)
        {
        case 16:
            algorithm = ALGORITHM_AES128_GCM;
            break;
        case 32:
            algorithm = ALGORITHM_AES256_GCM;
            break;
        default:
            break;
        }

        return algorithm;
    }

    private byte[] output(
        int length)
    {
        if (length > output.length)
        {
            output = new byte[length];
        }

        return output;
    }

    private int update(
        DirectBufferEx bytes,
        int index,
        int length,
        byte[] output,
        int outputOffset)
        throws ShortBufferException
    {
        int position = outputOffset;
        int offset = index;
        int remaining = length;

        while (remaining > 0)
        {
            int chunkLength = Math.min(remaining, chunk.length);
            bytes.getBytes(offset, chunk, 0, chunkLength);
            position += cipher.update(chunk, 0, chunkLength, output, position);
            offset += chunkLength;
            remaining -= chunkLength;
        }

        return position;
    }

    private static void putInt(
        byte[] buffer,
        int offset,
        int value)
    {
        buffer[offset] = (byte) (value >>> 24);
        buffer[offset + 1] = (byte) (value >>> 16);
        buffer[offset + 2] = (byte) (value >>> 8);
        buffer[offset + 3] = (byte) value;
    }

    private static FileSystemSecretsInfo supplySecretsInfo(
        Function<String, Path> resolvePath,
        FileSystemSecretsConfig config)
    {
        FileSystemSecretsInfo info = null;

        if (config != null)
        {
            try
            {
                Path storePath = resolvePath.apply(config.store);
                try (InputStream input = Files.newInputStream(storePath))
                {
                    String type = Optional.ofNullable(config.type).orElse(STORE_TYPE_DEFAULT);
                    char[] password = Optional.ofNullable(config.password).map(String::toCharArray).orElse(null);

                    KeyStore store = KeyStore.getInstance(type);
                    store.load(input, password);

                    info = new FileSystemSecretsInfo(store, password, config.entries);
                }
            }
            catch (Exception ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }
        }

        return info;
    }

    private static FileSystemStoreInfo supplyStoreInfo(
        Function<String, Path> resolvePath,
        FileSystemStoreConfig config)
    {
        FileSystemStoreInfo info = null;

        if (config != null)
        {
            try
            {
                Path storePath = resolvePath.apply(config.store);
                try (InputStream input = Files.newInputStream(storePath))
                {
                    String type = Optional.ofNullable(config.type).orElse(STORE_TYPE_DEFAULT);
                    char[] password = Optional.ofNullable(config.password).map(String::toCharArray).orElse(null);

                    KeyStore store = KeyStore.getInstance(type);
                    store.load(input, password);

                    info = new FileSystemStoreInfo(store, password, config.entries);
                }
            }
            catch (Exception ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }
        }

        return info;
    }

    private KeyManagerFactory newSignersFactory(
        List<String> aliases,
        FileSystemStoreInfo signers,
        FileSystemStoreInfo keys)
    {
        KeyManagerFactory factory = null;

        if (aliases != null)
        {
            factory = keys.newKeysFactory(aliases.stream()
                .map(signers::certificate)
                .filter(Objects::nonNull)
                .map(TrustedCertificateEntry::getTrustedCertificate)
                .filter(X509Certificate.class::isInstance)
                .map(X509Certificate.class::cast)
                .map(X509Certificate::getSubjectX500Principal)
                .map(keys::issuedKeys)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .toList());
        }

        return factory;
    }

    private TrustManagerFactory newTrustFactory(
        FileSystemStoreInfo store,
        List<String> aliases,
        KeyStore cacerts)
    {
        TrustManagerFactory factory = null;

        try
        {
            if (aliases != null || cacerts != null)
            {
                KeyStore trust = KeyStore.getInstance(STORE_TYPE_DEFAULT);
                trust.load(null, null);

                if (aliases != null && store != null)
                {
                    for (String alias : aliases)
                    {
                        TrustedCertificateEntry cert = store.certificate(alias);
                        if (cert != null)
                        {
                            trust.setEntry(alias, cert, null);
                        }
                    }
                }

                if (cacerts != null && aliases != null)
                {
                    for (String alias : aliases)
                    {
                        TrustedCertificateEntry cacert = FileSystemStoreInfo.certificate(cacerts, alias);
                        if (cacert != null)
                        {
                            trust.setEntry(alias, cacert, null);
                        }
                    }
                }

                switch (revocation)
                {
                case CRL:
                    factory = TrustManagerFactory.getInstance(PKIX_ALGORITHM);
                    PKIXBuilderParameters pkixParams = new PKIXBuilderParameters(trust, new X509CertSelector());
                    pkixParams.setRevocationEnabled(true);

                    CertPathValidator validator = CertPathValidator.getInstance(PKIX_ALGORITHM);
                    PKIXRevocationChecker checker = (PKIXRevocationChecker) validator.getRevocationChecker();
                    checker.setOptions(EnumSet.of(
                        PKIXRevocationChecker.Option.PREFER_CRLS
                    ));
                    pkixParams.addCertPathChecker(checker);

                    CertPathTrustManagerParameters tmParams = new CertPathTrustManagerParameters(pkixParams);
                    factory.init(tmParams);
                    break;
                default:
                    factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    factory.init(trust);
                    break;
                }
            }
        }
        catch (Exception ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return factory;
    }

    private static final class FileSystemStoreInfo
    {
        private final KeyStore store;
        private final KeyStore.PasswordProtection protection;
        private final List<String> entries;

        private FileSystemStoreInfo(
            KeyStore store,
            char[] password,
            List<String> entries)
        {
            this.store = store;
            this.protection = password != null ? new KeyStore.PasswordProtection(password) : null;
            this.entries = entries;
        }

        private KeyManagerFactory newKeysFactory(
            List<String> aliases)
        {
            KeyManagerFactory factory = null;

            try
            {
                if (aliases != null)
                {
                    KeyStore keys = KeyStore.getInstance(STORE_TYPE_DEFAULT);
                    keys.load(null, protection.getPassword());

                    for (String alias : aliases)
                    {
                        PrivateKeyEntry key = key(alias);
                        if (key != null)
                        {
                            keys.setEntry(alias, key, protection);
                        }
                    }

                    factory = KeyManagerFactory.getInstance("PKIX");
                    factory.init(keys, protection.getPassword());
                }
            }
            catch (Exception ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }

            return factory;
        }

        private List<String> allKeyAliases()
        {
            return entries != null ? entries : allAliases(PrivateKeyEntry.class, protection);
        }

        private List<String> allCertificateAliases()
        {
            return entries != null ? entries : allAliases(TrustedCertificateEntry.class, null);
        }

        private List<String> allAliases(
            Class<? extends Entry> type,
            KeyStore.PasswordProtection entryProtection)
        {
            List<String> aliases = Collections.emptyList();

            try
            {
                aliases = Collections.list(store.aliases()).stream()
                    .filter(alias -> entry(store, entryProtection, alias, type) != null)
                    .toList();
            }
            catch (Exception ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }

            return aliases;
        }

        private PrivateKeyEntry key(
            String alias)
        {
            return entries == null || entries.contains(alias)
                ? entry(store, protection, alias, PrivateKeyEntry.class)
                : null;
        }

        private TrustedCertificateEntry certificate(
            String alias)
        {
            return entries == null || entries.contains(alias)
                ? entry(store, null, alias, TrustedCertificateEntry.class)
                : null;
        }

        private List<String> issuedKeys(
            X500Principal issuer)
        {
            List<String> keys = null;

            try
            {
                List<String> candidateKeys = Collections.list(store.aliases()).stream()
                    .filter(alias -> issuedKey(alias, issuer))
                    .toList();

                keys = candidateKeys.isEmpty() ? null : candidateKeys;
            }
            catch (Exception ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }

            return keys;
        }

        private boolean issuedKey(
            String alias,
            X500Principal issuer)
        {
            PrivateKeyEntry key = key(alias);
            Certificate certificate = key != null ? key.getCertificate() : null;
            return certificate != null &&
                certificate instanceof X509Certificate &&
                issuer.equals(((X509Certificate) certificate).getIssuerX500Principal());
        }

        private static TrustedCertificateEntry certificate(
            KeyStore store,
            String alias)
        {
            return entry(store, null, alias, TrustedCertificateEntry.class);
        }

        private static <T extends Entry> T entry(
            KeyStore store,
            KeyStore.PasswordProtection protection,
            String alias,
            Class<T> type)
        {
            T typed = null;

            try
            {
                Entry entry = store.getEntry(alias, protection);
                if (type.isInstance(entry))
                {
                    typed = type.cast(entry);
                }
            }
            catch (GeneralSecurityException ex)
            {
            }

            return typed;
        }
    }

    private static final class FileSystemSecretsInfo
    {
        private final KeyStore store;
        private final KeyStore.PasswordProtection protection;
        private final Map<String, FileSystemSecretEntryInfo> entries;
        private final Map<String, SecretKey> resolvedKeys;

        private FileSystemSecretsInfo(
            KeyStore store,
            char[] password,
            Map<String, FileSystemSecretEntryConfig> entries)
        {
            this.store = store;
            this.protection = password != null ? new KeyStore.PasswordProtection(password) : null;

            Map<String, FileSystemSecretEntryInfo> resolved = new LinkedHashMap<>();
            if (entries != null)
            {
                entries.forEach((name, entry) -> resolved.put(name, new FileSystemSecretEntryInfo(name, entry)));
            }
            this.entries = resolved;
            this.resolvedKeys = new LinkedHashMap<>();
        }

        private FileSystemSecretEntryInfo entry(
            String name)
        {
            return entries.get(name);
        }

        private FileSystemSecretEntryInfo entry(
            DirectBufferEx bytes,
            int offset,
            int length)
        {
            FileSystemSecretEntryInfo matched = null;

            for (FileSystemSecretEntryInfo candidate : entries.values())
            {
                if (candidate.matchesName(bytes, offset, length))
                {
                    matched = candidate;
                    break;
                }
            }

            return matched;
        }

        private SecretKey secretKey(
            String alias)
        {
            return resolvedKeys.computeIfAbsent(alias, this::resolveSecretKey);
        }

        private SecretKey resolveSecretKey(
            String alias)
        {
            SecretKey key = null;

            try
            {
                Entry entry = store.getEntry(alias, protection);
                if (entry instanceof SecretKeyEntry secretKeyEntry)
                {
                    key = secretKeyEntry.getSecretKey();
                }
            }
            catch (GeneralSecurityException ex)
            {
            }

            return key;
        }
    }

    private static final class FileSystemSecretEntryInfo
    {
        private final String name;
        private final byte[] nameBytes;
        private final int active;
        private final Map<Integer, String> aliases;
        private final String algorithm;

        private FileSystemSecretEntryInfo(
            String name,
            FileSystemSecretEntryConfig config)
        {
            this.name = name;
            this.nameBytes = name.getBytes(StandardCharsets.UTF_8);
            this.active = Integer.parseInt(config.active);

            Map<Integer, String> aliases = new LinkedHashMap<>();
            config.versions.forEach((version, alias) -> aliases.put(Integer.parseInt(version), alias));
            this.aliases = aliases;

            this.algorithm = config.algorithm;
        }

        private String name()
        {
            return name;
        }

        private byte[] nameBytes()
        {
            return nameBytes;
        }

        private boolean matchesName(
            DirectBufferEx bytes,
            int offset,
            int length)
        {
            boolean matches = nameBytes.length == length;

            for (int i = 0; matches && i < length; i++)
            {
                matches = nameBytes[i] == bytes.getByte(offset + i);
            }

            return matches;
        }

        private String alias(
            int version)
        {
            return aliases.get(version);
        }

        private String activeAlias()
        {
            return aliases.get(active);
        }
    }

    private final class FileSystemSecretKeyManager implements SecretKeyManager
    {
        private final Set<String> permitted;

        private FileSystemSecretKeyManager(
            Set<String> permitted)
        {
            this.permitted = permitted;
        }

        @Override
        public int wrap(
            String keyName,
            DirectBufferEx bytes,
            int index,
            int length,
            MutableDirectBufferEx dst,
            int dstIndex)
        {
            int producedLength = permitted.contains(keyName) ? wrapCore(keyName, bytes, index, length) : -1;

            if (producedLength >= 0)
            {
                dst.putBytes(dstIndex, output, 0, producedLength);
            }

            return producedLength;
        }

        @Override
        public int unwrap(
            DirectBufferEx bytes,
            int index,
            int length,
            MutableDirectBufferEx dst,
            int dstIndex)
        {
            int producedLength = unwrapCore(bytes, index, length, permitted::contains);

            if (producedLength >= 0)
            {
                dst.putBytes(dstIndex, output, 0, producedLength);
            }

            return producedLength;
        }
    }
}
