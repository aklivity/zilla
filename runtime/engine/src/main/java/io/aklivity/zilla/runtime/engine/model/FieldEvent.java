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
 * A field value extracted from a value as a {@link ModelPipeline} transforms it.
 * <p>
 * {@link #value()} is valid only for the duration of the {@link ModelVisitor#onField} call;
 * an implementation that needs the bytes beyond the call must copy them out.
 * </p>
 */
public interface FieldEvent
{
    /**
     * @return the registered extraction path the value was found at
     */
    String path();

    /**
     * @return the field value, already bounded to exactly its own bytes
     */
    DirectBufferEx value();
}
