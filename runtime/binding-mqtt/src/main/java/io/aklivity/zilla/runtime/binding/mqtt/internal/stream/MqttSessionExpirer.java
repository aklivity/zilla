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
package io.aklivity.zilla.runtime.binding.mqtt.internal.stream;

import static io.aklivity.zilla.runtime.binding.mqtt.internal.stream.MqttClientStates.OWNER_KEY_POSTFIX;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.stream.MqttClientStates.STATE_KEY_POSTFIX;

import java.io.Closeable;
import java.util.List;

import io.aklivity.zilla.runtime.engine.store.StoreHandler;

final class MqttSessionExpirer
{
    private final StoreHandler store;
    private final MqttSharedRegistry registry;
    private final MqttClientStates states = new MqttClientStates();

    MqttSessionExpirer(
        StoreHandler store,
        MqttSharedRegistry registry)
    {
        this.store = store;
        this.registry = registry;
    }

    Closeable attach()
    {
        return store.expire("", this::onExpired);
    }

    private void onExpired(
        String key,
        String value)
    {
        if (key.endsWith(STATE_KEY_POSTFIX))
        {
            final String clientId = key.substring(0, key.length() - STATE_KEY_POSTFIX.length());
            final MqttClientStateRecord record = states.decode(value);

            if (record != null && !record.memberships.isEmpty())
            {
                store.get(clientId + OWNER_KEY_POSTFIX, (ownerKey, owner) ->
                {
                    if (owner == null)
                    {
                        releaseMemberships(clientId, record.memberships, 0);
                    }
                });
            }
        }
    }

    private void releaseMemberships(
        String clientId,
        List<MqttMembershipRecord> memberships,
        int index)
    {
        if (index < memberships.size())
        {
            final MqttMembershipRecord membership = memberships.get(index);
            final String key = MqttSharedRegistry.key(membership.shareName, membership.filter);
            final Runnable next = () -> releaseMemberships(clientId, memberships, index + 1);

            if (membership.token != null)
            {
                registry.release(key, MqttSharedRegistry.memberKey(membership.shareName, membership.filter, clientId),
                    membership.token, next);
            }
            else
            {
                registry.leave(key, next);
            }
        }
    }
}
