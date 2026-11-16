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

import io.aklivity.zilla.runtime.binding.llm.internal.dialect.LlmDialectNames;

final class LlmMemberKeys
{
    static final int DROP = 0;
    static final int COPY = 1;
    static final int JUDGE = 2;
    static final int SHARED = 3;

    private static final char DIALECT_END = ':';
    private static final String SHARED_DIALECT = "x";

    private final String dialect;
    private final KeyView target = new KeyView();

    LlmMemberKeys(
        String dialect)
    {
        this.dialect = dialect;
    }

    int select(
        CharSequence key)
    {
        return select(key, false);
    }

    int selectShared(
        CharSequence key)
    {
        return select(key, true);
    }

    private int select(
        CharSequence key,
        boolean shared)
    {
        final int length = key.length();
        int colon = 0;

        while (colon < length && key.charAt(colon) != DIALECT_END)
        {
            colon++;
        }

        int action = COPY;

        if (colon == length)
        {
            target.wrap(key, 0, length);

            if (LlmCacheBreakpoint.MEMBER.contentEquals(key))
            {
                action = DROP;
            }
        }
        else if (colon == SHARED_DIALECT.length() && SHARED_DIALECT.charAt(0) == key.charAt(0))
        {
            action = shared ? SHARED : DROP;

            if (shared)
            {
                target.wrap(key, colon + 1, length);
            }
        }
        else if (LlmDialectNames.admits(key, 0, colon, dialect))
        {
            target.wrap(key, colon + 1, length);
        }
        else if (LlmBehaviour.listed(key))
        {
            action = JUDGE;
        }
        else
        {
            action = DROP;
        }

        return action;
    }

    int selectPrefixed(
        CharSequence key)
    {
        boolean prefixed = false;

        for (int index = 0; !prefixed && index < key.length(); index++)
        {
            prefixed = key.charAt(index) == DIALECT_END;
        }

        return prefixed ? select(key) : DROP;
    }

    int copyAll(
        CharSequence key)
    {
        target.wrap(key, 0, key.length());

        return COPY;
    }

    CharSequence target()
    {
        return target;
    }

    private static final class KeyView implements CharSequence
    {
        private CharSequence key;
        private int from;
        private int to;

        void wrap(
            CharSequence key,
            int from,
            int to)
        {
            this.key = key;
            this.from = from;
            this.to = to;
        }

        @Override
        public int length()
        {
            return to - from;
        }

        @Override
        public char charAt(
            int index)
        {
            return key.charAt(from + index);
        }

        @Override
        public CharSequence subSequence(
            int start,
            int end)
        {
            return key.subSequence(from + start, from + end);
        }

        @Override
        public String toString()
        {
            return key.subSequence(from, to).toString();
        }
    }
}
