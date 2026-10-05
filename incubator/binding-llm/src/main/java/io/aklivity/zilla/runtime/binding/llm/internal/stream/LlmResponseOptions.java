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

final class LlmResponseOptions
{
    private final String[] document;
    private final String[] known;
    private final String[] delivered;
    private final String[] effective;

    LlmResponseOptions(
        int count)
    {
        this.document = new String[count];
        this.known = new String[count];
        this.delivered = new String[count];
        this.effective = new String[count];
    }

    void set(
        int slot,
        String value)
    {
        document[slot] = value;
    }

    void endDocument()
    {
        for (int slot = 0; slot < document.length; slot++)
        {
            if (document[slot] != null)
            {
                known[slot] = document[slot];
                document[slot] = null;
            }
        }
    }

    void clear()
    {
        for (int slot = 0; slot < document.length; slot++)
        {
            document[slot] = null;
            known[slot] = null;
            delivered[slot] = null;
        }
    }

    String[] begin()
    {
        boolean present = false;

        for (int slot = 0; slot < effective.length; slot++)
        {
            effective[slot] = document[slot] != null ? document[slot] : known[slot];
            delivered[slot] = effective[slot];
            present |= effective[slot] != null;
        }

        return present ? effective : null;
    }

    String[] end()
    {
        boolean changed = false;

        for (int slot = 0; slot < effective.length; slot++)
        {
            final String current = document[slot] != null ? document[slot] : known[slot];
            final boolean differs = current != null && !current.equals(delivered[slot]);

            effective[slot] = differs ? current : null;
            changed |= differs;
        }

        return changed ? effective : null;
    }
}
