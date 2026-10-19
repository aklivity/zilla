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
package io.aklivity.zilla.runtime.model.protobuf.ext;

/**
 * An extended pipeline's relationship to a local cache, if any, at the point it is folded in.
 */
public enum ProtobufCache
{
    /**
     * No local cache is involved; the pipeline decodes directly for the reader requesting it.
     */
    NONE,

    /**
     * The pipeline decodes ahead of any specific reader's request, producing the value a local cache
     * persists.
     */
    WRITE,

    /**
     * The pipeline decodes a value already in the form a {@link #WRITE} pipeline produced, for the reader
     * requesting it now.
     */
    READ
}
