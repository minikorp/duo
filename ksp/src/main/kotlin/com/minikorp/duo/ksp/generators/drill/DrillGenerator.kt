package com.minikorp.duo.ksp.generators.drill

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.minikorp.drill.Drill
import com.minikorp.duo.State
import com.minikorp.duo.ksp.*


object DrillGenerator {

    fun generate() {
        val stateSymbols = resolver.getSymbolsWithAnnotation(State::class.qualifiedName.toString())
        val drillSymbols = resolver.getSymbolsWithAnnotation(Drill::class.qualifiedName.toString())
        val allSymbols = (stateSymbols + drillSymbols).toList()

        allSymbols.forEach { symbol ->
            symbol.accept(
                object : KSDefaultVisitor<Unit, Unit>() {
                    override fun defaultHandler(node: KSNode, data: Unit) = Unit
                    override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
                        val fqn = classDeclaration.qualifiedName!!.asString()
                        if (DrillModel.types[fqn] == null) {
                            DrillModel.types[fqn] = DrillType(classDeclaration)
                        }
                    }
                },
                Unit,
            )
        }

        DrillModel.types.values.forEach {
            it.compile()
        }

        DrillModel.types.values.forEach {
            it.emit()
        }
    }
}