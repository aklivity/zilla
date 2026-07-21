/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.http.internal.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

public class HttpBindingConfigTest
{
    private static final int ADVERSARIAL_GROUP_COUNT = 10_000;
    private static final long LINEAR_TIME_BUDGET_MILLIS = 1000L;

    private final Pattern basicFormatPattern = basicFormatPattern();

    @Test
    public void shouldMatchUsernamePasswordFormat()
    {
        Matcher matcher = basicFormatPattern.matcher("Basic {username}:{password}");

        assertTrue(matcher.matches());
        assertEquals("{username}:{password}", matcher.group("format"));
    }

    @Test
    public void shouldNotMatchWithoutBasicPrefix()
    {
        Matcher matcher = basicFormatPattern.matcher("{username}:{password}");

        assertFalse(matcher.matches());
    }

    @Test
    public void shouldNotHangOnManyBraceGroups()
    {
        String adversarial = adversarialInput(ADVERSARIAL_GROUP_COUNT);

        long start = System.nanoTime();
        boolean matched = basicFormatPattern.matcher(adversarial).matches();
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertFalse(matched);
        assertTrue("expected linear-time match, took " + elapsedMillis + "ms", elapsedMillis < LINEAR_TIME_BUDGET_MILLIS);
    }

    private static String adversarialInput(
        int groupCount)
    {
        StringBuilder adversarial = new StringBuilder("Basic {|}");
        for (int i = 0; i < groupCount; i++)
        {
            adversarial.append(":{|}");
        }
        adversarial.append('\\');
        return adversarial.toString();
    }

    private static Pattern basicFormatPattern()
    {
        Pattern pattern;
        try
        {
            Field field = HttpBindingConfig.class.getDeclaredField("BASIC_FORMAT_PATTERN");
            field.setAccessible(true);
            pattern = (Pattern) field.get(null);
        }
        catch (ReflectiveOperationException ex)
        {
            throw new IllegalStateException(ex);
        }
        return pattern;
    }
}
