package r2dbcfun

object ProjectConfig {
    const val eap = false
    val kotlinVersion = if (eap) "1.4.20-RC" else "1.4.10"
    const val useKotestSnapshot = false
}