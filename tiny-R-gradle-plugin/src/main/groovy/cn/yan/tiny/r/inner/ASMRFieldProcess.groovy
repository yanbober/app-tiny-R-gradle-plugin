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
package cn.yan.tiny.r.inner

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

class ASMRFieldProcess {
    private RFieldInfo mRFieldInfo
    private boolean mDebug

    ASMRFieldProcess(boolean debug) {
        mRFieldInfo = new RFieldInfo()
        mDebug = debug
    }

    ASMRFieldProcess prepareFromJar(File file) {
        initRFieldInfoFromJar(file)
        return this
    }

    ASMRFieldProcess tinyReplaceForJar(File file) {
        replaceJarUseRPlace(file)
        return this
    }

    RFieldInfo getRFieldInfo() {
        return mRFieldInfo
    }

    private void initRFieldInfoFromJar(File file) {
        if (file.isDirectory()) {
            file.eachFileRecurse {
                initRFieldInfoFromJar(it)
            }
        } else {
            if (!file.name.endsWith(".jar")) {
                return
            }

            if (mDebug) println(file.path)
            JarFile jarFile = new JarFile(file)
            Enumeration<JarEntry> jarEntryEnumeration = jarFile.entries()
            while (jarEntryEnumeration.hasMoreElements()) {
                JarEntry jarEntry = jarEntryEnumeration.nextElement()
                InputStream inputStream = jarFile.getInputStream(jarEntry)
                if (!isMatchedROrSubClass(jarEntry.name)) {
                    continue
                }
                ClassReader classReader = new ClassReader(inputStream)
                ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM4) {
                    @Override
                    FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                        if (value instanceof Integer) {
                            String key = RFieldInfo.generateFieldInsnKey(jarEntry.name-".class", name)
                            mRFieldInfo.integerFieldMap.put(key, value)
                        } else {
                            mRFieldInfo.fieldContainsArrayFiles.add(jarEntry.name)
                        }
                        return super.visitField(access, name, desc, signature, value)
                    }
                }
                classReader.accept(classVisitor, 0)

                inputStream.close()
            }
            jarFile.close()
        }
    }

    private void replaceJarUseRPlace(File srcFile) {
        if (srcFile.isDirectory()) {
            srcFile.eachFileRecurse {
                replaceJarUseRPlace(it)
            }
        } else {
            if (!srcFile.name.endsWith(".jar")) {
                return
            }
            if (mDebug) println(srcFile.path)
            File targetFile = new File(srcFile.parentFile, srcFile.name+".tmp")
            if (targetFile.exists()) {
                targetFile.delete()
            }
            targetFile.createNewFile()

            JarFile jarFile = new JarFile(srcFile)
            Enumeration<JarEntry> jarEntryEnumeration = jarFile.entries()
            JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(targetFile))
            while (jarEntryEnumeration.hasMoreElements()) {
                JarEntry jarEntry = jarEntryEnumeration.nextElement()
                ZipEntry zipEntry = new ZipEntry(jarEntry.name)
                InputStream inputStream = jarFile.getInputStream(jarEntry)
                byte[] bytes = inputStream.bytes

                if (jarEntry.name.endsWith(".class") && !isMatchedROrSubClass(jarEntry.name)) {
                    ClassReader classReader = new ClassReader(bytes)
                    ClassWriter classWriter = new ClassWriter(classReader, 0)
                    ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM4, classWriter) {
                        @Override
                        MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                            MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions)
                            methodVisitor = new MethodVisitor(Opcodes.ASM4, methodVisitor) {
                                @Override
                                void visitFieldInsn(int opcode, String owner, String name1, String desc1) {
                                    String key = RFieldInfo.generateFieldInsnKey(owner, name1)
                                    Integer value = mRFieldInfo.integerFieldMap.get(key)
                                    if (value != null) {
                                        if (mDebug) println "replace field ${key} to constant in ${jarEntry.name}"
                                        super.visitLdcInsn(value)
                                    } else {
                                        super.visitFieldInsn(opcode, owner, name1, desc1)
                                    }
                                }
                            }
                            return methodVisitor
                        }
                    }
                    classReader.accept(classVisitor, 0)
                    bytes = classWriter.toByteArray()
                }

                boolean skype = false
                if (isMatchedROrSubClass(jarEntry.name)) {
                    if (mRFieldInfo.fieldContainsArrayFiles.contains(jarEntry.name)) {
                        bytes = getClearedContainsArrayNoArrayField(bytes)
                        if (mDebug) println "resize conatins final array class ${jarEntry.name}"
                    } else {
                        if (mDebug) println "remove full repace class ${jarEntry.name}"
                        skype = true
                    }
                }

                if (!skype) {
                    jarOutputStream.putNextEntry(zipEntry)
                    jarOutputStream.write(bytes)
                    jarOutputStream.closeEntry()
                }
                inputStream.close()
            }
            jarOutputStream.close()
            jarFile.close()
            srcFile.delete()
            targetFile.renameTo(srcFile)
        }
    }

    private byte[] getClearedContainsArrayNoArrayField(byte[] bytes) {
        ClassReader classReader = new ClassReader(bytes)
        ClassWriter classWriter = new ClassWriter(classReader, 0)
        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM4, classWriter) {
            @Override
            FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                if (value instanceof Integer) {
                    return null
                }
                return super.visitField(access, name, desc, signature, value)
            }
        }
        classReader.accept(classVisitor, 0)
        return classWriter.toByteArray()
    }

    private boolean isMatchedROrSubClass(String filePath) {
        if (filePath ==~ '''.*/R\\.class|.*/R\\$.*\\.class''') {
            return true
        }
        return false
    }
}