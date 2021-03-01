package com.lovelysystems.signpdf

import com.lovelysystems.signpdf.signer.getSigner
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.DefaultHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.request.isMultipart
import io.ktor.request.receiveMultipart
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.routing.routing
import java.io.ByteArrayOutputStream

class PDFContent(private val bytes: ByteArray) : OutgoingContent.ByteArrayContent() {
    override fun bytes(): ByteArray = bytes
    override val contentType: ContentType?
        get() = ContentType("application", "pdf")
}

fun Application.main() {

    // pdfbox optimization: To get higher rendering speed on JDK8 or later
    System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider")
    install(DefaultHeaders)
    install(CallLogging)
    val config = environment.config.config("signer")
    val signer = getSigner(config)

    routing {
        post("/sign") {
            if (!call.request.isMultipart()) {
                call.respond(HttpStatusCode.BadRequest.description("Not a multipart request"))
            } else {
                val multipart = call.receiveMultipart()
                var name: String? = null
                var location: String? = null
                var reason: String? = null
                var contactInfo: String? = null
                var content: ByteArray? = null

                val failures = arrayListOf<String>()

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem ->
                            when (part.name) {
                                "name" -> name = part.value
                                "location" -> location = part.value
                                "reason" -> reason = part.value
                                "contactInfo" -> contactInfo = part.value
                                else -> failures.add("Unknown form field ${part.name}")
                            }
                        is PartData.FileItem ->
                            if (part.name == "file") {
                                part.streamProvider().use {
                                    content = it.readBytes()
                                }
                            } else {
                                failures.add("Unknown file field ${part.name}")
                            }
                    }

                }
                if (content == null) {
                    failures.add("file field is not defined")
                }
                if (!failures.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, failures.joinToString("\n"))
                } else {
                    val output = ByteArrayOutputStream()
                    val pdf = PDF(content!!.inputStream(), output, name!!, reason!!, location!!, contactInfo!!);
                    pdf.sign(signer)
                    pdf.close()
                    call.respond(PDFContent(output.toByteArray()))
                }
            }
        }
    }
}

