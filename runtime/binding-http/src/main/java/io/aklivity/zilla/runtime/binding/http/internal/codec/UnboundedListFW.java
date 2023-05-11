/*
 * Copyright 2021-2023 Aklivity Inc.
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
// TODO: license
package io.aklivity.zilla.runtime.binding.http.internal.codec;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.binding.http.internal.types.Flyweight;

public final class UnboundedListFW<T extends Flyweight> extends Flyweight
{
    private final T itemRO;

    private int limit;

    public UnboundedListFW(T itemRO)
    {
        this.itemRO = Objects.requireNonNull(itemRO);
    }

    @Override
    public int limit()
    {
        return limit;
    }

    @Override
    public UnboundedListFW<T> wrap(DirectBuffer buffer, int offset, int maxLimit)
    {
        super.wrap(buffer, offset, maxLimit);
        for (limit = offset; limit < maxLimit; limit = itemRO.limit())
        {
            itemRO.wrap(buffer, limit, maxLimit);
        }
        checkLimit(limit(), maxLimit);
        return this;
    }

    public UnboundedListFW<T> forEach(Consumer<? super T> consumer)
    {
        for (int offset = offset(); offset < maxLimit(); offset = itemRO.limit())
        {
            itemRO.wrap(buffer(), offset, maxLimit());
            consumer.accept(itemRO);
        }
        return this;
    }

    public boolean anyMatch(Predicate<? super T> predicate)
    {
        for (int offset = offset(); offset < maxLimit(); offset = itemRO.limit())
        {
            itemRO.wrap(buffer(), offset, maxLimit());
            if (predicate.test(itemRO))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString()
    {
        return "LIST";
    }

    public static final class Builder<B extends Flyweight.Builder<T>, T extends Flyweight>
            extends Flyweight.Builder<UnboundedListFW<T>>
    {
        private final B itemRW;

        public Builder(B itemRW, T itemRO)
        {
            super(new UnboundedListFW<T>(itemRO));
            this.itemRW = itemRW;
        }

        public Builder<B, T> wrap(MutableDirectBuffer buffer, int offset, int maxLimit)
        {
            super.wrap(buffer, offset, maxLimit);
            super.limit(offset);
            itemRW.wrap(buffer, offset, maxLimit);
            return this;
        }

        public Builder<B, T> item(Consumer<B> mutator)
        {
            mutator.accept(itemRW);
            limit(itemRW.build().limit());
            itemRW.wrap(buffer(), limit(), maxLimit());
            return this;
        }
    }
}
