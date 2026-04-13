package com.greybox.projectmesh.debug

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.lang.Thread.UncaughtExceptionHandler

/**
 * JVM-only tests for CrashHandler.
 * We DO NOT invoke Android runtime (no Context/Intent usage at runtime).
 * All checks are reflection-based and safe for plain JVM.
 */
class CrashHandlerTest {

    /**
     * CrashHandler must implement Thread.UncaughtExceptionHandler.
     * (If someone removes/changes this, we catch it early.)
     */
    @Test
    fun crashHandler_implements_UncaughtExceptionHandler() {
        assertTrue(
            UncaughtExceptionHandler::class.java.isAssignableFrom(CrashHandler::class.java)
        )
    }

    /**
     * Verify the primary constructor shape:
     * (Context, UncaughtExceptionHandler, Class<*>)
     * We don't instantiate anything; we only look up the signature.
     */
    @Test
    fun crashHandler_has_expected_constructor_signature() {
        val ctx = android.content.Context::class.java
        val ueh = UncaughtExceptionHandler::class.java
        val klass = Class::class.java

        // If the constructor is missing or signature changes, this throws NoSuchMethodException
        val ctor = CrashHandler::class.java.getDeclaredConstructor(ctx, ueh, klass)
        assertNotNull(ctor)
    }

    /**
     * Companion must expose:
     *  - init(Context, Class<*>)
     *  - getThrowableFromIntent(Intent): Throwable?
     * We assert presence and parameter/return types by reflection.
     */
    @Test
    fun companion_has_init_and_getThrowableFromIntent_signatures() {
        // Access the Kotlin "Companion" object using plain Java reflection.
        val companionField = CrashHandler::class.java.getDeclaredField("Companion")
        companionField.isAccessible = true
        val companion = companionField.get(null)
            ?: throw AssertionError("CrashHandler must have a companion object")

        val companionClass = companion::class.java

        // init(Context, Class<*>)
        val ctx = android.content.Context::class.java
        val klass = Class::class.java
        val initMethod = companionClass.getMethod("init", ctx, klass)
        assertEquals(Void.TYPE, initMethod.returnType)

        // getThrowableFromIntent(Intent): Throwable?
        val intent = android.content.Intent::class.java
        val getMethod = companionClass.getMethod("getThrowableFromIntent", intent)
        assertTrue(Throwable::class.java.isAssignableFrom(getMethod.returnType))
        // Nullable return can't be asserted at runtime; we only check the declared type.
    }

    /**
     * Sanity: the Gson strategy (JSON round-trip) works in general.
     * We DON'T use Throwable here because JDK 17+ blocks reflective access
     * to its internal fields, which causes JsonIOException.
     * Real Throwable JSON handling will be validated in instrumented tests.
     */
    @Test
    fun gson_can_roundtrip_simple_crash_payload() {
        data class CrashPayload(val message: String)

        val gson = Gson()
        val original = CrashPayload("crash-demo")
        val json = gson.toJson(original)
        val parsed = gson.fromJson(json, CrashPayload::class.java)

        assertEquals("crash-demo", parsed.message)
    }
}
