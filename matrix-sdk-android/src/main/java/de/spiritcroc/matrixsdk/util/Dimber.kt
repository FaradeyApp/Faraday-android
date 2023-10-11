package de.spiritcroc.matrixsdk.util

import timber.log.Timber

// Timber wrapper
class Dimber(val tag: String, val key: String) {
    fun v(msgGen: () -> String) {
        if (DbgUtil.isDbgEnabled(key)) {
            Timber.tag(tag).v(msgGen())
        }
    }
    fun d(msgGen: () -> String) {
        if (DbgUtil.isDbgEnabled(key)) {
            Timber.tag(tag).d(msgGen())
        }
    }
    fun i(msgGen: () -> String) {
        if (DbgUtil.isDbgEnabled(key)) {
            Timber.tag(tag).i(msgGen())
        }
    }
    fun w(msgGen: () -> String) {
        if (DbgUtil.isDbgEnabled(key)) {
            Timber.tag(tag).w(msgGen())
        }
    }
    fun e(msgGen: () -> String) {
        if (DbgUtil.isDbgEnabled(key)) {
            Timber.tag(tag).e(msgGen())
        }
    }
    fun exec(doFun: () -> Unit) {
        if (DbgUtil.isDbgEnabled(key)) {
            doFun()
        }
    }
}
