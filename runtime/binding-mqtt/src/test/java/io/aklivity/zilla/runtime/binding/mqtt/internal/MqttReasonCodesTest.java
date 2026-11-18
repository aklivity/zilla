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

import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.GRANTED_QOS_2;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.IMPLEMENTATION_SPECIFIC_ERROR;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.NOT_AUTHORIZED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.NO_CONNACK;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.NO_MATCHING_SUBSCRIBERS;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.PACKET_IDENTIFIER_IN_USE;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.PAYLOAD_FORMAT_INVALID;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.QUOTA_EXCEEDED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.RETAIN_NOT_SUPPORTED;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SUCCESS;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.TOPIC_NAME_INVALID;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.UNSPECIFIED_ERROR;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.acknowledgesPublish;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.connackReasonCodeV4;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.isConnackReasonCode;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.isError;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MqttReasonCodesTest
{
    @Test
    public void shouldAcknowledgePublishFailureReasonCodes()
    {
        assertTrue(acknowledgesPublish(UNSPECIFIED_ERROR));
        assertTrue(acknowledgesPublish(IMPLEMENTATION_SPECIFIC_ERROR));
        assertTrue(acknowledgesPublish(NOT_AUTHORIZED));
        assertTrue(acknowledgesPublish(TOPIC_NAME_INVALID));
        assertTrue(acknowledgesPublish(PACKET_IDENTIFIER_IN_USE));
        assertTrue(acknowledgesPublish(QUOTA_EXCEEDED));
        assertTrue(acknowledgesPublish(PAYLOAD_FORMAT_INVALID));
    }

    @Test
    public void shouldNotAcknowledgePublishWithOtherReasonCodes()
    {
        assertFalse(acknowledgesPublish(SUCCESS));
        assertFalse(acknowledgesPublish(RETAIN_NOT_SUPPORTED));
        assertFalse(acknowledgesPublish(0x9b));
    }

    @Test
    public void shouldNotRecognizeSuccessAsError()
    {
        assertFalse(isError(SUCCESS));
        assertFalse(isError(GRANTED_QOS_2));
        assertFalse(isError(NO_MATCHING_SUBSCRIBERS));
    }

    @Test
    public void shouldRecognizeCodesFromUnspecifiedErrorAsError()
    {
        assertTrue(isError(UNSPECIFIED_ERROR));
        assertTrue(isError(IMPLEMENTATION_SPECIFIC_ERROR));
        assertTrue(isError(0xff));
    }

    @Test
    public void shouldKeepConnackReturnCodesForV4()
    {
        for (int code = 0x00; code <= 0x05; code++)
        {
            assertEquals(code, connackReasonCodeV4(code));
        }
    }

    @Test
    public void shouldMapConnackReasonCodesToReturnCodesForV4()
    {
        assertEquals(0x01, connackReasonCodeV4(0x84));
        assertEquals(0x04, connackReasonCodeV4(0x86));
        assertEquals(0x05, connackReasonCodeV4(0x87));
        assertEquals(0x03, connackReasonCodeV4(0x88));
        assertEquals(0x03, connackReasonCodeV4(0x89));
        assertEquals(0x03, connackReasonCodeV4(0x9d));
    }

    @Test
    public void shouldNotMapOtherReasonCodesForV4()
    {
        assertEquals(NO_CONNACK, connackReasonCodeV4(0x06));
        assertEquals(NO_CONNACK, connackReasonCodeV4(0x81));
        assertEquals(NO_CONNACK, connackReasonCodeV4(0x82));
        assertEquals(NO_CONNACK, connackReasonCodeV4(0x8d));
        assertEquals(NO_CONNACK, connackReasonCodeV4(0x8e));
        assertEquals(NO_CONNACK, connackReasonCodeV4(0x9b));
    }

    @Test
    public void shouldRecognizeConnackReasonCodesForV5()
    {
        assertTrue(isConnackReasonCode(SUCCESS));
        assertTrue(isConnackReasonCode(0x89));
        assertTrue(isConnackReasonCode(0x9b));
        assertTrue(isConnackReasonCode(0x9d));
        assertFalse(isConnackReasonCode(0x8d));
        assertFalse(isConnackReasonCode(0x8e));
    }
}
