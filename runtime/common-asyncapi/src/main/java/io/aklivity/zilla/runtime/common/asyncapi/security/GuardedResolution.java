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
package io.aklivity.zilla.runtime.common.asyncapi.security;

import java.util.List;

public final class GuardedResolution
{
    public final List<GuardedRef> guarded;
    public final String reason;

    public static GuardedResolution allowed(
        List<GuardedRef> guarded)
    {
        return new GuardedResolution(guarded, null);
    }

    public static GuardedResolution denied(
        String reason)
    {
        return new GuardedResolution(null, reason);
    }

    public boolean denied()
    {
        return reason != null;
    }

    private GuardedResolution(
        List<GuardedRef> guarded,
        String reason)
    {
        this.guarded = guarded;
        this.reason = reason;
    }
}
