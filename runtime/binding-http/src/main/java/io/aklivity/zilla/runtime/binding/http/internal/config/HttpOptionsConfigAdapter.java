/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.http.internal.config;

import static io.aklivity.zilla.runtime.binding.http.config.HttpPolicyConfig.CROSS_ORIGIN;
import static io.aklivity.zilla.runtime.binding.http.config.HttpPolicyConfig.SAME_ORIGIN;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.http.config.HttpAccessControlConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpAccessControlConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpAllowConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpAllowConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpAuthorizationConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpCredentialsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpCredentialsConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpExposeConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpExposeConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequest;
import io.aklivity.zilla.runtime.binding.http.config.HttpVersion;
import io.aklivity.zilla.runtime.binding.http.internal.HttpBinding;
import io.aklivity.zilla.runtime.binding.http.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class HttpOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String VERSIONS_NAME = "versions";
    private static final String OVERRIDES_NAME = "overrides";
    private static final String ACCESS_CONTROL_NAME = "access-control";
    private static final String POLICY_NAME = "policy";
    private static final String POLICY_VALUE_SAME_ORIGIN = "same-origin";
    private static final String POLICY_VALUE_CROSS_ORIGIN = "cross-origin";
    private static final String AUTHORIZATION_NAME = "authorization";
    private static final String AUTHORIZATION_CREDENTIALS_NAME = "credentials";
    private static final String AUTHORIZATION_CREDENTIALS_HEADERS_NAME = "headers";
    private static final String AUTHORIZATION_CREDENTIALS_QUERY_NAME = "query";
    private static final String AUTHORIZATION_CREDENTIALS_COOKIES_NAME = "cookies";
    private static final String ALLOW_NAME = "allow";
    private static final String ALLOW_ORIGINS_NAME = "origins";
    private static final String ALLOW_METHODS_NAME = "methods";
    private static final String ALLOW_HEADERS_NAME = "headers";
    private static final String ALLOW_CREDENTIALS_NAME = "credentials";
    private static final String MAX_AGE_NAME = "max-age";
    private static final String EXPOSE_NAME = "expose";
    private static final String EXPOSE_HEADERS_NAME = "headers";
    private static final String REQUESTS_NAME = "requests";

    private final HttpRequestAdapter httpRequest = new HttpRequestAdapter();

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return HttpBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        HttpOptionsConfig httpOptions = (HttpOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (httpOptions.versions != null &&
            !httpOptions.versions.isEmpty())
        {
            JsonArrayBuilder entries = Json.createArrayBuilder();
            httpOptions.versions.forEach(v -> entries.add(v.asString()));

            object.add(VERSIONS_NAME, entries);
        }

        HttpAccessControlConfig httpAccess = httpOptions.access;
        if (httpAccess != null)
        {
            JsonObjectBuilder access = Json.createObjectBuilder();

            switch (httpAccess.policy)
            {
            case SAME_ORIGIN:
                access.add(POLICY_NAME, POLICY_VALUE_SAME_ORIGIN);
                break;
            case CROSS_ORIGIN:
                access.add(POLICY_NAME, POLICY_VALUE_CROSS_ORIGIN);

                HttpAllowConfig httpAllow = httpAccess.allow;
                if (httpAllow != null)
                {
                    JsonObjectBuilder allow = Json.createObjectBuilder();

                    if (httpAllow.origins != null)
                    {
                        JsonArrayBuilder origins = Json.createArrayBuilder();
                        httpAllow.origins.forEach(origins::add);
                        allow.add(ALLOW_ORIGINS_NAME, origins);
                    }

                    if (httpAllow.methods != null)
                    {
                        JsonArrayBuilder methods = Json.createArrayBuilder();
                        httpAllow.methods.forEach(methods::add);
                        allow.add(ALLOW_METHODS_NAME, methods);
                    }

                    if (httpAllow.headers != null)
                    {
                        JsonArrayBuilder headers = Json.createArrayBuilder();
                        httpAllow.headers.forEach(headers::add);
                        allow.add(ALLOW_HEADERS_NAME, headers);
                    }

                    if (httpAllow.credentials)
                    {
                        allow.add(ALLOW_CREDENTIALS_NAME, httpAllow.credentials);
                    }

                    access.add(ALLOW_NAME, allow);
                }

                Duration maxAge = httpAccess.maxAge;
                if (maxAge != null)
                {
                    access.add(MAX_AGE_NAME, maxAge.toSeconds());
                }

                HttpExposeConfig httpExpose = httpAccess.expose;
                if (httpExpose != null)
                {
                    JsonObjectBuilder expose = Json.createObjectBuilder();

                    if (httpExpose.headers != null)
                    {
                        JsonArrayBuilder headers = Json.createArrayBuilder();
                        httpExpose.headers.forEach(headers::add);
                        expose.add(EXPOSE_HEADERS_NAME, headers);
                    }

                    access.add(EXPOSE_NAME, expose);
                }
                break;
            }

            object.add(ACCESS_CONTROL_NAME, access);
        }

        HttpAuthorizationConfig httpAuthorization = httpOptions.authorization;
        if (httpAuthorization != null)
        {
            JsonObjectBuilder authorizations = Json.createObjectBuilder();

            JsonObjectBuilder authorization = Json.createObjectBuilder();

            HttpCredentialsConfig httpCredentials = httpAuthorization.credentials;
            if (httpCredentials != null)
            {
                JsonObjectBuilder credentials = Json.createObjectBuilder();

                if (httpCredentials.headers != null)
                {
                    JsonObjectBuilder headers = Json.createObjectBuilder();

                    httpCredentials.headers.forEach(p -> headers.add(p.name, p.pattern));

                    credentials.add(AUTHORIZATION_CREDENTIALS_HEADERS_NAME, headers);
                }

                if (httpCredentials.parameters != null)
                {
                    JsonObjectBuilder parameters = Json.createObjectBuilder();

                    httpCredentials.parameters.forEach(p -> parameters.add(p.name, p.pattern));

                    credentials.add(AUTHORIZATION_CREDENTIALS_QUERY_NAME, parameters);
                }

                if (httpCredentials.cookies != null)
                {
                    JsonObjectBuilder cookies = Json.createObjectBuilder();

                    httpCredentials.cookies.forEach(p -> cookies.add(p.name, p.pattern));

                    credentials.add(AUTHORIZATION_CREDENTIALS_COOKIES_NAME, cookies);
                }

                authorization.add(AUTHORIZATION_CREDENTIALS_NAME, credentials);

                authorizations.add(httpAuthorization.name, authorization);
            }

            object.add(AUTHORIZATION_NAME, authorizations);
        }

        if (httpOptions.overrides != null &&
            !httpOptions.overrides.isEmpty())
        {
            JsonObjectBuilder entries = Json.createObjectBuilder();
            httpOptions.overrides.forEach((k, v) -> entries.add(k.asString(), v.asString()));

            object.add(OVERRIDES_NAME, entries);
        }

        if (httpOptions.requests != null)
        {
            JsonArrayBuilder requests = Json.createArrayBuilder();
            httpOptions.requests.stream()
                .map(httpRequest::adaptToJson)
                .forEach(requests::add);
            object.add(REQUESTS_NAME, requests);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        HttpOptionsConfigBuilder<HttpOptionsConfig> httpOptions = HttpOptionsConfig.builder();

        if (object.containsKey(VERSIONS_NAME))
        {
            object.getJsonArray(VERSIONS_NAME)
                .forEach(v -> httpOptions.version(HttpVersion.of(JsonString.class.cast(v).getString())));
        }

        if (object.containsKey(AUTHORIZATION_NAME))
        {
            HttpAuthorizationConfigBuilder<?> httpAuthorization = httpOptions.authorization();

            JsonObject authorizations = object.getJsonObject(AUTHORIZATION_NAME);
            for (String name : authorizations.keySet())
            {
                JsonObject authorization = authorizations.getJsonObject(name);
                JsonObject credentials = authorization.getJsonObject(AUTHORIZATION_CREDENTIALS_NAME);

                if (credentials != null)
                {
                    HttpCredentialsConfigBuilder<?> httpCredentials = httpAuthorization
                        .name(name)
                        .credentials();

                    if (credentials.containsKey(AUTHORIZATION_CREDENTIALS_HEADERS_NAME))
                    {
                        credentials.getJsonObject(AUTHORIZATION_CREDENTIALS_HEADERS_NAME)
                            .forEach((n, v) -> httpCredentials.header()
                                .name(n)
                                .pattern(JsonString.class.cast(v).getString())
                                .build());
                    }

                    if (credentials.containsKey(AUTHORIZATION_CREDENTIALS_QUERY_NAME))
                    {
                        credentials.getJsonObject(AUTHORIZATION_CREDENTIALS_QUERY_NAME)
                            .forEach((n, v) -> httpCredentials.parameter()
                                .name(n)
                                .pattern(JsonString.class.cast(v).getString())
                                .build());
                    }

                    if (credentials.containsKey(AUTHORIZATION_CREDENTIALS_COOKIES_NAME))
                    {
                        credentials.getJsonObject(AUTHORIZATION_CREDENTIALS_COOKIES_NAME)
                            .forEach((n, v) -> httpCredentials.cookie()
                                .name(n)
                                .pattern(JsonString.class.cast(v).getString())
                                .build());
                    }

                    httpCredentials.build();
                }
            }

            httpAuthorization.build();
        }

        if (object.containsKey(ACCESS_CONTROL_NAME))
        {
            JsonObject access = object.getJsonObject(ACCESS_CONTROL_NAME);

            if (access.containsKey(POLICY_NAME))
            {
                HttpAccessControlConfigBuilder<?> httpAccess = httpOptions.access();

                String policy = access.getString(POLICY_NAME);
                switch (policy)
                {
                case POLICY_VALUE_SAME_ORIGIN:
                    httpAccess.policy(SAME_ORIGIN);
                    break;
                case POLICY_VALUE_CROSS_ORIGIN:
                    httpAccess.policy(CROSS_ORIGIN);

                    if (access.containsKey(ALLOW_NAME))
                    {
                        HttpAllowConfigBuilder<?> httpAllow = httpAccess.allow();
                        JsonObject allow = access.getJsonObject(ALLOW_NAME);

                        if (allow.containsKey(ALLOW_ORIGINS_NAME))
                        {
                            allow.getJsonArray(ALLOW_ORIGINS_NAME)
                                .forEach(v -> httpAllow.origin(JsonString.class.cast(v).getString()));
                        }

                        if (allow.containsKey(ALLOW_METHODS_NAME))
                        {
                            allow.getJsonArray(ALLOW_METHODS_NAME)
                                .forEach(v -> httpAllow.method(JsonString.class.cast(v).getString()));
                        }

                        if (allow.containsKey(ALLOW_HEADERS_NAME))
                        {
                            allow.getJsonArray(ALLOW_HEADERS_NAME)
                                .forEach(v -> httpAllow.header(JsonString.class.cast(v).getString()));
                        }

                        if (allow.containsKey(ALLOW_CREDENTIALS_NAME))
                        {
                            httpAllow.credentials(allow.getBoolean(ALLOW_CREDENTIALS_NAME));
                        }

                        httpAllow.build();
                    }

                    if (access.containsKey(MAX_AGE_NAME))
                    {
                        httpAccess.maxAge(Duration.ofSeconds(access.getJsonNumber(MAX_AGE_NAME).longValue()));
                    }

                    if (access.containsKey(EXPOSE_NAME))
                    {
                        HttpExposeConfigBuilder<?> httpExpose = httpAccess.expose();
                        JsonObject expose = access.getJsonObject(EXPOSE_NAME);

                        if (expose.containsKey(ALLOW_HEADERS_NAME))
                        {
                            expose.getJsonArray(ALLOW_HEADERS_NAME)
                                .forEach(v -> httpExpose.header(JsonString.class.cast(v).getString()));
                        }

                        httpExpose.build();
                    }

                    httpAccess.build();
                    break;
                }
            }
        }

        if (object.containsKey(OVERRIDES_NAME))
        {
            object.getJsonObject(OVERRIDES_NAME)
                .forEach((k, v) ->
                    httpOptions.override(new String8FW(k), new String16FW(JsonString.class.cast(v).getString())));
        }

        if (object.containsKey(REQUESTS_NAME))
        {
            List<HttpRequest> requests = object.getJsonArray(REQUESTS_NAME).stream()
                .map(item -> httpRequest.adaptFromJson((JsonObject) item))
                .collect(Collectors.toList());
            httpOptions.requests(requests);
        }

        return httpOptions.build();
    }
}
