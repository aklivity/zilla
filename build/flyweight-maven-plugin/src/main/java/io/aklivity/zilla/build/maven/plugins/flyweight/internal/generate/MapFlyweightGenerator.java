/*
 * Copyright 2021-2022 Aklivity Inc.
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

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.DIRECT_BUFFER_TYPE;
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.MUTABLE_DIRECT_BUFFER_TYPE;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstType;

public final class MapFlyweightGenerator extends ClassSpecGenerator
{
    private final TypeSpec.Builder builder;
    private final TypeName keyTypeName;
    private final TypeName valueTypeName;
    private final ClassName templateMapTypeName;
    private final TypeName parameterizedMapName;
    private final BuilderClassBuilder builderClassBuilder;

    public MapFlyweightGenerator(
        ClassName mapName,
        ClassName flyweightName,
        ClassName templateMapTypeName,
        AstType mapKeyType,
        ClassName mapKeyTypeName,
        AstType mapValueType,
        ClassName mapValueTypeName,
        TypeResolver resolver)
    {
        super(mapName);
        this.keyTypeName = Objects.requireNonNullElse(mapKeyTypeName, TypeVariableName.get(mapKeyType.name(), flyweightName));
        this.valueTypeName = Objects.requireNonNullElse(mapValueTypeName,
            TypeVariableName.get(mapValueType.name(), flyweightName));
        this.templateMapTypeName = templateMapTypeName;
        ClassName mapFWType = resolver.resolveClass(AstType.MAP);
        if (mapKeyTypeName == null && mapValueTypeName == null)
        {
            parameterizedMapName = ParameterizedTypeName.get(mapName, TypeVariableName.get(mapKeyType.name()),
                TypeVariableName.get(mapValueType.name()));
        }
        else if (mapKeyTypeName == null)
        {
            parameterizedMapName = ParameterizedTypeName.get(mapName, TypeVariableName.get(mapKeyType.name()));
        }
        else if (mapValueTypeName == null)
        {
            parameterizedMapName = ParameterizedTypeName.get(mapName, TypeVariableName.get(mapValueType.name()));
        }
        else
        {
            parameterizedMapName = mapName;
        }
        this.builder = builder(mapName, mapFWType, flyweightName, mapKeyType, mapKeyTypeName, mapValueType, mapValueTypeName);
        this.builderClassBuilder = new BuilderClassBuilder(mapName, parameterizedMapName, mapFWType, flyweightName,
            templateMapTypeName, mapKeyType, mapKeyTypeName, keyTypeName, mapValueType, mapValueTypeName, valueTypeName);
    }

    private TypeSpec.Builder builder(
        ClassName mapName,
        ClassName mapFWType,
        ClassName flyweightName,
        AstType mapKeyType,
        ClassName mapKeyTypeName,
        AstType mapValueType,
        ClassName mapValueTypeName)
    {
        TypeSpec.Builder classBuilder = classBuilder(mapName);
        if (mapKeyTypeName == null)
        {
            classBuilder.addTypeVariable(TypeVariableName.get(mapKeyType.name(), flyweightName));
        }
        if (mapValueTypeName == null)
        {
            classBuilder.addTypeVariable(TypeVariableName.get(mapValueType.name(), flyweightName));
        }
        TypeName superClassType = ParameterizedTypeName.get(mapFWType, keyTypeName, valueTypeName);
        return classBuilder.superclass(superClassType)
            .addModifiers(PUBLIC, FINAL);
    }

    @Override
    public TypeSpec generate()
    {
        return builder
            .addField(mapField())
            .addMethod(constructor())
            .addMethod(lengthMethod())
            .addMethod(fieldCountMethod())
            .addMethod(entriesMethod())
            .addMethod(forEachMethod())
            .addMethod(tryWrapMethod())
            .addMethod(wrapMethod())
            .addMethod(limitMethod())
            .addMethod(toStringMethod())
            .addType(builderClassBuilder.build())
            .build();
    }

    private FieldSpec mapField()
    {
        ParameterizedTypeName parameterizedMapTypeName = ParameterizedTypeName.get(templateMapTypeName, keyTypeName,
            valueTypeName);
        String fieldName = String.format("%sRO", fieldName(templateMapTypeName));
        return FieldSpec.builder(parameterizedMapTypeName, fieldName)
            .addModifiers(PRIVATE, FINAL)
            .build();
    }

    private MethodSpec constructor()
    {
        return constructorBuilder()
            .addModifiers(PUBLIC)
            .addParameter(keyTypeName, "keyRO")
            .addParameter(valueTypeName, "valueRO")
            .addStatement("$LRO = new $T<>(keyRO, valueRO)", fieldName(templateMapTypeName), templateMapTypeName)
            .build();
    }

    private MethodSpec lengthMethod()
    {
        return methodBuilder("length")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(int.class)
            .addStatement("return $LRO.get().length()", fieldName(templateMapTypeName))
            .build();
    }

    private MethodSpec fieldCountMethod()
    {
        return methodBuilder("fieldCount")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(int.class)
            .addStatement("return $LRO.get().fieldCount()", fieldName(templateMapTypeName))
            .build();
    }

    private MethodSpec entriesMethod()
    {
        return methodBuilder("entries")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(DIRECT_BUFFER_TYPE)
            .addStatement("return $LRO.get().entries()", fieldName(templateMapTypeName))
            .build();
    }

    private MethodSpec forEachMethod()
    {
        TypeName parameterizedBiConsumerType = ParameterizedTypeName.get(ClassName.get(BiConsumer.class), keyTypeName,
            valueTypeName);
        return methodBuilder("forEach")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .addParameter(parameterizedBiConsumerType, "consumer")
            .addStatement("$LRO.get().forEach(consumer)", fieldName(templateMapTypeName))
            .build();
    }

    private MethodSpec tryWrapMethod()
    {
        return methodBuilder("tryWrap")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(parameterizedMapName)
            .addParameter(DIRECT_BUFFER_TYPE, "buffer")
            .addParameter(int.class, "offset")
            .addParameter(int.class, "maxLimit")
            .beginControlFlow("if (super.tryWrap(buffer, offset, maxLimit) == null)")
            .addStatement("return null")
            .endControlFlow()
            .beginControlFlow("if ($LRO.tryWrap(buffer, offset, maxLimit) == null)", fieldName(templateMapTypeName))
            .addStatement("return null")
            .endControlFlow()
            .beginControlFlow("if (limit() > maxLimit)")
            .addStatement("return null")
            .endControlFlow()
            .addStatement("return this")
            .build();
    }

    private MethodSpec wrapMethod()
    {
        return methodBuilder("wrap")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(parameterizedMapName)
            .addParameter(DIRECT_BUFFER_TYPE, "buffer")
            .addParameter(int.class, "offset")
            .addParameter(int.class, "maxLimit")
            .addStatement("super.wrap(buffer, offset, maxLimit)")
            .addStatement("$LRO.wrap(buffer, offset, maxLimit)", fieldName(templateMapTypeName))
            .addStatement("checkLimit(limit(), maxLimit)")
            .addStatement("return this")
            .build();
    }

    private MethodSpec limitMethod()
    {
        return methodBuilder("limit")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(int.class)
            .addStatement("return $LRO.limit()", fieldName(templateMapTypeName))
            .build();
    }

    private MethodSpec toStringMethod()
    {
        return methodBuilder("toString")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(String.class)
            .addStatement("return String.format(\"$L[%d, %d]\", $LRO.get().length(), $LRO.get().fieldCount())",
                thisName.simpleName(), fieldName(templateMapTypeName), fieldName(templateMapTypeName))
            .build();
    }

    private static final class BuilderClassBuilder
    {
        private final TypeSpec.Builder classBuilder;
        private final ClassName mapName;
        private final TypeName parameterizedMapName;
        private final ClassName templateMapTypeName;
        private final TypeName keyTypeName;
        private final TypeName valueTypeName;
        private final TypeName keyBuilderTypeName;
        private final TypeName valueBuilderTypeName;
        private final TypeName parameterizedMapBuilderName;

        private BuilderClassBuilder(
            ClassName mapName,
            TypeName parameterizedMapName,
            ClassName mapFWName,
            ClassName flyweightType,
            ClassName templateMapTypeName,
            AstType mapKeyType,
            ClassName mapKeyTypeName,
            TypeName keyTypeName,
            AstType mapValueType,
            ClassName mapValueTypeName,
            TypeName valueTypeName)
        {
            this.mapName = mapName;
            this.parameterizedMapName = parameterizedMapName;
            this.templateMapTypeName = templateMapTypeName;
            this.keyTypeName = keyTypeName;
            this.valueTypeName = valueTypeName;
            ClassName mapBuilderName = mapName.nestedClass("Builder");
            ClassName mapFWBuilderName = mapFWName.nestedClass("Builder");
            ClassName flyweightBuilderType = flyweightType.nestedClass("Builder");
            keyBuilderTypeName = mapKeyTypeName != null ? mapKeyTypeName.nestedClass("Builder") :
                TypeVariableName.get(String.format("%s%s", mapKeyType.name(), "B"));
            valueBuilderTypeName = mapValueTypeName != null ? mapValueTypeName.nestedClass("Builder") :
                TypeVariableName.get(String.format("%s%s", mapValueType.name(), "B"));
            TypeName parameterizedMapFWBuilder = ParameterizedTypeName.get(mapFWBuilderName, parameterizedMapName, keyTypeName,
                valueTypeName, keyBuilderTypeName, valueBuilderTypeName);
            this.classBuilder = classBuilder(mapBuilderName.simpleName())
                .addModifiers(PUBLIC, STATIC, FINAL)
                .superclass(parameterizedMapFWBuilder);
            if (mapKeyTypeName == null)
            {
                TypeVariableName typeVarK = TypeVariableName.get(mapKeyType.name(), flyweightType);
                classBuilder.addTypeVariable(typeVarK)
                    .addTypeVariable(TypeVariableName.get(String.format("%s%s", mapKeyType.name(), "B"),
                        ParameterizedTypeName.get(flyweightBuilderType, typeVarK)));
            }
            if (mapValueTypeName == null)
            {
                TypeVariableName typeVarV = TypeVariableName.get(mapValueType.name(), flyweightType);
                classBuilder.addTypeVariable(typeVarV)
                    .addTypeVariable(TypeVariableName.get(String.format("%s%s", mapValueType.name(), "B"),
                        ParameterizedTypeName.get(flyweightBuilderType, typeVarV)));
            }

            if (mapKeyTypeName == null && mapValueTypeName == null)
            {
                parameterizedMapBuilderName = ParameterizedTypeName.get(mapBuilderName, keyTypeName, valueTypeName,
                    keyBuilderTypeName, valueBuilderTypeName);
            }
            else if (mapKeyTypeName == null)
            {
                parameterizedMapBuilderName = ParameterizedTypeName.get(mapBuilderName, keyTypeName, keyBuilderTypeName);
            }
            else if (mapValueTypeName == null)
            {
                parameterizedMapBuilderName = ParameterizedTypeName.get(mapBuilderName, valueTypeName, valueBuilderTypeName);
            }
            else
            {
                parameterizedMapBuilderName = mapBuilderName;
            }
        }

        public TypeSpec build()
        {
            return classBuilder
                .addField(mapBuilderField())
                .addMethod(constructor())
                .addMethod(wrapMethod())
                .addMethod(entryMethod())
                .addMethod(entriesMethod())
                .addMethod(buildMethod())
                .build();
        }

        private FieldSpec mapBuilderField()
        {
            ParameterizedTypeName parameterizedMapTypeName = ParameterizedTypeName.get(templateMapTypeName.nestedClass("Builder"),
                keyTypeName, valueTypeName, keyBuilderTypeName, valueBuilderTypeName);
            String fieldName = String.format("%sRW", fieldName(templateMapTypeName));
            return FieldSpec.builder(parameterizedMapTypeName, fieldName)
                .addModifiers(PRIVATE, FINAL)
                .build();
        }

        private MethodSpec constructor()
        {
            return constructorBuilder()
                .addModifiers(PUBLIC)
                .addParameter(keyTypeName, "keyRO")
                .addParameter(valueTypeName, "valueRO")
                .addParameter(keyBuilderTypeName, "keyRW")
                .addParameter(valueBuilderTypeName, "valueRW")
                .addStatement("super(new $T<>(keyRO, valueRO))", mapName)
                .addStatement("$LRW = new $T.Builder<>(keyRO, valueRO, keyRW, valueRW)",
                    fieldName(templateMapTypeName), templateMapTypeName)
                .build();
        }

        private MethodSpec wrapMethod()
        {
            return methodBuilder("wrap")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(parameterizedMapBuilderName)
                .addParameter(MUTABLE_DIRECT_BUFFER_TYPE, "buffer")
                .addParameter(int.class, "offset")
                .addParameter(int.class, "maxLimit")
                .addStatement("super.wrap(buffer, offset, maxLimit)")
                .addStatement("$LRW.wrap(buffer, offset, maxLimit)", fieldName(templateMapTypeName))
                .addStatement("return this")
                .build();
        }

        private MethodSpec entryMethod()
        {
            ClassName consumerType = ClassName.get(Consumer.class);
            TypeName parameterizedConsumerTypeWithKey = ParameterizedTypeName.get(consumerType, keyBuilderTypeName);
            TypeName parameterizedConsumerTypeWithValue = ParameterizedTypeName.get(consumerType, valueBuilderTypeName);
            return methodBuilder("entry")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(parameterizedMapBuilderName)
                .addParameter(parameterizedConsumerTypeWithKey, "key")
                .addParameter(parameterizedConsumerTypeWithValue, "value")
                .addStatement("$LRW.entry(key, value)", fieldName(templateMapTypeName))
                .addStatement("limit($LRW.limit())", fieldName(templateMapTypeName))
                .addStatement("return this")
                .build();
        }

        private MethodSpec entriesMethod()
        {
            return methodBuilder("entries")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(parameterizedMapBuilderName)
                .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                .addParameter(int.class, "index")
                .addParameter(int.class, "length")
                .addParameter(int.class, "fieldCount")
                .addStatement("$LRW.entries(buffer, index, length, fieldCount)", fieldName(templateMapTypeName))
                .addStatement("limit($LRW.limit())", fieldName(templateMapTypeName))
                .addStatement("return this")
                .build();
        }

        private MethodSpec buildMethod()
        {
            return methodBuilder("build")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(parameterizedMapName)
                .addStatement("limit($LRW.build().limit())", fieldName(templateMapTypeName))
                .addStatement("return super.build()")
                .build();
        }
    }

    private static String fieldName(
        TypeName type)
    {
        String fieldName =  ((ClassName) type).simpleName();
        return String.format("%s%s", Character.toLowerCase(fieldName.charAt(0)), fieldName.substring(1, fieldName.length() - 2));
    }
}
