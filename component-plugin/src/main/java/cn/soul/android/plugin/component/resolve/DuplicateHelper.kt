package cn.soul.android.plugin.component.resolve

import java.io.File
import java.io.IOException
import java.util.*
import java.util.zip.ZipFile

/**
 * @author : panxinghai
 * date : 2019-06-25 18:06
 */
object DuplicateHelper {
    fun checkDuplicate(ap_File: File?): HashMap<Long, ArrayList<String>> {
        var zipFile: ZipFile? = null
        val crcMap = HashMap<Long, String>()
        val map = HashMap<Long, ArrayList<String>>()
        var count = 0
        var repeatCount = 0
        var totalDuplicateSize: Long = 0
        try {
            // open a zip file for reading
            zipFile = ZipFile(ap_File)
            // get an enumeration of the ZIP file entries
            val e = zipFile.entries()
            while (e.hasMoreElements()) {
                val entry = e.nextElement()
                // get the name of the entry
                val entryName = entry.name

                // get the CRC-32 checksum of the uncompressed entry data, or -1 if not known
                val crc = entry.crc

//                System.out.println(entryName + " with CRC-32: " + crc);
                count++
                val value = crcMap[crc]
                if (value == null) {
                    crcMap[crc] = entryName
                } else {
                    repeatCount++
                    totalDuplicateSize += entry.size
                    var list = map[crc]
                    if (list == null) {
                        list = ArrayList()
                        map[crc] = list
                        list.add(value)
                    }
                    list.add(entryName)
                }
            }
            println("resource file count:$count; found repeatCount:$repeatCount; duplicate resource total size:$totalDuplicateSize")
            for ((key, value) in map) {
//                System.out.println("crc: " + entry.getKey() + ":" + Arrays.toString(new List[]{Collections.singletonList(entry.getValue())}));
            }
            return map
        } catch (ioe: IOException) {
            println("Error opening zip file$ioe")
        } finally {
            try {
                zipFile?.close()
            } catch (ioe: IOException) {
                println("Error while closing zip file$ioe")
            }
        }
        return map
    }
}