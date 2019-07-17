package cn.soul.android.plugin.component.tasks.transform

import cn.soul.android.plugin.component.PluginVariantScope
import com.android.SdkConstants
import com.android.build.api.transform.*
import com.android.build.gradle.internal.dsl.PackagingOptions
import com.android.build.gradle.internal.packaging.PackagingFileAction
import com.android.build.gradle.internal.packaging.ParsedPackagingOptions
import com.android.build.gradle.internal.pipeline.ExtendedContentType
import com.android.build.gradle.internal.pipeline.IncrementalFileMergerTransformUtils
import com.android.builder.files.FileCacheByPath
import com.android.builder.merge.*
import com.android.utils.FileUtils
import com.android.utils.ImmutableCollectors
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import java.io.*
import java.util.ArrayList
import java.util.HashMap
import java.util.function.Predicate
import java.util.regex.Pattern
import java.util.stream.Collectors
import com.google.common.base.Preconditions.checkNotNull
import java.util.function.Consumer

/**
 * @author panxinghai
 *
 * date : 2019-07-17 10:48
 */
open class MergeJavaResourcesTransform(val packagingOptions: PackagingOptions,
                                  val mergeScopes: MutableSet<in QualifiedContent.Scope>,
                                  val mergedType: QualifiedContent.ContentType,
                                  private val name: String,
                                  val scope: PluginVariantScope) : Transform() {
    private val JAR_ABI_PATTERN = Pattern.compile("lib/([^/]+)/[^/]+")
    private val ABI_FILENAME_PATTERN = Pattern.compile(".*\\.so")

    private var intermediateDir: File
    private var mMergedType = ImmutableSet.of(mergedType)

    private var acceptedPathsPredicate: Predicate<String>
    private var cacheDir: File

    init {
        this.intermediateDir = scope.getIncrementalDir(
                scope.fullVariantName + "-" + name)

        cacheDir = File(intermediateDir, "zip-cache")

        when {
            mergedType === QualifiedContent.DefaultContentType.RESOURCES -> acceptedPathsPredicate = Predicate { path ->
                !path.endsWith(SdkConstants.DOT_CLASS) && !path.endsWith(SdkConstants.DOT_NATIVE_LIBS)
            }
            mergedType === ExtendedContentType.NATIVE_LIBS -> acceptedPathsPredicate = Predicate { path ->
                val m = JAR_ABI_PATTERN.matcher(path)

                // if the ABI is accepted, check the 3rd segment
                if (m.matches()) {
                    // remove the beginning of the path (lib/<abi>/)
                    val filename = path.substring(5 + m.group(1).length)
                    // and check the filename
                    return@Predicate ABI_FILENAME_PATTERN.matcher(filename).matches() ||
                            SdkConstants.FN_GDBSERVER == filename ||
                            SdkConstants.FN_GDB_SETUP == filename
                }

                false
            }
            else -> throw UnsupportedOperationException(
                    "mergedType param must be RESOURCES or NATIVE_LIBS")
        }
    }

    override fun getName(): String {
        return name
    }

    override fun getInputTypes(): Set<QualifiedContent.ContentType> {
        return mMergedType
    }

    override fun getScopes(): MutableSet<in QualifiedContent.Scope>? {
        return mergeScopes
    }

    override fun getSecondaryDirectoryOutputs(): Collection<File> {
        return ImmutableList.of(cacheDir)
    }

    override fun getParameterInputs(): Map<String, Any> {
        return ImmutableMap.of<String, Any>(
                "exclude", packagingOptions.excludes,
                "pickFirst", packagingOptions.pickFirsts,
                "merge", packagingOptions.merges)
    }

    override fun isIncremental(): Boolean {
        return true
    }

    /**
     * Obtains the file where incremental state is saved.
     *
     * @return the file, may not exist
     */
    private fun incrementalStateFile(): File {
        return File(intermediateDir, "merge-state")
    }

    /**
     * Loads the incremental state.
     *
     * @return `null` if the state is not defined
     * @throws IOException failed to load the incremental state
     */
    @Throws(IOException::class)
    private fun loadMergeState(): IncrementalFileMergerState? {
        val incrementalFile = incrementalStateFile()
        if (!incrementalFile.isFile) {
            return null
        }

        try {
            ObjectInputStream(FileInputStream(incrementalFile)).use { i -> return i.readObject() as IncrementalFileMergerState }
        } catch (e: ClassNotFoundException) {
            throw IOException(e)
        }

    }

    /**
     * Save the incremental merge state.
     *
     * @param state the state
     * @throws IOException failed to save the state
     */
    @Throws(IOException::class)
    private fun saveMergeState(state: IncrementalFileMergerState?) {
        val incrementalFile = incrementalStateFile()

        FileUtils.mkdirs(incrementalFile.parentFile)
        ObjectOutputStream(FileOutputStream(incrementalFile)).use { o -> o.writeObject(state) }
    }

    @Throws(IOException::class, TransformException::class)
    override fun transform(invocation: TransformInvocation) {
        FileUtils.mkdirs(cacheDir)
        val zipCache = FileCacheByPath(cacheDir)

        val outputProvider = invocation.outputProvider
        checkNotNull(outputProvider, "Missing output object for transform " + getName())

        val packagingOptions = ParsedPackagingOptions(this.packagingOptions)

        var full = false
        var state = loadMergeState()
        if (state == null || !invocation.isIncremental) {
            /*
             * This is a full build.
             */
            state = IncrementalFileMergerState()
            outputProvider.deleteAll()
            full = true
        }

        val cacheUpdates = ArrayList<Runnable>()

        val contentMap = HashMap<IncrementalFileMergerInput, QualifiedContent>()
        var inputs: List<IncrementalFileMergerInput> = ArrayList(
                IncrementalFileMergerTransformUtils.toInput(
                        invocation,
                        zipCache,
                        cacheUpdates,
                        full,
                        contentMap))

        /*
         * In an ideal world, we could just send the inputs to the file merger. However, in the
         * real world we live in, things are more complicated :)
         *
         * We need to:
         *
         * 1. We need to bring inputs that refer to the project scope before the other inputs.
         * 2. Prefix libraries that come from directories with "lib/".
         * 3. Filter all inputs to remove anything not accepted by acceptedPathsPredicate neither
         * by packagingOptions.
         */

        // Sort inputs to move project scopes to the start.
//        inputs.sort { i0, i1 ->
//            val v0 = if (contentMap[i0]!!.scopes.contains(QualifiedContent.Scope.PROJECT)) 0 else 1
//            val v1 = if (contentMap[i1]!!.scopes.contains(QualifiedContent.Scope.PROJECT)) 0 else 1
//            v0 - v1
//        }

        // Prefix libraries with "lib/" if we're doing libraries.
        assert(mMergedType.size == 1)
        val mergedType = this.mMergedType.iterator().next()
        if (mergedType === ExtendedContentType.NATIVE_LIBS) {
//            inputs = inputs.stream()
//                    .map { i ->
//                        val qc = contentMap[i]
//                        if (qc!!.file.isDirectory) {
//                            i = RenameIncrementalFileMergerInput(
//                                    i,
//                                    { s -> "lib/$s" },
//                                    { s -> s.substring("lib/".length) })
//                            contentMap[i] = qc
//                        }
//
//                        i
//                    }
//                    .collect(Collectors.toList())
        }

        // Filter inputs.
        val inputFilter = acceptedPathsPredicate.and { path -> packagingOptions.getAction(path) != PackagingFileAction.EXCLUDE }
        inputs = inputs.stream()
                .map<IncrementalFileMergerInput> { i ->
                    val i2 = FilterIncrementalFileMergerInput(i, inputFilter)
                    contentMap[i2] = contentMap[i]!!
                    i2
                }
                .collect(Collectors.toList())

        /*
         * Create the algorithm used by the merge transform. This algorithm decides on which
         * algorithm to delegate to depending on the packaging option of the path. By default it
         * requires just one file (no merging).
         */
        val mergeTransformAlgorithm = StreamMergeAlgorithms.select { path ->
            val packagingAction = packagingOptions.getAction(path)
            when (packagingAction) {
                PackagingFileAction.EXCLUDE ->
                    // Should have been excluded from the input.
                    throw AssertionError()
                PackagingFileAction.PICK_FIRST -> return@select StreamMergeAlgorithms.pickFirst()
                PackagingFileAction.MERGE -> return@select StreamMergeAlgorithms.concat()
                PackagingFileAction.NONE -> return@select StreamMergeAlgorithms.acceptOnlyOne()
                else -> throw AssertionError()
            }
        }

        /*
                * Create an output that uses the algorithm. This is not the final output because,
                * unfortunately, we still have the complexity of the project scope overriding other scopes
                * to solve.
                *
                * When resources inside a jar file are extracted to a directory, the results may not be
                * expected on Windows if the file names end with "." (bug 65337573), or if there is an
                * uppercase/lowercase conflict. To work around this issue, we copy these resources to a
                * jar file.
                */
        val baseOutput: IncrementalFileMergerOutput
        if (mergedType === QualifiedContent.DefaultContentType.RESOURCES) {
            val outputLocation = outputProvider.getContentLocation(
                    "resources", outputTypes, scopes, Format.JAR)
            baseOutput = IncrementalFileMergerOutputs.fromAlgorithmAndWriter(
                    mergeTransformAlgorithm, MergeOutputWriters.toZip(outputLocation))
        } else {
            val outputLocation = outputProvider.getContentLocation(
                    "resources", outputTypes, scopes, Format.DIRECTORY)
            baseOutput = IncrementalFileMergerOutputs.fromAlgorithmAndWriter(
                    mergeTransformAlgorithm,
                    MergeOutputWriters.toDirectory(outputLocation))
        }

        /*
         * We need a custom output to handle the case in which the same path appears in multiple
         * inputs and the action is NONE, but only one input is actually PROJECT. In this specific
         * case we will ignore all other inputs.
         */

        val projectInputs = contentMap.keys.stream()
                .filter { i -> contentMap[i]!!.scopes.contains(QualifiedContent.Scope.PROJECT) }
                .collect(Collectors.toSet())

        val output = object : DelegateIncrementalFileMergerOutput(baseOutput) {
            override fun create(
                    path: String,
                    inputs: List<IncrementalFileMergerInput>) {
                super.create(path, filter(path, inputs))
            }

            override fun update(
                    path: String,
                    prevInputNames: List<String>,
                    inputs: List<IncrementalFileMergerInput>) {
                super.update(path, prevInputNames, filter(path, inputs))
            }

            override fun remove(path: String) {
                super.remove(path)
            }

            private fun filter(
                    path: String,
                    inputs: List<IncrementalFileMergerInput>): ImmutableList<IncrementalFileMergerInput> {
                var inputs = inputs
                val packagingAction = packagingOptions.getAction(path)
                if (packagingAction == PackagingFileAction.NONE && inputs.stream().anyMatch { projectInputs.contains(it) }) {
                    inputs = inputs.stream()
                            .filter { projectInputs.contains(it) }
                            .collect(ImmutableCollectors.toImmutableList())
                }

                return ImmutableList.copyOf(inputs)
            }
        }

        state = IncrementalFileMerger.merge(ImmutableList.copyOf(inputs), output, state)
        saveMergeState(state)

        cacheUpdates.forEach(Consumer<Runnable> { it.run() })
    }
}