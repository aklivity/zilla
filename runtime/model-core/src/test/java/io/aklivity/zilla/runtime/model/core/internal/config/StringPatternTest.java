/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.model.core.internal.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.regex.Pattern;

import org.junit.Test;

import io.aklivity.zilla.runtime.model.core.config.StringPattern;

public class StringPatternTest
{
    @Test
    public void shouldVerifyStringPatternOf()
    {
        assertEquals(StringPattern.EMAIL.pattern, StringPattern.of("email"));
        assertEquals(StringPattern.DATE.pattern, StringPattern.of("date"));
        assertEquals(StringPattern.DATE_TIME.pattern, StringPattern.of("date-time"));
    }

    @Test
    public void shouldValidateEmailPattern()
    {
        Pattern pattern = Pattern.compile(StringPattern.of("email"));
        assertTrue(pattern.matcher("abc@test.com").matches());
        assertFalse(pattern.matcher("abc@test.123").matches());
        assertTrue(pattern.matcher("1.2-3_.abc@test.in").matches());
        assertFalse(pattern.matcher("abc@abc@test.123").matches());
    }

    @Test
    public void shouldValidateDatePattern()
    {
        Pattern pattern = Pattern.compile(StringPattern.of("date"));
        assertTrue(pattern.matcher("2017-07-02").matches());
        assertFalse(pattern.matcher("2017-07-2").matches());
        assertFalse(pattern.matcher("2017-17-20").matches());
        assertFalse(pattern.matcher("2017-07-35").matches());
    }

    @Test
    public void shouldValidateDateTimePattern()
    {
        Pattern pattern = Pattern.compile(StringPattern.of("date-time"));
        assertTrue(pattern.matcher("2017-07-21T17:32:28Z").matches());
        assertFalse(pattern.matcher("2017-07-21T17:32:28").matches());
        assertFalse(pattern.matcher("2017-07-21T17:32Z").matches());
        assertFalse(pattern.matcher("2017-07-21T17:2:28A").matches());
    }
}
