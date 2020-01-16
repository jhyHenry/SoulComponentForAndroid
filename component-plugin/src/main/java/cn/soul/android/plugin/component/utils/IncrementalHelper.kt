package cn.soul.android.plugin.component.utils

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import javassist.CtClass
import org.apache.commons.codec.digest.DigestUtils
import java.io.File
import java.lang.reflect.Type

/**
 * @author panxinghai
 *
 * date : 2020-01-14 17:02
 */
object IncrementalHelper {
    private lateinit var gson: Gson

    init {
        registerGsonAdapter()
    }

    private fun saveInfo(info: Any, type: Type, fileName: String, outputDir: File) {
        val string = gson.toJson(info, type)
        val file = File(outputDir, "persistence-${DigestUtils.md5Hex(fileName)}.json")
        file.writeText(string)
    }

    private fun <T> loadInfo(type: Type, fileName: String, outputDir: File): T? {
        val file = File(outputDir, "persistence-${DigestUtils.md5Hex(fileName)}.json")
        if (!file.exists()) {
            return null
        }
        val string = file.readText()
        return gson.fromJson<T>(string, type)
    }

    fun savePersistentField(target: Any, outputDir: File) {
        target.javaClass.declaredFields.forEach {
            val persistent = it.getAnnotation(Persistent::class.java) ?: return@forEach
            val fileName = if (persistent.fileName == "") it.name else persistent.fileName
            it.isAccessible = true
            saveInfo(it.get(target), it.genericType, fileName, outputDir)
        }
    }

    fun loadPersistentField(target: Any, outputDir: File) {
        target.javaClass.declaredFields.forEach {
            val persistent = it.getAnnotation(Persistent::class.java) ?: return@forEach
            val fileName = if (persistent.fileName == "") it.name else persistent.fileName
            val info = loadInfo<Any>(it.genericType, fileName, outputDir)
            if (info != null) {
                it.isAccessible = true
                it.set(target, info)
            }
        }
    }

    private fun registerGsonAdapter() {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.registerTypeAdapter(CtClass::class.java, CtClassTypeAdapter())
                .registerTypeAdapter(Collection::class.java, CollectionAdapter())
        gson = gsonBuilder.create()
    }

    private class CtClassTypeAdapter : TypeAdapter<CtClass>() {
        override fun write(out: JsonWriter?, value: CtClass?) {
            if (out == null) {
                return
            }
            out.beginObject()
            out.name("javassist-ctClass").value(value?.name)
            out.endObject()
        }

        override fun read(`in`: JsonReader?): CtClass {
            var ctClass: CtClass = CtClass.voidType
            if (`in` == null) {
                return ctClass
            }
            `in`.beginObject()
            while (`in`.hasNext()) {
                if (`in`.nextName() == "javassist-ctClass") {
                    try {
                        ctClass = InjectHelper.instance.getClassPool()[`in`.nextString()]
                    } catch (e: Exception) {
                        //cannot got ctClass, its most likely the class has been deleted
                        Log.w(e.toString())
                    }
                }
                break
            }
            `in`.endObject()
            return ctClass
        }
    }

    class CollectionAdapter : JsonDeserializer<Collection<*>> {
        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Collection<*>? {
            val collection = context?.deserialize<Collection<*>>(json, typeOfT)
            if (collection != null) {
                val iterator = collection.iterator()
                if (iterator is MutableIterator) {
                    iterator.forEach {
                        if (it is CtClass && it == CtClass.voidType) {
                            Log.e("remove ct class. type:$typeOfT.")
                            iterator.remove()
                            return@forEach
                        }
                        if (it == null) {
                            iterator.remove()
                            return@forEach
                        }
                    }
                }
            }
            return collection
        }
    }

//    class MapAdapter : JsonDeserializer<Map<*, CtClass>> {
//        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Collection<CtClass>? {
//            val collection = context?.deserialize<Collection<CtClass>>(json, typeOfT)
//            if (collection != null) {
//                val iterator = collection.iterator()
//                if (iterator is MutableIterator) {
//                    iterator.forEach {
//                        if (it == CtClass.voidType) {
//                            Log.e("remove ct class. type:$typeOfT.")
//                            iterator.remove()
//                        }
//                    }
//                }
//            }
//            return collection
//        }
//    }
}