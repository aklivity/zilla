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
package io.aklivity.zilla.runtime.common.lang;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

public class MatchersTest
{
    @Test
    public void shouldMatchLiteralGlob()
    {
        Pattern pattern = Matchers.glob("produce");

        assertTrue(pattern.matcher("produce").matches());
        assertFalse(pattern.matcher("consume").matches());
    }

    @Test
    public void shouldMatchGlobByPrefix()
    {
        Pattern pattern = Matchers.glob("orders*");

        assertTrue(pattern.matcher("orders").matches());
        assertTrue(pattern.matcher("orders-eu").matches());
        assertFalse(pattern.matcher("shipments").matches());
    }

    @Test
    public void shouldMatchAnyByBareWildcard()
    {
        Pattern pattern = Matchers.glob("*");

        assertTrue(pattern.matcher("orders").matches());
        assertTrue(pattern.matcher("").matches());
    }

    @Test
    public void shouldEscapeRegexMetacharactersInLiteralSegments()
    {
        Pattern pattern = Matchers.glob("orders.eu");

        assertTrue(pattern.matcher("orders.eu").matches());
        assertFalse(pattern.matcher("ordersXeu").matches());
    }

    @Test
    public void shouldMatchGlobWithMultipleWildcards()
    {
        Pattern pattern = Matchers.glob("get_*_by_*");

        assertTrue(pattern.matcher("get_order_by_id").matches());
        assertFalse(pattern.matcher("get_order").matches());
    }

    @Test
    public void shouldReturnNullWhenCompilingNullGlobList()
    {
        assertNull(Matchers.globAll(null));
    }

    @Test
    public void shouldCompileEachGlobInList()
    {
        List<Pattern> patterns = Matchers.globAll(asList("orders*", "shipments"));

        assertTrue(patterns.get(0).matcher("orders-eu").matches());
        assertTrue(patterns.get(1).matcher("shipments").matches());
    }

    @Test
    public void shouldAdmitAnyNameWhenAllowListNull()
    {
        assertTrue(Matchers.admits(null, "orders"));
        assertTrue(Matchers.admits(null, "anything"));
    }

    @Test
    public void shouldNotAdmitAnyNameWhenAllowListEmpty()
    {
        assertFalse(Matchers.admits(List.of(), "orders"));
    }

    @Test
    public void shouldAdmitNameWithinAllowList()
    {
        List<Pattern> allow = Matchers.globAll(asList("orders", "shipments"));

        assertTrue(Matchers.admits(allow, "orders"));
        assertTrue(Matchers.admits(allow, "shipments"));
        assertFalse(Matchers.admits(allow, "payments"));
    }

    @Test
    public void shouldAdmitNameMatchingGlobWithinAllowList()
    {
        List<Pattern> allow = Matchers.globAll(asList("orders*"));

        assertTrue(Matchers.admits(allow, "orders"));
        assertTrue(Matchers.admits(allow, "orders-eu"));
        assertFalse(Matchers.admits(allow, "shipments"));
    }
}
