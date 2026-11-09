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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.Test;

public class A2aPushCapabilitiesTest
{
    @Test
    public void shouldAdvertisePushNotifications()
    {
        final JsonObject card = json("{\"name\":\"a\",\"capabilities\":{\"streaming\":true,\"pushNotifications\":false}}");

        assertThat(A2aPushCapabilities.advertise(card),
            equalTo(json("{\"name\":\"a\",\"capabilities\":{\"streaming\":true,\"pushNotifications\":true}}")));
    }

    @Test
    public void shouldAdvertisePushNotificationsWithoutCapabilities()
    {
        assertThat(A2aPushCapabilities.advertise(json("{\"name\":\"a\"}")),
            equalTo(json("{\"name\":\"a\",\"capabilities\":{\"pushNotifications\":true}}")));
    }

    @Test
    public void shouldAdvertisePushNotificationsWithoutExistingMember()
    {
        assertThat(A2aPushCapabilities.advertise(json("{\"capabilities\":{\"streaming\":true},\"name\":\"a\"}")).toString(),
            equalTo("{\"capabilities\":{\"streaming\":true,\"pushNotifications\":true},\"name\":\"a\"}"));
    }

    private static JsonObject json(
        String text)
    {
        return Json.createReader(new StringReader(text)).readObject();
    }
}
