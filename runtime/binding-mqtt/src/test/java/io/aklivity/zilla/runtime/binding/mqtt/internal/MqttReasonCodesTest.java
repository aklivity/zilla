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
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.NO_MATCHING_SUBSCRIBERS;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.SUCCESS;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.UNSPECIFIED_ERROR;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttReasonCodes.isError;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MqttReasonCodesTest
{
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
}
