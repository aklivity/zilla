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

package io.aklivity.zilla.runtime.binding.a2a.internal.push;

import jakarta.json.JsonObject;

public record A2aPushSeal(
    String agent,
    String taskId,
    String url,
    String token,
    JsonObject authentication,
    long expiry,
    String version,
    String nonce)
{
    public boolean expired(
        long nowSeconds)
    {
        return nowSeconds > expiry;
    }
}
