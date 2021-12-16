/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.build.maven.plugins.cog.internal.generate;

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.BIT_UTIL_TYPE;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.DIRECT_BUFFER_TYPE;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.MUTABLE_DIRECT_BUFFER_TYPE;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.UNSAFE_BUFFER_TYPE;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.nio.ByteOrder;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

public final class List32FWGenerator extends ClassSpecGenerator
{
    private final TypeSpec.Builder classBuilder;
    private final BuilderClassBuilder builderClassBuilder;

    public List32FWGenerator(
        ClassName flyweightType,
        ClassName listType)
    {
        super(listType.peerClass("List32FW"));

        this.classBuilder = classBuilder(thisName).superclass(listType).addModifiers(PUBLIC, FINAL);
        this.builderClassBuilder = new BuilderClassBuilder(thisName, flyweightType.nestedClass("Builder"),
            listType.nestedClass("Builder"));
    }

    @Override
    public TypeSpec generate()
    {
        return classBuilder.addField(fieldsField())
            .addField(lengthSizeConstant())
            .addField(fieldCountSizeConstant())
            .addField(lengthOffsetConstant())
            .addField(fieldCountOffsetConstant())
            .addField(fieldsOffsetConstant())
            .addField(byteOrderField())
            .addMethod(constructor())
            .addMethod(constructorWithByteOrder())
            .addMethod(limitMethod())
            .addMethod(lengthMethod())
            .addMethod(fieldCountMethod())
            .addMethod(fieldsMethod())
            .addMethod(tryWrapMethod())
            .addMethod(wrapMethod())
            .addMethod(toStringMethod())
            .addType(builderClassBuilder.build())
            .build();
    }

    private FieldSpec fieldsField()
    {
        return FieldSpec.builder(DIRECT_BUFFER_TYPE, "fieldsRO", PRIVATE, FINAL)
            .initializer("new $T(0L, 0)", UNSAFE_BUFFER_TYPE)
            .build();
    }

    private FieldSpec lengthSizeConstant()
    {
        return FieldSpec.builder(int.class, "LENGTH_SIZE", PRIVATE, STATIC, FINAL)
            .initializer("$T.SIZE_OF_INT", BIT_UTIL_TYPE)
            .build();
    }

    private FieldSpec fieldCountSizeConstant()
    {
        return FieldSpec.builder(int.class, "FIELD_COUNT_SIZE", PRIVATE, STATIC, FINAL)
            .initializer("$T.SIZE_OF_INT", BIT_UTIL_TYPE)
            .build();
    }

    private FieldSpec lengthOffsetConstant()
    {
        return FieldSpec.builder(int.class, "LENGTH_OFFSET", PRIVATE, STATIC, FINAL)
            .initializer("0")
            .build();
    }

    private FieldSpec fieldCountOffsetConstant()
    {
        return FieldSpec.builder(int.class, "FIELD_COUNT_OFFSET", PRIVATE, STATIC, FINAL)
            .initializer("LENGTH_OFFSET + LENGTH_SIZE")
            .build();
    }

    private FieldSpec fieldsOffsetConstant()
    {
        return FieldSpec.builder(int.class, "FIELDS_OFFSET", PRIVATE, STATIC, FINAL)
            .initializer("FIELD_COUNT_OFFSET + FIELD_COUNT_SIZE")
            .build();
    }

    private FieldSpec byteOrderField()
    {
        return FieldSpec.builder(ByteOrder.class, "byteOrder", PRIVATE, FINAL)
            .build();
    }

    private MethodSpec constructor()
    {
        return constructorBuilder()
            .addModifiers(PUBLIC)
            .addStatement("this.byteOrder = ByteOrder.nativeOrder()")
            .build();
    }

    private MethodSpec constructorWithByteOrder()
    {
        return constructorBuilder()
            .addModifiers(PUBLIC)
            .addParameter(ByteOrder.class, "byteOrder")
            .addStatement("this.byteOrder = byteOrder")
            .build();
    }

    private MethodSpec limitMethod()
    {
        return methodBuilder("limit")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(int.class)
            .addStatement("return offset() + LENGTH_SIZE + length()")
            .build();
    }

    private MethodSpec lengthMethod()
    {
        return methodBuilder("length")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(int.class)
            .addStatement("return buffer().getInt(offset() + LENGTH_OFFSET, byteOrder)")
            .build();
    }

    private MethodSpec fieldCountMethod()
    {
        return methodBuilder("fieldCount")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(int.class)
            .addStatement("return buffer().getInt(offset() + FIELD_COUNT_OFFSET, byteOrder)")
            .build();
    }

    private MethodSpec fieldsMethod()
    {
        return methodBuilder("fields")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(DIRECT_BUFFER_TYPE)
            .addStatement("return fieldsRO")
            .build();
    }

    private MethodSpec tryWrapMethod()
    {
        return methodBuilder("tryWrap")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .addParameter(DIRECT_BUFFER_TYPE, "buffer")
            .addParameter(int.class, "offset")
            .addParameter(int.class, "maxLimit")
            .returns(thisName)
            .beginControlFlow("if (super.tryWrap(buffer, offset, maxLimit) == null)")
            .addStatement("return null")
            .endControlFlow()
            .addStatement("final int fieldsSize = length() - FIELD_COUNT_SIZE")
            .addStatement("fieldsRO.wrap(buffer, offset + FIELDS_OFFSET, fieldsSize)")
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
            .addParameter(DIRECT_BUFFER_TYPE, "buffer")
            .addParameter(int.class, "offset")
            .addParameter(int.class, "maxLimit")
            .returns(thisName)
            .addStatement("super.wrap(buffer, offset, maxLimit)")
            .addStatement("final int fieldsSize = length() - FIELD_COUNT_SIZE")
            .addStatement("fieldsRO.wrap(buffer, offset + FIELDS_OFFSET, fieldsSize)")
            .addStatement("checkLimit(limit(), maxLimit)")
            .addStatement("return this")
            .build();
    }

    private MethodSpec toStringMethod()
    {
        return methodBuilder("toString")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(String.class)
            .addStatement("return String.format(\"list32<%d, %d>\", length(), fieldCount())")
            .build();
    }

    private static final class BuilderClassBuilder
    {
        private final TypeSpec.Builder classBuilder;
        private final ClassName classType;
        private final ClassName listType;
        private final ClassName visitorType;

        private BuilderClassBuilder(
            ClassName listType,
            ClassName flyweightBuilderRawType,
            ClassName builderRawType)
        {
            TypeName builderType = ParameterizedTypeName.get(builderRawType, listType);
            this.listType = listType;
            this.classType = listType.nestedClass("Builder");
            this.classBuilder = classBuilder(classType.simpleName())
                .addModifiers(PUBLIC, STATIC, FINAL)
                .superclass(builderType);
            this.visitorType = flyweightBuilderRawType.nestedClass("Visitor");
        }

        public TypeSpec build()
        {
            return classBuilder
                .addField(byteOrder())
                .addField(fieldCount())
                .addMethod(constructor())
                .addMethod(constructorWithByteOrder())
                .addMethod(fieldMethod())
                .addMethod(fieldsMethodViaVisitor())
                .addMethod(fieldsMethodViaBuffer())
                .addMethod(wrapMethod())
                .addMethod(buildMethod())
                .build();
        }

        private FieldSpec byteOrder()
        {
            return FieldSpec.builder(ByteOrder.class, "byteOrder", PRIVATE, FINAL)
                .build();
        }

        private FieldSpec fieldCount()
        {
            return FieldSpec.builder(int.class, "fieldCount", PRIVATE)
                .build();
        }

        private MethodSpec constructor()
        {
            return constructorBuilder()
                .addModifiers(PUBLIC)
                .addStatement("super(new List32FW())")
                .addStatement("this.byteOrder = $T.nativeOrder()", ByteOrder.class)
                .build();
        }

        private MethodSpec constructorWithByteOrder()
        {
            return constructorBuilder()
                .addModifiers(PUBLIC)
                .addParameter(ByteOrder.class, "byteOrder")
                .addStatement("super(new List32FW(byteOrder))")
                .addStatement("this.byteOrder = byteOrder")
                .build();
        }

        private MethodSpec fieldMethod()
        {
            return methodBuilder("field")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(listType.nestedClass("Builder"))
                .addParameter(visitorType, "visitor")
                .addStatement("int length = visitor.visit(buffer(), limit(), maxLimit())")
                .addStatement("fieldCount++")
                .addStatement("int newLimit = limit() + length")
                .addStatement("checkLimit(newLimit, maxLimit())")
                .addStatement("limit(newLimit)")
                .addStatement("return this")
                .build();
        }

        private MethodSpec fieldsMethodViaVisitor()
        {
            return methodBuilder("fields")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(listType.nestedClass("Builder"))
                .addParameter(int.class, "fieldCount")
                .addParameter(visitorType, "visitor")
                .addStatement("int length = visitor.visit(buffer(), limit(), maxLimit())")
                .addStatement("this.fieldCount += fieldCount")
                .addStatement("int newLimit = limit() + length")
                .addStatement("checkLimit(newLimit, maxLimit())")
                .addStatement("limit(newLimit)")
                .addStatement("return this")
                .build();
        }

        private MethodSpec fieldsMethodViaBuffer()
        {
            return methodBuilder("fields")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(listType.nestedClass("Builder"))
                .addParameter(int.class, "fieldCount")
                .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                .addParameter(int.class, "index")
                .addParameter(int.class, "length")
                .addStatement("this.fieldCount += fieldCount")
                .addStatement("int newLimit = limit() + length")
                .addStatement("checkLimit(newLimit, maxLimit())")
                .addStatement("buffer().putBytes(limit(), buffer, index, length)")
                .addStatement("limit(newLimit)")
                .addStatement("return this")
                .build();
        }

        private MethodSpec wrapMethod()
        {
            return methodBuilder("wrap")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(listType.nestedClass("Builder"))
                .addParameter(MUTABLE_DIRECT_BUFFER_TYPE, "buffer")
                .addParameter(int.class, "offset")
                .addParameter(int.class, "maxLimit")
                .addStatement("super.wrap(buffer, offset, maxLimit)")
                .addStatement("int newLimit = offset + FIELDS_OFFSET")
                .addStatement("checkLimit(newLimit, maxLimit)")
                .addStatement("limit(newLimit)")
                .addStatement("this.fieldCount = 0")
                .addStatement("return this")
                .build();
        }

        private MethodSpec buildMethod()
        {
            return methodBuilder("build")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(listType)
                .addStatement("buffer().putInt(offset() + LENGTH_OFFSET, limit() - offset() - FIELD_COUNT_OFFSET, byteOrder)")
                .addStatement("buffer().putInt(offset() + FIELD_COUNT_OFFSET, fieldCount, byteOrder)")
                .addStatement("return super.build()")
                .build();
        }
    }
}
