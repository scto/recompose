/*
 * Copyright 2020 Sebastian Kaspari
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package recompose.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import recompose.composer.Composer
import recompose.parser.Parser
import java.io.File
import java.lang.Exception

/**
 * Command-line interface (CLI) for translating layout XML files into Kotlin code using Jetpack Compose.
 */
class CommandLineComposer : CliktCommand(
    name = "recompose"
) {
    private val input by argument(
        help = "Layout XML files to convert to Kotlin"
    ).file(
        mustExist = true,
        canBeDir = false,
        mustBeReadable = true
    ).multiple(
        required = true
    )

    private val outputDirectory by option(
        "-o",
        "--output",
        help = "Output directory for Kotlin code"
    ).file(
        canBeDir = true,
        canBeFile = false,
        mustExist = true,
        mustBeWritable = true
    )

    val complete by option("-c", "--complete", help="Generate complete file, with imports and preview (Dinero specific - you need to customize the source)").flag()

    private val parser = Parser()
    private lateinit var composer : Composer

    override fun run() {
        composer = Composer(startIndentation = if (complete) 1 else 0)
        for (file in input) {
            val code = translate(file) ?: continue
            val filename = file.nameWithoutExtension
            val filecontent = if (!complete) code else
"""import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.RadioButton
import androidx.compose.material.RangeSlider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.core.content.ContextCompat
import dk.dinero.base.R
import dk.dinero.dinero.app.insights.DineroComposeTheme
import dk.dinero.dinero.app.konverteret.Image
import dk.dinero.dinero.app.konverteret.background
import dk.dinero.dinero.app.konverteret.imageResource

@Preview(showBackground = true)
@Composable
fun ${filename}Preview() {
    DineroComposeTheme {
        Surface(modifier = Modifier.wrapContentHeight()) {
            ${filename}()
        }
    }
    R.layout.${filename}
}

@Composable
@OptIn(ExperimentalMaterialApi::class)
fun ${filename}() {
    ${code}
}"""

            val target = determineTarget(outputDirectory, file)
            write(target, filecontent)

            println("Translated: ${file.name} -> ${target.path}")
        }
    }

    private fun translate(file: File): String? {
        file.bufferedReader().use { reader ->
            return try {
                val layout = parser.parse(reader)
                composer.compose(layout)
            } catch (e: Parser.ParserException) {
                showError(file, e)
                null
            } catch (e: Composer.ComposerException) {
                showError(file, e)
                null
            }
        }
    }

    private fun write(target: File, code: String) {
        target.outputStream().bufferedWriter().use { writer ->
            writer.write(code)
        }
    }

    private fun determineTarget(outputDirectory: File?, input: File): File {
        val targetDirectory = outputDirectory ?: input.parentFile

        val extension = input.name.lastIndexOf('.')
        val fileName = if (extension != -1) {
            input.name.substring(0, extension) + ".kt"
        } else {
            input.name + ".kt"
        }

        return File(targetDirectory, fileName)
    }

    private fun showError(file: File, e: Exception) {
        System.err.println("Could not translate file: ${file.path}")
        System.err.println(" - ${e.message}")
    }
}
