/*
 * Copyright 2021-2024 Aklivity Inc.
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

import org.agrona.DirectBuffer;

/**
 * Receives field values extracted from a value as a {@link ModelPipeline} transforms it.
 * <p>
 * A {@code ModelVisitor} is wired to a pipeline at {@link ModelHandler#supplyPipeline} time and
 * confined to the same single I/O thread. The pipeline invokes {@link #onField} once per registered
 * path that is present in a value, when that value completes. The supplied buffer slice is valid
 * only for the duration of the call; an implementation that needs the bytes beyond the call must
 * copy them out.
 * </p>
 *
 * @see ModelHandler#supplyPipeline(ModelVisitor)
 */
@FunctionalInterface
public interface ModelVisitor
{
    /**
     * No-op visitor that ignores every extracted field.
     */
    ModelVisitor NONE = (path, buffer, index, length) -> {};

    /**
     * Called with the buffer slice containing an extracted field value.
     *
     * @param path    the registered extraction path the value was found at
     * @param buffer  the buffer containing the field value
     * @param index   the offset of the field value
     * @param length  the length of the field value
     */
    void onField(
        String path,
        DirectBuffer buffer,
        int index,
        int length);
}
