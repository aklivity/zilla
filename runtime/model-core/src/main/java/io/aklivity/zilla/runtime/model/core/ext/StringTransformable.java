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
package io.aklivity.zilla.runtime.model.core.ext;

/**
 * Something a {@link StringTransform} stage can be appended to, in data-flow order. This narrower
 * supertype lets a caller outside {@code model-core} append stages to an in-progress whole-value
 * pipeline without depending on any fuller, terminal-bearing API.
 */
public interface StringTransformable
{
    StringTransformable transform(
        StringTransform transform);
}
