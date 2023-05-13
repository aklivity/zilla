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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.security.KeyStore.TrustedCertificateEntry;
import java.security.cert.X509Certificate;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.tls.internal.TlsConfiguration;
import io.aklivity.zilla.runtime.engine.Configuration;

public class TlsTrustTest
{
    @Test
    public void shouldConfigureTrustViaCacerts()
    {
        TlsConfiguration config = new TlsConfiguration(new Configuration());
        TrustedCertificateEntry[] entries = TlsTrust.cacerts(config);

        assertThat(entries, not(nullValue()));
        assertThat(entries, not(emptyArray()));
        assertThat(entries[0], not(nullValue()));
        assertThat(entries[0].getTrustedCertificate(), instanceOf(X509Certificate.class));
    }
}
