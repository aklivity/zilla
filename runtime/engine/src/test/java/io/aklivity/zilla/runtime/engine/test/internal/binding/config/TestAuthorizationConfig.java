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
package io.aklivity.zilla.runtime.engine.test.internal.binding.config;

import java.util.Map;

public final class TestAuthorizationConfig
{
    public final String name;
    public final String credentials;
    public final String callback;
    public final Map<String, String> callbackParams;
    public final String expectIdentity;
    public final String expectCredentials;
    public final Map<String, String> attributes;

    public TestAuthorizationConfig(
        String name,
        String credentials,
        Map<String, String> attributes)
    {
        this(name, credentials, null, null, null, null, attributes);
    }

    public TestAuthorizationConfig(
        String name,
        String credentials,
        String callback,
        Map<String, String> callbackParams,
        String expectIdentity,
        String expectCredentials,
        Map<String, String> attributes)
    {
        this.name = name;
        this.credentials = credentials;
        this.callback = callback;
        this.callbackParams = callbackParams;
        this.expectIdentity = expectIdentity;
        this.expectCredentials = expectCredentials;
        this.attributes = attributes;
    }
}
