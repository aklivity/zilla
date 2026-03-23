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
package io.aklivity.zilla.runtime.engine.config;

/**
 * Provides resource file reading to config adapters during YAML deserialization.
 *
 * @deprecated Resource resolution is now handled via the engine's path resolution
 *             mechanism. {@link OptionsConfigAdapterSpi} implementations that previously
 *             called {@link #readResource} should use the engine's
 *             {@code EngineContext#resolvePath} instead. This interface will be removed
 *             in a future release.
 */
@Deprecated
public interface ConfigAdapterContext
{
    /**
     * Reads the contents of a resource file at the given location and returns it as a string.
     *
     * @param location  a path or URI identifying the resource to read
     * @return the resource contents as a string
     * @deprecated Use the engine's path resolution mechanism instead.
     */
    @Deprecated
    String readResource(
        String location);
}
