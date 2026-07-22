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
package io.aklivity.zilla.config.binding.mqtt;

public enum MqttVersion
{
    V3_1_1("v3.1.1", 4),
    V_5("v5", 5);

    private final String specification;
    private final int protocol;

    MqttVersion(
        String specification,
        int protocol)
    {
        this.specification = specification;
        this.protocol = protocol;
    }

    public String specification()
    {
        return specification;
    }

    public int protocol()
    {
        return protocol;
    }

    public static MqttVersion ofSpecification(
        String specification)
    {
        MqttVersion version = null;
        for (MqttVersion v : values())
        {
            if (v.specification().equals(specification))
            {
                version = v;
                break;
            }
        }
        return version;
    }

    public static MqttVersion ofProtocol(
        int protocol)
    {
        MqttVersion version = null;
        for (MqttVersion v : values())
        {
            if (v.protocol() == protocol)
            {
                version = v;
                break;
            }
        }
        return version;
    }
}
