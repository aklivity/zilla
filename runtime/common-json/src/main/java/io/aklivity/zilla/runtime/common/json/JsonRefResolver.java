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
 * Resolves a non-local JSON Schema {@code $ref} (one not beginning with {@code #}) to the
 * referenced schema document text. Local pointer refs are resolved by {@link JsonSchema}
 * against the root document and never reach this resolver.
 * <p>
 * Implementations must be <em>synchronous</em> — a cache lookup, not blocking I/O. Any
 * asynchronous retrieval (e.g. fetching a referenced schema from a registry) is the caller's
 * responsibility, performed off the hot path before the referenced text is needed here.
 */
@FunctionalInterface
public interface JsonRefResolver
{
    String resolve(
        String ref);
}
