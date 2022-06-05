@file:OptIn(DelicateKotlinPoetApi::class)

package com.minikorp.duo.ksp.generators.drill

import com.minikorp.drill.DrillList
import com.minikorp.drill.DrillMap
import com.minikorp.drill.DrillSet
import com.minikorp.drill.UNSET_VALUE
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy

class DrillPropertyHelper {

    class CollectionPropertyGenerator(
        val factory: (from: ParameterizedTypeName, to: TypeName) -> CodeBlock,
        val drillTypeGenerator: (from: ParameterizedTypeName, to: TypeName) -> ParameterizedTypeName
    )

    companion object {

        val unsetClassName = UNSET_VALUE.javaClass.asClassName()

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

        private val mapGenerator = { mapType: ParameterizedTypeName, mutableType: TypeName ->
            DrillMap::class
                .asClassName()
                .parameterizedBy(
                    mapType.copy(nullable = false),
                    mapType.typeArguments[0],
                    mapType.typeArguments[1],
                    mutableType
                ).copy(nullable = mapType.isNullable) as ParameterizedTypeName
        }

        private val setGenerator = { setType: ParameterizedTypeName, mutableType: TypeName ->
            DrillSet::class
                .asClassName()
                .parameterizedBy(
                    setType.copy(nullable = false),
                    setType.typeArguments[0],
                    mutableType
                ).copy(nullable = setType.isNullable) as ParameterizedTypeName
        }

        val supportedCollectionTypes = mapOf<TypeName, CollectionPropertyGenerator>(
            LIST to CollectionPropertyGenerator(
                factory = { from, to -> CodeBlock.of("it.toList()") },
                drillTypeGenerator = listGenerator
            ),
            MUTABLE_LIST to CollectionPropertyGenerator(
                factory = { from, to -> CodeBlock.of("it.toMutableList()") },
                drillTypeGenerator = listGenerator
            ),
            ArrayList::class.asClassName() to CollectionPropertyGenerator(
                factory = { from, to ->
                    CodeBlock.of(
                        "%T().apply { addAll(it) }",
                        ArrayList::class.asClassName().parameterizedBy(from.typeArguments[0])
                    )
                },
                drillTypeGenerator = listGenerator
            ),
            MAP to CollectionPropertyGenerator(
                factory = { from, to -> CodeBlock.of("it") },
                drillTypeGenerator = mapGenerator
            ),
            MUTABLE_MAP to CollectionPropertyGenerator(
                factory = { from, to -> CodeBlock.of("it.toMutableMap()") },
                drillTypeGenerator = mapGenerator
            ),
            HashMap::class.asClassName() to CollectionPropertyGenerator(
                factory = { from, to ->
                    CodeBlock.of(
                        "%T(it)",
                        HashMap::class.asClassName()
                    )
                },
                drillTypeGenerator = mapGenerator
            ),
            SET to CollectionPropertyGenerator(
                factory = { from, to -> CodeBlock.of("it.toSet()") },
                drillTypeGenerator = setGenerator
            ),
        )
    }
}
