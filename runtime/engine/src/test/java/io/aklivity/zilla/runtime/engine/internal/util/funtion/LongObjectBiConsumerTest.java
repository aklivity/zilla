/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.util.funtion;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.internal.util.function.LongObjectBiConsumer;

public class LongObjectBiConsumerTest
{
    @Test
    public void shouldHandleDefaultAndThen()
    {
        final long time = 1L;
        final String text = "Hello World";

        LongObjectBiConsumer<String> consumer1 = (t, txt) ->
        {
            assertEquals(time, t);
            assertEquals(txt, text);
        };

        LongObjectBiConsumer<String> consumer2 = (t, txt) ->
        {
            assertEquals(time, t);
            assertEquals(txt, text);
        };

        consumer1.andThen(consumer2).accept((Long) time, text);
    }
}
