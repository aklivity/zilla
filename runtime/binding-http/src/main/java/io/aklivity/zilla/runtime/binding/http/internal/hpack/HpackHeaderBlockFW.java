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
package io.aklivity.zilla.runtime.binding.http.internal.hpack;

import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.binding.http.internal.codec.UnboundedListFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.http.internal.types.HttpHeaderFW;

/*
    Flyweight for HPACK Header Block

    +-------------------------------+-------------------------------+
    |                        HeaderField 1                          |
    +---------------------------------------------------------------+
    |                        HeaderField 2                          |
    +---------------------------------------------------------------+
    |                            ...                                |
    +---------------------------------------------------------------+

 */
public class HpackHeaderBlockFW extends Flyweight
{

    private final UnboundedListFW<HpackHeaderFieldFW> listFW = new UnboundedListFW<>(new HpackHeaderFieldFW());

    @Override
    public int limit()
    {
        return listFW.limit();
    }

    public HpackHeaderBlockFW forEach(Consumer<HpackHeaderFieldFW> headerField)
    {
        listFW.forEach(headerField::accept);
        return this;
    }

    public boolean error()
    {
        return listFW.anyMatch(HpackHeaderFieldFW::error);
    }

    @Override
    public HpackHeaderBlockFW wrap(DirectBuffer buffer, int offset, int maxLimit)
    {
        super.wrap(buffer, offset, maxLimit);
        listFW.wrap(buffer, offset, maxLimit);
        return this;
    }

    public static final class Builder extends Flyweight.Builder<HpackHeaderBlockFW>
    {
        private final UnboundedListFW.Builder<HpackHeaderFieldFW.Builder, HpackHeaderFieldFW> headersRW =
                new UnboundedListFW.Builder<>(new HpackHeaderFieldFW.Builder(), new HpackHeaderFieldFW());

        public Builder()
        {
            super(new HpackHeaderBlockFW());
        }

        public Builder header(Consumer<HpackHeaderFieldFW.Builder> mutator)
        {
            headersRW.item(mutator);
            super.limit(headersRW.limit());
            return this;
        }

        public Builder set(
                UnboundedListFW<HttpHeaderFW> headers,
                BiFunction<HttpHeaderFW, HpackHeaderFieldFW.Builder, HpackHeaderFieldFW> mapper)
        {
            headers.forEach(h -> header(builder -> mapper.apply(h, builder)));
            return this;
        }

        public Builder wrap(MutableDirectBuffer buffer, int offset, int maxLimit)
        {
            super.wrap(buffer, offset, maxLimit);
            headersRW.wrap(buffer, offset, maxLimit);
            return this;
        }

    }

}
