/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.collections.*
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.plugin.stat.*
import org.jetbrains.kotlin.gradle.report.BuildReportType
import org.jetbrains.kotlin.gradle.testbase.*
import org.jetbrains.kotlin.test.testFramework.KtUsefulTestCase.assertNotEmpty
import org.junit.jupiter.api.DisplayName
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

@DisplayName("Build statistics")
@JvmGradlePluginTests
class BuildStatisticsWithKtorIT : KGPBaseTest() {

    companion object {
        fun runWithKtorService(action: (Int) -> Unit) {
            val port = getEmptyPort().localPort
            val server = embeddedServer(Netty, host="localhost", port = port)
            {
                val requests = ArrayBlockingQueue<String>(10)

                routing {
                    post("/badRequest") {
                        System.err.println("KTOR DEBUG: bad request")
                        call.respond(HttpStatusCode.BadRequest, "Some reason")
                    }
                    post("/put") {
                        System.err.println("KTOR DEBUG: put")
                        val body = call.receive<String>()
                        requests.add(body)
                        System.err.println("KTOR DEBUG: body: $body")
                        call.respond(HttpStatusCode.OK)
                    }
                    get("/validate") {
                        try {
                            System.err.println("KTOR DEBUG: validate")
                            call.respond(status = HttpStatusCode.OK, requests.poll(2, TimeUnit.SECONDS))
                        } catch (e: Exception) {
                            call.respond(status = HttpStatusCode.NotFound, e.message ?: e::class)
                        }
                    }

                }
            }.start()
            action(port)
            server.stop(1000, 1000)
        }

        private fun getEmptyPort(): ServerSocket {
            val startPort = 8080
            val endPort = 8180
            for (port in startPort..endPort) {
                try {
                    return ServerSocket(port).also {
                        println("Use $port port")
                        it.close()
                    }
                } catch (_: IOException) {
                    continue // try next port
                }
            }
            throw IOException("Failed to find free IP port in range $startPort..$endPort")
        }

        private fun validateCall(port: Int, validate: (JsonObject) -> Unit) {
            try {
                val connection = URL("http://localhost:$port/validate").openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()
                assertEquals(HttpStatusCode.OK.value, connection.responseCode)
                val body = connection.inputStream.bufferedReader().readText()
                val jsonObject = JsonParser.parseString(body).asJsonObject
                validate(jsonObject)
            } catch (e: IOException) {
                assertFails { "Unable to open connection : ${e.message}" }
            }
        }

        fun validateTaskData(port: Int, validate: (CompileStatisticsData) -> Unit) {
            validateCall(port) { jsonObject ->
                val type = jsonObject["type"].asString
                assertEquals(BuildDataType.TASK_DATA, BuildDataType.valueOf(type))
                val taskData = Gson().fromJson(jsonObject, CompileStatisticsData::class.java)
                validate(taskData)
            }
        }

        fun validateBuildData(port: Int, validate: (BuildFinishStatisticsData) -> Unit) {
            validateCall(port) { jsonObject ->
                val type = jsonObject["type"].asString
                assertEquals(BuildDataType.BUILD_DATA, BuildDataType.valueOf(type))
                val buildData = Gson().fromJson(jsonObject, BuildFinishStatisticsData::class.java)
                validate(buildData)
            }
        }

    }

    @DisplayName("Http build report request problems are logged only ones")
    @GradleTest
    fun testHttpServiceWithBadRequest(gradleVersion: GradleVersion) {
        runWithKtorService { port ->
            project("incrementalMultiproject", gradleVersion) {
                enableStatisticReports(BuildReportType.HTTP, "http://localhost:$port/badRequest")
                build("assemble") {
                    assertOutputContainsExactTimes("Failed to send statistic to", 1)
                }
            }
        }
    }

    @DisplayName("Validate mandatory field for http request body")
    @GradleTest
    fun testHttpRequest(gradleVersion: GradleVersion) {
        runWithKtorService { port ->
            project("incrementalMultiproject", gradleVersion) {
                setProjectForTest(port)
                build("clean", "assemble") {
                    assertOutputDoesNotContain("Failed to send statistic to")
                }
            }
            validateTaskData(port) { taskData ->
                assertEquals(":lib:compileKotlin", taskData.taskName)
                assertContains(taskData.tags, "NON_INCREMENTAL")
                assertContains(taskData.nonIncrementalAttributes.map { it.name }, "UNKNOWN_CHANGES_IN_GRADLE_INPUTS")
                assertTrue(taskData.performanceMetrics.keys.isNotEmpty())
                assertTrue(taskData.buildTimesMetrics.keys.isNotEmpty())
                assertEquals(
                    defaultBuildOptions.kotlinVersion, taskData.kotlinVersion,
                    "Unexpected kotlinVersion: ${taskData.kotlinVersion} instead of ${defaultBuildOptions.kotlinVersion}"
                )
            }
            validateTaskData(port) { taskData ->
                assertEquals(":app:compileKotlin", taskData.taskName)
                assertContains(taskData.tags, "NON_INCREMENTAL")
                assertContains(taskData.nonIncrementalAttributes.map { it.name }, "UNKNOWN_CHANGES_IN_GRADLE_INPUTS")
                assertTrue(taskData.performanceMetrics.keys.isNotEmpty())
                assertTrue(taskData.buildTimesMetrics.keys.isNotEmpty())
                assertEquals(
                    defaultBuildOptions.kotlinVersion, taskData.kotlinVersion,
                    "Unexpected kotlinVersion: ${taskData.kotlinVersion} instead of ${defaultBuildOptions.kotlinVersion}"
                )
            }
            validateBuildData(port) { buildData ->
                assertContains(buildData.startParameters.tasks, "assemble")
            }
        }
    }

    @DisplayName("Validate configuration cache tag")
    @GradleTest
    fun testConfigurationCache(gradleVersion: GradleVersion) {
        runWithKtorService { port ->

            val buildOptions = defaultBuildOptions.copy(configurationCache = true)
            project("incrementalMultiproject", gradleVersion) {
                setProjectForTest(port)
                build("assemble", buildOptions = buildOptions) {
                    assertOutputDoesNotContain("Failed to send statistic to")
                }
                projectPath.resolve("lib/src/main/kotlin/bar/B.kt").modify { it.replace("fun b() {}", "fun b() = 1") }
                build("assemble", buildOptions = buildOptions) {
                    assertOutputDoesNotContain("Failed to send statistic to")
                }
            }
            validateTaskData(port) { taskData ->
                assertEquals(":lib:compileKotlin", taskData.taskName)
                assertContentEquals(listOf("ARTIFACT_TRANSFORM", "CONFIGURATION_CACHE", "NON_INCREMENTAL"), taskData.tags.sorted(), )
                assertEquals(
                    defaultBuildOptions.kotlinVersion, taskData.kotlinVersion,
                    "Unexpected kotlinVersion: ${taskData.kotlinVersion} instead of ${defaultBuildOptions.kotlinVersion}"
                )
            }
            validateTaskData(port) { taskData ->
                assertEquals(":app:compileKotlin", taskData.taskName)
                assertContentEquals(taskData.tags.sorted(), listOf("ARTIFACT_TRANSFORM", "CONFIGURATION_CACHE", "NON_INCREMENTAL"))
                assertEquals(
                    defaultBuildOptions.kotlinVersion, taskData.kotlinVersion,
                    "Unexpected kotlinVersion: ${taskData.kotlinVersion} instead of ${defaultBuildOptions.kotlinVersion}"
                )
            }
            validateBuildData(port) { buildData ->
                assertContains(buildData.startParameters.tasks, "assemble")
            }
            //second build
            validateTaskData(port) { taskData ->
                assertEquals(":lib:compileKotlin", taskData.taskName)
                assertContentEquals(taskData.tags.sorted(), listOf("ARTIFACT_TRANSFORM", "CONFIGURATION_CACHE", "INCREMENTAL"))
            }
            validateTaskData(port) { taskData ->
                assertEquals(":app:compileKotlin", taskData.taskName)
                assertContentEquals(taskData.tags.sorted(), listOf("ARTIFACT_TRANSFORM", "CONFIGURATION_CACHE", "INCREMENTAL"))
            }
        }
    }

    private fun TestProject.setProjectForTest(port: Int) {
        enableStatisticReports(BuildReportType.HTTP, "http://localhost:$port/put")
    }
}






