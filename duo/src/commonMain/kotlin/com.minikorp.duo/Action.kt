package com.minikorp.duo

/**
 * Base type for anything that can be dispatched through [Store.dispatch] or [Store.offer].
 *
 * This type only exists to prevent typing errors (dispatching something that is not an action)
 * that would result into hard to track bugs.
 */
interface Action



