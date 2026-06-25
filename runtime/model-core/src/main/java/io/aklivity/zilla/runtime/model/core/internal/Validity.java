/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.model.core.internal;

// The three-state outcome of validating a core-model value, separating a structural/parse failure from a
// semantic/constraint failure so the pipeline can relax only the latter under LENIENT.
enum Validity
{
    // a structurally well-formed value that satisfies every semantic rule
    VALID,
    // the bytes could not be decoded into a value of the model's format — a parse failure, always rejected
    MALFORMED,
    // a structurally well-formed value that violates a semantic rule (range, multiple, length, pattern) —
    // rejected under STRICT, passed through under LENIENT
    INVALID
}
