package cn.soul.android.plugin.component.action

import java.io.File

/**
 * Created by nebula on 2019-08-25
 */
class RFileAction {
    companion object {
        fun removeRFileFinalModifier(rFile: File) {
            rFile.walk()
                    .filter { it.name == "R.java" }
                    .forEach {
                        val stringBuilder = StringBuilder()
                        it.useLines { seq ->
                            seq.forEach { str ->
                                stringBuilder.append(str.replace("public static final", "public static"))
                                        .append("\n")
                            }
                        }
                        it.writeText(stringBuilder.toString())
                    }
        }
    }
}