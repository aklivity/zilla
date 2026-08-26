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

/**
 * A read-direction {@link ModelPipeline}'s relationship to a local cache, if any, at the point a caller
 * supplies it via {@link ModelHandler#supplyDecoder}.
 */
public enum ModelCache
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
