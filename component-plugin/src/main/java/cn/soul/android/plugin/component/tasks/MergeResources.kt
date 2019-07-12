package cn.soul.android.plugin.component.tasks

import android.databinding.tool.LayoutXmlProcessor
import cn.soul.android.plugin.component.PluginVariantScope
import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.aapt.AaptGeneration
import com.android.build.gradle.internal.aapt.AaptGradleFactory
import com.android.build.gradle.internal.aapt.WorkerExecutorResourceCompilationService
import com.android.build.gradle.internal.api.sourcesets.FilesProvider
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ANDROID_RES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.res.getAapt2FromMavenIfEnabled
import com.android.build.gradle.internal.res.namespaced.NamespaceRemover
import com.android.build.gradle.internal.res.namespaced.registerAaptService
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.IncrementalTask
import com.android.build.gradle.internal.tasks.TaskInputHelper
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.MergeManifests
import com.android.build.gradle.tasks.ResourceException
import com.android.builder.core.AndroidBuilder
import com.android.builder.core.BuilderConstants
import com.android.builder.png.VectorDrawableRenderer
import com.android.ide.common.blame.MergingLog
import com.android.ide.common.blame.MergingLogRewriter
import com.android.ide.common.blame.ParsingProcessOutputHandler
import com.android.ide.common.blame.parser.ToolOutputParser
import com.android.ide.common.blame.parser.aapt.Aapt2OutputParser
import com.android.ide.common.blame.parser.aapt.AaptOutputParser
import com.android.ide.common.process.ProcessOutputHandler
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.*
import com.android.ide.common.vectordrawable.ResourcesNotSupportedException
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.resources.Density
import com.android.utils.FileUtils
import com.android.utils.ILogger
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.IOException
import java.util.*
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors
import javax.inject.Inject
import javax.xml.bind.JAXBException

/**
 * @author panxinghai
 *
 * date : 2019-07-11 17:31
 */
open class MergeResources() : IncrementalTask() {
    private lateinit var workerExecutorFacade: WorkerExecutorFacade

    @Inject
    constructor(executor: WorkerExecutor) : this() {
        workerExecutorFacade = Workers.getWorker(executor)
    }

    // ----- PUBLIC TASK API -----

    /**
     * Directory to write the merged resources to
     */
    private var outputDir: File? = null

    private var generatedPngsOutputDir: File? = null

    // ----- PRIVATE TASK API -----

    private var filesProvider: FilesProvider? = null

    /**
     * Optional file to write any publicly imported resource types and names to
     */
    private var publicFile: File? = null

    private var processResources: Boolean = false

    private var crunchPng: Boolean = false

    private var validateEnabled: Boolean = false

    private var blameLogFolder: File? = null

    // file inputs as raw files, lazy behind a memoized/bypassed supplier
    private var sourceFolderInputs: Supplier<Collection<File>>? = null
    private var resources: Map<String, BuildableArtifact>? = null
    //private Supplier<List<ResourceSet>> resSetSupplier;

    private var processedInputs: MutableList<ResourceSet>? = null

    private var libraries: ArtifactCollection? = null

    private var renderscriptResOutputDir: FileCollection? = null
    private var generatedResOutputDir: FileCollection? = null
    private var microApkResDirectory: FileCollection? = null
    private var extraGeneratedResFolders: FileCollection? = null

    private val fileValidity = FileValidity<ResourceSet>()

    private var disableVectorDrawables: Boolean = false

    private var vectorSupportLibraryIsUsed: Boolean = false

    private var generatedDensities: Collection<String>? = null

    private var minSdk: Supplier<Int>? = null

    private var variantScope: PluginVariantScope? = null

    private var aaptGeneration: AaptGeneration? = null

    @Nullable
    private var aapt2FromMaven: FileCollection? = null

    @Nullable
    private var dataBindingLayoutProcessor: SingleFileProcessor? = null

    /** Where data binding exports its outputs after parsing layout files.  */
    @Nullable
    private var dataBindingLayoutInfoOutFolder: File? = null

    @Nullable
    private var mergedNotCompiledResourcesOutputDirectory: File? = null

    private var pseudoLocalesEnabled: Boolean = false

    private var flags: ImmutableSet<Flag>? = null

    @NonNull
    private fun getResourceProcessor(
            @NonNull aaptGeneration: AaptGeneration?,
            @NonNull builder: AndroidBuilder,
            @Nullable aapt2FromMaven: FileCollection?,
            @NonNull workerExecutor: WorkerExecutorFacade,
            crunchPng: Boolean,
            @NonNull scope: PluginVariantScope?,
            @Nullable blameLog: MergingLog?,
            flags: ImmutableSet<Flag>,
            processResources: Boolean): ResourceCompilationService {
        // If we received the flag for removing namespaces we need to use the namespace remover to
        // process the resources.
        if (flags.contains(Flag.REMOVE_RESOURCE_NAMESPACES)) {
            return NamespaceRemover
        }

        // If we're not removing namespaces and there's no need to compile the resources, return a
        // no-op resource processor.
        if (!processResources) {
            return CopyToOutputDirectoryResourceCompilationService
        }

        if (aaptGeneration == AaptGeneration.AAPT_V2_DAEMON_SHARED_POOL) {
            val aapt2ServiceKey = registerAaptService(
                    aapt2FromMaven, builder.buildToolInfo, builder.logger)

            return WorkerExecutorResourceCompilationService(workerExecutor, aapt2ServiceKey)
        }

        // Finally, use AAPT or one of AAPT2 versions based on the project flags.
        return QueueableResourceCompilationService(
                AaptGradleFactory.make(
                        aaptGeneration!!,
                        builder,
                        createProcessOutputHandler(aaptGeneration, builder, blameLog),
                        crunchPng,
                        scope!!.getGlobalScope()
                                .extension
                                .aaptOptions
                                .cruncherProcesses))
    }

    @Nullable
    private fun createProcessOutputHandler(
            @NonNull aaptGeneration: AaptGeneration?,
            @NonNull builder: AndroidBuilder,
            @Nullable blameLog: MergingLog?): ProcessOutputHandler? {
        if (blameLog == null) {
            return null
        }

        val parsers = if (aaptGeneration == AaptGeneration.AAPT_V1)
            AaptOutputParser()
        else
            Aapt2OutputParser()

        return ParsingProcessOutputHandler(
                ToolOutputParser(parsers, builder.logger),
                MergingLogRewriter(Function { blameLog.find(it) }, builder.messageReceiver))
    }

    @Input
    fun getBuildToolsVersion(): String {
        return buildTools.revision.toString()
    }

    override fun isIncremental(): Boolean {
        return true
    }

    @Nullable
    @OutputDirectory
    @Optional
    fun getDataBindingLayoutInfoOutFolder(): File? {
        return dataBindingLayoutInfoOutFolder
    }


    @Throws(IOException::class, JAXBException::class)
    override fun doFullTaskAction() {
        val preprocessor = getPreprocessor()

        // this is full run, clean the previous outputs
        val destinationDir = getOutputDir()
        FileUtils.cleanOutputDir(destinationDir!!)
        if (dataBindingLayoutInfoOutFolder != null) {
            FileUtils.deleteDirectoryContents(dataBindingLayoutInfoOutFolder!!)
        }

        val resourceSets = getConfiguredResourceSets(preprocessor)

        // create a new merger and populate it with the sets.
        val merger = ResourceMerger(minSdk!!.get())
        var mergingLog: MergingLog? = null
        if (blameLogFolder != null) {
            FileUtils.cleanOutputDir(blameLogFolder!!)
            mergingLog = MergingLog(blameLogFolder)
        }

        try {
            getResourceProcessor(
                    aaptGeneration,
                    builder,
                    aapt2FromMaven,
                    workerExecutorFacade,
                    crunchPng,
                    variantScope,
                    mergingLog,
                    flags!!,
                    processResources).use { resourceCompiler ->

                for (resourceSet in resourceSets) {
                    resourceSet.loadFromFiles(iLogger)
                    merger.addDataSet(resourceSet)
                }

                val writer = MergedResourceWriter(
                        workerExecutorFacade,
                        destinationDir,
                        getPublicFile(),
                        mergingLog,
                        preprocessor,
                        resourceCompiler,
                        incrementalFolder,
                        dataBindingLayoutProcessor,
                        mergedNotCompiledResourcesOutputDirectory,
                        pseudoLocalesEnabled,
                        getCrunchPng())

                merger.mergeData(writer, false /*doCleanUp*/)

                if (dataBindingLayoutProcessor != null) {
                    dataBindingLayoutProcessor!!.end()
                }

                // No exception? Write the known state.
                merger.writeBlobTo(incrementalFolder, writer, false)
            }
        } catch (e: MergingException) {
            println(e.message)
            merger.cleanBlob(incrementalFolder)
            throw ResourceException(e.message, e)
        } finally {
            cleanup()
        }
    }

    @Throws(IOException::class, JAXBException::class)
    override fun doIncrementalTaskAction(changedInputs: Map<File, FileStatus>?) {
        val preprocessor = getPreprocessor()

        // create a merger and load the known state.
        val merger = ResourceMerger(minSdk!!.get())
        try {
            if (!/*incrementalState*/merger.loadFromBlob(incrementalFolder, true)) {
                doFullTaskAction()
                return
            }

            for (resourceSet in merger.dataSets) {
                resourceSet.setPreprocessor(preprocessor)
            }

            val resourceSets = getConfiguredResourceSets(preprocessor)

            // compare the known state to the current sets to detect incompatibility.
            // This is in case there's a change that's too hard to do incrementally. In this case
            // we'll simply revert to full build.
            if (!merger.checkValidUpdate(resourceSets)) {
                logger.info("Changed Resource sets: full task run!")
                doFullTaskAction()
                return
            }

            // The incremental process is the following:
            // Loop on all the changed files, find which ResourceSet it belongs to, then ask
            // the resource set to update itself with the new file.
            for ((changedFile, value) in changedInputs!!) {

                merger.findDataSetContaining(changedFile, fileValidity)
                if (fileValidity.status == FileValidity.FileStatus.UNKNOWN_FILE) {
                    doFullTaskAction()
                    return
                } else if (fileValidity.status == FileValidity.FileStatus.VALID_FILE) {
                    if (!fileValidity.dataSet.updateWith(
                                    fileValidity.sourceFile, changedFile, value,
                                    iLogger)) {
                        logger.info(
                                String.format("Failed to process %s event! Full task run",
                                        value))
                        doFullTaskAction()
                        return
                    }
                }
            }

            val mergingLog = if (getBlameLogFolder() != null) MergingLog(getBlameLogFolder()) else null

            getResourceProcessor(
                    aaptGeneration,
                    builder,
                    aapt2FromMaven,
                    workerExecutorFacade,
                    crunchPng,
                    variantScope,
                    mergingLog,
                    flags!!,
                    processResources).use { resourceCompiler ->

                val writer = MergedResourceWriter(
                        workerExecutorFacade,
                        getOutputDir(),
                        getPublicFile(),
                        mergingLog,
                        preprocessor,
                        resourceCompiler,
                        incrementalFolder,
                        dataBindingLayoutProcessor,
                        mergedNotCompiledResourcesOutputDirectory,
                        pseudoLocalesEnabled,
                        getCrunchPng())

                merger.mergeData(writer, false /*doCleanUp*/)

                if (dataBindingLayoutProcessor != null) {
                    dataBindingLayoutProcessor!!.end()
                }

                // No exception? Write the known state.
                merger.writeBlobTo(incrementalFolder, writer, false)
            }
        } catch (e: MergingException) {
            merger.cleanBlob(incrementalFolder)
            throw ResourceException(e.message, e)
        } finally {
            cleanup()
        }
    }

    private class MergeResourcesVectorDrawableRenderer(
            minSdk: Int,
            supportLibraryIsUsed: Boolean,
            outputDir: File,
            densities: Collection<Density>,
            loggerSupplier: Supplier<ILogger>) : VectorDrawableRenderer(minSdk, supportLibraryIsUsed, outputDir, densities, loggerSupplier) {

        @Throws(IOException::class)
        override fun generateFile(@NonNull toBeGenerated: File, @NonNull original: File) {
            try {
                super.generateFile(toBeGenerated, original)
            } catch (e: ResourcesNotSupportedException) {
                // Add gradle-specific error message.
                throw GradleException(
                        String.format(
                                "Can't process attribute %1\$s=\"%2\$s\": "
                                        + "references to other resources are not supported by "
                                        + "build-time PNG generation. "
                                        + "See http://developer.android.com/tools/help/vector-asset-studio.html "
                                        + "for details.",
                                e.name, e.value))
            }

        }
    }

    /**
     * Only one pre-processor for now. The code will need slight changes when we add more.
     */
    @NonNull
    private fun getPreprocessor(): ResourcePreprocessor {
        if (disableVectorDrawables) {
            // If the user doesn't want any PNGs, leave the XML file alone as well.
            return NoOpResourcePreprocessor.INSTANCE
        }

        val densities = getGeneratedDensities()!!.stream().map<Density> { Density.getEnum(it) }.collect(Collectors.toList())

        return MergeResourcesVectorDrawableRenderer(
                minSdk!!.get(),
                vectorSupportLibraryIsUsed,
                generatedPngsOutputDir!!,
                densities,
                LoggerWrapper.supplierFor(MergeResources::class.java))
    }

    @NonNull
    private fun getConfiguredResourceSets(preprocessor: ResourcePreprocessor): List<ResourceSet> {
        // It is possible that this get called twice in case the incremental run fails and reverts
        // back to full task run. Because the cached ResourceList is modified we don't want
        // to recompute this twice (plus, why recompute it twice anyway?)
        if (processedInputs == null) {
            processedInputs = computeResourceSetList()
            val generatedSets = ArrayList<ResourceSet>(processedInputs!!.size)

            for (resourceSet in processedInputs!!) {
                resourceSet.setPreprocessor(preprocessor)
                val generatedSet = GeneratedResourceSet(resourceSet)
                resourceSet.setGeneratedSet(generatedSet)
                generatedSets.add(generatedSet)
            }

            // We want to keep the order of the inputs. Given inputs:
            // (A, B, C, D)
            // We want to get:
            // (A-generated, A, B-generated, B, C-generated, C, D-generated, D).
            // Therefore, when later in {@link DataMerger} we look for sources going through the
            // list backwards, B-generated will take priority over A (but not B).
            // A real life use-case would be if an app module generated resource overrode a library
            // module generated resource (existing not in generated but bundled dir at this stage):
            // (lib, app debug, app main)
            // We will get:
            // (lib generated, lib, app debug generated, app debug, app main generated, app main)
            for (i in generatedSets.indices) {
                processedInputs!!.add(2 * i, generatedSets[i])
            }
        }

        return processedInputs!!
    }

    /**
     * Releases resource sets not needed anymore, otherwise they will waste heap space for the
     * duration of the build.
     *
     *
     * This might be called twice when an incremental build falls back to a full one.
     */
    private fun cleanup() {
        fileValidity.clear()
        processedInputs = null
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getRenderscriptResOutputDir(): FileCollection? {
        return renderscriptResOutputDir
    }

    @VisibleForTesting
    internal fun setRenderscriptResOutputDir(@NonNull renderscriptResOutputDir: FileCollection) {
        this.renderscriptResOutputDir = renderscriptResOutputDir
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getGeneratedResOutputDir(): FileCollection? {
        return generatedResOutputDir
    }

    @VisibleForTesting
    internal fun setGeneratedResOutputDir(@NonNull generatedResOutputDir: FileCollection) {
        this.generatedResOutputDir = generatedResOutputDir
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    fun getMicroApkResDirectory(): FileCollection? {
        return microApkResDirectory
    }

    @VisibleForTesting
    internal fun setMicroApkResDirectory(@NonNull microApkResDirectory: FileCollection) {
        this.microApkResDirectory = microApkResDirectory
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    fun getExtraGeneratedResFolders(): FileCollection? {
        return extraGeneratedResFolders
    }

    @VisibleForTesting
    internal fun setExtraGeneratedResFolders(@NonNull extraGeneratedResFolders: FileCollection) {
        this.extraGeneratedResFolders = extraGeneratedResFolders
    }

    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getLibraries(): FileCollection? {
        return if (libraries != null) {
            libraries!!.artifactFiles
        } else null

    }

    @VisibleForTesting
    internal fun setLibraries(libraries: ArtifactCollection) {
        this.libraries = libraries
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getSourceFolderInputs(): Collection<File> {
        return sourceFolderInputs!!.get()
    }

    @OutputDirectory
    fun getOutputDir(): File? {
        return outputDir
    }

    fun setOutputDir(outputDir: File) {
        this.outputDir = outputDir
    }

    @Input
    fun getCrunchPng(): Boolean {
        return crunchPng
    }

    @Input
    fun getProcessResources(): Boolean {
        return processResources
    }

    @Optional
    @OutputFile
    fun getPublicFile(): File? {
        return publicFile
    }

    fun setPublicFile(publicFile: File) {
        this.publicFile = publicFile
    }

    // Synthetic input: the validation flag is set on the resource sets in ConfigAction.execute.
    @Input
    fun isValidateEnabled(): Boolean {
        return validateEnabled
    }

    @OutputDirectory
    @Optional
    fun getBlameLogFolder(): File? {
        return blameLogFolder
    }

    fun setBlameLogFolder(blameLogFolder: File) {
        this.blameLogFolder = blameLogFolder
    }

    @Optional
    @OutputDirectory
    fun getGeneratedPngsOutputDir(): File? {
        return generatedPngsOutputDir
    }

    @Input
    fun getGeneratedDensities(): Collection<String>? {
        return generatedDensities
    }

    @Input
    fun getMinSdk(): Int {
        return minSdk!!.get()
    }

    @Input
    fun isVectorSupportLibraryUsed(): Boolean {
        return vectorSupportLibraryIsUsed
    }

    @Input
    fun getAaptGeneration(): String {
        return aaptGeneration!!.name
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    @Nullable
    fun getAapt2FromMaven(): FileCollection? {
        return aapt2FromMaven
    }

    @Nullable
    @OutputDirectory
    @Optional
    fun getMergedNotCompiledResourcesOutputDirectory(): File? {
        return mergedNotCompiledResourcesOutputDirectory
    }

    @Input
    fun isPseudoLocalesEnabled(): Boolean {
        return pseudoLocalesEnabled
    }

    @Input
    fun getFlags(): String {
        return flags!!.stream().map<String> { it.name }.sorted().collect(Collectors.joining(","))
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getResources(): Collection<BuildableArtifact> {
        return resources!!.values
    }

    @VisibleForTesting
    fun setResources(resources: Map<String, BuildableArtifact>) {
        this.resources = resources
    }

    private fun getResSet(): List<ResourceSet> {
        val builder = ImmutableList.builder<ResourceSet>()
        for ((key, value) in resources!!) {
            val resourceSet = ResourceSet(
                    key, ResourceNamespace.RES_AUTO, null, validateEnabled)
            resourceSet.addSources(value.files)
            builder.add(resourceSet)
        }
        return builder.build()
    }

    /**
     * Computes the list of resource sets to be used during execution based all the inputs.
     */
    @VisibleForTesting
    @NonNull
    internal fun computeResourceSetList(): MutableList<ResourceSet> {
        val sourceFolderSets = getResSet()
        var size = sourceFolderSets.size + 4
        if (libraries != null) {
            size += libraries!!.artifacts.size
        }

        val resourceSetList = ArrayList<ResourceSet>(size)

        // add at the beginning since the libraries are less important than the folder based
        // resource sets.
        // get the dependencies first
        if (libraries != null) {
            val libArtifacts = libraries!!.artifacts
            // the order of the artifact is descending order, so we need to reverse it.
            for (artifact in libArtifacts) {
                val resourceSet = ResourceSet(
                        MergeManifests.getArtifactName(artifact),
                        ResourceNamespace.RES_AUTO, null,
                        validateEnabled)
                resourceSet.isFromDependency = true
                resourceSet.addSource(artifact.file)

                // add to 0 always, since we need to reverse the order.
                resourceSetList.add(0, resourceSet)
            }
        }

        // add the folder based next
        resourceSetList.addAll(sourceFolderSets)

        // We add the generated folders to the main set
        val generatedResFolders = ArrayList<File>()

        generatedResFolders.addAll(renderscriptResOutputDir!!.files)
        generatedResFolders.addAll(generatedResOutputDir!!.files)

        val extraFolders = getExtraGeneratedResFolders()
        if (extraFolders != null) {
            generatedResFolders.addAll(extraFolders.files)
        }
        if (microApkResDirectory != null) {
            generatedResFolders.addAll(microApkResDirectory!!.files)
        }

        // add the generated files to the main set.
        val mainResourceSet = sourceFolderSets[0]
        assert(mainResourceSet.configName == BuilderConstants.MAIN)
        mainResourceSet.addSources(generatedResFolders)

        return resourceSetList
    }

    class ConfigAction(
            @param:NonNull @field:NonNull
            private val scope: PluginVariantScope,
            @param:NonNull @field:NonNull private val mergeType: TaskManager.MergeType,
            @param:NonNull @field:NonNull
            private val taskNamePrefix: String,
            @param:Nullable @field:Nullable
            private val outputLocation: File,
            @param:Nullable @field:Nullable private val mergedNotCompiledOutputDirectory: File?,
            private val includeDependencies: Boolean,
            private val processResources: Boolean,
            @param:NonNull @field:NonNull private val flags: ImmutableSet<Flag>) : TaskConfigAction<MergeResources> {
        private val processVectorDrawables: Boolean

        init {
            this.processVectorDrawables = flags.contains(Flag.PROCESS_VECTOR_DRAWABLES)
        }

        @NonNull
        override fun getName(): String {
            return scope.getTaskName(taskNamePrefix, "Resources")
        }

        @NonNull
        override fun getType(): Class<MergeResources> {
            return MergeResources::class.java
        }

        override fun execute(@NonNull task: MergeResources) {
            val variantData = scope.getVariantData()
            variantData.androidResources.forEach {
                println("component: ${it.key}:${it.value}")
                it.value.files.forEach {file->
                    println("\t\t${file.absolutePath}")
                }
            }
            val project = scope.getGlobalScope().project

            task.filesProvider = scope.getGlobalScope().filesProvider
            task.minSdk = TaskInputHelper.memoize {
                variantData
                        .variantConfiguration
                        .minSdkVersion
                        .apiLevel
            }

            task.aaptGeneration = AaptGeneration.fromProjectOptions(scope.getGlobalScope().projectOptions)
            task.aapt2FromMaven = getAapt2FromMavenIfEnabled(scope.getGlobalScope())
            task.setAndroidBuilder(scope.getGlobalScope().androidBuilder)
            task.variantName = scope.getVariantConfiguration().fullName
            task.incrementalFolder = scope.getIncrementalDir(name)
            task.variantScope = scope
            // Libraries use this task twice, once for compilation (with dependencies),
            // where blame is useful, and once for packaging where it is not.
            if (includeDependencies) {
                task.blameLogFolder = scope.getResourceBlameLogDir()
            }
            task.processResources = processResources
            task.crunchPng = scope.isCrunchPngs()

            val vectorDrawablesOptions = variantData
                    .variantConfiguration
                    .mergedFlavor
                    .vectorDrawables
            task.generatedDensities = vectorDrawablesOptions.generatedDensities
            if (task.generatedDensities == null) {
                task.generatedDensities = emptySet()
            }

            task.disableVectorDrawables = !processVectorDrawables || task.generatedDensities!!.isEmpty()

            // TODO: When support library starts supporting gradients (http://b/62421666), remove
            // the vectorSupportLibraryIsUsed field and set disableVectorDrawables when
            // the getUseSupportLibrary method returns TRUE.
            task.vectorSupportLibraryIsUsed = java.lang.Boolean.TRUE == vectorDrawablesOptions.useSupportLibrary

            val validateEnabled = !scope.getGlobalScope()
                    .projectOptions
                    .get(BooleanOption.DISABLE_RESOURCE_VALIDATION)
            task.validateEnabled = validateEnabled

            if (includeDependencies) {
                task.libraries = scope.getArtifactCollection(
                        RUNTIME_CLASSPATH, ALL, ANDROID_RES)
            }

            task.resources = variantData.androidResources
            task.sourceFolderInputs = Supplier {
                variantData
                        .variantConfiguration
                        .getSourceFiles { it.resDirectories }
            }
            task.extraGeneratedResFolders = variantData.extraGeneratedResFolders
            task.renderscriptResOutputDir = project.files(scope.getRenderscriptResOutputDir())
            task.generatedResOutputDir = project.files(scope.getGeneratedResOutputDir())
//            if (scope.taskContainer.microApkTask != null && variantData.variantConfiguration.buildType.isEmbedMicroApp) {
//                task.microApkResDirectory = project.files(scope.microApkResDirectory)
//            }

            task.outputDir = outputLocation
            if (!task.disableVectorDrawables) {
                task.generatedPngsOutputDir = scope.getGeneratedPngsOutputDir()
            }

            // In LibraryTaskManager#createMergeResourcesTasks, there are actually two
            // MergeResources tasks sharing the same task type (MergeResources) and ConfigAction
            // code: packageResources with mergeType == PACKAGE, and mergeResources with
            // mergeType == MERGE. Since the following line of code is called for each task, the
            // latter one wins: The mergeResources task with mergeType == MERGE is the one that is
            // finally registered in the current scope.
            // Filed https://issuetracker.google.com//110412851 to clean this up at some point.
            scope.getTaskContainer().pluginMergeResourcesTask = task

            if (scope.getGlobalScope().extension.dataBinding.isEnabled) {
                // Keep as an output.
                task.dataBindingLayoutInfoOutFolder = scope.getArtifacts()
                        .appendArtifact(
                                if (mergeType === TaskManager.MergeType.MERGE)
                                    InternalArtifactType.DATA_BINDING_LAYOUT_INFO_TYPE_MERGE
                                else
                                    InternalArtifactType.DATA_BINDING_LAYOUT_INFO_TYPE_PACKAGE,
                                task,
                                "out")
                task.dataBindingLayoutProcessor = object : SingleFileProcessor {

                    // Lazily instantiate the processor to avoid parsing the manifest.
                    private var processor: LayoutXmlProcessor? = null

                    private fun getProcessor(): LayoutXmlProcessor? {
                        if (processor == null) {
                            processor = variantData.layoutXmlProcessor
                        }
                        return processor
                    }

                    @Throws(Exception::class)
                    override fun processSingleFile(file: File, out: File): Boolean {
                        return getProcessor()!!.processSingleFile(file, out)
                    }

                    override fun processRemovedFile(file: File) {
                        getProcessor()!!.processRemovedFile(file)
                    }

                    @Throws(JAXBException::class)
                    override fun end() {
                        getProcessor()!!
                                .writeLayoutInfoFiles(
                                        task.dataBindingLayoutInfoOutFolder)
                    }
                }
            }

            task.mergedNotCompiledResourcesOutputDirectory = mergedNotCompiledOutputDirectory

            task.pseudoLocalesEnabled = scope.getVariantConfiguration()
                    .buildType
                    .isPseudoLocalesEnabled
            task.flags = flags
        }
    }

    enum class Flag {
        REMOVE_RESOURCE_NAMESPACES,
        PROCESS_VECTOR_DRAWABLES
    }
}