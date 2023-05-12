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
package io.aklivity.zilla.runtime.binding.tls.internal.config;

import static java.util.Collections.list;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStore.TrustedCertificateEntry;
import java.util.LinkedList;
import java.util.List;

import io.aklivity.zilla.runtime.binding.tls.internal.TlsConfiguration;

public final class TlsTrust
{
    private TlsTrust()
    {
    }

    public static TrustedCertificateEntry[] cacerts(
        TlsConfiguration config)
    {
        String storeType = config.cacertsStoreType();
        String store = config.cacertsStore();
        String storePass = config.cacertsStorePass();

        TrustedCertificateEntry[] certificates = null;

        if (store == null || !Files.exists(Paths.get(store)))
        {
            String home = System.getProperty("java.home");

            store = String.format("%s/lib/security/jssecacerts", home);

            if (!Files.exists(Paths.get(store)))
            {
                store = String.format("%s/lib/security/cacerts", home);

                if (!Files.exists(Paths.get(store)))
                {
                    store = null;
                }
            }
        }

        if (store != null)
        {
            try
            {
                KeyStore cacerts = KeyStore.getInstance(storeType);
                cacerts.load(new FileInputStream(store), storePass != null ? storePass.toCharArray() : null);

                List<TrustedCertificateEntry> trusted = new LinkedList<>();
                for (String alias : list(cacerts.aliases()))
                {
                    if (cacerts.isCertificateEntry(alias))
                    {
                        TrustedCertificateEntry entry = (TrustedCertificateEntry) cacerts.getEntry(alias, null);
                        trusted.add(entry);
                    }
                }

                certificates = trusted.toArray(TrustedCertificateEntry[]::new);
            }
            catch (GeneralSecurityException | IOException ex)
            {
                // unable to load
            }
        }

        return certificates;
    }
}
