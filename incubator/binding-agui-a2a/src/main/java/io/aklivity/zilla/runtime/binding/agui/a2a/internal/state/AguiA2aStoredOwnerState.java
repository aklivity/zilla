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

import java.io.StringReader;
import java.time.Duration;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;

import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public final class AguiA2aStoredOwnerState implements AguiA2aOwnerState
{
    private static final String CONTEXT_KEY_PREFIX = "agui-a2a/ctx/";
    private static final String HANDLE_KEY_PREFIX = "agui-a2a/h/";
    private static final String CONTEXT_ID_NAME = "contextId";
    private static final String KEY_HASH_NAME = "keyHash";
    private static final String KIND_NAME = "kind";
    private static final String TASK_ID_NAME = "taskId";
    private static final String SUBJECT_NAME = "subject";

    private final AguiA2aLocalOwnerState local;
    private final StoreHandler store;
    private final Duration threadTtl;
    private final Duration handleTtl;

    public AguiA2aStoredOwnerState(
        AguiA2aLocalOwnerState local,
        StoreHandler store,
        Duration threadTtl,
        Duration handleTtl)
    {
        this.local = local;
        this.store = store;
        this.threadTtl = threadTtl;
        this.handleTtl = handleTtl;
    }

    @Override
    public String lookupContext(
        AguiA2aThreadKey key)
    {
        return local.lookupContext(key);
    }

    @Override
    public void rememberContext(
        AguiA2aThreadKey key,
        String contextId)
    {
        local.rememberContext(key, contextId);

        if (contextId != null)
        {
            final JsonObject value = Json.createObjectBuilder()
                .add(CONTEXT_ID_NAME, contextId)
                .build();

            store.put(contextKey(key), value.toString(), threadTtl, ignored -> {});
        }
    }

    @Override
    public void restoreContext(
        AguiA2aThreadKey key,
        Runnable ready)
    {
        if (local.lookupContext(key) != null)
        {
            ready.run();
        }
        else
        {
            store.get(contextKey(key), (storeKey, value) -> onContextRestored(key, value, ready));
        }
    }

    @Override
    public String issueHandle(
        AguiA2aThreadKey key,
        AguiA2aHandleKind kind,
        String taskId,
        String subject)
    {
        final String handle = local.issueHandle(key, kind, taskId, subject);

        final JsonObjectBuilder value = Json.createObjectBuilder()
            .add(KEY_HASH_NAME, hex(key.hash()))
            .add(KIND_NAME, kind.name())
            .add(TASK_ID_NAME, taskId);

        if (subject != null)
        {
            value.add(SUBJECT_NAME, subject);
        }

        store.put(handleKey(handle), value.build().toString(), handleTtl, ignored -> {});

        return handle;
    }

    @Override
    public AguiA2aHandleTarget resolveTarget(
        AguiA2aThreadKey key,
        AguiA2aHandleKind kind,
        String handle)
    {
        final AguiA2aHandleTarget target = local.resolveTarget(key, kind, handle);

        if (target != null)
        {
            store.getAndDelete(handleKey(handle), ignored -> {});
        }

        return target;
    }

    @Override
    public void restoreHandle(
        AguiA2aThreadKey key,
        AguiA2aHandleKind kind,
        String handle,
        Runnable ready)
    {
        if (handle == null || local.holdsHandle(handle))
        {
            ready.run();
        }
        else
        {
            store.getAndDelete(handleKey(handle), value -> onHandleRestored(key, kind, handle, value, ready));
        }
    }

    private void onContextRestored(
        AguiA2aThreadKey key,
        String value,
        Runnable ready)
    {
        final JsonObject object = parse(value);

        if (object != null && object.containsKey(CONTEXT_ID_NAME))
        {
            local.rememberContext(key, object.getString(CONTEXT_ID_NAME));
        }

        ready.run();
    }

    private void onHandleRestored(
        AguiA2aThreadKey key,
        AguiA2aHandleKind kind,
        String handle,
        String value,
        Runnable ready)
    {
        final JsonObject object = parse(value);

        if (object != null)
        {
            final boolean owned = hex(key.hash()).equals(object.getString(KEY_HASH_NAME, null)) &&
                kind.name().equals(object.getString(KIND_NAME, null));

            if (owned)
            {
                local.adoptHandle(handle, key, kind, object.getString(TASK_ID_NAME, null),
                    object.getString(SUBJECT_NAME, null));
            }
            else
            {
                store.put(handleKey(handle), value, handleTtl, ignored -> {});
            }
        }

        ready.run();
    }

    private static JsonObject parse(
        String value)
    {
        JsonObject object = null;

        if (value != null)
        {
            try (JsonReader reader = Json.createReader(new StringReader(value)))
            {
                object = reader.readObject();
            }
            catch (JsonException | ClassCastException ex)
            {
                object = null;
            }
        }

        return object;
    }

    private static String contextKey(
        AguiA2aThreadKey key)
    {
        return CONTEXT_KEY_PREFIX + hex(key.hash());
    }

    private static String handleKey(
        String handle)
    {
        return HANDLE_KEY_PREFIX + handle;
    }

    private static String hex(
        long hash)
    {
        return String.format("%016x", hash);
    }
}
