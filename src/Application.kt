package com.banana.api

import com.google.gson.Gson
import com.banana.api.ChuckNorrisJokeMessage.ErrorMessage
import com.banana.api.ChuckNorrisJokeMessage.JokeMessage
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readText
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.serialization.DefaultJsonConfiguration
import io.ktor.serialization.serialization
import kotlinx.serialization.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.modules.SerializersModule
import java.io.File

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@OptIn(UnstableDefault::class)
@ImplicitReflectionSerializer
fun Application.module(testing: Boolean = false) {
    install(ContentNegotiation) {
        gson {
            setPrettyPrinting()

            disableHtmlEscaping()
            disableInnerClassSerialization()
            enableComplexMapKeySerialization()

            serializeNulls()

            serializeSpecialFloatingPointValues()
            excludeFieldsWithoutExposeAnnotation()
        }
    }

    val client = HttpClient {
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
    }
    routing {
        get("/") {

            val messageModule = SerializersModule { // 1
                polymorphic(ChuckNorrisJokeMessage::class) { // 2
                    ErrorMessage::class with ErrorMessage.serializer()
                    JokeMessage::class with JokeMessage.serializer() // 4
                }
            }

            val json = Json(context = messageModule,configuration = JsonConfiguration(classDiscriminator = "kind"))
            //val json = Json(context = messageModule, configuration = JsonConfiguration(useArrayPolymorphism = true))
            //val json = Json(context = messageModule)
            val chuckNorrisJokes = (1..567).map {
                val response = client.get<HttpResponse> {
                    url("http://api.icndb.com/jokes/$it")
                    contentType(ContentType.Application.Json)
                }
                val message = response.readText()
                println(message)
                val apiResponseObject = json.parse(ChuckNorrisJokeMessage.serializer(), message)
                apiResponseObject
            }.filterIsInstance<JokeMessage>()
                .map {
                    it.value.id to it.value
                }.toMap()

            val file = File("chuck-norris-joke-db.txt")
            val mapLikeSerializer = MapSerializer(String.serializer(), ChuckNorrisJoke.serializer())
            file.writeText(Gson().toJson(chuckNorrisJokes))
            call.respond(chuckNorrisJokes)
        }
    }
}

@Serializable
sealed class ChuckNorrisJokeMessage {
    @Serializable
    data class ErrorMessage(@SerialName("kind") val type: String, val value: String) : ChuckNorrisJokeMessage()

    @Serializable
    data class JokeMessage(@SerialName("kind") val type: String, val value: ChuckNorrisJoke) : ChuckNorrisJokeMessage()
}

@Serializable
data class ChuckNorrisJoke(val id: String, val joke: String, val categories: List<String>)

