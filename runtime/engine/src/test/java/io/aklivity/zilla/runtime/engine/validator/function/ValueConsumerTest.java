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
package io.aklivity.zilla.runtime.engine.validator.function;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ValueConsumerTest
{
    @Test
    public void shouldDefaultOnMessageAndClose()
    {
        ValueConsumer next = (buffer, index, length) ->
        {
            assertNotNull(buffer);
            assertTrue(index >= 0);
            assertTrue(length >= 0);
        };
    }

    @Test
    public void shouldCreateNoOpValueConsumer()
    {
        ValueConsumer next = ValueConsumer.NOP;
        assertNotNull(next);
    }
}
