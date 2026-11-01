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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.state;

public interface AguiA2aOwnerState
{
    String lookupContext(
        AguiA2aThreadKey key);

    void rememberContext(
        AguiA2aThreadKey key,
        String contextId);

    String issueHandle(
        AguiA2aThreadKey key,
        AguiA2aHandleKind kind,
        String taskId,
        String subject);

    AguiA2aHandleTarget resolveTarget(
        AguiA2aThreadKey key,
        AguiA2aHandleKind kind,
        String handle);

    default void restoreContext(
        AguiA2aThreadKey key,
        Runnable ready)
    {
        ready.run();
    }

    default void restoreHandle(
        AguiA2aThreadKey key,
        AguiA2aHandleKind kind,
        String handle,
        Runnable ready)
    {
        ready.run();
    }

    default String issueHandle(
        AguiA2aThreadKey key,
        AguiA2aHandleKind kind,
        String taskId)
    {
        return issueHandle(key, kind, taskId, null);
    }

    default String resolveHandle(
        AguiA2aThreadKey key,
        AguiA2aHandleKind kind,
        String handle)
    {
        final AguiA2aHandleTarget target = resolveTarget(key, kind, handle);

        return target != null ? target.taskId() : null;
    }
}
