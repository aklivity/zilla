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
 * A schema violation, described at the point of detection and handed to a {@link JsonReporter} before the
 * event that triggered it is forwarded downstream. Delivered only when the pipeline opts in with
 * {@link JsonStream#detailed(boolean)}.
 * <p>
 * The instance is a reused, call-scoped view: every accessor is valid only for the duration of the
 * {@link JsonReporter#violated(JsonViolation)} callback and nothing is allocated per violation. A reporter
 * that needs to retain any of it must copy it out before returning.
 */
public interface JsonViolation
{
    /**
     * The schema keyword that failed, such as {@code required}, {@code additionalProperties},
     * {@code const} or {@code minimum}.
     */
    String keyword();

    /**
     * The RFC 6901 JSON Pointer of the instance the keyword was applied to: the containing object for a
     * keyword that concerns a member, otherwise the offending value.
     */
    CharSequence pointer();

    /**
     * The name of the member the violation concerns — the undeclared member that is present, or the
     * required member that is absent — or {@code null} for a keyword that does not concern a member.
     */
    CharSequence member();

    /**
     * Access to the scalar value that failed the keyword, or {@code null} when the violation does not
     * concern a scalar value. The view reads the value the violation was decided on.
     */
    JsonSource value();
}
