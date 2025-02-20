/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.model.core.internal;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ValidatorHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;

public class BooleanValidatorTest
{
    private final EngineContext context = mock(EngineContext.class);

    @Test
    public void shouldVerifyValidBooleanMessage()
    {
        BooleanValidatorHandler handler = new BooleanValidatorHandler(context);
        DirectBuffer data = new UnsafeBuffer();

        byte[] bytes = {1};

        data.wrap(bytes);
        assertTrue(handler.validate(0L, 0L, ValidatorHandler.FLAGS_COMPLETE, data, 0, data.capacity(), ValueConsumer.NOP));
    }
}
