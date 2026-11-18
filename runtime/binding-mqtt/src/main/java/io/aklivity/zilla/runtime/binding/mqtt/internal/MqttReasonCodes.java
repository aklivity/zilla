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
package io.aklivity.zilla.runtime.binding.mqtt.internal;

public final class MqttReasonCodes
{
    public static final int NO_CONNACK = -1;

    public static final byte SUCCESS = 0x00;

    public static final byte NORMAL_DISCONNECT = 0x00;

    public static final byte GRANTED_QOS_0 = 0x00;
    public static final byte GRANTED_QOS_1 = 0x01;
    public static final byte GRANTED_QOS_2 = 0x02;

    public static final byte DISCONNECT_WITH_WILL_MESSAGE = 0x04;

    public static final byte NO_MATCHING_SUBSCRIBERS = 0x10;
    public static final byte NO_SUBSCRIPTION_EXISTED = 0x11;

    public static final byte CONTINUE_AUTHENTICATION = 0x18;
    public static final byte REAUTHENTICATE = 0x19;

    public static final byte UNSPECIFIED_ERROR = (byte) 0x80;
    public static final byte MALFORMED_PACKET = (byte) 0x81;
    public static final byte PROTOCOL_ERROR = (byte) 0x82;
    public static final byte UNACCEPTABLE_PROTOCOL_VERSION_V4 = (byte) 0x01;
    public static final byte IDENTIFIER_REJECTED = (byte) 0x02;
    public static final byte SERVER_UNAVAILABLE_V4 = (byte) 0x03;
    public static final byte BAD_USER_NAME_OR_PASSWORD_V4 = (byte) 0x04;
    public static final byte NOT_AUTHORIZED_V4 = (byte) 0x05;
    public static final byte SUBACK_FAILURE_CODE_V4 = (byte) 0x80;
    public static final byte PACKET_TOO_LARGE = (byte) 0x95;
    public static final byte WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED = (byte) 0xa2;
    public static final byte SHARED_SUBSCRIPTION_NOT_SUPPORTED = (byte) 0x9e;
    public static final byte SUBSCRIPTION_IDS_NOT_SUPPORTED = (byte) 0xa1;
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
    public static final byte PAYLOAD_FORMAT_INVALID = (byte) 0x99;
    public static final byte QOS_NOT_SUPPORTED = (byte) 0x9b;
    public static final byte USE_ANOTHER_SERVER = (byte) 0x9c;
    public static final byte SERVER_MOVED = (byte) 0x9d;
    public static final byte QUOTA_EXCEEDED = (byte) 0x97;
    public static final byte MESSAGE_RATE_TOO_HIGH = (byte) 0x96;
    public static final byte ADMINISTRATIVE_ACTION = (byte) 0x98;
    public static final byte MAXIMUM_CONNECT_TIME = (byte) 0xa0;
    public static final byte CONNECTION_RATE_EXCEEDED = (byte) 0x9f;

    public static final byte PACKET_IDENTIFIER_IN_USE = (byte) 0x91;
    public static final byte PACKET_IDENTIFIER_NOT_FOUND = (byte) 0x92;
    public static final byte RECEIVE_MAXIMUM_EXCEEDED = (byte) 0x93;
    public static final byte TOPIC_ALIAS_INVALID = (byte) 0x94;

    public static final byte RETAIN_NOT_SUPPORTED = (byte) 0x9A;

    public static boolean acknowledgesPublish(
        int reasonCode)
    {
        boolean acknowledges;

        switch ((byte) reasonCode)
        {
        case UNSPECIFIED_ERROR:
        case IMPLEMENTATION_SPECIFIC_ERROR:
        case NOT_AUTHORIZED:
        case TOPIC_NAME_INVALID:
        case PACKET_IDENTIFIER_IN_USE:
        case QUOTA_EXCEEDED:
        case PAYLOAD_FORMAT_INVALID:
            acknowledges = true;
            break;
        default:
            acknowledges = false;
            break;
        }

        return acknowledges;
    }

    public static boolean isConnackReasonCode(
        int reasonCode)
    {
        final int code = reasonCode & 0xff;

        return code == SUCCESS ||
            code >= (UNSPECIFIED_ERROR & 0xff) && code <= (BANNED & 0xff) ||
            code == (BAD_AUTHENTICATION_METHOD & 0xff) ||
            code == (TOPIC_NAME_INVALID & 0xff) ||
            code == (PACKET_TOO_LARGE & 0xff) ||
            code == (QUOTA_EXCEEDED & 0xff) ||
            code == (PAYLOAD_FORMAT_INVALID & 0xff) ||
            code == (RETAIN_NOT_SUPPORTED & 0xff) ||
            code == (QOS_NOT_SUPPORTED & 0xff) ||
            code == (USE_ANOTHER_SERVER & 0xff) ||
            code == (SERVER_MOVED & 0xff) ||
            code == (CONNECTION_RATE_EXCEEDED & 0xff);
    }

    private MqttReasonCodes()
    {
        // Utility
    }

    public static int connackReasonCodeV4(
        int reasonCode)
    {
        final int code = reasonCode & 0xff;

        final int connackReasonCode;
        switch (code)
        {
        case 0x00:
        case 0x01:
        case 0x02:
        case 0x03:
        case 0x04:
        case 0x05:
            connackReasonCode = code;
            break;
        case 0x84:
            connackReasonCode = UNACCEPTABLE_PROTOCOL_VERSION_V4;
            break;
        case 0x85:
            connackReasonCode = IDENTIFIER_REJECTED;
            break;
        case 0x86:
            connackReasonCode = BAD_USER_NAME_OR_PASSWORD_V4;
            break;
        case 0x87:
            connackReasonCode = NOT_AUTHORIZED_V4;
            break;
        case 0x88:
        case 0x89:
        case 0x9d:
            connackReasonCode = SERVER_UNAVAILABLE_V4;
            break;
        default:
            connackReasonCode = NO_CONNACK;
            break;
        }

        return connackReasonCode;
    }

    public static boolean isDisconnectReasonCode(
        int reasonCode)
    {
        final int code = reasonCode & 0xff;

        return code == NORMAL_DISCONNECT ||
            code == DISCONNECT_WITH_WILL_MESSAGE ||
            code >= (UNSPECIFIED_ERROR & 0xff) && code <= (IMPLEMENTATION_SPECIFIC_ERROR & 0xff) ||
            code == (NOT_AUTHORIZED & 0xff) ||
            code == (SERVER_BUSY & 0xff) ||
            code == (SERVER_SHUTTING_DOWN & 0xff) ||
            code == (KEEP_ALIVE_TIMEOUT & 0xff) ||
            code == (SESSION_TAKEN_OVER & 0xff) ||
            code == (TOPIC_FILTER_INVALID & 0xff) ||
            code == (TOPIC_NAME_INVALID & 0xff) ||
            code >= (RECEIVE_MAXIMUM_EXCEEDED & 0xff) && code <= (PACKET_TOO_LARGE & 0xff) ||
            code >= (MESSAGE_RATE_TOO_HIGH & 0xff) && code <= (QUOTA_EXCEEDED & 0xff) ||
            code == (ADMINISTRATIVE_ACTION & 0xff) ||
            code == (PAYLOAD_FORMAT_INVALID & 0xff) ||
            code >= (RETAIN_NOT_SUPPORTED & 0xff) && code <= (CONNECTION_RATE_EXCEEDED & 0xff) ||
            code >= (MAXIMUM_CONNECT_TIME & 0xff) && code <= (WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED & 0xff);
    }

    public static boolean isError(
        int reasonCode)
    {
        return (reasonCode & 0xff) >= (UNSPECIFIED_ERROR & 0xff);
    }
}
