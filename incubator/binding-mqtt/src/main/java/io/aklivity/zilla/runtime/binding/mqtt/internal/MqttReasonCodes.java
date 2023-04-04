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
package io.aklivity.zilla.runtime.binding.mqtt.internal;

public final class MqttReasonCodes
{
    public static final byte SUCCESS = 0x00;

    public static final byte NORMAL_DISCONNECT = 0x00;

    public static final byte GRANTED_QOS_1 = 0x00;
    public static final byte GRANTED_QOS_2 = 0x01;
    public static final byte GRANTED_QOS_3 = 0x02;

    public static final byte DISCONNECT_WITH_WILL_MESSAGE = 0x04;

    public static final byte NO_MATCHING_SUBSCRIBERS = 0x10;
    public static final byte NO_SUBSCRIPTION_EXISTED = 0x11;

    public static final byte CONTINUE_AUTHENTICATION = 0x18;
    public static final byte REAUTHENTICATE = 0x19;

    public static final byte UNSPECIFIED_ERROR = (byte) 0x80;
    public static final byte MALFORMED_PACKET = (byte) 0x81;
    public static final byte PROTOCOL_ERROR = (byte) 0x82;
    public static final byte WILDCARDS_NOT_SUPPORTED = (byte) 0xa2;
    public static final byte IMPLEMENTATION_SPECIFIC_ERROR = (byte) 0x83;
    public static final byte UNSUPPORTED_PROTOCOL_VERSION = (byte) 0x84;
    public static final byte CLIENT_IDENTIFIER_NOT_VALID = (byte) 0x85;
    public static final byte BAD_USER_NAME_OR_PASSWORD = (byte) 0x86;
    public static final byte NOT_AUTHORIZED = (byte) 0x87;
    public static final byte SERVER_UNAVAILABLE = (byte) 0x88;
    public static final byte SERVER_BUSY = (byte) 0x89;
    public static final byte BANNED = (byte) 0x8A;
    public static final byte SERVER_SHUTTING_DOWN = (byte) 0x8B;
    public static final byte BAD_AUTHENTICATION_METHOD = (byte) 0x8C;

    public static final byte KEEP_ALIVE_TIMEOUT = (byte) 0x8D;
    public static final byte SESSION_TAKEN_OVER = (byte) 0x8E;

    public static final byte TOPIC_FILTER_INVALID = (byte) 0x8F;
    public static final byte TOPIC_NAME_INVALID = (byte) 0x90;

    public static final byte PACKET_IDENTIFIER_IN_USE = (byte) 0x91;
    public static final byte PACKET_IDENTIFIER_NOT_FOUND = (byte) 0x92;
    public static final byte RECEIVE_MAXIMUM_EXCEEDED = (byte) 0x93;
    public static final byte TOPIC_ALIAS_INVALID = (byte) 0x94;

    public static final byte RETAIN_NOT_SUPPORTED = (byte) 0x9A;

    private MqttReasonCodes()
    {
        // Utility
    }
}
