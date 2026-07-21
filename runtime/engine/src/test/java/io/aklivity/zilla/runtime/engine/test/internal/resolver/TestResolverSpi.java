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
package io.aklivity.zilla.runtime.engine.test.internal.resolver;

import java.util.Base64;
import java.util.Random;

import io.aklivity.zilla.runtime.engine.resolver.ResolverSpi;

public class TestResolverSpi implements ResolverSpi
{
    private static final String RANDOM_BASE64_PREFIX = "randomBase64.";

    public String resolve(
        String var)
    {
        String result = null;

        if ("PASSWORD".equals(var))
        {
            result = "ACTUALPASSWORD";
        }
        else if ("PORT".equals(var))
        {
            result = "1234";
        }
        else if ("EXPRESSION".equals(var))
        {
            result = "${{test.EXPRESSION}}";
        }
        else if (var.startsWith(RANDOM_BASE64_PREFIX))
        {
            final int length = Integer.parseInt(var.substring(RANDOM_BASE64_PREFIX.length()));
            final byte[] bytes = new byte[length];
            new Random(length).nextBytes(bytes);
            result = Base64.getEncoder().encodeToString(bytes);
        }

        return result;
    }
}
