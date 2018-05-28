package com.lovelysystems.signpdf

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.content.OutgoingContent
import io.ktor.content.PartData
import io.ktor.content.forEachPart
import io.ktor.features.CallLogging
import io.ktor.features.DefaultHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.isMultipart
import io.ktor.request.receiveMultipart
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.routing.routing
import java.io.ByteArrayOutputStream
import java.io.File

class PDFContent(private val bytes: ByteArray) : OutgoingContent.ByteArrayContent() {
    override fun bytes(): ByteArray = bytes
    override val contentType: ContentType?
        get() = ContentType("application", "pdf")
}

fun Application.main() {
    install(DefaultHeaders)
    install(CallLogging)

    val config = environment.config.config("signer")
    val keyStorePath = config.property("keyStorePath").getString()
    val keyStorePass = config.property("keyStorePass").getString().toCharArray()
    val keyStoreStream = File(keyStorePath).inputStream()

    val signer = Sign(keyStoreStream, keyStorePass)

    routing {
        post("/sign") {
            //val multipart = call.receiveMultipart()
            if (!call.request.isMultipart()) {
                call.respond(HttpStatusCode.BadRequest.description("Not a multipart request"))
            } else {
                val multipart = call.receiveMultipart()

                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val output = ByteArrayOutputStream()
                        part.streamProvider().use {
                            signer.sign(it, output)
                        }
                        call.respond(PDFContent(output.toByteArray()))
                    }
                }
            }
        }
    }
}
