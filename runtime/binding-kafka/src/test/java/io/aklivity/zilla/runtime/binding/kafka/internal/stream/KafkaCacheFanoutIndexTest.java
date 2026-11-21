/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.agrona.collections.IntArrayList;
import org.junit.Test;

public class KafkaCacheFanoutIndexTest
{
    private final KafkaCacheFanoutIndex<String> index = new KafkaCacheFanoutIndex<>();
    private final List<String> woken = new ArrayList<>();

    @Test
    public void shouldBeEmptyInitially()
    {
        assertTrue(index.isEmpty());
        assertFalse(index.hasIndexed());
    }

    @Test
    public void shouldWakeMemberRegisteredUnderTerm()
    {
        index.register("a", terms(1));
        index.register("b", terms(2));

        index.beginEntry();
        index.wake(1, woken::add);

        assertEquals(List.of("a"), woken);
        assertTrue(index.hasIndexed());
        assertFalse(index.isEmpty());
    }

    @Test
    public void shouldNotWakeForUnknownTerm()
    {
        index.register("a", terms(1));

        index.beginEntry();
        index.wake(3, woken::add);

        assertTrue(woken.isEmpty());
    }

    @Test
    public void shouldWakeEveryMemberSharingTerm()
    {
        index.register("a", terms(1));
        index.register("b", terms(1));

        index.beginEntry();
        index.wake(1, woken::add);

        assertEquals(2, woken.size());
        assertTrue(woken.containsAll(List.of("a", "b")));
    }

    @Test
    public void shouldWakeMemberOncePerEntryAcrossTerms()
    {
        index.register("a", terms(1, 2));

        index.beginEntry();
        index.wake(1, woken::add);
        index.wake(2, woken::add);

        assertEquals(List.of("a"), woken);
    }

    @Test
    public void shouldWakeAgainOnNextEntry()
    {
        index.register("a", terms(1));

        index.beginEntry();
        index.wake(1, woken::add);
        index.beginEntry();
        index.wake(1, woken::add);

        assertEquals(List.of("a", "a"), woken);
    }

    @Test
    public void shouldKeepWildcardOutOfTermWakes()
    {
        index.register("w", terms());
        index.register("a", terms(1));

        index.beginEntry();
        index.wake(1, woken::add);

        assertEquals(List.of("a"), woken);
    }

    @Test
    public void shouldListWildcardMembers()
    {
        index.register("w", terms());
        index.register("a", terms(1));
        index.register("x", terms());

        index.forEachWildcard(woken::add);

        assertEquals(2, woken.size());
        assertTrue(woken.containsAll(List.of("w", "x")));
        assertFalse(index.isEmpty());
    }

    @Test
    public void shouldUnregisterFromAllTerms()
    {
        index.register("a", terms(1, 2));
        index.unregister("a");

        index.beginEntry();
        index.wake(1, woken::add);
        index.wake(2, woken::add);

        assertTrue(woken.isEmpty());
        assertTrue(index.isEmpty());
        assertFalse(index.hasIndexed());
    }

    @Test
    public void shouldUnregisterWildcard()
    {
        index.register("w", terms());
        index.unregister("w");

        index.forEachWildcard(woken::add);

        assertTrue(woken.isEmpty());
        assertTrue(index.isEmpty());
    }

    @Test
    public void shouldClear()
    {
        index.register("a", terms(1));
        index.register("w", terms());
        index.clear();

        index.beginEntry();
        index.wake(1, woken::add);
        index.forEachWildcard(woken::add);

        assertTrue(woken.isEmpty());
        assertTrue(index.isEmpty());
    }

    @Test
    public void shouldWakeRemainingMembersWhenWokenMemberUnregistersAnother()
    {
        index.register("a", terms(1));
        index.register("b", terms(1));
        index.register("c", terms(1));

        index.beginEntry();
        index.wake(1, member ->
        {
            woken.add(member);
            if (member.equals("c"))
            {
                index.unregister("a");
            }
        });

        assertTrue(woken.contains("c"));
        assertTrue(woken.contains("b"));
        assertEquals(woken.size(), woken.stream().distinct().count());
    }

    @Test
    public void shouldWakeRemainingMembersWhenWokenMemberUnregistersItself()
    {
        index.register("a", terms(1));
        index.register("b", terms(1));
        index.register("c", terms(1));

        index.beginEntry();
        index.wake(1, member ->
        {
            woken.add(member);
            index.unregister(member);
        });

        assertEquals(3, woken.size());
        assertTrue(woken.containsAll(List.of("a", "b", "c")));
        assertTrue(index.isEmpty());
    }

    private static IntArrayList terms(
        int... values)
    {
        final IntArrayList terms = new IntArrayList();
        for (int value : values)
        {
            terms.addInt(value);
        }
        return terms;
    }
}
