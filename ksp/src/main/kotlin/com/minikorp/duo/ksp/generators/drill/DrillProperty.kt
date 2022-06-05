@file:OptIn(KotlinPoetKspPreview::class)

package com.minikorp.duo.ksp.generators.drill

import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.minikorp.drill.Drillable
import com.minikorp.drill.UNSET_VALUE
import com.minikorp.duo.ksp.NAME_SHADOWING_ANNOTATION
import com.minikorp.duo.ksp.UNCHECKED_ANNOTATION
import com.minikorp.duo.ksp.safeCapitalize
import com.minikorp.duo.ksp.suppressAnnotation
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toTypeName

class DrillProperty(
    val container: DrillType,
    val propertyDeclaration: KSPropertyDeclaration,
) {

    enum class Kind {
        REFERENCE, DRILLABLE_PROP, COLLECTION
    }

    lateinit var propertyKind: Kind
    val name = propertyDeclaration.simpleName.asString()
    val propTypeName = propertyDeclaration.type.toTypeName()

    private val backingFieldName: String = "_$name"
    private lateinit var mutableType: TypeName

    lateinit var freeze: CodeBlock

    val drillType = DrillModel.findMatchingDrillType(propertyDeclaration.type)
    val collectionType = generateCollectionType(propertyDeclaration.type.toTypeName())

    private val backingFieldPropertySpec =
        PropertySpec.builder(name = backingFieldName, type = ANY.copy(nullable = true))
            .addModifiers(KModifier.PRIVATE)
            .mutable(true)
            .initializer("%T", UNSET_VALUE::class.asClassName())
            .build()

    fun compile() {

        when {
            drillType != null -> {
                propertyKind = Kind.DRILLABLE_PROP
                compileDrillableProperty(drillType)
            }
            isASupportedCollectionType(propertyDeclaration.type) -> {
                propertyKind = Kind.COLLECTION
                compileCollectionProperty()
            }
            else -> {
                propertyKind = Kind.REFERENCE
                compileReferenceProperty()
            }
        }
    }

    fun emitProperty(typeSpec: TypeSpec.Builder) {
        when (this.propertyKind) {
            Kind.REFERENCE -> emitReferenceProperty(typeSpec)
            Kind.DRILLABLE_PROP -> emitDrillableProperty(typeSpec)
            Kind.COLLECTION -> emitCollectionProperty(typeSpec)
        }
    }

    private fun compileReferenceProperty() {
        mutableType = propertyDeclaration.type.toTypeName()
        freeze = CodeBlock.of(name) // just the property
    }

    private fun emitReferenceProperty(typeSpec: TypeSpec.Builder) {
        typeSpec.addProperty(
            PropertySpec.builder(name = name, type = mutableType)
                .mutable(true)
                .initializer("${Drillable<*>::ref.name}().${name}")
                .setter(
                    FunSpec.setterBuilder()
                        .addParameter("value", mutableType)
                        .addCode("field = value.also { markDirty() }")
                        .build()
                ).build()
        )
    }

    private fun compileDrillableProperty(drillType: DrillType) {
        val safeCall = if (propTypeName.isNullable) "?." else "."

        mutableType = drillType.mutableClassName
        container.dependencies.add(drillType)

        freeze = CodeBlock.of(
            """if ($backingFieldName === %T)
                | ${Drillable<*>::ref.name}().$name else
                | ($backingFieldName as %T)${safeCall}freeze()""".trimMargin(),
            DrillPropertyHelper.unsetClassName,
            mutableType
        )
    }

    private fun emitDrillableProperty(typeSpec: TypeSpec.Builder) {
        val safeCall = if (propTypeName.isNullable) "?." else "."

        val drillType = DrillModel.findMatchingDrillType(propertyDeclaration.type)
        val mutableClassName = drillType!!.mutableClassName
        val accessorField =
            PropertySpec.builder(name, mutableClassName)
                .mutable(false)
                .getter(
                    FunSpec.getterBuilder()
                        .beginControlFlow(
                            "if (${backingFieldName} === %T)",
                            DrillPropertyHelper.unsetClassName
                        )
                        .addStatement("$backingFieldName = ${Drillable<*>::ref.name}()${safeCall}toMutable(this)")
                        .endControlFlow()
                        .addStatement("return $backingFieldName as %T", mutableClassName)
                        .build()
                )
                .build()

        val setterFunction = FunSpec.builder("set" + name.safeCapitalize())
            .addParameter(name, propTypeName)
            .addStatement("$backingFieldName = ${name}${safeCall}toMutable(this).also { markDirty() }")
            .build()

        typeSpec.addFunction(setterFunction)
        typeSpec.addProperties(listOf(backingFieldPropertySpec, accessorField))
    }

    private fun isASupportedCollectionType(typeRef: KSTypeReference): Boolean {
        val typeName = typeRef.toTypeName()
        return typeName is ParameterizedTypeName
                && DrillPropertyHelper
            .supportedCollectionTypes.containsKey(typeName.rawType)
    }

    private fun compileCollectionProperty() {
        val safeCall = if (propTypeName.isNullable) "?." else "."

        mutableType = collectionType.typeName
        freeze = CodeBlock.of(
            """if ($backingFieldName === %T) ${Drillable<*>::ref.name}().$name  
                | else ($backingFieldName as %T)${safeCall}freeze()""".trimMargin(),
            DrillPropertyHelper.unsetClassName,
            collectionType.typeName.copy(nullable = propTypeName.isNullable)
        )
    }

    private fun emitCollectionProperty(typeSpec: TypeSpec.Builder) {
        val safeCall = if (propTypeName.isNullable) "?." else "."

        val accessorField = PropertySpec.builder(name, mutableType)
            .getter(
                FunSpec.getterBuilder()
                    .addAnnotation(
                        suppressAnnotation(
                            UNCHECKED_ANNOTATION,
                            NAME_SHADOWING_ANNOTATION
                        )
                    )
                    .beginControlFlow(
                        "if (${backingFieldName} === %T)",
                        DrillPropertyHelper.unsetClassName
                    )
                    .addCode("$backingFieldName = ${Drillable<*>::ref.name}()${safeCall}$name.let {\n")
                    .addCode("val container = this\n")
                    .addCode(collectionType.mutate)
                    .addCode("\n}\n")
                    .endControlFlow()
                    .addStatement("return $backingFieldName as %T", mutableType)
                    .build()
            ).build()

        val setterFunction = FunSpec.builder("set" + name.safeCapitalize())
            .addAnnotation(suppressAnnotation(NAME_SHADOWING_ANNOTATION))
            .addParameter(name, propTypeName)
            .addCode("$backingFieldName = $name.let {\n")
            .addCode("val container = this\n")
            .addCode(collectionType.mutate)
            .addCode("\n}\n")
            .addCode("markDirty()")
            .build()

        typeSpec.addProperty(backingFieldPropertySpec)
        typeSpec.addProperty(accessorField)
        typeSpec.addFunction(setterFunction)
    }
}

private fun generateCollectionType(type: TypeName): CollectionType {
    val safeCall = if (type.isNullable) "?." else "."

    val nestedModel = DrillModel.findMatchingDrillType(type)
    if (nestedModel != null) {
        val nestedTypeName = nestedModel.mutableClassName.copy(nullable = type.isNullable)
        return CollectionType(
            typeName = nestedTypeName,
            mutate = CodeBlock.of("it${safeCall}toMutable(container)"),
            freeze = CodeBlock.of("it${safeCall}freeze()")
        )
    }

    if (type is ParameterizedTypeName) {
        val generator = DrillPropertyHelper.supportedCollectionTypes[type.rawType]
        if (generator != null) {
            val param = generateCollectionType(type.typeArguments.last())
            val collectionType = generator.drillTypeGenerator(type, param.typeName)

            return CollectionType(
                typeName = collectionType,
                mutate = CodeBlock.builder()
                    .add("it${safeCall}toMutable(\n")
                    .indent()
                    .add("parent  = container,\n")
                    .add("factory = { ").add(generator.factory(type, param.typeName)).add(" },\n")
                    .add("mutate  = { container, it -> ${param.mutate} },\n")
                    .add("freeze  = { ${param.freeze} }\n)")
                    .unindent()
                    .build(),
                freeze = CodeBlock.of("it${safeCall}freeze()")
            )
        }
    }

    //Any other type that can't be made mutable
    return CollectionType(
        typeName = type,
        mutate = CodeBlock.of("it"),
        freeze = CodeBlock.of("it")
    )
}

data class CollectionType(
    val typeName: TypeName,
    val mutate: CodeBlock,
    val freeze: CodeBlock
)