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
package io.aklivity.zilla.specs.model.core.internal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ModelCoreFunctionsTest
{
    @Test
    public void shouldRepeatText()
    {
        String repeated = ModelCoreFunctions.repeat("v", 100_000);

        assertEquals(100_000, repeated.length());
        assertEquals(0, repeated.chars().filter(c -> c != 'v').count());
    }

    @Test
    public void shouldResolvePrefixName()
    {
        ModelCoreFunctions.Mapper mapper = new ModelCoreFunctions.Mapper();

        assertEquals("model_core", mapper.getPrefixName());
    }
}
