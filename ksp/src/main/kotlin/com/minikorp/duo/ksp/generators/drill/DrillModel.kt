package com.minikorp.duo.ksp.generators.drill

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

/**
 * Fully Qualified Name
 */
typealias FQN = String

object DrillModel {
    val types: MutableMap<FQN, DrillType> = HashMap()

    fun findMatchingDrillType(type: TypeName): DrillType? {
        if (type is ClassName) {
            return types[type.canonicalName]
        }
        return null
    }

    fun findMatchingDrillType(type: KSTypeReference): DrillType? {
        val declaration = type.resolve().declaration
        if (declaration is KSClassDeclaration) {
            return types[declaration.qualifiedName!!.asString()]
        }
        return null
    }
}


