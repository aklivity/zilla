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
package io.aklivity.zilla.specs.model.json.internal;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Constructor;

import org.junit.Test;

public class JsonFunctionsTest
{
    @Test
    public void shouldRepeat() throws Exception
    {
        assertEquals("vvv", JsonFunctions.repeat("v", 3));
    }

    @Test
    public void shouldResolveMapperPrefix() throws Exception
    {
        JsonFunctions.Mapper mapper = new JsonFunctions.Mapper();

        assertEquals("model_json", mapper.getPrefixName());
    }

    @Test
    public void shouldConstructUtilityClass() throws Exception
    {
        Constructor<JsonFunctions> constructor = JsonFunctions.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        constructor.newInstance();
    }
}
