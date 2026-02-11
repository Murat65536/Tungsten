package kaptainwutax.tungsten.simulation.tools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import kaptainwutax.tungsten.simulation.SimMetadata;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Interpreter;

/**
 * Generates a single simulated class that extends ClientPlayerEntity.
 * All methods from the 5-class hierarchy are flattened into this one class,
 * so virtual dispatch on 'this' always finds the ASM-transformed version.
 * <p>
 * Methods overridden at multiple hierarchy levels are handled by renaming
 * parent versions with a prefix (sim$ClassName$methodName) and rewriting
 * super calls to target the renamed version.
 */
public final class SimClassGenerator {
	// Ordered from root to leaf (processing order for hierarchy)
	private static final List<String> HIERARCHY_ROOT_TO_LEAF = List.of(
		"net.minecraft.entity.Entity",
		"net.minecraft.entity.LivingEntity",
		"net.minecraft.entity.player.PlayerEntity",
		"net.minecraft.client.network.AbstractClientPlayerEntity",
		"net.minecraft.client.network.ClientPlayerEntity"
	);

	private static final String LEAF_CLASS = "net.minecraft.client.network.ClientPlayerEntity";
	private static final String CLIENT_PLAYER_INTERNAL = "net/minecraft/client/network/ClientPlayerEntity";

	private SimClassGenerator() {
	}

	public static void main(String[] args) throws Exception {
		Args parsed = Args.parse(args);
		WhitelistConfig config = WhitelistConfig.load(parsed.whitelistPath);

		Set<String> hierarchy = new LinkedHashSet<>();
		SimMetadata metadata = new SimMetadata();
		metadata.immutableTypes.addAll(config.immutableTypes());

		for (String className : HIERARCHY_ROOT_TO_LEAF) {
			String internalName = toInternalName(className);
			if (!config.hasClass(internalName)) {
				throw new IllegalStateException("Whitelist missing class: " + className);
			}
			hierarchy.add(internalName);
		}

		// Determine the simulated class name
		String simInternal = parsed.simPrefix + "/SimulatedPlayer";
		String simDotName = simInternal.replace('/', '.');
		metadata.hierarchyClasses.add(simDotName);
		metadata.classMap.put(LEAF_CLASS, simDotName);

		// Read all hierarchy classes
		Map<String, ClassNode> classNodes = new LinkedHashMap<>();
		for (String className : HIERARCHY_ROOT_TO_LEAF) {
			String internalName = toInternalName(className);
			byte[] classBytes = readClassBytes(internalName, parsed.inputJar);
			ClassNode node = new ClassNode();
			new ClassReader(classBytes).accept(node, 0);
			classNodes.put(internalName, node);

			// Record field metadata
			List<SimMetadata.FieldSpec> metaFields = new ArrayList<>();
			Set<String> unmatched = new LinkedHashSet<>(config.expectedFieldKeys(internalName));
			Set<String> whitelisted = new LinkedHashSet<>();
			for (FieldNode field : node.fields) {
				boolean isStatic = (field.access & Opcodes.ACC_STATIC) != 0;
				if (!isStatic) {
					metaFields.add(new SimMetadata.FieldSpec(field.name, field.desc));
				}
				if (!isStatic && config.isWhitelistedField(internalName, field.name, field.desc)) {
					unmatched.remove(field.name + ":" + field.desc);
					unmatched.remove(field.name);
					whitelisted.add(field.name);
				}
			}
			if (!unmatched.isEmpty()) {
				throw new IllegalStateException("Whitelist fields not found for " + className + ": " + unmatched);
			}
			metadata.fieldMap.put(className, metaFields);
			metadata.whitelistedFields.put(className, whitelisted);
		}

		// Build the flattened class
		ClassNode simNode = new ClassNode();
		simNode.version = Opcodes.V21;
		simNode.access = Opcodes.ACC_PUBLIC;
		simNode.name = simInternal;
		simNode.superName = CLIENT_PLAYER_INTERNAL;

		// Track which method signatures (name+desc) are already added.
		// Methods from the leaf class take priority (added first), then going
		// up to root. Parent versions of conflicting methods are renamed.
		// Key: name + desc
		Map<String, String> addedMethods = new HashMap<>();
		// Maps (originalOwner, name, desc) -> renamedName for super call rewriting
		Map<String, String> superCallMap = new HashMap<>();
		// Track which original class each method node came from
		Map<MethodNode, String> methodOrigin = new HashMap<>();

		// Build set of final methods across the hierarchy that we cannot override
		Set<String> finalMethods = new LinkedHashSet<>();
		for (String className : HIERARCHY_ROOT_TO_LEAF) {
			String internalName = toInternalName(className);
			ClassNode origNode = classNodes.get(internalName);
			for (MethodNode method : origNode.methods) {
				if ((method.access & Opcodes.ACC_FINAL) != 0 && (method.access & Opcodes.ACC_STATIC) == 0) {
					finalMethods.add(method.name + method.desc);
				}
			}
		}

		// Process from leaf to root so leaf methods get priority
		for (int i = HIERARCHY_ROOT_TO_LEAF.size() - 1; i >= 0; i--) {
			String className = HIERARCHY_ROOT_TO_LEAF.get(i);
			String internalName = toInternalName(className);
			ClassNode origNode = classNodes.get(internalName);

			for (MethodNode method : origNode.methods) {
				if ("<init>".equals(method.name) || "<clinit>".equals(method.name)) {
					continue;
				}
				if (method.instructions == null || method.instructions.size() == 0) {
					continue;
				}
				if ((method.access & Opcodes.ACC_STATIC) != 0) {
					continue; // skip static methods — they belong to the original class
				}
				if (finalMethods.contains(method.name + method.desc)) {
					continue; // skip final methods — cannot override, use original impl
				}

				// Apply ASM transforms (stubbing, taint analysis) on the original bytecode
				MethodNode transformed = cloneMethod(method);

				// Apply isCamera override
				if (CLIENT_PLAYER_INTERNAL.equals(internalName) && "isCamera".equals(transformed.name) && "()Z".equals(transformed.desc)) {
					transformed.instructions.clear();
					transformed.tryCatchBlocks.clear();
					transformed.instructions.add(new InsnNode(Opcodes.ICONST_1));
					transformed.instructions.add(new InsnNode(Opcodes.IRETURN));
				} else if (config.isStubbedInHierarchy(transformed.name, new ArrayList<>(hierarchy))) {
					// Stub out method body
					transformed.instructions.clear();
					transformed.tryCatchBlocks.clear();
					Type returnType = Type.getReturnType(transformed.desc);
					if (returnType == Type.VOID_TYPE) {
						transformed.instructions.add(new InsnNode(Opcodes.RETURN));
					} else {
						transformed.instructions.add(pushDefault(returnType));
						transformed.instructions.add(new InsnNode(returnType.getOpcode(Opcodes.IRETURN)));
					}
				} else {
					// Apply taint analysis transforms
					transformMethod(internalName, transformed, config, hierarchy);
				}

				String methodKey = transformed.name + transformed.desc;
				if (addedMethods.containsKey(methodKey)) {
					// This method was already added from a more-derived class.
					// Rename this parent version for super call dispatch.
					String simpleName = internalName.substring(internalName.lastIndexOf('/') + 1);
					String renamedName = "sim$" + simpleName + "$" + transformed.name;
					superCallMap.put(internalName + "." + transformed.name + transformed.desc, renamedName);
					transformed.name = renamedName;
					// Make it private to avoid accidentally overriding
					transformed.access = (transformed.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PRIVATE;
				} else {
					addedMethods.put(methodKey, internalName);
					// Widen private methods to protected so they act as virtual overrides
					// (needed for invokevirtual dispatch to find our copy)
					if ((transformed.access & Opcodes.ACC_PRIVATE) != 0) {
						transformed.access = (transformed.access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PROTECTED;
					}
				}

				simNode.methods.add(transformed);
				methodOrigin.put(transformed, internalName);
			}
		}

		// Build parent chain for hierarchy resolution
		Map<String, String> parentMap = new LinkedHashMap<>();
		for (int i = 0; i < HIERARCHY_ROOT_TO_LEAF.size(); i++) {
			String className = HIERARCHY_ROOT_TO_LEAF.get(i);
			String internalName = toInternalName(className);
			ClassNode node = classNodes.get(internalName);
			if (hierarchy.contains(node.superName)) {
				parentMap.put(internalName, node.superName);
			}
		}

		// Now rewrite all invokespecial (super) calls and invokevirtual calls
		// within the flattened class to target the correct renamed methods.
		rewriteMethodCalls(simNode, hierarchy, superCallMap, simInternal, parentMap, classNodes, methodOrigin);

		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
			@Override
			protected String getCommonSuperClass(String type1, String type2) {
				// If either type is the simulated class, treat it as ClientPlayerEntity
				// for hierarchy resolution purposes
				String resolved1 = type1.equals(simInternal) ? CLIENT_PLAYER_INTERNAL : type1;
				String resolved2 = type2.equals(simInternal) ? CLIENT_PLAYER_INTERNAL : type2;
				
				// Check if one is assignable to the other within the known hierarchy
				Set<String> ancestors1 = getAncestors(resolved1, hierarchy);
				Set<String> ancestors2 = getAncestors(resolved2, hierarchy);
				
				// If type2 is an ancestor of type1, return type2
				if (ancestors1.contains(resolved2)) return type2;
				// If type1 is an ancestor of type2, return type1
				if (ancestors2.contains(resolved1)) return type1;
				
				// Check for common hierarchy ancestor
				for (String a : ancestors1) {
					if (ancestors2.contains(a)) return a;
				}
				
				try {
					return super.getCommonSuperClass(resolved1, resolved2);
				} catch (Exception ex) {
					return "java/lang/Object";
				}
			}
		};
		simNode.accept(writer);
		byte[] output = writer.toByteArray();

		Path outputPath = parsed.outputDir.resolve(simInternal + ".class");
		Files.createDirectories(outputPath.getParent());
		Files.write(outputPath, output);

		metadata.save(parsed.metadataPath);
	}

	/**
	 * Rewrite method calls within the flattened class:
	 * - invokespecial to hierarchy classes: distinguish super calls vs private method calls
	 * - invokevirtual for private hierarchy methods: redirect to simulated class
	 */
	private static void rewriteMethodCalls(ClassNode simNode, Set<String> hierarchy,
			Map<String, String> superCallMap, String simInternal,
			Map<String, String> parentMap, Map<String, ClassNode> classNodes,
			Map<MethodNode, String> methodOrigin) {
		
		// Build a set of all method names+desc that exist on the flattened class,
		// mapping from original name+desc to their final name (which may be renamed)
		// Key: originalOwner + "." + origName + origDesc → finalMethodName on simNode
		Map<String, String> allMethodFinalNames = new HashMap<>();
		for (MethodNode m : simNode.methods) {
			String origin = methodOrigin.get(m);
			if (origin != null) {
				// Determine the original name before renaming
				String origName = m.name;
				if (m.name.startsWith("sim$")) {
					// Renamed method — extract original name from sim$ClassName$origName
					int secondDollar = m.name.indexOf('$', 4);
					if (secondDollar >= 0) {
						origName = m.name.substring(secondDollar + 1);
					}
				}
				allMethodFinalNames.put(origin + "." + origName + m.desc, m.name);
			}
		}

		// Build a set of private methods across the hierarchy for invokevirtual rewriting
		Set<String> privateMethods = new LinkedHashSet<>();
		for (String className : HIERARCHY_ROOT_TO_LEAF) {
			String internalName = toInternalName(className);
			ClassNode node = classNodes.get(internalName);
			if (node != null) {
				for (MethodNode m : node.methods) {
					if ((m.access & Opcodes.ACC_PRIVATE) != 0 && (m.access & Opcodes.ACC_STATIC) == 0) {
						privateMethods.add(internalName + "." + m.name + m.desc);
					}
				}
			}
		}

		for (MethodNode method : simNode.methods) {
			if (method.instructions == null) continue;
			String origin = methodOrigin.get(method);
			
			for (AbstractInsnNode insn : method.instructions) {
				if (!(insn instanceof MethodInsnNode min)) continue;

				if (min.getOpcode() == Opcodes.INVOKESPECIAL && hierarchy.contains(min.owner) && !"<init>".equals(min.name)) {
					String key = min.owner + "." + min.name + min.desc;
					if (privateMethods.contains(key)) {
						// Private method call (NestMates invokespecial on same-class private method).
						// Change to invokevirtual with original owner — access widener makes it accessible.
						// Virtual dispatch finds our copy on SimulatedPlayer for 'this' receivers,
						// and the original for other entity receivers.
						min.setOpcode(Opcodes.INVOKEVIRTUAL);
						// Keep original owner — don't change to simInternal
					} else if (origin != null && min.owner.equals(origin)) {
						// Non-private method call within the same original class — shouldn't happen
						// for invokespecial (non-<init>) unless it's a super call misclassified.
						// Keep original.
					} else {
						// Super call to a parent class.
						String resolvedName = resolveSuperCall(min.owner, min.name, min.desc, superCallMap, parentMap, classNodes);
						if (resolvedName != null) {
							min.owner = simInternal;
							min.name = resolvedName;
						} else {
							// Method only exists once in the hierarchy (the primary version)
							// or is a final method (skipped). Fall back to real parent chain.
							min.owner = CLIENT_PLAYER_INTERNAL;
						}
					}
				} else if (min.getOpcode() == Opcodes.INVOKEVIRTUAL && hierarchy.contains(min.owner)) {
					// For private methods called via invokevirtual (Java 11+ NestMates),
					// access widener makes them accessible and virtual dispatch handles finding
					// our copy on SimulatedPlayer. No rewrite needed.
					// Non-private invokevirtual is also NOT rewritten —
					// virtual dispatch naturally finds the override.
				}
			}
		}
	}

	/**
	 * Resolve a super call by walking up the hierarchy from the invokespecial owner.
	 * Returns the renamed method name if a renamed version was found, or null if the
	 * method only exists once (i.e., it's the primary version in the flattened class).
	 */
	private static String resolveSuperCall(String owner, String methodName, String methodDesc,
			Map<String, String> superCallMap, Map<String, String> parentMap,
			Map<String, ClassNode> classNodes) {
		// Walk from the owner up to find a class that has this method declared
		String current = owner;
		while (current != null) {
			String key = current + "." + methodName + methodDesc;
			String renamed = superCallMap.get(key);
			if (renamed != null) {
				return renamed;
			}
			// Check if this class actually declares the method
			ClassNode node = classNodes.get(current);
			if (node != null) {
				for (MethodNode m : node.methods) {
					if (m.name.equals(methodName) && m.desc.equals(methodDesc)) {
						// Found the declaration, but it's not in the super call map
						// This means it's the primary (non-renamed) version
						return null;
					}
				}
			}
			current = parentMap.get(current);
		}
		return null;
	}

	/**
	 * Get the set of ancestor internal names for a type within the known hierarchy.
	 * Includes the type itself and walks up the hierarchy chain.
	 */
	private static Set<String> getAncestors(String internalName, Set<String> hierarchy) {
		Set<String> ancestors = new LinkedHashSet<>();
		ancestors.add(internalName);
		// Walk up the known hierarchy
		for (int i = HIERARCHY_ROOT_TO_LEAF.size() - 1; i >= 0; i--) {
			String hierInternal = toInternalName(HIERARCHY_ROOT_TO_LEAF.get(i));
			if (hierInternal.equals(internalName)) {
				// Found this type in hierarchy — add all ancestors above it
				for (int j = i - 1; j >= 0; j--) {
					ancestors.add(toInternalName(HIERARCHY_ROOT_TO_LEAF.get(j)));
				}
				break;
			}
		}
		return ancestors;
	}

	private static MethodNode cloneMethod(MethodNode original) {
		MethodNode clone = new MethodNode(
			original.access, original.name, original.desc, original.signature, null
		);
		original.accept(clone);
		// Clear debug info to avoid ClassFormatError: Duplicated LocalVariableTable entries
		if (clone.localVariables != null) {
			clone.localVariables.clear();
		}
		if (clone.visibleLocalVariableAnnotations != null) {
			clone.visibleLocalVariableAnnotations.clear();
		}
		if (clone.invisibleLocalVariableAnnotations != null) {
			clone.invisibleLocalVariableAnnotations.clear();
		}
		return clone;
	}

	private static void transformMethod(
		String ownerInternal,
		MethodNode method,
		WhitelistConfig config,
		Set<String> hierarchy
	) throws Exception {
		Interpreter<BasicValue> interpreter = new TaintInterpreter(
			fieldInsn -> isRemovedField(fieldInsn, config, hierarchy), hierarchy);
		Analyzer<BasicValue> analyzer = new Analyzer<>(interpreter);
		Frame<BasicValue>[] frames = analyzer.analyze(ownerInternal, method);
		AbstractInsnNode[] instructions = method.instructions.toArray();

		for (int i = 0; i < instructions.length; i++) {
			AbstractInsnNode insn = instructions[i];
			Frame<BasicValue> frame = frames[i];
			if (insn instanceof FieldInsnNode fieldInsn) {
				applyFieldRewrite(method.instructions, fieldInsn, frame, config, hierarchy);
			} else if (insn instanceof MethodInsnNode methodInsn) {
				applyInvokeRewrite(method.instructions, methodInsn, frame, hierarchy);
			}
		}
	}

	private static void applyFieldRewrite(
		InsnList insns,
		FieldInsnNode fieldInsn,
		Frame<BasicValue> frame,
		WhitelistConfig config,
		Set<String> hierarchy
	) {
		boolean removed = isRemovedField(fieldInsn, config, hierarchy);
		switch (fieldInsn.getOpcode()) {
			case Opcodes.GETFIELD -> {
				boolean tainted = isReceiverTainted(frame, 1);
				if (removed || tainted) {
					InsnList replacement = new InsnList();
					replacement.add(new InsnNode(Opcodes.POP));
					replacement.add(pushDefault(Type.getType(fieldInsn.desc)));
					replace(insns, fieldInsn, replacement);
				}
			}
			case Opcodes.GETSTATIC -> {
				if (removed) {
					InsnList replacement = new InsnList();
					replacement.add(pushDefault(Type.getType(fieldInsn.desc)));
					replace(insns, fieldInsn, replacement);
				}
			}
			case Opcodes.PUTFIELD -> {
				boolean tainted = isReceiverTainted(frame, 2);
				if (removed || tainted) {
					InsnList replacement = new InsnList();
					replacement.add(popValue(Type.getType(fieldInsn.desc)));
					replacement.add(new InsnNode(Opcodes.POP));
					replace(insns, fieldInsn, replacement);
				}
			}
			case Opcodes.PUTSTATIC -> {
				if (removed) {
					InsnList replacement = new InsnList();
					replacement.add(popValue(Type.getType(fieldInsn.desc)));
					replace(insns, fieldInsn, replacement);
				}
			}
			default -> {
			}
		}
	}

	private static void applyInvokeRewrite(
		InsnList insns,
		MethodInsnNode methodInsn,
		Frame<BasicValue> frame,
		Set<String> hierarchy
	) {
		int opcode = methodInsn.getOpcode();
		Type[] args = Type.getArgumentTypes(methodInsn.desc);

		boolean taintedReceiver = false;
		if (opcode != Opcodes.INVOKESTATIC) {
			taintedReceiver = isReceiverTainted(frame, args.length + 1);
		}

		if (!taintedReceiver) {
			return;
		}

		InsnList replacement = new InsnList();
		for (int i = args.length - 1; i >= 0; i--) {
			replacement.add(popValue(args[i]));
		}
		if (opcode != Opcodes.INVOKESTATIC) {
			replacement.add(new InsnNode(Opcodes.POP)); // pop receiver
		}
		Type returnType = Type.getReturnType(methodInsn.desc);
		if (returnType != Type.VOID_TYPE) {
			replacement.add(pushDefault(returnType));
		}
		replace(insns, methodInsn, replacement);
	}

	private static boolean isReceiverTainted(Frame<BasicValue> frame, int stackOffset) {
		if (frame == null) {
			return false;
		}
		int index = frame.getStackSize() - stackOffset;
		if (index < 0) {
			return false;
		}
		TaintValue value = (TaintValue)frame.getStack(index);
		return value != null && value.tainted;
	}

	private static boolean isRemovedField(FieldInsnNode fieldInsn, WhitelistConfig config, Set<String> hierarchy) {
		if (!hierarchy.contains(fieldInsn.owner)) {
			return false;
		}
		if (fieldInsn.getOpcode() == Opcodes.GETSTATIC || fieldInsn.getOpcode() == Opcodes.PUTSTATIC) {
			return false;
		}
		for (String hierClass : hierarchy) {
			if (config.isWhitelistedField(hierClass, fieldInsn.name, fieldInsn.desc)) {
				return false;
			}
		}
		return true;
	}

	private static void replace(InsnList insns, AbstractInsnNode target, InsnList replacement) {
		insns.insertBefore(target, replacement);
		insns.remove(target);
	}

	private static InsnNode popValue(Type type) {
		return new InsnNode(type.getSize() == 2 ? Opcodes.POP2 : Opcodes.POP);
	}

	private static final Set<String> EMPTY_LIST_TYPES = Set.of(
		"java/util/List", "java/util/Collection", "java/lang/Iterable"
	);

	private static AbstractInsnNode pushDefault(Type type) {
		return switch (type.getSort()) {
			case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> new InsnNode(Opcodes.ICONST_0);
			case Type.LONG -> new InsnNode(Opcodes.LCONST_0);
			case Type.FLOAT -> new InsnNode(Opcodes.FCONST_0);
			case Type.DOUBLE -> new InsnNode(Opcodes.DCONST_0);
			default -> {
				if (type.getSort() == Type.OBJECT && EMPTY_LIST_TYPES.contains(type.getInternalName())) {
					yield new MethodInsnNode(
						Opcodes.INVOKESTATIC,
						"java/util/Collections",
						"emptyList",
						"()Ljava/util/List;",
						false
					);
				}
				if (type.getSort() == Type.OBJECT && "java/util/Optional".equals(type.getInternalName())) {
					yield new MethodInsnNode(
						Opcodes.INVOKESTATIC,
						"java/util/Optional",
						"empty",
						"()Ljava/util/Optional;",
						false
					);
				}
				yield new InsnNode(Opcodes.ACONST_NULL);
			}
		};
	}

	private static byte[] readClassBytes(String internalName, Path inputJar) throws IOException {
		String entryName = internalName + ".class";
		if (inputJar != null) {
			try (JarFile jar = new JarFile(inputJar.toFile())) {
				JarEntry entry = jar.getJarEntry(entryName);
				if (entry == null) {
					throw new IOException("Class not found in jar: " + entryName);
				}
				try (InputStream input = jar.getInputStream(entry)) {
					return input.readAllBytes();
				}
			}
		}

		InputStream input = SimClassGenerator.class.getClassLoader().getResourceAsStream(entryName);
		if (input == null) {
			throw new IOException("Class resource not found: " + entryName);
		}
		try (InputStream stream = input) {
			return stream.readAllBytes();
		}
	}

	private static String toInternalName(String name) {
		Objects.requireNonNull(name, "name");
		return name.indexOf('/') >= 0 ? name : name.replace('.', '/');
	}

	private static final class Args {
		private final Path whitelistPath;
		private final Path outputDir;
		private final Path metadataPath;
		private final Path inputJar;
		private final String simPrefix;

		private Args(Path whitelistPath, Path outputDir, Path metadataPath, Path inputJar, String simPrefix) {
			this.whitelistPath = whitelistPath;
			this.outputDir = outputDir;
			this.metadataPath = metadataPath;
			this.inputJar = inputJar;
			this.simPrefix = simPrefix;
		}

		private static Args parse(String[] args) {
			Path whitelistPath = null;
			Path outputDir = null;
			Path metadataPath = null;
			Path inputJar = null;
			String simPrefix = "kaptainwutax/tungsten/simulation/simulated";
			for (int i = 0; i < args.length; i++) {
				String arg = args[i];
				switch (arg) {
					case "--whitelist" -> whitelistPath = Paths.get(args[++i]);
					case "--output-dir" -> outputDir = Paths.get(args[++i]);
					case "--metadata" -> metadataPath = Paths.get(args[++i]);
					case "--input-jar" -> inputJar = Paths.get(args[++i]);
					case "--sim-prefix" -> simPrefix = args[++i];
					default -> throw new IllegalArgumentException("Unknown arg: " + arg);
				}
			}
			if (whitelistPath == null || outputDir == null || metadataPath == null) {
				throw new IllegalArgumentException("Missing required args: --whitelist --output-dir --metadata");
			}
			return new Args(whitelistPath, outputDir, metadataPath, inputJar, simPrefix);
		}
	}

	private static final class TaintValue extends BasicValue {
		private final boolean tainted;

		private TaintValue(Type type, boolean tainted) {
			super(type);
			this.tainted = tainted;
		}
	}

	private static final class TaintInterpreter extends BasicInterpreter {
		private final java.util.function.Predicate<FieldInsnNode> removedField;
		private final Set<String> hierarchy;

		private TaintInterpreter(java.util.function.Predicate<FieldInsnNode> removedField, Set<String> hierarchy) {
			super(Opcodes.ASM9);
			this.removedField = removedField;
			this.hierarchy = hierarchy;
		}

		@Override
		public TaintValue newValue(Type type) {
			if (type == null) {
				return new TaintValue(null, false);
			}
			if (type == Type.VOID_TYPE) {
				return null;
			}
			return new TaintValue(type, false);
		}

		@Override
		public TaintValue newOperation(AbstractInsnNode insn) {
			if (insn instanceof FieldInsnNode fieldInsn && insn.getOpcode() == Opcodes.GETSTATIC) {
				return new TaintValue(Type.getType(fieldInsn.desc), removedField.test(fieldInsn));
			}
			try {
				return wrap(super.newOperation(insn), false);
			} catch (AnalyzerException ex) {
				throw new IllegalStateException("Analyzer failure", ex);
			}
		}

		@Override
		public TaintValue copyOperation(AbstractInsnNode insn, BasicValue value) {
			TaintValue taintValue = (TaintValue)value;
			return new TaintValue(taintValue.getType(), taintValue.tainted);
		}

		@Override
		public TaintValue unaryOperation(AbstractInsnNode insn, BasicValue value) {
			TaintValue taintValue = (TaintValue)value;
			if (insn instanceof FieldInsnNode fieldInsn && insn.getOpcode() == Opcodes.GETFIELD) {
				boolean tainted = taintValue.tainted || removedField.test(fieldInsn);
				return new TaintValue(Type.getType(fieldInsn.desc), tainted);
			}
			try {
				return wrap(super.unaryOperation(insn, value), taintValue.tainted);
			} catch (AnalyzerException ex) {
				throw new IllegalStateException("Analyzer failure", ex);
			}
		}

		@Override
		public TaintValue binaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2) {
			boolean tainted = ((TaintValue)value1).tainted || ((TaintValue)value2).tainted;
			try {
				return wrap(super.binaryOperation(insn, value1, value2), tainted);
			} catch (AnalyzerException ex) {
				throw new IllegalStateException("Analyzer failure", ex);
			}
		}

		@Override
		public TaintValue ternaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2, BasicValue value3) {
			boolean tainted = ((TaintValue)value1).tainted || ((TaintValue)value2).tainted || ((TaintValue)value3).tainted;
			try {
				return wrap(super.ternaryOperation(insn, value1, value2, value3), tainted);
			} catch (AnalyzerException ex) {
				throw new IllegalStateException("Analyzer failure", ex);
			}
		}

		@Override
		public TaintValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) {
			boolean tainted = false;
			if (insn instanceof MethodInsnNode methodInsn) {
				if (methodInsn.getOpcode() != Opcodes.INVOKESTATIC) {
					tainted = !values.isEmpty() && ((TaintValue)values.get(0)).tainted;
				}
			}
			try {
				return wrap(super.naryOperation(insn, values), tainted);
			} catch (AnalyzerException ex) {
				throw new IllegalStateException("Analyzer failure", ex);
			}
		}

		@Override
		public TaintValue merge(BasicValue value1, BasicValue value2) {
			TaintValue taintValue1 = (TaintValue)value1;
			TaintValue taintValue2 = (TaintValue)value2;
			BasicValue merged = super.merge(value1, value2);
			if (merged == null) {
				return null;
			}
			return new TaintValue(merged.getType(), taintValue1.tainted || taintValue2.tainted);
		}

		private static TaintValue wrap(BasicValue value, boolean tainted) {
			if (value == null) {
				return null;
			}
			return new TaintValue(value.getType(), tainted);
		}
	}
}
