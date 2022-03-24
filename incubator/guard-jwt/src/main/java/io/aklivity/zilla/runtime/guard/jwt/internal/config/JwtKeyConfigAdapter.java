/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.guard.jwt.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

public final class JwtKeyConfigAdapter implements JsonbAdapter<JwtKeyConfig, JsonObject>
{
    private static final String ALG_NAME = "alg";
    private static final String KTY_NAME = "kty";
    private static final String KID_NAME = "kid";
    private static final String USE_NAME = "use";
    private static final String N_NAME = "n";
    private static final String E_NAME = "e";
    private static final String CRV_NAME = "crv";
    private static final String X_NAME = "x";
    private static final String Y_NAME = "y";

    @Override
    public JsonObject adaptToJson(
        JwtKeyConfig key)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(KTY_NAME, key.kty);

        if (key.n != null)
        {
            object.add(N_NAME, key.n);
        }

        if (key.e != null)
        {
            object.add(E_NAME, key.e);
        }

        if (key.alg != null)
        {
            object.add(ALG_NAME, key.alg);
        }

        if (key.crv != null)
        {
            object.add(CRV_NAME, key.crv);
        }

        if (key.x != null)
        {
            object.add(X_NAME, key.x);
        }

        if (key.y != null)
        {
            object.add(Y_NAME, key.y);
        }

        if (key.use != null)
        {
            object.add(USE_NAME, key.use);
        }

        object.add(KID_NAME, key.kid);

        return object.build();
    }

    @Override
    public JwtKeyConfig adaptFromJson(
        JsonObject object)
    {
        String kty = object.getString(KTY_NAME);
        String kid = object.getString(KID_NAME);
        String use = object.containsKey(USE_NAME) ? object.getString(USE_NAME) : null;

        String n = object.containsKey(N_NAME) ? object.getString(N_NAME) : null;
        String e = object.containsKey(E_NAME) ? object.getString(E_NAME) : null;
        String alg = object.containsKey(ALG_NAME) ? object.getString(ALG_NAME) : null;

        String crv = object.containsKey(CRV_NAME) ? object.getString(CRV_NAME) : null;
        String x = object.containsKey(X_NAME) ? object.getString(X_NAME) : null;
        String y = object.containsKey(Y_NAME) ? object.getString(Y_NAME) : null;

        return new JwtKeyConfig(kty, kid, use, n, e, alg, crv, x, y);
    }
}
