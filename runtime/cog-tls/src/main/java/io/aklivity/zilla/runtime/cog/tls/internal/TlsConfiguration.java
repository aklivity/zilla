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
package io.aklivity.zilla.runtime.cog.tls.internal;

import io.aklivity.zilla.runtime.engine.cog.Configuration;

public class TlsConfiguration extends Configuration
{
    public static final IntPropertyDef TLS_HANDSHAKE_WINDOW_BYTES;
    public static final IntPropertyDef TLS_HANDSHAKE_TIMEOUT;
    public static final PropertyDef<String> TLS_KEY_MANAGER_ALGORITHM;
    public static final BooleanPropertyDef TLS_IGNORE_EMPTY_VAULT_REFS;
    public static final LongPropertyDef TLS_AWAIT_SYNC_CLOSE_MILLIS;
    public static final BooleanPropertyDef TLS_PROACTIVE_CLIENT_REPLY_BEGIN;

    private static final ConfigurationDef TLS_CONFIG;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.cog.tls");
        TLS_HANDSHAKE_WINDOW_BYTES = config.property("handshake.window.bytes", 65536);
        TLS_HANDSHAKE_TIMEOUT = config.property("handshake.timeout", 10);
        TLS_KEY_MANAGER_ALGORITHM = config.property("handshake.key.manager.algorithm", "PKIX");
        TLS_IGNORE_EMPTY_VAULT_REFS = config.property("ignore.empty.vault.refs", false);
        TLS_AWAIT_SYNC_CLOSE_MILLIS = config.property("await.sync.close.millis", 3000L);
        TLS_PROACTIVE_CLIENT_REPLY_BEGIN = config.property("proactive.client.reply.begin", false);
        TLS_CONFIG = config;
    }

    public TlsConfiguration(
        Configuration config)
    {
        super(TLS_CONFIG, config);
    }

    public int handshakeWindowBytes()
    {
        return TLS_HANDSHAKE_WINDOW_BYTES.getAsInt(this);
    }

    public int handshakeTimeout()
    {
        return TLS_HANDSHAKE_TIMEOUT.getAsInt(this);
    }

    public String keyManagerAlgorithm()
    {
        return TLS_KEY_MANAGER_ALGORITHM.get(this);
    }

    public boolean ignoreEmptyVaultRefs()
    {
        return TLS_IGNORE_EMPTY_VAULT_REFS.getAsBoolean(this);
    }

    public long awaitSyncCloseMillis()
    {
        return TLS_AWAIT_SYNC_CLOSE_MILLIS.get(this);
    }

    public boolean proactiveClientReplyBegin()
    {
        return TLS_PROACTIVE_CLIENT_REPLY_BEGIN.get(this);
    }
}
