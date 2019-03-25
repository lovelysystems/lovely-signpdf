package com.lovelysystems.signpdf

import io.ktor.config.ApplicationConfig
import java.io.File

fun getSigner(signerConfig: ApplicationConfig): Signer {
    when (signerConfig.property("type").getString()) {
        "swisscom" -> return createSwisscomSigner(signerConfig)
        else ->
            return createSelfSignedSigner(signerConfig)
    }
}


private fun createSwisscomSigner(config: ApplicationConfig): Signer {
    val keyStorePath = config.property("keyStorePath").getString()
    val keyStorePass = File(config.property("keyStorePassPath").getString()).readLines().first().toCharArray()
    val keyStoreStream = File(keyStorePath).inputStream()
    val connector = SwisscomConnector(
            config.property("url").getString(),
            keyStoreStream,
            keyStorePass,
            config.property("timeout").getString().toInt()
    )

    val soap = Soap(config.property("claimIdentity").getString(), connector)
    return SwisscomSigner(soap)
}

private fun createSelfSignedSigner(config: ApplicationConfig): Signer {
    return SelfSignedSigner()
}
