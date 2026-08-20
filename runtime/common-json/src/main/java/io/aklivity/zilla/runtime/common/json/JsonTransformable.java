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
package io.aklivity.zilla.runtime.common.json;

/**
 * Something a {@link JsonTransform} stage can be appended to, in data-flow order. {@link JsonStream} is
 * the primary implementation; this narrower, self-bounded supertype lets a caller outside {@code common-json}
 * append stages to an in-progress stream and get back its own concrete stream type, without depending on
 * {@link JsonStream}'s fuller, terminal-bearing API.
 *
 * @param <T>  the concrete stream type, so {@link #transform(JsonTransform)} returns it rather than this
 *             narrower supertype
 */
public interface JsonTransformable<T extends JsonTransformable<T>>
{
    T transform(
        JsonTransform transform);
}
