package com.minikorp.duo.ksp.generators.drill.adapters

import com.minikorp.drill.DrillList
import com.minikorp.drill.DrillMap
import com.minikorp.duo.ksp.NAME_SHADOWING_ANNOTATION
import com.minikorp.duo.ksp.UNCHECKED_ANNOTATION
import com.minikorp.duo.ksp.generators.drill.DrillClassGenerator
import com.minikorp.duo.ksp.generators.drill.DrillPropertyModel
import com.minikorp.duo.ksp.safeCapitalize
import com.minikorp.duo.ksp.suppressAnnotation
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy

/**
 * This adapter generates property mutating code for
 * properties of type List<T>, Map<K, T>, Set<T> where
 * T can be another Mutable type.
 */
class CollectionPropertyAdapter(sourceProp: DrillPropertyModel) : AbstractPropertyAdapter(sourceProp) {

    class Generator(
        val factory: (from: ParameterizedTypeName, to: TypeName) -> CodeBlock,
        val drillTypeGenerator: (from: ParameterizedTypeName, to: TypeName) -> ParameterizedTypeName
    )

    companion object {
        private val listGenerator = { listType: ParameterizedTypeName,
                                      mutableType: TypeName ->
            DrillList::class
                .asClassName()
                .parameterizedBy(
                    listType.copy(nullable = false), //ListType
                    listType.typeArguments[0], //Immutable
                    mutableType //Mutable
                ).copy(nullable = listType.isNullable) as ParameterizedTypeName
        }

        private val mapGenerator = { mapType: ParameterizedTypeName, to: TypeName ->
            DrillMap::class
                .asClassName()
                .parameterizedBy(
                    mapType.copy(nullable = false),
                    mapType.typeArguments[0],
                    mapType.typeArguments[1],
                    to
                ).copy(nullable = mapType.isNullable) as ParameterizedTypeName
        }

        val supportedCollectionTypes = mapOf<TypeName, Generator>(
            LIST to Generator(
                factory = { from, to -> CodeBlock.of("it.toList()") },
                drillTypeGenerator = listGenerator
            ),
            MUTABLE_LIST to Generator(
                factory = { from, to -> CodeBlock.of("it.toMutableList()") },
                drillTypeGenerator = listGenerator
            ),
            ArrayList::class.asClassName() to Generator(
                factory = { from, to ->
                    CodeBlock.of(
                        "%T().apply { addAll(it) }",
                        ArrayList::class.asClassName().parameterizedBy(from.typeArguments[0])
                    )
                },
                drillTypeGenerator = listGenerator
            ),
            MAP to Generator(
                factory = { from, to -> CodeBlock.of("it") },
                drillTypeGenerator = mapGenerator
            ),
            MUTABLE_MAP to Generator(
                factory = { from, to -> CodeBlock.of("it.toMutableMap()") },
                drillTypeGenerator = mapGenerator
            ),
            HashMap::class.asClassName() to Generator(
                factory = { from, to ->
                    CodeBlock.of(
                        "%T(it)",
                        HashMap::class.asClassName()
                    )
                },
                drillTypeGenerator = mapGenerator
            )

        )

        fun supportsType(type: TypeName): Boolean {
            return type is ParameterizedTypeName && supportedCollectionTypes.containsKey(type.rawType)
        }
    }

    /**
     * The resolved type after the generics in (List<T>, Map<K,T>) have
     * been resolved to the corresponding mutable types.
     */
    private val resolvedType = generateCollectionType(sourceProp.typeName)

    override val freezeExpression: CodeBlock
        get() = CodeBlock.of(
            "if ($backingPropertyName === %T) " +
                    "$refPropertyAccessor " +
                    "else ($backingPropertyName as %T)${call}freeze()",
            unsetClassName,
            resolvedType.typeName.copy(nullable = nullable)
        )

    override fun generate(builder: TypeSpec.Builder) {
        val accessorField = PropertySpec.builder(sourceProp.name, resolvedType.typeName)
            .addKdoc(refPropertyKdoc)
            .mutable(false)
            .getter(
                FunSpec.getterBuilder()
                    .addAnnotation(
                        suppressAnnotation(
                            UNCHECKED_ANNOTATION,
                            NAME_SHADOWING_ANNOTATION
                        )
                    )
                    .beginControlFlow(
                        "if (${backingPropertyName} === %T)",
                        unsetClassName
                    )
                    .addStatement("$backingPropertyName = ${refPropertyAccessor}.let { ")
                    .addStatement("val container = this")
                    .addCode(resolvedType.mutate).addCode(" }")
                    .endControlFlow()
                    .addStatement("return $backingPropertyName as %T", resolvedType.typeName)
                    .build()
            ).build()

        val setterFunction = FunSpec.builder("set" + sourceProp.name.safeCapitalize())
            .addAnnotation(suppressAnnotation(NAME_SHADOWING_ANNOTATION))
            .addParameter(sourceProp.name, sourceProp.typeName)
            .addStatement("val param = ${sourceProp.name}")
            .addCode("$backingPropertyName = param.let { ")
            .addStatement("val container = this")
            .addCode(resolvedType.mutate)
            .addCode(" }\n")
            .addStatement("markDirty()")
            .build()

        builder.addFunction(setterFunction)
        builder.addProperty(backingField)
        builder.addProperty(accessorField)
    }

    private fun generateCollectionType(type: TypeName): CollectionType {
        val call = if (type.isNullable) "?." else "."

        val nestedModel = DrillClassGenerator.findMutableClass(type)
        if (nestedModel != null) {
            val nestedTypeName = nestedModel.mutableClassName.copy(nullable = type.isNullable)
            return CollectionType(
                typeName = nestedTypeName,
                mutate = CodeBlock.of("it${call}toMutable(container)"),
                freeze = CodeBlock.of("it${call}freeze()")
            )
        }

        if (type is ParameterizedTypeName) {
            val generator = supportedCollectionTypes[type.rawType]
            if (generator != null) {
                val param = generateCollectionType(type.typeArguments.last())
                val collectionType = generator.drillTypeGenerator(type, param.typeName)

                return CollectionType(
                    typeName = collectionType,
                    mutate = CodeBlock.builder()
                        .add("it${call}toMutable(container,\n")
                        .indent()
                        .add("factory={ ").add(generator.factory(type, param.typeName)).add(" },\n")
                        .add("mutate={ container, it -> ${param.mutate} },\n")
                        .add("freeze={ ${param.freeze} })")
                        .unindent()
                        .build(),
                    freeze = CodeBlock.of("it${call}freeze()")
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
}
