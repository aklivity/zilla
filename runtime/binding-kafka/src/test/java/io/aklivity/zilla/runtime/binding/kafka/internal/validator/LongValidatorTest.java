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
package io.aklivity.zilla.runtime.binding.kafka.internal.validator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class LongValidatorTest
{
    private final LongValidator validator = new LongValidator();

    @Test
    public void shouldVerifyValidLong()
    {
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0, 0, 0, 0, 0, 0, 0, 42};
        data.wrap(bytes, 0, bytes.length);
        assertTrue(validator.validate(data, 0, data.capacity()));
    }

    @Test
    public void shouldVerifyInvalidLong()
    {
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0, 0, 0, 42};
        data.wrap(bytes, 0, bytes.length);
        assertFalse(validator.validate(data, 0, data.capacity()));
    }
}
