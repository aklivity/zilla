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
package io.aklivity.zilla.runtime.engine.validator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.internal.validator.IntegerValidator;
import io.aklivity.zilla.runtime.engine.internal.validator.config.IntegerValidatorConfig;

public class IntegerValidatorTest
{
    private final IntegerValidatorConfig config = new IntegerValidatorConfig();
    private final IntegerValidator validator = new IntegerValidator(config);

    @Test
    public void shouldVerifyValidInteger()
    {
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {0, 0, 0, 42};
        data.wrap(bytes, 0, bytes.length);
        assertTrue(validator.read(data, 0, data.capacity()));
    }

    @Test
    public void shouldVerifyInvalidInteger()
    {
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = "Not an Integer".getBytes();
        data.wrap(bytes, 0, bytes.length);
        assertFalse(validator.write(data, 0, data.capacity()));
    }
}
