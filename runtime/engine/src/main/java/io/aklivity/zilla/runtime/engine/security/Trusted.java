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
package io.aklivity.zilla.runtime.engine.security;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

public final class Trusted
{
    private Trusted()
    {
    }

    public static KeyStore cacerts(
        Configuration config)
    {
        return config instanceof EngineConfiguration engineConfig
            ? cacerts(engineConfig)
            : cacerts(new EngineConfiguration(config));
    }

    private static KeyStore cacerts(
        EngineConfiguration config)
    {
        String storeType = config.cacertsStoreType();
        String storePath = config.cacertsStore();
        String storePass = config.cacertsStorePass();

        KeyStore cacerts = null;

        if (storePath == null || !Files.exists(Paths.get(storePath)))
        {
            String home = System.getProperty("java.home");

            storePath = String.format("%s/lib/security/jssecacerts", home);

            if (!Files.exists(Paths.get(storePath)))
            {
                storePath = String.format("%s/lib/security/cacerts", home);

                if (!Files.exists(Paths.get(storePath)))
                {
                    storePath = null;
                }
            }
        }

        if (storePath != null)
        {
            try
            {
                KeyStore store = KeyStore.getInstance(storeType);
                store.load(new FileInputStream(storePath), storePass != null ? storePass.toCharArray() : null);

                cacerts = store;
            }
            catch (GeneralSecurityException | IOException ex)
            {
                // unable to load
            }
        }

        return cacerts;
    }
}
