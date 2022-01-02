/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.tls.internal.config;

import java.util.List;

import io.aklivity.zilla.runtime.engine.config.Options;

public final class TlsOptions extends Options
{
    public final String version;
    public final List<String> keys;
    public final List<String> trust;
    public final List<String> sni;
    public final List<String> alpn;
    public final TlsMutual mutual;
    public final List<String> signers;
    public final boolean trustcacerts;

    public TlsOptions(
        String version,
        List<String> keys,
        List<String> trust,
        List<String> sni,
        List<String> alpn,
        TlsMutual mutual,
        List<String> signers,
        boolean trustcacerts)
    {
        this.version = version;
        this.keys = keys;
        this.trust = trust;
        this.sni = sni;
        this.alpn = alpn;
        this.mutual = mutual;
        this.signers = signers;
        this.trustcacerts = trustcacerts;
    }
}
