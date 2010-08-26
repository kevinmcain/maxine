/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.vm.prototype;

import java.io.*;

import com.sun.max.ide.*;
import com.sun.max.lang.*;
import com.sun.max.platform.*;
import com.sun.max.profile.*;
import com.sun.max.program.*;
import com.sun.max.program.option.*;
import com.sun.max.vm.*;
import com.sun.max.vm.classfile.*;
import com.sun.max.vm.classfile.constant.*;
import com.sun.max.vm.type.*;

/**
 * Construction of a virtual machine image begins here by running on a host virtual
 * machine (e.g. Hotspot). This process involves creating a target VM configuration
 * and loading and initializing the classes that implement VM services. The representation
 * of the virtual machine being built is referred to as the "prototype", and the final
 * product, a binary image that contains the compiled machine code of the virtual machine
 * as well as objects and metadata that implement the virtual machine, is called the
 * "image".
 *
 * @author Bernd Mathiske
 * @author Doug Simon
 * @author Ben L. Titzer
 */
public final class BootImageGenerator {

    public static final String IMAGE_OBJECT_TREE_FILE_NAME = "maxine.object.tree";
    public static final String IMAGE_METHOD_TREE_FILE_NAME = "maxine.method.tree";
    public static final String IMAGE_JAR_FILE_NAME = "maxine.jar";
    public static final String IMAGE_FILE_NAME = "maxine.vm";
    public static final String STATS_FILE_NAME = "maxine.stats";

    public static final String DEFAULT_VM_DIRECTORY = "Native" + File.separator + "generated" + File.separator +
        OperatingSystem.fromName(System.getProperty(Prototype.OPERATING_SYSTEM_PROPERTY, OperatingSystem.current().name())).name().toLowerCase();

    private final OptionSet options = new OptionSet();

    private final Option<Boolean> help = options.newBooleanOption("help", false,
            "Show help message and exit.");

    private final Option<Boolean> treeOption = options.newBooleanOption("tree", false,
            "Create a file showing the connectivity of objects in the image.");

    private final Option<Boolean> statsOption = options.newBooleanOption("stats", false,
            "Create a file detailing the number and size of each type of object in the image.");

    private final Option<File> vmDirectoryOption = options.newFileOption("vmdir", getDefaultVMDirectory(),
            "The output directory for the binary image generator.");

    private final Option<Boolean> testCallerJit = options.newBooleanOption("test-caller-jit", false,
            "For the Java tester, this option specifies that each test case's harness should be compiled " +
            "with the JIT compiler (helpful for testing JIT->JIT and JIT->opt calls).");

    private final Option<Boolean> testCalleeJit = options.newBooleanOption("test-callee-jit", false,
            "For the Java tester, this option specifies that each test case's method should be compiled " +
            "with the JIT compiler (helpful for testing JIT->JIT and opt->JIT calls).");

    private final Option<Boolean> testCalleeC1X = options.newBooleanOption("test-callee-c1x", false,
            "For the Java tester, this option specifies that each test case's method should be compiled " +
            "with the C1X compiler (helpful for testing C1X->C1X and opt->C1X calls).");

    private final Option<Boolean> testNative = options.newBooleanOption("native-tests", false,
            "For the Java tester, this option specifies that " + System.mapLibraryName("javatest") + " should be dynamically loaded.");

    public final Option<Boolean> prototypeJit = options.newBooleanOption("prototype-jit", false,
        "Selects JIT as the default for building the boot image.");

    private final Option<String> vmArguments = options.newStringOption("vmargs", null,
            "A set of one or more VM arguments. This is useful for exercising VM functionality or " +
            "enabling VM tracing while bootstrapping.");

    /**
     * Used in the Java tester to indicate whether to test the resolution and linking mechanism for
     * test methods.
     */
    public static boolean unlinked = false;

    /**
     * Used in the Java tester to indicate whether to compile the testing harness itself with the JIT.
     */
    public static boolean callerJit = false;

    /**
     * Used by the Java tester to indicate whether to compile the tests themselves with the JIT.
     */
    public static boolean calleeJit = false;

    /**
     * Used by the Java tester to indicate whether to compile the tests themselves with the JIT.
     */
    public static boolean calleeC1X = false;

    /**
     * Used by the Java tester to indicate that testing requires dynamically loading native libraries.
     */
    public static boolean nativeTests = false;

    /**
     * Gets the default VM directory where the VM executable, shared libraries, boot image
     * and related files are located.
     */
    public static File getDefaultVMDirectory() {
        return new File(JavaProject.findVcsProjectDirectory().getParentFile().getAbsoluteFile(), DEFAULT_VM_DIRECTORY);
    }

    /**
     * Gets the boot image file given a VM directory.
     *
     * @param vmdir a VM directory. If {@code null}, then {@link #getDefaultVMDirectory()} is used.
     */
    public static File getBootImageFile(File vmdir) {
        if (vmdir == null) {
            vmdir = getDefaultVMDirectory();
        }
        return new File(vmdir, IMAGE_FILE_NAME);
    }

    /**
     * Gets the boot image jar file given a VM directory.
     *
     * @param vmdir a VM directory. If {@code null}, then {@link #getDefaultVMDirectory()} is used.
     */
    public static File getBootImageJarFile(File vmdir) {
        if (vmdir == null) {
            vmdir = getDefaultVMDirectory();
        }
        return new File(vmdir, IMAGE_JAR_FILE_NAME);
    }

    /**
     * Gets the object tree file given a VM directory.
     *
     * @param vmdir a VM directory. If {@code null}, then {@link #getDefaultVMDirectory()} is used.
     */
    public static File getBootImageObjectTreeFile(File vmdir) {
        if (vmdir == null) {
            vmdir = getDefaultVMDirectory();
        }
        return new File(vmdir, IMAGE_OBJECT_TREE_FILE_NAME);
    }

    /**
     * Gets the method tree file given a VM directory.
     *
     * @param vmdir a VM directory. If {@code null}, then {@link #getDefaultVMDirectory()} is used.
     */
    public static File getBootImageMethodTreeFile(File vmdir) {
        if (vmdir == null) {
            vmdir = getDefaultVMDirectory();
        }
        return new File(vmdir, IMAGE_METHOD_TREE_FILE_NAME);
    }

    /**
     * Creates and runs the binary image generator with the specified command line arguments.
     *
     * @param programArguments the arguments from the command line
     */
    public BootImageGenerator(String[] programArguments) {
        final long start = System.currentTimeMillis();
        try {
            final PrototypeGenerator prototypeGenerator = new PrototypeGenerator(options);
            Trace.addTo(options);

            options.parseArguments(programArguments);

            if (help.getValue()) {
                prototypeGenerator.createVMConfiguration(prototypeGenerator.createDefaultVMConfiguration());
                options.printHelp(System.out, 80);
                return;
            }

            if (vmArguments.getValue() != null) {
                VMOption.setVMArguments(vmArguments.getValue().split("\\s+"));
            }

            BootImageGenerator.calleeC1X = testCalleeC1X.getValue();
            BootImageGenerator.calleeJit = testCalleeJit.getValue();
            BootImageGenerator.callerJit = testCallerJit.getValue();
            BootImageGenerator.nativeTests = testNative.getValue();

            final File vmDirectory = vmDirectoryOption.getValue();
            vmDirectory.mkdirs();

            final DataPrototype dataPrototype = prototypeGenerator.createDataPrototype(treeOption.getValue(), prototypeJit.getValue());
            VMConfiguration.target().finalizeSchemes(MaxineVM.Phase.BOOTSTRAPPING);

            final GraphPrototype graphPrototype = dataPrototype.graphPrototype();

            VMOptions.beforeExit();

            // write the statistics
            if (statsOption.getValue()) {
                writeStats(graphPrototype, new File(vmDirectory, STATS_FILE_NAME));
            }
            writeJar(new File(vmDirectory, IMAGE_JAR_FILE_NAME));
            writeImage(dataPrototype, new File(vmDirectory, IMAGE_FILE_NAME));
            if (treeOption.getValue()) {
                // write the tree file only if specified by the user.
                writeObjectTree(dataPrototype, graphPrototype, new File(vmDirectory, IMAGE_OBJECT_TREE_FILE_NAME));
            }
            writeMethodTree(graphPrototype.compiledPrototype, new File(vmDirectory, IMAGE_METHOD_TREE_FILE_NAME));

        } catch (IOException ioException) {
            ProgramError.unexpected("could not write file ", ioException);
        } finally {
            final long timeInMilliseconds = System.currentTimeMillis() - start;
            if (statsOption.getValue()) {
                try {
                    writeMiscStatistics(Trace.stream());
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            }
            Trace.line(1, "Total time: " + (timeInMilliseconds / 1000.0f) + " seconds");
            System.out.flush();
        }
    }

    /**
     * Writes the image data to the specified file.
     *
     * @param dataPrototype the data prototype containing a data-level representation of the image
     * @param file the file to which to write the data prototype
     */
    private void writeImage(DataPrototype dataPrototype, File file) {
        try {
            final FileOutputStream outputStream = new FileOutputStream(file);
            final BootImage bootImage = new BootImage(dataPrototype);
            try {
                Trace.begin(1, "writing boot image file: " + file);
                bootImage.write(outputStream);
                Trace.end(1, "end boot image file: " + file + " (" + Longs.toUnitsString(file.length(), false) + ")");
            } catch (IOException ioException) {
                ProgramError.unexpected("could not write file: " + file, ioException);
            } finally {
                try {
                    outputStream.close();
                } catch (IOException ioException) {
                    ProgramWarning.message("could not close file: " + file);
                }
            }
        } catch (FileNotFoundException fileNotFoundException) {
            throw ProgramError.unexpected("could not open file: " + file);
        } catch (BootImageException bootImageException) {
            ProgramError.unexpected("could not construct proper boot image", bootImageException);
        }
    }

    /**
     * Writes a jar file containing all of the (potentially rewritten) VM class files to the specified file.
     *
     * @param file the file to which to write the jar
     * @throws IOException if there is a problem writing the jar
     */
    private void writeJar(File file) throws IOException {
        Trace.begin(1, "writing boot image jar file: " + file);
        ClassfileReader.writeClassfilesToJar(file);
        Trace.end(1, "end boot image jar file: " + file + " (" + Longs.toUnitsString(file.length(), false) + ")");
    }

    /**
     * Writes miscellaneous statistics about the boot image creation process to a file.
     *
     * @param graphPrototype the graph (i.e. nodes/edges) representation of the prototype
     * @param file the file to which to write the statistics
     * @throws IOException if there is a problem writing to the file
     */
    private void writeStats(GraphPrototype graphPrototype, File file) throws IOException {
        Trace.begin(1, "writing boot image statistics file: " + file);
        final FileOutputStream fileOutputStream = new FileOutputStream(file);
        GraphStats stats = new GraphStats(graphPrototype);
        stats.dumpStats(new PrintStream(fileOutputStream));
        fileOutputStream.close();
        Trace.end(1, "end boot image statistics file: " + file + " (" + Longs.toUnitsString(file.length(), false) + ")");
        new SavingsEstimator(stats).report(Trace.stream());
    }

    /**
     * Writes the object tree to a file. The object tree helps to diagnose space usage problems,
     * typically caused by including too much into the image.
     *
     * @param dataPrototype the data representation of the prototype
     * @param graphPrototype the graph representation of the prototype
     * @param file the file to which to write the object
     * @throws IOException
     */
    private void writeObjectTree(DataPrototype dataPrototype, GraphPrototype graphPrototype, File file) throws IOException {
        Trace.begin(1, "writing boot image object tree file: " + file);
        final FileOutputStream fileOutputStream = new FileOutputStream(file);
        final DataOutputStream dataOutputStream = new DataOutputStream(new BufferedOutputStream(fileOutputStream, 1000000));
        BootImageObjectTree.saveTree(dataOutputStream, graphPrototype.links(), dataPrototype.allocationMap());
        dataOutputStream.flush();
        fileOutputStream.close();
        Trace.end(1, "writing boot image object tree file: " + file + " (" + Longs.toUnitsString(file.length(), false) + ")");
    }

    /**
     * Writes the method tree to a file. The method tree helps to diagnose the inclusion
     * of methods into the image that should not be included.
     *
     * @param compiledPrototype the compiled-code representation of the prototype
     * @param file the file to which to write the tree
     * @throws IOException if there is a problem writing to the file
     */
    private void writeMethodTree(CompiledPrototype compiledPrototype, File file) throws IOException {
        Trace.begin(1, "writing boot image method tree file: " + file);
        final FileOutputStream fileOutputStream = new FileOutputStream(file);
        final DataOutputStream dataOutputStream = new DataOutputStream(new BufferedOutputStream(fileOutputStream, 1000000));
        BootImageMethodTree.saveTree(dataOutputStream, compiledPrototype.links());
        dataOutputStream.flush();
        fileOutputStream.close();
        Trace.end(1, "writing boot image method tree file: " + file + " (" + Longs.toUnitsString(file.length(), false) + ")");
    }

    /**
     * The main entry point, which creates a binary image generator and then runs it.
     *
     * @param programArguments the arguments from the command line
     */
    public static void main(String[] programArguments) {
        new BootImageGenerator(programArguments);
    }

    /**
     * Writes various statistics about the image creation process to the standard output.
     * @param out the output stream to which to write the statistics
     */
    private static void writeMiscStatistics(PrintStream out) {
        Trace.line(1, "# utf8 constants: " + SymbolTable.length());
        Trace.line(1, "# type descriptors: " + TypeDescriptor.numberOfDescriptors());
        Trace.line(1, "# signature descriptors: " + SignatureDescriptor.totalNumberOfDescriptors());

        GlobalMetrics.report(out);
    }
}
