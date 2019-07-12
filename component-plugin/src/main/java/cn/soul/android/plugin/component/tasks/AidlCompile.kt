package cn.soul.android.plugin.component.tasks

import cn.soul.android.plugin.component.PluginVariantScope
import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.AIDL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.tasks.IncrementalTask
import com.android.builder.compiling.DependencyFileProcessor
import com.android.builder.internal.incremental.DependencyData
import com.android.builder.internal.incremental.DependencyDataStore
import com.android.ide.common.internal.WaitableExecutor
import com.android.ide.common.process.LoggedProcessOutputHandler
import com.android.ide.common.process.ProcessException
import com.android.ide.common.process.ProcessOutputHandler
import com.android.ide.common.resources.FileStatus
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import com.google.common.collect.Multimap
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.util.PatternSet
import java.io.File
import java.io.IOException
import java.util.*
import java.util.function.Supplier

/**
 * @author panxinghai
 *
 * date : 2019-07-11 17:20
 */
open class AidlCompile : IncrementalTask() {
    private val DEPENDENCY_STORE = "dependency.store"
    private val PATTERN_SET = PatternSet().include("**/*.aidl")

    private var sourceOutputDir: File? = null

    @Nullable
    private var packagedDir: File? = null

    @Nullable
    private var packageWhitelist: Collection<String>? = null

    private var sourceDirs: Supplier<Collection<File>>? = null
    private var importDirs: FileCollection? = null

    @Input
    fun getBuildToolsVersion(): String {
        return buildTools.revision.toString()
    }

    @InputFiles
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getSourceFiles(): FileTree {
        // this is because aidl may be in the same folder as Java and we want to restrict to
        // .aidl files and not java files.
        return project.files(sourceDirs!!.get()).asFileTree.matching(PATTERN_SET)
    }

    private class DepFileProcessor : DependencyFileProcessor {
        internal var dependencyDataList: MutableList<DependencyData> =
                Collections.synchronizedList(Lists.newArrayList())


        @Throws(IOException::class)
        override fun processFile(@NonNull dependencyFile: File): DependencyData? {
            val data = DependencyData.parseDependencyFile(dependencyFile)
            if (data != null) {
                dependencyDataList.add(data)
            }

            return data
        }
    }

    @Internal
    override fun isIncremental(): Boolean {
        // TODO fix once dep file parsing is resolved.
        return false
    }

    /**
     * Action methods to compile all the files.
     *
     *
     * The method receives a [DependencyFileProcessor] to be used by the [ ] during the compilation.
     *
     * @param dependencyFileProcessor a DependencyFileProcessor
     */
    @Throws(InterruptedException::class, ProcessException::class, IOException::class)
    private fun compileAllFiles(dependencyFileProcessor: DependencyFileProcessor) {
        builder.compileAllAidlFiles(
                sourceDirs!!.get(),
                getSourceOutputDir()!!,
                getPackagedDir(),
                getPackageWhitelist(),
                getImportDirs()!!.files,
                dependencyFileProcessor,
                LoggedProcessOutputHandler(iLogger)
        )
    }

    /** Returns the import folders.  */
    @NonNull
    @Internal
    private fun getImportFolders(): Iterable<File> {
        return Iterables.concat(getImportDirs()!!.files, sourceDirs!!.get())
    }

    /**
     * Compiles a single file.
     *
     * @param sourceFolder the file to compile.
     * @param file the file to compile.
     * @param importFolders the import folders.
     * @param dependencyFileProcessor a DependencyFileProcessor
     */
    @Throws(InterruptedException::class, ProcessException::class, IOException::class)
    private fun compileSingleFile(
            @NonNull sourceFolder: File,
            @NonNull file: File,
            @Nullable importFolders: Iterable<File>,
            @NonNull dependencyFileProcessor: DependencyFileProcessor,
            @NonNull processOutputHandler: ProcessOutputHandler
    ) {
        builder.compileAidlFile(
                sourceFolder,
                file,
                getSourceOutputDir()!!,
                getPackagedDir(),
                getPackageWhitelist(),
                Preconditions.checkNotNull(importFolders),
                dependencyFileProcessor,
                processOutputHandler
        )
    }

    @Throws(IOException::class)
    override fun doFullTaskAction() {
        // this is full run, clean the previous output
        val destinationDir = getSourceOutputDir()
        val parcelableDir = getPackagedDir()
        FileUtils.cleanOutputDir(destinationDir!!)
        if (parcelableDir != null) {
            FileUtils.cleanOutputDir(parcelableDir)
        }

        val processor = DepFileProcessor()

        try {
            compileAllFiles(processor)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

        val dataList = processor.dependencyDataList

        val store = DependencyDataStore()
        store.addData(dataList)

        try {
            store.saveTo(File(incrementalFolder, DEPENDENCY_STORE))
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

    @Throws(IOException::class)
    override fun doIncrementalTaskAction(changedInputs: Map<File, FileStatus>?) {
        val incrementalData = File(incrementalFolder, DEPENDENCY_STORE)
        val store = DependencyDataStore()
        val inputMap: Multimap<String, DependencyData>
        try {
            inputMap = store.loadFrom(incrementalData)
        } catch (ignored: Exception) {
            FileUtils.delete(incrementalData)
            project.logger.info(
                    "Failed to read dependency store: full task run!"
            )
            doFullTaskAction()
            return
        }

        val importFolders = getImportFolders()
        val processor = DepFileProcessor()
        val processOutputHandler = LoggedProcessOutputHandler(iLogger)

        // use an executor to parallelize the compilation of multiple files.
        val executor = WaitableExecutor.useGlobalSharedThreadPool()

        val mainFileMap = store.mainFileMap

        for ((file, status) in changedInputs!!) {

            when (status) {
                FileStatus.NEW -> executor.execute<Any> {
                    compileSingleFile(
                            getSourceFolder(file), file, importFolders,
                            processor, processOutputHandler
                    )
                    null
                }
                FileStatus.CHANGED -> {
                    val impactedData = inputMap.get(file.absolutePath)
                    if (impactedData != null) {
                        for (data in impactedData) {
                            executor.execute<Any> {
                                val file = File(data.mainFile)
                                compileSingleFile(
                                        getSourceFolder(file), file,
                                        importFolders, processor, processOutputHandler
                                )
                                null
                            }
                        }
                    }
                }
                FileStatus.REMOVED -> {
                    val data2 = mainFileMap[file.absolutePath]
                    if (data2 != null) {
                        executor.execute<Any> {
                            cleanUpOutputFrom(data2)
                            null
                        }
                        store.remove(data2)
                    }
                }
            }
        }

        try {
            executor.waitForTasksWithQuickFail<Any>(true /*cancelRemaining*/)
        } catch (t: Throwable) {
            FileUtils.delete(incrementalData)
            throw RuntimeException(t)
        }

        // get all the update data for the recompiled objects
        store.updateAll(processor.dependencyDataList)

        try {
            store.saveTo(incrementalData)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

    private fun getSourceFolder(@NonNull file: File): File {
        var parentDir: File? = file
        while (parentDir != null) {
            if (sourceDirs!!.get().contains(parentDir)) {
                return parentDir
            }
            parentDir = packagedDir?.parentFile
        }

        throw IllegalArgumentException(String.format("File '%s' is not in a source dir", file))
    }

    @Throws(IOException::class)
    private fun cleanUpOutputFrom(@NonNull dependencyData: DependencyData) {
        for (output in dependencyData.outputFiles) {
            FileUtils.delete(File(output))
        }
        for (output in dependencyData.secondaryOutputFiles) {
            FileUtils.delete(File(output))
        }
    }

    @OutputDirectory
    fun getSourceOutputDir(): File? {
        return sourceOutputDir
    }

    fun setSourceOutputDir(sourceOutputDir: File) {
        this.sourceOutputDir = sourceOutputDir
    }

    @OutputDirectory
    @Optional
    @Nullable
    fun getPackagedDir(): File? {
        return packagedDir
    }

    fun setPackagedDir(@Nullable packagedDir: File) {
        this.packagedDir = packagedDir
    }

    @Input
    @Optional
    @Nullable
    fun getPackageWhitelist(): Collection<String>? {
        return packageWhitelist
    }

    fun setPackageWhitelist(@Nullable packageWhitelist: Collection<String>) {
        this.packageWhitelist = packageWhitelist
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getImportDirs(): FileCollection? {
        return importDirs
    }

    class ConfigAction(
            private val scope: PluginVariantScope
    ) : TaskConfigAction<AidlCompile> {

        @NonNull
        override fun getName(): String {
            return scope.getTaskName("compile", "Aidl")
        }

        @NonNull
        override fun getType(): Class<AidlCompile> {
            return AidlCompile::class.java
        }

        override fun execute(@NonNull aidlTask: AidlCompile) {
            val variantConfiguration = scope.getVariantConfiguration()

            scope.getTaskContainer().pluginAidlCompile = aidlTask

            aidlTask.setAndroidBuilder(scope.getGlobalScope().androidBuilder)
            aidlTask.variantName = scope.getVariantConfiguration().fullName
            aidlTask.incrementalFolder = scope.getIncrementalDir(name)

            aidlTask.sourceDirs = Supplier { variantConfiguration.aidlSourceList }
            aidlTask.importDirs = scope.getArtifactFileCollection(
                    COMPILE_CLASSPATH, ALL, AIDL
            )

            aidlTask.sourceOutputDir = scope.getAidlSourceOutputDir()

            if (variantConfiguration.type.isAar) {
                aidlTask.packagedDir = scope.getArtifacts()
                        .appendArtifact(
                                InternalArtifactType.AIDL_PARCELABLE, aidlTask, "out"
                        )
                aidlTask.packageWhitelist = scope.getGlobalScope().extension.aidlPackageWhiteList
            }
        }
    }
}