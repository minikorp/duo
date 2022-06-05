package com.minikorp.duo.ksp

interface Generator {
    fun buildModel()
    fun emit()
}