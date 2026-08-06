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
package io.aklivity.zilla.runtime.engine.model;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Immutable, read-only view of the field observed at the current {@link FieldEvent} as a
 * {@link ModelTransform} chain pumps events through its stages.
 * <p>
 * A {@code ModelSource} has no cursor-advancing method, so a stage cannot disturb the pump. The buffer
 * {@link #getValue()} exposes is a non-owning, on-stack view valid only for the duration of the
 * {@link ModelTransform#transform} call; a stage that needs the bytes beyond the call must copy them out.
 * </p>
 * <p>
 * The value is presented in the format-agnostic extraction rendering: the raw bytes of a string, byte
 * array, or fixed-width value, and the ASCII text of a numeric or boolean value. This is the same
 * rendering a format surfaces for field extraction, so a transform reads and substitutes values without
 * knowing anything about the format's wire representation.
 * </p>
 *
 * @see ModelTransform
 */
public interface ModelSource
{
    /**
     * The path of the field at the current event, as a JSON path rooted at the value (e.g. {@code $.name}).
     *
     * @return the field path, or {@code null} at {@link FieldEvent#START_VALUE} and
     *         {@link FieldEvent#END_VALUE}
     */
    String getPath();

    /**
     * Non-owning, on-stack view of the whole field value at the current event, its {@code capacity()}
     * being the value length. Empty at {@link FieldEvent#START_VALUE}, {@link FieldEvent#END_VALUE}, and
     * {@link FieldEvent#DECLINED}.
     *
     * @return the field value bytes
     */
    DirectBufferEx getValue();
}
