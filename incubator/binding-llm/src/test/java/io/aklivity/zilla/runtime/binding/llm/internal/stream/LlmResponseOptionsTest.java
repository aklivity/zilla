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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LlmResponseOptionsTest
{
    @Test
    public void shouldReportNothingWhenAbsent()
    {
        final LlmResponseOptions options = new LlmResponseOptions(2);

        assertNull(options.begin());
        assertNull(options.end());
    }

    @Test
    public void shouldDeliverDocumentMembersAtBegin()
    {
        final LlmResponseOptions options = new LlmResponseOptions(3);

        options.set(0, "1700000000");
        options.set(1, "default");

        assertArrayEquals(new String[] {"1700000000", "default", null}, options.begin());
    }

    @Test
    public void shouldDeliverLaterMembersAtEnd()
    {
        final LlmResponseOptions options = new LlmResponseOptions(3);

        options.set(0, "1700000000");
        options.begin();
        options.endDocument();

        options.set(2, "fp_abc");
        options.endDocument();

        assertArrayEquals(new String[] {null, null, "fp_abc"}, options.end());
    }

    @Test
    public void shouldNotRepeatDeliveredMembers()
    {
        final LlmResponseOptions options = new LlmResponseOptions(2);

        options.set(0, "1700000000");
        options.begin();
        options.endDocument();

        options.set(0, "1700000000");
        options.endDocument();

        assertNull(options.end());
    }

    @Test
    public void shouldDeliverChangedMembersAtEnd()
    {
        final LlmResponseOptions options = new LlmResponseOptions(2);

        options.set(1, "default");
        options.begin();
        options.endDocument();

        options.set(1, "scale");
        options.endDocument();

        assertArrayEquals(new String[] {null, "scale"}, options.end());
    }

    @Test
    public void shouldCarryKnownMembersAcrossDocuments()
    {
        final LlmResponseOptions options = new LlmResponseOptions(2);

        options.set(0, "1700000000");
        options.endDocument();

        assertArrayEquals(new String[] {"1700000000", null}, options.begin());
    }

    @Test
    public void shouldClear()
    {
        final LlmResponseOptions options = new LlmResponseOptions(2);

        options.set(0, "1700000000");
        options.begin();
        options.endDocument();
        options.clear();

        assertNull(options.begin());
    }
}
