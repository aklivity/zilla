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
package io.aklivity.zilla.runtime.common.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Constructor;

import org.junit.jupiter.api.Test;

class JsonConfigExTest
{
    @Test
    void shouldAggregateCanonicalKeys() throws Exception
    {
        assertEquals(JsonParserEx.PATH_INCLUDES, JsonConfigEx.PATH_INCLUDES);
        assertEquals(JsonParserEx.PATH_EXCLUDES, JsonConfigEx.PATH_EXCLUDES);
        assertEquals(JsonParserEx.TOKEN_MAX_BYTES, JsonConfigEx.TOKEN_MAX_BYTES);
        assertEquals(JsonGeneratorEx.GENERATE_ESCAPED, JsonConfigEx.GENERATE_ESCAPED);

        Constructor<JsonConfigEx> constructor = JsonConfigEx.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        constructor.newInstance();
    }
}
