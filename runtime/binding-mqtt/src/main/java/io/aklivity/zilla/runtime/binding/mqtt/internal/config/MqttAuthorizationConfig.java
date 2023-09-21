/*
 * Copyright 2021-2023 Aklivity Inc.
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

package io.aklivity.zilla.runtime.binding.mqtt.internal.config;

import java.util.List;

public final class MqttAuthorizationConfig
{
    public final String name;
    public final MqttCredentialsConfig credentials;

    public MqttAuthorizationConfig(
        String name,
        MqttCredentialsConfig credentials)
    {
        this.name = name;
        this.credentials = credentials;
    }

    public static final class MqttCredentialsConfig
    {
        public final List<MqttPatternConfig> connect;

        public MqttCredentialsConfig(
            List<MqttPatternConfig> connect)
        {
            this.connect = connect;
        }
    }

    public enum MqttConnectProperty
    {
        USERNAME,
        PASSWORD;

        public static MqttConnectProperty ofName(
            String value)
        {
            MqttConnectProperty field = null;
            switch (value)
            {
            case "username":
                field = USERNAME;
                break;
            case "password":
                field = PASSWORD;
                break;
            }
            return field;
        }
    }

    public static final class MqttPatternConfig
    {
        public final MqttConnectProperty property;
        public final String pattern;

        public MqttPatternConfig(
            MqttConnectProperty property,
            String pattern)
        {
            this.property = property;
            this.pattern = pattern;
        }
    }
}

