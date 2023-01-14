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
package io.aklivity.zilla.runtime.engine.internal.util.funtion;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.internal.util.function.LongObjectBiFunction;

public class LongObjectBiFunctionTest
{
    @Test
    public void shouldHandleDefault()
    {
        LongObjectBiFunction<String, String> function1 = (time, greeting) ->
        {
            assertEquals(1L, time);
            assertEquals("Hello World", greeting);

            return "Hello there!";
        };

        assertEquals("Hello there!", function1.apply((Long) 1L, "Hello World"));
    }
}
