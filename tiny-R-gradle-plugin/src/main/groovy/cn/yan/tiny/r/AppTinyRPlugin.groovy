/**
 * MIT License
 *
 * Copyright (c) 2018 yanbo
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package cn.yan.tiny.r

import cn.yan.tiny.r.inner.ASMRFieldProcess
import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.ApkVariant
import com.android.build.gradle.api.BaseVariantOutput
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class AppTinyRPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        if (project.plugins.hasPlugin(LibraryPlugin)) {
            throw new GradleException("AppTinyRPlugin can't be used to library module.")
        }

        if (project.plugins.hasPlugin(AppPlugin)) {
            project.extensions.create(AppTinyRExtension.NAME, AppTinyRExtension)

            project.afterEvaluate {
                def appTinyR = project[AppTinyRExtension.NAME]
                def enabled = (appTinyR == null) ? false : appTinyR.enabled
                def debug = (appTinyR == null) ? false : appTinyR.debug
                if (enabled) {
                    if (debug) println "App tiny R field gradle plugin is enable."
                    BaseExtension android = project.extensions.getByType(AppExtension)
                    androidVariantRun(project, android, debug)
                }
            }
        }
    }

    private void androidVariantRun(Project project, BaseExtension android, boolean debug) {
        android.applicationVariants.all { ApkVariant variant ->
            variant.outputs.each { BaseVariantOutput output ->
                def taskName = "transformClassesAndResourcesWithProguardFor${variant.name.capitalize()}"
                def dexTask = project.tasks.findByName(taskName)
                if (!dexTask) {
                    return
                }

                dexTask.doLast {
                    if (debug) println "start init R field info from jar..."
                    ASMRFieldProcess asmrFieldProcess = new ASMRFieldProcess(debug)
                    dexTask.outputs.files.files.each {
                        asmrFieldProcess.prepareFromJar(it)
                    }
                    if (debug) println "init R field info from jar result is:${asmrFieldProcess.getRFieldInfo().toString()}\n"

                    if (debug) println "\nstart replace final Integer field to constant and tiny the class..."
                    dexTask.outputs.files.files.each {
                        asmrFieldProcess.tinyReplaceForJar(it)
                    }
                    if (debug) println "replace final Integer field to constant and tiny the class finished."
                }
            }
        }
    }
}
