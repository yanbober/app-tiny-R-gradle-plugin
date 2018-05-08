# app-tiny-R-gradle-plugin

一个有效优化减少 Android 项目 Field 个数，减小 APK 大小的构建工具。

在我们平时项目中遇到方法数 65535 问题很常见，遇到 Field 数 65535 就不是那么常见了，最近由于项目结构调整遇见了这个问题，分析项目和构建产物后发现对 R 文件进行处理可以有效的缩减 Field 个数，效果非常明显，同时由于 R 的缩减也带来了方法数、Class 个数、APK 包大小的适当缩小。

通过这个插件的原理你会对 Android 多项目构建 R 文件及资源构建合并等知识有深度的理解，也会发现 Google Android 团队当年将子项目的 R Field 改为非 final 说是为了提升构建性能和防止资源索引冲突，但是却带来了很多冗余的 Field 被打包到了最终的 APK，所以说当初这个优化看起来其实没有那么完美，期待将来的官方构建工具对这块有所改进。

## 使用

在主项目工程模块的 build.gradle 文件中添加如下配置：
```gradle
buildscript {
    repositories {
        maven { url 'https://jitpack.io' }
        }
    }

    dependencies {
        classpath 'com.github.yanbober:app-tiny-R-gradle-plugin:1.0.0'
    }
}

apply plugin: 'appTinyR'
```

在 apply plugin: 'appTinyR' 之后任意构建脚本位置添加如下开关：
```gradle
appTinyR {
    enabled true //开启优化
    debug true  //是否打印优化过程构建详细信息
}
```

在你的项目混淆配置文件中添加如下配置：
```
-keepclassmembers class **.R$* {
	 public static <fields>;
}

-keep class **.R$* {*;}
```

项目开启混淆然后开始构建即可享用优化带来的效果。

## 注意

本插件依赖 Android Gradle 构建过程的混淆任务（transformClassesAndResourcesWithProguardFor${variant.name.capitalize()}"），也就意味着该插件的优化操作必须是开启混淆的构建任务，所以务必开启混淆。

## 效果

以下数据以一个无任何业务逻辑新建的 App 工程开启混淆编译 release 包为例，如果项目中包含带资源的 aar 包或者 lib module 越多（组件化架构）其优化效果越明显。

未使用 app-tiny-R-gradle-plugin 时 release 包相关数据：
```
//unused appTinyR plugin
:demo-app:countReleaseDexMethods
Total methods in demo-app-release-unsigned.apk: 8359 (12.76% used)
Total fields in demo-app-release-unsigned.apk:  8331 (12.71% used)
Total classes in demo-app-release-unsigned.apk:  1069 (1.63% used)
```

使用 app-tiny-R-gradle-plugin 时 release 包相关数据：
```
//used appTinyR plugin
:demo-app:countReleaseDexMethods
Total methods in demo-app-release-unsigned.apk: 8263 (12.61% used)
Total fields in demo-app-release-unsigned.apk:  3285 (5.01% used)
Total classes in demo-app-release-unsigned.apk:  984 (1.50% used)
```

以上数据使用 dexcount gradle 插件生成，通过上面对比数据可以发现，对于最简单的 App 来说使用插件后 Total fields 迅速减少了 60.5% 以上，Total methods 和 Total classes 及最终 apk 大小也得到了适当的减少，总体来说对于 Field 爆炸来说有非常可观的效果。

## 原理

将所有项目模块及 aar 中的 R class 类尽可能优化掉，然后将整个项目使用到这些 R class 中 Field 的地方尽可能通过 ASM 字节码工具替换成常量来达到优化。
之所以说尽可能优化掉是因为 R 文件中有些 Field 不是 `final Integer` 的，是 `final Integer[]` 的，所以遇到这些数组类型的 Field 就不能直接替换常量，需要保持，故而对于包含数组 Field 的 R 文件就仅仅替换删除其中非数组的 Field 来让其尽可能变少。对于所有 Field 都是 final Integer 来说就可以完全删掉这个 R 类进行替换。
插件 Hook 位置选择在混淆之后是因为在第一版中是通过 Android Gradle 的 Transform API 在生成 class 文件后进行优化的，发现优化后如果开启混淆会导致混淆失败，因为 ASM 是没法删除已编译的 class 中 Constant Pool 部分，所以 ASM 替换后类中 Fieldref 的值还是 "xx/xx/xxx/R$string"，故而导致混淆分析时会提示 "xx/xx/xxx/R$string" missing 了，所以就换了思路放在混淆产出中去处理优化。
由于混淆之后再通过 ASM 替换会遇到正则匹配失败问题，所以需要在混淆清单中配置 keep R 类操作（不用担心不混淆它带来体积问题，因为大多数 R 最终都被删掉了）。
此外在实现插件过程中有小伙伴有疑问，说构建混淆流程就会将常量进行替换，为什么还要做这个插件？原因很简单，有这些疑惑的小伙伴可以看下自己的构建产物，同时其官方文档查看下构建资源合并，还有就是想想如果是那样的话，子模块及 aar 中的资源索引怎么办，然后就明白啦。

## 调试

如果你在打包过程中想观察详细优化 R Field 流程及效果，可以按照如下步骤操作查看。

```gradle
apply plugin: 'appTinyR'

appTinyR {
    enabled true
    debug true //开启调试
}
```

你会在 Gradle 构建过程中看到如下关于 tiny R DEBUG 信息（有删减）：
```
start init R field info from jar...
/home/yan/github/app-tiny-R-gradle-plugin/demo-app/build/intermediates/transforms/proguard/release/0.jar
init R field info from jar result is:
integerFieldMap size: 5522
fieldContainsArrayFiles size: 10
fieldContainsArrayFiles is: [android/support/v7/appcompat/R$styleable.class, android/support/compat/R$styleable.class, android/support/coreui/R$styleable.class, android/support/graphics/drawable/R$styleable.class, android/support/coreutils/R$styleable.class, android/support/graphics/drawable/animated/R$styleable.class, cn/yan/demo/reslib/R$styleable.class, android/support/fragment/R$styleable.class, cn/yan/app/R$styleable.class, android/support/constraint/R$styleable.class]

start replace final Integer field to constant and tiny the class...
/home/yan/github/app-tiny-R-gradle-plugin/demo-app/build/intermediates/transforms/proguard/release/0.jar
replace field android/support/v7/appcompat/R$styleable@ActionBarLayout_android_layout_gravity to constant in android/support/v7/app/a$a.class
......
......
replace field android/support/compat/R$id@tag_transition_group to constant in android/support/v4/g/q$c.class

remove full repace class cn/yan/demo/reslib/R$dimen.class
remove full repace class cn/yan/demo/reslib/R$anim.class
......
resize conatins final array class cn/yan/app/R$styleable.class
remove full repace class cn/yan/app/R$mipmap.class
......
remove full repace class android/support/coreui/R$layout.class
remove full repace class android/support/coreui/R$drawable.class
remove full repace class android/support/coreui/R$bool.class
......
resize conatins final array class android/support/coreui/R$styleable.class

replace final Integer field to constant and tiny the class finished.
```
