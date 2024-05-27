package com.harsh.taptargetview


internal object ReflectUtil {
    /** Returns the value of the given private field from the source object  */
    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    fun getPrivateField(source: Any, fieldName: String?): Any {
        val objectField = source.javaClass.getDeclaredField(fieldName)
        objectField.isAccessible = true
        return objectField[source]
    }
}

