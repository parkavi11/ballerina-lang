/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.wso2.ballerinalang.compiler.bir.codegen;

import org.ballerinalang.compiler.BLangCompilerException;
import org.ballerinalang.model.elements.PackageID;
import org.ballerinalang.util.diagnostic.DiagnosticCode;
import org.objectweb.asm.ClassTooLargeException;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodTooLargeException;
import org.objectweb.asm.MethodVisitor;
import org.wso2.ballerinalang.compiler.PackageCache;
import org.wso2.ballerinalang.compiler.bir.codegen.internal.JarFile;
import org.wso2.ballerinalang.compiler.bir.codegen.internal.JavaClass;
import org.wso2.ballerinalang.compiler.bir.codegen.internal.LambdaMetadata;
import org.wso2.ballerinalang.compiler.bir.codegen.interop.BIRFunctionWrapper;
import org.wso2.ballerinalang.compiler.bir.codegen.interop.ExternalMethodGen;
import org.wso2.ballerinalang.compiler.bir.codegen.interop.InteropValidator;
import org.wso2.ballerinalang.compiler.bir.codegen.interop.JInteropException;
import org.wso2.ballerinalang.compiler.bir.codegen.interop.OldStyleExternalFunctionWrapper;
import org.wso2.ballerinalang.compiler.bir.model.BIRInstruction;
import org.wso2.ballerinalang.compiler.bir.model.BIRNode;
import org.wso2.ballerinalang.compiler.bir.model.BIRNode.BIRFunction;
import org.wso2.ballerinalang.compiler.bir.model.BIRNode.BIRGlobalVariableDcl;
import org.wso2.ballerinalang.compiler.bir.model.BIRNode.BIRPackage;
import org.wso2.ballerinalang.compiler.bir.model.BIRNode.BIRTypeDefinition;
import org.wso2.ballerinalang.compiler.bir.model.BIRNode.BIRVariableDcl;
import org.wso2.ballerinalang.compiler.bir.model.BIRNonTerminator.NewInstance;
import org.wso2.ballerinalang.compiler.bir.model.VarKind;
import org.wso2.ballerinalang.compiler.bir.model.VarScope;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolTable;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BObjectTypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BPackageSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.Symbols;
import org.wso2.ballerinalang.compiler.semantics.model.types.BInvokableType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BNilType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BObjectType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BServiceType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BType;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.Name;
import org.wso2.ballerinalang.compiler.util.Names;
import org.wso2.ballerinalang.compiler.util.TypeTags;
import org.wso2.ballerinalang.compiler.util.diagnotic.BLangDiagnosticLogHelper;
import org.wso2.ballerinalang.util.Flags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_8;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.CURRENT_MODULE_INIT;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.FILE_NAME_PERIOD_SEPERATOR;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.JAVA_PACKAGE_SEPERATOR;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.JAVA_THREAD;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.LOCK_STORE;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.MODULE_INIT_CLASS_NAME;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.MODULE_STOP;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.OBJECT;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.VALUE_CREATOR;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmDesugarPhase.addDefaultableBooleanVarsToSignature;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmDesugarPhase.rewriteRecordInits;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmMethodGen.cleanupBalExt;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmMethodGen.cleanupPathSeperators;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmMethodGen.generateDefaultConstructor;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmMethodGen.generateField;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmMethodGen.getFunction;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmMethodGen.getFunctions;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmMethodGen.getMainFunc;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmMethodGen.getMethodDesc;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmMethodGen.getTypeDef;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmMethodGen.isExternFunc;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmTerminatorGen.toNameString;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmTypeGen.generateCreateTypesMethod;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmTypeGen.generateUserDefinedTypeFields;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmTypeGen.generateValueCreatorMethods;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmTypeGen.isServiceDefAvailable;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmValueGen.getTypeValueClassName;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmValueGen.injectDefaultParamInitsToAttachedFuncs;
import static org.wso2.ballerinalang.compiler.bir.codegen.interop.ExternalMethodGen.createExternalFunctionWrapper;
import static org.wso2.ballerinalang.compiler.bir.codegen.interop.ExternalMethodGen.injectDefaultParamInits;
import static org.wso2.ballerinalang.compiler.bir.codegen.interop.ExternalMethodGen.isBallerinaBuiltinModule;

/**
 * BIR module to JVM byte code generation class.
 *
 * @since 1.2.0
 */
public class JvmPackageGen {

    static final boolean IS_BSTRING;
    private static final CompilerContext.Key<JvmPackageGen> JVM_PACKAGE_GEN_KEY = new CompilerContext.Key<>();

    static {
        String bStringProp = System.getProperty("ballerina.bstring");
        IS_BSTRING = (bStringProp != null && !"".equals(bStringProp));
    }

    public final SymbolTable symbolTable;
    public final PackageCache packageCache;
    private final JvmMethodGen jvmMethodGen;
    private Map<String, BIRFunctionWrapper> birFunctionMap;
    private Map<String, String> externalMapCache;
    private Map<String, String> globalVarClassNames;
    private Map<String, PackageID> dependentModules;
    private BLangDiagnosticLogHelper dlog;

    private JvmPackageGen(CompilerContext compilerContext) {

        compilerContext.put(JVM_PACKAGE_GEN_KEY, this);

        birFunctionMap = new HashMap<>();
        globalVarClassNames = new HashMap<>();
        externalMapCache = new HashMap<>();
        dependentModules = new LinkedHashMap<>();
        symbolTable = SymbolTable.getInstance(compilerContext);
        packageCache = PackageCache.getInstance(compilerContext);
        dlog = BLangDiagnosticLogHelper.getInstance(compilerContext);
        jvmMethodGen = new JvmMethodGen(this);

        JvmCastGen.symbolTable = symbolTable;
        JvmInstructionGen.anyType = symbolTable.anyType;
    }

    public static JvmPackageGen getInstance(CompilerContext compilerContext) {

        JvmPackageGen jvmPackageGen = compilerContext.get(JVM_PACKAGE_GEN_KEY);
        if (jvmPackageGen == null) {
            jvmPackageGen = new JvmPackageGen(compilerContext);
        }

        return jvmPackageGen;
    }

    private static String getBvmAlias(String orgName, String moduleName) {

        if (Names.ANON_ORG.value.equals(orgName)) {
            return moduleName;
        }
        return orgName + "/" + moduleName;
    }

    private static void addBuiltinImports(BIRPackage currentModule, Set<PackageID> dependentModuleArray) {
        // Add the builtin and utils modules to the imported list of modules

        Name ballerinaOrgName = new Name("ballerina");
        Name builtInVersion = new Name("");

        PackageID annotationsModule = new PackageID(ballerinaOrgName, new Name("lang.annotations"), builtInVersion);

        if (isSameModule(currentModule, annotationsModule)) {
            return;
        }

        dependentModuleArray.add(annotationsModule);

        if (isLangModule(currentModule)) {
            return;
        }

        PackageID internalModule = new PackageID(ballerinaOrgName, new Name("lang.__internal"), builtInVersion);

        if (isSameModule(currentModule, internalModule)) {
            return;
        }

        dependentModuleArray.add(internalModule);

        PackageID langArrayModule = new PackageID(ballerinaOrgName, new Name("lang.array"), builtInVersion);
        PackageID langDecimalModule = new PackageID(ballerinaOrgName, new Name("lang.decimal"), builtInVersion);
        PackageID langErrorModule = new PackageID(ballerinaOrgName, new Name("lang.error"), builtInVersion);
        PackageID langFloatModule = new PackageID(ballerinaOrgName, new Name("lang.float"), builtInVersion);
        PackageID langFutureModule = new PackageID(ballerinaOrgName, new Name("lang.future"), builtInVersion);
        PackageID langIntModule = new PackageID(ballerinaOrgName, new Name("lang.int"), builtInVersion);
        PackageID langMapModule = new PackageID(ballerinaOrgName, new Name("lang.map"), builtInVersion);
        PackageID langObjectModule = new PackageID(ballerinaOrgName, new Name("lang.object"), builtInVersion);
        PackageID langStreamModule = new PackageID(ballerinaOrgName, new Name("lang.stream"), builtInVersion);
        PackageID langTableModule = new PackageID(ballerinaOrgName, new Name("lang.table"), builtInVersion);
        PackageID langStringModule = new PackageID(ballerinaOrgName, new Name("lang.string"), builtInVersion);
        PackageID langValueModule = new PackageID(ballerinaOrgName, new Name("lang.value"), builtInVersion);
        PackageID langXmlModule = new PackageID(ballerinaOrgName, new Name("lang.xml"), builtInVersion);
        PackageID langTypedescModule = new PackageID(ballerinaOrgName, new Name("lang.typedesc"), builtInVersion);
        PackageID langBooleanModule = new PackageID(ballerinaOrgName, new Name("lang.boolean"), builtInVersion);

        dependentModuleArray.add(langArrayModule);
        dependentModuleArray.add(langDecimalModule);
        dependentModuleArray.add(langErrorModule);
        dependentModuleArray.add(langFloatModule);
        dependentModuleArray.add(langFutureModule);
        dependentModuleArray.add(langIntModule);
        dependentModuleArray.add(langMapModule);
        dependentModuleArray.add(langObjectModule);
        dependentModuleArray.add(langStreamModule);
        dependentModuleArray.add(langTableModule);
        dependentModuleArray.add(langStringModule);
        dependentModuleArray.add(langValueModule);
        dependentModuleArray.add(langXmlModule);
        dependentModuleArray.add(langTypedescModule);
        dependentModuleArray.add(langBooleanModule);
    }

    private static boolean isSameModule(BIRPackage moduleId, PackageID importModule) {

        if (!moduleId.org.value.equals(importModule.orgName.value)) {
            return false;
        } else if (!moduleId.name.value.equals(importModule.name.value)) {
            return false;
        } else {
            return moduleId.version.value.equals(importModule.version.value);
        }
    }

    private static boolean isLangModule(BIRPackage moduleId) {

        if (!"ballerina".equals(moduleId.org.value)) {
            return false;
        }
        return moduleId.name.value.indexOf("lang.") == 0;
    }

    private static void generatePackageVariable(BIRGlobalVariableDcl globalVar, ClassWriter cw) {

        String varName = globalVar.name.value;
        BType bType = globalVar.type;
        generateField(cw, bType, varName, true);
    }

    private static void generateLockForVariable(ClassWriter cw) {

        String lockStoreClass = "L" + LOCK_STORE + ";";
        FieldVisitor fv;
        fv = cw.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC, "LOCK_STORE", lockStoreClass, null, null);
        fv.visitEnd();
    }

    private static void generateStaticInitializer(ClassWriter cw, String className,
                                                  boolean serviceEPAvailable) {

        MethodVisitor mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);

        String lockStoreClass = "L" + LOCK_STORE + ";";
        mv.visitTypeInsn(NEW, LOCK_STORE);
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, LOCK_STORE, "<init>", "()V", false);
        mv.visitFieldInsn(PUTSTATIC, className, "LOCK_STORE", lockStoreClass);

        setServiceEPAvailableField(cw, mv, serviceEPAvailable, className);

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void setServiceEPAvailableField(ClassWriter cw, MethodVisitor mv, boolean serviceEPAvailable,
                                                   String initClass) {

        FieldVisitor fv = cw.visitField(ACC_PUBLIC + ACC_STATIC, "serviceEPAvailable", "Z", null, null);
        fv.visitEnd();

        if (serviceEPAvailable) {
            mv.visitInsn(ICONST_1);
            mv.visitFieldInsn(PUTSTATIC, initClass, "serviceEPAvailable", "Z");
        } else {
            mv.visitInsn(ICONST_0);
            mv.visitFieldInsn(PUTSTATIC, initClass, "serviceEPAvailable", "Z");
        }
    }

    static String computeLockNameFromString(String varName) {

        return "$lock" + varName;
    }

    static String getModuleLevelClassName(String orgName, String moduleName, String sourceFileName) {

        String className = cleanupSourceFileName(sourceFileName);
        // handle source file path start with '/'.
        if (className.startsWith(JAVA_PACKAGE_SEPERATOR)) {
            className = className.substring(1);
        }
        if (!moduleName.equals(".")) {
            className = cleanupName(moduleName) + "/" + className;
        }

        if (!orgName.equalsIgnoreCase("$anon")) {
            className = cleanupName(orgName) + "/" + className;
        }

        return className;
    }

    public static String getPackageName(Name orgName, Name moduleName) {

        return getPackageName(orgName.getValue(), moduleName.getValue());
    }

    public static String getPackageName(String orgName, String moduleName) {

        String packageName = "";
        if (!moduleName.equals(".")) {
            packageName = cleanupName(moduleName) + "/";
        }

        if (!orgName.equalsIgnoreCase("$anon")) {
            packageName = cleanupName(orgName) + "/" + packageName;
        }

        return packageName;
    }

    private static String cleanupName(String name) {

        return name.replace(".", "_");
    }

    private static String cleanupSourceFileName(String name) {

        return name.replace(".", FILE_NAME_PERIOD_SEPERATOR);
    }

    public static String cleanupPackageName(String pkgName) {

        int index = pkgName.lastIndexOf("/");
        if (index > 0) {
            return pkgName.substring(0, index);
        } else {
            return pkgName;
        }
    }

    public static BIRFunctionWrapper getFunctionWrapper(BIRFunction currentFunc, String orgName, String moduleName,
                                                        String version, String moduleClass) {

        BInvokableType functionTypeDesc = currentFunc.type;
        BIRVariableDcl receiver = currentFunc.receiver;
        BType attachedType = receiver != null ? receiver.type : null;
        String jvmMethodDescription = getMethodDesc(functionTypeDesc.paramTypes, functionTypeDesc.retType,
                attachedType, false);
        String jvmMethodDescriptionBString = getMethodDesc(functionTypeDesc.paramTypes, functionTypeDesc.retType,
                attachedType, false);

        return new BIRFunctionWrapper(orgName, moduleName, version, currentFunc, moduleClass, jvmMethodDescription,
                jvmMethodDescriptionBString);
    }

    static PackageID packageToModuleId(BIRPackage mod) {

        return new PackageID(mod.org, mod.name, mod.version);
    }

    private static void generateShutdownSignalListener(String initClass, Map<String, byte[]> jarEntries) {

        String innerClassName = initClass + "$SignalListener";
        ClassWriter cw = new BallerinaClassWriter(COMPUTE_FRAMES);
        cw.visit(V1_8, ACC_SUPER, innerClassName, null, JAVA_THREAD, null);

        // create constructor
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, JAVA_THREAD, "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // implement run() method
        mv = cw.visitMethod(ACC_PUBLIC, "run", "()V", null, null);
        mv.visitCode();

        mv.visitMethodInsn(INVOKESTATIC, initClass, MODULE_STOP, "()V", false);

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        jarEntries.put(innerClassName + ".class", cw.toByteArray());
    }

    private static BIRFunction findFunction(BIRNode parentNode, String funcName) {

        BIRFunction func;
        if (parentNode instanceof BIRTypeDefinition) {
            BIRTypeDefinition typeDef = (BIRTypeDefinition) parentNode;
            func = findFunction(typeDef.attachedFuncs, funcName);
        } else if (parentNode instanceof BIRPackage) {
            BIRPackage pkg = (BIRPackage) parentNode;
            func = findFunction(pkg.functions, funcName);
        } else {
            throw new IllegalStateException();
        }

        return func;
    }

    private static BIRFunction findFunction(List<BIRFunction> functions, String funcName) {

        for (BIRFunction func : functions) {
            if (JvmMethodGen.cleanupFunctionName(func.name.value).equals(funcName)) {
                return func;
            }
        }

        throw new IllegalStateException("cannot find function: '" + funcName + "'");
    }

    BType lookupTypeDef(NewInstance objectNewIns) {

        if (!objectNewIns.isExternalDef) {
            return objectNewIns.def.type;
        } else {
            PackageID id = objectNewIns.externalPackageId;
            BPackageSymbol symbol = packageCache.getSymbol(id.orgName + "/" + id.name);
            if (symbol != null) {
                BObjectTypeSymbol objectTypeSymbol =
                        (BObjectTypeSymbol) symbol.scope.lookup(new Name(objectNewIns.objectName)).symbol;
                if (objectTypeSymbol != null) {
                    return objectTypeSymbol.type;
                }
            }

            throw new BLangCompilerException("Reference to unknown type " + objectNewIns.externalPackageId
                    + "/" + objectNewIns.objectName);
        }
    }

    String lookupGlobalVarClassName(String pkgName, String varName) {

        String key = pkgName + varName;
        if (!globalVarClassNames.containsKey(key)) {
            return pkgName + MODULE_INIT_CLASS_NAME;
        } else {
            return globalVarClassNames.get(key);
        }
    }

    private void generateDependencyList(BPackageSymbol packageSymbol, JarFile jarFile,
                                        InteropValidator interopValidator) {

        if (packageSymbol.bir != null) {
            generatePackage(packageSymbol.bir, jarFile, interopValidator, false);
        } else {
            for (BPackageSymbol importPkgSymbol : packageSymbol.imports) {
                if (importPkgSymbol == null) {
                    continue;
                }
                generateDependencyList(importPkgSymbol, jarFile, interopValidator);
            }
        }

        PackageID moduleId = packageSymbol.pkgID;

        String pkgName = getPackageName(moduleId.orgName, moduleId.name);
        if (!dependentModules.containsKey(pkgName)) {
            dependentModules.put(pkgName, moduleId);
        }
    }

    void generatePackage(BIRNode.BIRPackage module, JarFile jarFile, InteropValidator interopValidator,
                         boolean isEntry) {

        String orgName = module.org.value;
        String moduleName = module.name.value;
        String pkgName = getPackageName(orgName, moduleName);

        Set<PackageID> dependentModuleSet = new LinkedHashSet<>();

        addBuiltinImports(module, dependentModuleSet);

        for (BIRNode.BIRImportModule importModule : module.importModules) {
            BPackageSymbol pkgSymbol = packageCache.getSymbol(getBvmAlias(importModule.org.value,
                    importModule.name.value));
            generateDependencyList(pkgSymbol, jarFile, interopValidator);
            if (dlog.getErrorCount() > 0) {
                return;
            }
        }

        String typeOwnerClass = getModuleLevelClassName(orgName, moduleName, MODULE_INIT_CLASS_NAME);
        Map<String, JavaClass> jvmClassMap = generateClassNameMappings(module, pkgName, typeOwnerClass,
                interopValidator, isEntry);
        if (!isEntry || dlog.getErrorCount() > 0) {
            return;
        }

        // desugar parameter initialization
        injectDefaultParamInits(module, jvmMethodGen, this);
        injectDefaultParamInitsToAttachedFuncs(module, jvmMethodGen, this);

        // create dependant modules flat array
        createDependantModuleFlatArray(dependentModuleSet);
        List<PackageID> dependentModuleArray = new ArrayList<>(dependentModuleSet);

        // enrich current package with package initializers
        jvmMethodGen.enrichPkgWithInitializers(jvmClassMap, typeOwnerClass, module, dependentModuleArray);

        // generate the shutdown listener class.
        generateShutdownSignalListener(typeOwnerClass, jarFile.pkgEntries);

        // desugar the record init function
        rewriteRecordInits(module.typeDefs);

        // generate object/record value classes
        JvmValueGen valueGen = new JvmValueGen(module, this, jvmMethodGen);
        valueGen.generateValueClasses(jarFile.pkgEntries);

        // generate frame classes
        jvmMethodGen.generateFrameClasses(module, jarFile.pkgEntries);

        // generate module classes
        generateModuleClasses(module, jarFile, orgName, moduleName, typeOwnerClass, jvmClassMap, dependentModuleArray);

    }

    private void generateModuleClasses(BIRPackage module, JarFile jarFile, String orgName, String moduleName,
                                       String typeOwnerClass, Map<String, JavaClass> jvmClassMap,
                                       List<PackageID> dependentModuleArray) {

        jvmClassMap.entrySet().parallelStream().forEach(entry -> {
            String moduleClass = entry.getKey();
            JavaClass javaClass = entry.getValue();
            ClassWriter cw = new BallerinaClassWriter(COMPUTE_FRAMES);
            LambdaMetadata lambdaMetadata = new LambdaMetadata(moduleClass);

            if (Objects.equals(moduleClass, typeOwnerClass)) {
                cw.visit(V1_8, ACC_PUBLIC + ACC_SUPER, moduleClass, null, VALUE_CREATOR, null);
                generateDefaultConstructor(cw, VALUE_CREATOR);
                generateUserDefinedTypeFields(cw, module.typeDefs);
                generateValueCreatorMethods(cw, module.typeDefs, module, typeOwnerClass, symbolTable);
                // populate global variable to class name mapping and generate them
                for (BIRGlobalVariableDcl globalVar : module.globalVars) {
                    if (globalVar != null) {
                        generatePackageVariable(globalVar, cw);
                    }
                }

                BIRFunction mainFunc = getMainFunc(module.functions);
                String mainClass = "";
                if (mainFunc != null) {
                    mainClass = getModuleLevelClassName(orgName, moduleName,
                            cleanupPathSeperators(cleanupBalExt(mainFunc.pos.getSource().cUnitName)));
                }

                boolean serviceEPAvailable = isServiceDefAvailable(module.typeDefs);

                jvmMethodGen.generateMainMethod(mainFunc, cw, module, moduleClass, serviceEPAvailable);
                if (mainFunc != null) {
                    jvmMethodGen.generateLambdaForMain(mainFunc, cw, module, mainClass, moduleClass);
                }
                jvmMethodGen.generateLambdaForPackageInits(cw, module, mainClass, moduleClass, dependentModuleArray);
                jarFile.manifestEntries.put("Main-Class", moduleClass);

                generateLockForVariable(cw);
                generateStaticInitializer(cw, moduleClass, serviceEPAvailable);
                generateCreateTypesMethod(cw, module.typeDefs, typeOwnerClass, symbolTable);
                jvmMethodGen.generateModuleInitializer(cw, module, typeOwnerClass);
                jvmMethodGen.generateExecutionStopMethod(cw, typeOwnerClass, module, dependentModuleArray,
                        typeOwnerClass);
            } else {
                cw.visit(V1_8, ACC_PUBLIC + ACC_SUPER, moduleClass, null, OBJECT, null);
                generateDefaultConstructor(cw, OBJECT);
            }
            cw.visitSource(javaClass.sourceFileName, null);
            // generate methods
            for (BIRFunction func : javaClass.functions) {
                String workerName = getFunction(func).workerName == null ? null : func.workerName.value;
                jvmMethodGen.generateMethod(getFunction(func), cw, module, null, false, workerName, lambdaMetadata);
            }
            // generate lambdas created during generating methods
            for (Map.Entry<String, BIRInstruction> lambda : lambdaMetadata.getLambdas().entrySet()) {
                String name = lambda.getKey();
                BIRInstruction call = lambda.getValue();
                jvmMethodGen.generateLambdaMethod(call, cw, name);
            }
            cw.visitEnd();

            byte[] bytes = getBytes(cw, module);
            jarFile.pkgEntries.put(moduleClass + ".class", bytes);
        });
    }

    private void createDependantModuleFlatArray(Set<PackageID> dependentModuleArray) {

        for (Map.Entry<String, PackageID> entry : dependentModules.entrySet()) {
            PackageID id = entry.getValue();
            dependentModuleArray.add(id);
        }
    }

    /**
     * Java Class will be generate for each source file. This method add class mappings to globalVar and filters the
     * unctions based on their source file name and then returns map of associated java class contents.
     *
     * @param module           bir module
     * @param pkgName          module name
     * @param initClass        module init class name
     * @param interopValidator interop validator instance
     * @param isEntry          is entry module flag
     * @return The map of javaClass records on given source file name
     */
    private Map<String, JavaClass> generateClassNameMappings(BIRPackage module, String pkgName, String initClass,
                                                             InteropValidator interopValidator,
                                                             boolean isEntry) {

        String orgName = module.org.value;
        String moduleName = module.name.value;
        String version = module.version.value;
        Map<String, JavaClass> jvmClassMap = new HashMap<>();

        if (isEntry) {
            for (BIRNode.BIRConstant constant : module.constants) {
                module.globalVars.add(new BIRGlobalVariableDcl(constant.pos, constant.flags, constant.type, null,
                        constant.name, VarScope.GLOBAL, VarKind.CONSTANT, ""));
            }
        }
        for (BIRGlobalVariableDcl globalVar : module.globalVars) {
            if (globalVar != null) {
                globalVarClassNames.put(pkgName + globalVar.name.value, initClass);
            }
        }

        globalVarClassNames.put(pkgName + "LOCK_STORE", initClass);
        // filter out functions.
        List<BIRFunction> functions = module.functions;
        if (functions.size() > 0) {
            int funcSize = functions.size();
            int count = 0;
            // Generate init class. Init function should be the first function of the package, hence check first
            // function.
            BIRFunction initFunc = functions.get(0);
            String functionName = initFunc.name.value;
            JavaClass klass = new JavaClass(initFunc.pos.src.cUnitName);
            klass.functions.add(0, initFunc);
            jvmMethodGen.addInitAndTypeInitInstructions(module, initFunc);
            jvmClassMap.put(initClass, klass);
            birFunctionMap.put(pkgName + functionName, getFunctionWrapper(initFunc, orgName, moduleName,
                    version, initClass));
            count += 1;

            // Add start function
            BIRFunction startFunc = functions.get(1);
            functionName = startFunc.name.value;
            birFunctionMap.put(pkgName + functionName, getFunctionWrapper(startFunc, orgName, moduleName,
                    version, initClass));
            klass.functions.add(1, startFunc);
            count += 1;

            // Add stop function
            BIRFunction stopFunc = functions.get(2);
            functionName = stopFunc.name.value;
            birFunctionMap.put(pkgName + functionName, getFunctionWrapper(stopFunc, orgName, moduleName,
                    version, initClass));
            klass.functions.add(2, stopFunc);
            count += 1;

            // Generate classes for other functions.
            while (count < funcSize) {
                BIRFunction birFunc = functions.get(count);
                count = count + 1;
                // link the bir function for lookup
                String birFuncName = birFunc.name.value;

                String balFileName;

                if (birFunc.pos == null) {
                    balFileName = MODULE_INIT_CLASS_NAME;
                } else {
                    balFileName = birFunc.pos.src.cUnitName;
                }
                String birModuleClassName = getModuleLevelClassName(orgName, moduleName,
                        cleanupPathSeperators(cleanupBalExt(balFileName)));

                if (!isBallerinaBuiltinModule(orgName, moduleName)) {
                    JavaClass javaClass = jvmClassMap.get(birModuleClassName);
                    if (javaClass != null) {
                        javaClass.functions.add(birFunc);
                    } else {
                        klass = new JavaClass(balFileName);
                        klass.functions.add(0, birFunc);
                        jvmClassMap.put(birModuleClassName, klass);
                    }
                }

                interopValidator.setEntryModuleValidation(isEntry);

                BIRFunctionWrapper birFuncWrapperOrError;
                try {
                    if (isExternFunc(getFunction(birFunc))) {
                        birFuncWrapperOrError = createExternalFunctionWrapper(interopValidator, birFunc, orgName,
                                moduleName, version, birModuleClassName, this);
                    } else {
                        if (isEntry) {
                            addDefaultableBooleanVarsToSignature(birFunc, symbolTable.booleanType);
                        }
                        birFuncWrapperOrError = getFunctionWrapper(birFunc, orgName, moduleName, version,
                                birModuleClassName);
                    }
                } catch (JInteropException e) {
                    dlog.error(birFunc.pos, e.getCode(), e.getMessage());
                    continue;
                }
                birFunctionMap.put(pkgName + birFuncName, birFuncWrapperOrError);
            }
        }

        // link module init function that will be generated
        BIRFunction moduleInitFunction = new BIRFunction(null, new Name(CURRENT_MODULE_INIT), 0,
                new BInvokableType(Collections.emptyList(), null, new BNilType(), null), new Name(""), 0, null);
        birFunctionMap.put(pkgName + CURRENT_MODULE_INIT, getFunctionWrapper(moduleInitFunction, orgName, moduleName,
                version, initClass));

        // link module stop function that will be generated
        BIRFunction moduleStopFunction = new BIRFunction(null, new Name(MODULE_STOP), 0,
                new BInvokableType(Collections.emptyList(), null, new BNilType(), null), new Name(""), 0, null);
        birFunctionMap.put(pkgName + MODULE_STOP, getFunctionWrapper(moduleStopFunction, orgName, moduleName,
                version, initClass));

        // link typedef - object attached native functions
        List<BIRTypeDefinition> typeDefs = module.typeDefs;

        for (BIRTypeDefinition optionalTypeDef : typeDefs) {
            BIRTypeDefinition typeDef = getTypeDef(optionalTypeDef);
            BType bType = typeDef.type;

            if ((bType.tag != TypeTags.OBJECT ||
                    Symbols.isFlagOn(((BObjectType) bType).tsymbol.flags, Flags.ABSTRACT)) &&
                    !(bType instanceof BServiceType)) {
                continue;
            }

            List<BIRFunction> attachedFuncs = getFunctions(typeDef.attachedFuncs);
            String typeName = toNameString(bType);
            for (BIRFunction func : attachedFuncs) {

                // link the bir function for lookup
                BIRFunction currentFunc = getFunction(func);
                String functionName = currentFunc.name.value;
                String lookupKey = typeName + "." + functionName;

                if (!isExternFunc(currentFunc)) {
                    String className = getTypeValueClassName(module, typeName);
                    birFunctionMap.put(pkgName + lookupKey, getFunctionWrapper(currentFunc, orgName, moduleName,
                            version, className));
                    continue;
                }

                String jClassName = lookupExternClassName(cleanupPackageName(pkgName), lookupKey);
                if (jClassName != null) {
                    OldStyleExternalFunctionWrapper wrapper =
                            ExternalMethodGen.createOldStyleExternalFunctionWrapper(currentFunc, orgName,
                                    moduleName, version, jClassName, jClassName, isEntry, symbolTable);
                    birFunctionMap.put(pkgName + lookupKey, wrapper);
                } else {
                    throw new BLangCompilerException("native function not available: " +
                            pkgName + lookupKey);
                }
            }
        }
        return jvmClassMap;
    }

    public String lookupExternClassName(String pkgName, String functionName) {

        return externalMapCache.get(cleanupName(pkgName) + "/" + functionName);
    }

    public byte[] getBytes(ClassWriter cw, BIRNode node) {

        byte[] result;
        try {
            return cw.toByteArray();
        } catch (MethodTooLargeException e) {
            String funcName = e.getMethodName();
            BIRFunction func = findFunction(node, funcName);
            dlog.error(func.pos, DiagnosticCode.METHOD_TOO_LARGE, func.name.value);
            result = new byte[0];
        } catch (ClassTooLargeException e) {
            dlog.error(node.pos, DiagnosticCode.FILE_TOO_LARGE, e.getClassName());
            result = new byte[0];
        } catch (Exception e) {
            throw new BLangCompilerException(e.getMessage(), e);
        }

        return result;
    }

    void clearPackageGenInfoMaps() {

        birFunctionMap.clear();
        globalVarClassNames.clear();
        externalMapCache.clear();
        dependentModules.clear();
    }

    public BIRFunctionWrapper lookupBIRFunctionWrapper(String lookupKey) {

        return this.birFunctionMap.get(lookupKey);
    }

    void addExternClassMapping(String key, String value) {

        this.externalMapCache.put(key, value);
    }
}
