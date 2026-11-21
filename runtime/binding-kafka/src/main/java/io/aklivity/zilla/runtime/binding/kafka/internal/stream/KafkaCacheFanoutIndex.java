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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntArrayList;

final class KafkaCacheFanoutIndex<M>
{
    private static final int[] NO_TERMS = new int[0];

    private final Map<M, Registration<M>> registrationsByMember = new HashMap<>();
    private final Int2ObjectHashMap<List<Registration<M>>> registrationsByTerm = new Int2ObjectHashMap<>();
    private final List<M> wildcards = new ArrayList<>();

    private long entry;

    void register(
        M member,
        IntArrayList terms)
    {
        final int[] distinct = distinct(terms);
        final Registration<M> registration = new Registration<>(member, distinct);

        registrationsByMember.put(member, registration);

        if (distinct.length == 0)
        {
            wildcards.add(member);
        }

        for (int term : distinct)
        {
            registrationsByTerm.computeIfAbsent(term, t -> new ArrayList<>()).add(registration);
        }
    }

    void unregister(
        M member)
    {
        final Registration<M> registration = registrationsByMember.remove(member);

        if (registration != null)
        {
            if (registration.terms.length == 0)
            {
                wildcards.remove(member);
            }

            for (int term : registration.terms)
            {
                final List<Registration<M>> registrations = registrationsByTerm.get(term);
                registrations.remove(registration);

                if (registrations.isEmpty())
                {
                    registrationsByTerm.remove(term);
                }
            }
        }
    }

    void clear()
    {
        registrationsByMember.clear();
        registrationsByTerm.clear();
        wildcards.clear();
    }

    boolean isEmpty()
    {
        return registrationsByMember.isEmpty();
    }

    boolean hasIndexed()
    {
        return !registrationsByTerm.isEmpty();
    }

    void forEachWildcard(
        Consumer<? super M> waker)
    {
        for (int index = wildcards.size() - 1; index >= 0; index--)
        {
            if (index < wildcards.size())
            {
                waker.accept(wildcards.get(index));
            }
        }
    }

    void beginEntry()
    {
        entry++;
    }

    void wake(
        int term,
        Consumer<? super M> waker)
    {
        final List<Registration<M>> registrations = registrationsByTerm.get(term);

        if (registrations != null)
        {
            for (int index = registrations.size() - 1; index >= 0; index--)
            {
                if (index < registrations.size())
                {
                    final Registration<M> registration = registrations.get(index);

                    if (registration.stamp != entry)
                    {
                        registration.stamp = entry;
                        waker.accept(registration.member);
                    }
                }
            }
        }
    }

    private static int[] distinct(
        IntArrayList terms)
    {
        int[] distinct = NO_TERMS;

        if (!terms.isEmpty())
        {
            final IntArrayList unique = new IntArrayList();
            for (int index = 0; index < terms.size(); index++)
            {
                final int term = terms.getInt(index);
                if (!unique.contains(term))
                {
                    unique.addInt(term);
                }
            }
            distinct = unique.toIntArray();
        }

        return distinct;
    }

    private static final class Registration<M>
    {
        private final M member;
        private final int[] terms;

        private long stamp;

        private Registration(
            M member,
            int[] terms)
        {
            this.member = member;
            this.terms = terms;
        }
    }
}
