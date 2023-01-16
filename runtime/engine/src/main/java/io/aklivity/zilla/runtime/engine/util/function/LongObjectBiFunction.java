/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.util.function;

import java.util.function.BiFunction;

@FunctionalInterface
public interface LongObjectBiFunction<U, R> extends BiFunction<Long, U, R>
{
    @Override
    default R apply(Long value, U u)
    {
        return this.apply(value.longValue(), u);
    }

    R apply(long l, U u);
}
