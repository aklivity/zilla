/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.config.binding.proxy;

public class ProxySecureInfoConfig
{
    public final String version;

    public final String cipher;

    public final String key;

    public final String name;

    public final String signature;

    public ProxySecureInfoConfig(
        String version,
        String cipher,
        String key,
        String name,
        String signature)
    {
        this.version = version;
        this.cipher = cipher;
        this.key = key;
        this.name = name;
        this.signature = signature;
    }
}
