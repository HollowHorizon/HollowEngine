package ru.hollowhorizon.hollowengine.common.scripting.core.host

import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.getScriptingClass
import kotlin.script.experimental.jvm.JvmGetScriptingClass
import kotlin.script.experimental.jvm.util.classpathFromClassloader

class HollowEngineScriptingHost : ScriptingHostConfiguration({
    getScriptingClass(JvmGetScriptingClass())
    classpathFromClassloader(Thread.currentThread().contextClassLoader)
})