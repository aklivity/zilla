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
package io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeVariableName;

public abstract class ParameterizedTypeSpecGenerator extends TypeSpecGenerator<ParameterizedTypeName>
{
    protected ParameterizedTypeSpecGenerator(
        ParameterizedTypeName thisType)
    {
        super(thisType, thisType.rawType);
    }

    protected ParameterizedTypeSpecGenerator(
        ClassName rawType,
        TypeVariableName... typeVars)
    {
        super(ParameterizedTypeName.get(rawType, typeVars), rawType);
    }
}
