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
package io.aklivity.zilla.runtime.vault.filesystem.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStore.TrustedCertificateEntry;

import org.junit.Test;

import io.aklivity.zilla.runtime.vault.filesystem.config.FileSystemOptionsConfig;

public class FileSystemVaultTest
{
    @Test
    public void shouldResolveServer() throws Exception
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .keys()
                .store("stores/server/keys")
                .type("pkcs12")
                .password("generated")
                .build()
            .trust()
                .store("stores/server/trust")
                .type("pkcs12")
                .password("generated")
                .build()
            .build();

        FileSystemVaultHandler vault = new FileSystemVaultHandler(options, FileSystemVaultTest.class::getResource);

        PrivateKeyEntry key = vault.key("localhost");
        TrustedCertificateEntry certificate = vault.certificate("clientca");

        assertThat(key, not(nullValue()));
        assertThat(certificate, not(nullValue()));
    }

    @Test
    public void shouldResolveClient() throws Exception
    {
        FileSystemOptionsConfig options = FileSystemOptionsConfig.builder()
            .keys()
                .store("stores/client/keys")
                .type("pkcs12")
                .password("generated")
                .build()
            .signers()
                .store("stores/server/trust")
                .type("pkcs12")
                .password("generated")
                .build()
            .build();

        FileSystemVaultHandler vault = new FileSystemVaultHandler(options, FileSystemVaultTest.class::getResource);

        PrivateKeyEntry key = vault.key("client1");
        PrivateKeyEntry[] signedKeys = vault.keys("clientca");

        assertThat(key, not(nullValue()));
        assertThat(signedKeys, not(nullValue()));
        assertThat(signedKeys.length, equalTo(1));
        assertThat(signedKeys[0], not(nullValue()));
    }
}
