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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

public final class A2aPushCapabilities
{
    private static final String CAPABILITIES_NAME = "capabilities";
    private static final String PUSH_NOTIFICATIONS_NAME = "pushNotifications";

    public static JsonObject advertise(
        JsonObject card)
    {
        final JsonObjectBuilder capabilities = Json.createObjectBuilder();
        if (card.get(CAPABILITIES_NAME) instanceof JsonObject existing)
        {
            existing.forEach((name, value) ->
            {
                if (!PUSH_NOTIFICATIONS_NAME.equals(name))
                {
                    capabilities.add(name, value);
                }
            });
        }
        capabilities.add(PUSH_NOTIFICATIONS_NAME, true);

        final JsonObjectBuilder advertised = Json.createObjectBuilder();
        card.forEach((name, value) ->
        {
            if (!CAPABILITIES_NAME.equals(name))
            {
                advertised.add(name, value);
            }
        });

        return advertised.add(CAPABILITIES_NAME, capabilities).build();
    }

    private A2aPushCapabilities()
    {
    }
}
