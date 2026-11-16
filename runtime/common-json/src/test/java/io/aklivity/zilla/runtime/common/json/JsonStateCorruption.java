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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class JsonStateCorruption
{
    // every non-final, non-array field of the object takes a value that is not the one it held, so a field a saved
    // record does not carry, and the next wrap does not rewrite, breaks the parse that follows a restore
    public static void corrupt(
        Object target)
    {
        try
        {
            for (Field field : target.getClass().getDeclaredFields())
            {
                final int modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers) && !field.getType().isArray())
                {
                    field.setAccessible(true);
                    corrupt(target, field);
                }
            }
        }
        catch (ReflectiveOperationException ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    // the parser and the tokenizer it drives
    public static void corruptParser(
        JsonParserEx parser)
    {
        try
        {
            corrupt(parser);
            final Field tokenizerField = parser.getClass().getDeclaredField("tokenizer");
            tokenizerField.setAccessible(true);
            corrupt(tokenizerField.get(parser));
        }
        catch (ReflectiveOperationException ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    private static void corrupt(
        Object target,
        Field field) throws ReflectiveOperationException
    {
        final Class<?> type = field.getType();
        if (type == boolean.class)
        {
            field.setBoolean(target, !field.getBoolean(target));
        }
        else if (type == int.class)
        {
            field.setInt(target, ~field.getInt(target) ^ 0x2a2a);
        }
        else if (type == long.class)
        {
            field.setLong(target, ~field.getLong(target) ^ 0x2a2a2a2a2aL);
        }
        else if (type.isEnum())
        {
            final Object[] constants = type.getEnumConstants();
            final Object current = field.get(target);
            final int next = current == null ? 0 : (((Enum<?>) current).ordinal() + 1) % constants.length;
            field.set(target, constants[next]);
        }
        else if (!type.isPrimitive())
        {
            field.set(target, null);
        }
    }

    private JsonStateCorruption()
    {
    }
}
