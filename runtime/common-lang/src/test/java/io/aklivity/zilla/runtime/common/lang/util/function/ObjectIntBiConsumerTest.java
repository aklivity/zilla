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
package io.aklivity.zilla.runtime.common.lang.util.function;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class ObjectIntBiConsumerTest
{
    @Test
    public void shouldHandleDefault()
    {
        ObjectIntBiConsumer<String> consumer = (text, value) ->
        {
            assertEquals("Hello World", text);
            assertEquals(1, value);
        };

        consumer.accept("Hello World", (Integer) 1);
    }

    @Test
    public void shouldInvokeBeforeAndThenAfter()
    {
        List<String> invoked = new ArrayList<>();

        ObjectIntBiConsumer<String> before = (text, value) -> invoked.add("before:" + text + value);
        ObjectIntBiConsumer<String> after = (text, value) -> invoked.add("after:" + text + value);

        before.andThen(after).accept("Hello World", 1);

        assertEquals(List.of("before:Hello World1", "after:Hello World1"), invoked);
    }
}
