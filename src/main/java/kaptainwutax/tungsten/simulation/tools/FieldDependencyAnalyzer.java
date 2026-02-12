package kaptainwutax.tungsten.simulation.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

/**
 * Build-time bytecode analyzer that automatically discovers which fields
 * in the Entity→ClientPlayerEntity hierarchy are relevant to position/movement.
 * <p>
 * Starting from seed fields (pos, velocity), it traces backward through
 * field dependencies to find every field that can transitively affect position.
 * Methods that write no relevant fields are identified as stubbable.
 * <p>
 * Output is a WhitelistConfig-compatible JSON that SimClassGenerator consumes.
 */
public final class FieldDependencyAnalyzer {

	private static final List<String> HIERARCHY_ROOT_TO_LEAF = List.of(
		"net.minecraft.entity.Entity",
		"net.minecraft.entity.LivingEntity",
		"net.minecraft.entity.player.PlayerEntity",
		"net.minecraft.client.network.AbstractClientPlayerEntity",
		"net.minecraft.client.network.ClientPlayerEntity"
	);

	private FieldDependencyAnalyzer() {
	}

	public static void main(String[] args) throws Exception {
		Args parsed = Args.parse(args);
		SeedsConfig seeds = SeedsConfig.load(parsed.seedsPath);

		Set<String> hierarchy = new LinkedHashSet<>();
		for (String className : HIERARCHY_ROOT_TO_LEAF) {
			hierarchy.add(toInternalName(className));
		}

		// Load all hierarchy classes
		Map<String, ClassNode> classNodes = new LinkedHashMap<>();
		for (String className : HIERARCHY_ROOT_TO_LEAF) {
			String internalName = toInternalName(className);
			byte[] classBytes = readClassBytes(internalName, parsed.inputJar);
			ClassNode node = new ClassNode();
			new ClassReader(classBytes).accept(node, 0);
			classNodes.put(internalName, node);
		}

		// Build field declaration map: fieldKey → declaring class
		// fieldKey = "ownerInternal.fieldName"
		Map<String, String> fieldDeclMap = new LinkedHashMap<>();
		Map<String, String> fieldDescMap = new LinkedHashMap<>();
		for (String className : HIERARCHY_ROOT_TO_LEAF) {
			String internalName = toInternalName(className);
			ClassNode node = classNodes.get(internalName);
			for (FieldNode field : node.fields) {
				if ((field.access & Opcodes.ACC_STATIC) != 0) continue;
				String key = internalName + "." + field.name;
				fieldDeclMap.put(key, internalName);
				fieldDescMap.put(key, field.desc);
			}
		}

		// Build method resolution table: for each signature, find the most-derived implementation
		Map<String, String> methodResolution = buildMethodResolution(classNodes);

		// Per-method analysis
		Map<String, MethodAnalysis> methodAnalyses = new LinkedHashMap<>();
		for (String className : HIERARCHY_ROOT_TO_LEAF) {
			String internalName = toInternalName(className);
			ClassNode node = classNodes.get(internalName);
			for (MethodNode method : node.methods) {
				if ("<init>".equals(method.name) || "<clinit>".equals(method.name)) continue;
				if ((method.access & Opcodes.ACC_STATIC) != 0) continue;
				if (method.instructions == null || method.instructions.size() == 0) continue;

				String methodKey = internalName + "." + method.name + method.desc;
				MethodAnalysis analysis = analyzeMethod(internalName, method, hierarchy, fieldDeclMap);
				methodAnalyses.put(methodKey, analysis);
			}
		}

		// Compute effective reads/writes via transitive closure over calls
		Map<String, Set<String>> effectiveReads = new LinkedHashMap<>();
		Map<String, Set<String>> effectiveWrites = new LinkedHashMap<>();
		computeTransitiveClosure(methodAnalyses, methodResolution, effectiveReads, effectiveWrites, hierarchy);

		// Build field dependency graph: edges from read fields to written fields per method
		// Edge A → B means "reading A can cause B to change"
		Map<String, Set<String>> impactedBy = new LinkedHashMap<>(); // B → set of A's that impact B
		for (var entry : methodAnalyses.entrySet()) {
			String methodKey = entry.getKey();
			Set<String> reads = effectiveReads.getOrDefault(methodKey, Set.of());
			Set<String> writes = effectiveWrites.getOrDefault(methodKey, Set.of());
			if (reads.isEmpty() || writes.isEmpty()) continue;
			for (String w : writes) {
				impactedBy.computeIfAbsent(w, k -> new LinkedHashSet<>()).addAll(reads);
			}
		}

		// Seed fields
		Set<String> seedFieldKeys = new LinkedHashSet<>();
		for (var entry : seeds.seedFields.entrySet()) {
			String internalName = toInternalName(entry.getKey());
			for (String fieldName : entry.getValue()) {
				seedFieldKeys.add(internalName + "." + fieldName);
			}
		}

		// Backward reachability from seeds
		Set<String> relevant = new LinkedHashSet<>(seedFieldKeys);
		boolean changed = true;
		while (changed) {
			changed = false;
			for (String field : new ArrayList<>(relevant)) {
				Set<String> sources = impactedBy.get(field);
				if (sources == null) continue;
				for (String source : sources) {
					if (relevant.add(source)) {
						changed = true;
					}
				}
			}
		}

		// Determine stubbable method NAMES.
		// A method name is stubbable if ALL versions across the hierarchy are:
		//   (a) void return type
		//   (b) effectiveWrites ∩ relevant = ∅
		// Since isStubbedInHierarchy matches by name, we must ensure ALL overrides are safe to stub.
		Map<String, List<String>> methodsByName = new LinkedHashMap<>();
		for (String methodKey : methodAnalyses.keySet()) {
			int dotIdx = methodKey.indexOf('.');
			String nameDesc = methodKey.substring(dotIdx + 1);
			String name = nameDesc.substring(0, nameDesc.indexOf('('));
			methodsByName.computeIfAbsent(name, k -> new ArrayList<>()).add(methodKey);
		}

		Set<String> stubbableNames = new LinkedHashSet<>();
		for (var entry : methodsByName.entrySet()) {
			String name = entry.getKey();
			boolean allStubbable = true;
			for (String methodKey : entry.getValue()) {
				String desc = methodKey.substring(methodKey.indexOf('('));
				Type returnType = Type.getReturnType(desc);
				if (returnType != Type.VOID_TYPE) {
					allStubbable = false;
					break;
				}
				Set<String> writes = effectiveWrites.getOrDefault(methodKey, Set.of());
				for (String w : writes) {
					if (relevant.contains(w)) {
						allStubbable = false;
						break;
					}
				}
				if (!allStubbable) break;
			}
			if (allStubbable) {
				stubbableNames.add(name);
			}
		}

		// Generate WhitelistConfig-compatible JSON
		JsonObject output = new JsonObject();

		// immutableTypes
		JsonArray immutableArr = new JsonArray();
		for (String t : seeds.immutableTypes) {
			immutableArr.add(t);
		}
		output.add("immutableTypes", immutableArr);

		// Per-class entries
		for (String className : HIERARCHY_ROOT_TO_LEAF) {
			String internalName = toInternalName(className);
			ClassNode node = classNodes.get(internalName);

			JsonObject classObj = new JsonObject();

			// Relevant fields for this class
			JsonArray fieldsArr = new JsonArray();
			for (FieldNode field : node.fields) {
				if ((field.access & Opcodes.ACC_STATIC) != 0) continue;
				String fieldKey = internalName + "." + field.name;
				if (relevant.contains(fieldKey)) {
					fieldsArr.add(field.name + ":" + field.desc);
				}
			}
			classObj.add("fields", fieldsArr);

			// Stubbable methods for this class
			JsonArray stubbedArr = new JsonArray();
			Set<String> addedStubNames = new LinkedHashSet<>();
			for (MethodNode method : node.methods) {
				if ("<init>".equals(method.name) || "<clinit>".equals(method.name)) continue;
				if ((method.access & Opcodes.ACC_STATIC) != 0) continue;
				if (stubbableNames.contains(method.name) && addedStubNames.add(method.name)) {
					stubbedArr.add(method.name);
				}
			}
			classObj.add("stubbedMethods", stubbedArr);

			output.add(className, classObj);
		}

		// Write output
		Path outputPath = parsed.outputPath;
		Files.createDirectories(outputPath.getParent());
		String json = new GsonBuilder().setPrettyPrinting().create().toJson(output);
		Files.writeString(outputPath, json, StandardCharsets.UTF_8);

		// Print summary
		int totalRelevant = 0;
		int totalStubbed = 0;
		for (String className : HIERARCHY_ROOT_TO_LEAF) {
			String internalName = toInternalName(className);
			ClassNode node = classNodes.get(internalName);
			int classRelevant = 0;
			int classStubbed = 0;
			for (FieldNode field : node.fields) {
				if ((field.access & Opcodes.ACC_STATIC) != 0) continue;
				if (relevant.contains(internalName + "." + field.name)) classRelevant++;
			}
			Set<String> stubNames = new LinkedHashSet<>();
			for (MethodNode method : node.methods) {
				if ("<init>".equals(method.name) || "<clinit>".equals(method.name)) continue;
				if ((method.access & Opcodes.ACC_STATIC) != 0) continue;
				if (stubbableNames.contains(method.name)) stubNames.add(method.name);
			}
			classStubbed = stubNames.size();
			totalRelevant += classRelevant;
			totalStubbed += classStubbed;
			String simpleName = className.substring(className.lastIndexOf('.') + 1);
			System.out.println("  " + simpleName + ": " + classRelevant + " relevant fields, " + classStubbed + " stubbed methods");
		}
		System.out.println("Total: " + totalRelevant + " relevant fields, " + totalStubbed + " stubbed methods");
		System.out.println("Wrote: " + outputPath);
	}

	/**
	 * Analyze a single method to extract direct field reads, writes, and calls on this.
	 */
	private static MethodAnalysis analyzeMethod(
		String ownerInternal, MethodNode method, Set<String> hierarchy,
		Map<String, String> fieldDeclMap
	) {
		Set<String> reads = new LinkedHashSet<>();
		Set<String> writes = new LinkedHashSet<>();
		Set<String> calls = new LinkedHashSet<>();

		Frame<BasicValue>[] frames;
		try {
			Analyzer<BasicValue> analyzer = new Analyzer<>(new ThisTrackingInterpreter());
			frames = analyzer.analyze(ownerInternal, method);
		} catch (AnalyzerException ex) {
			// If analysis fails, fall back to conservative: treat all hierarchy field accesses as this
			return analyzeMethodConservative(ownerInternal, method, hierarchy, fieldDeclMap);
		}

		AbstractInsnNode[] instructions = method.instructions.toArray();
		for (int i = 0; i < instructions.length; i++) {
			AbstractInsnNode insn = instructions[i];
			Frame<BasicValue> frame = frames[i];
			if (frame == null) continue;

			if (insn instanceof FieldInsnNode fieldInsn) {
				if (!hierarchy.contains(fieldInsn.owner)) continue;
				String fieldKey = fieldInsn.owner + "." + fieldInsn.name;

				switch (fieldInsn.getOpcode()) {
					case Opcodes.GETFIELD -> {
						if (isThisOnStack(frame, 1)) {
							reads.add(fieldKey);
						}
					}
					case Opcodes.PUTFIELD -> {
						Type fieldType = Type.getType(fieldInsn.desc);
						if (isThisOnStack(frame, 1 + fieldType.getSize())) {
							writes.add(fieldKey);
						}
					}
				}
			} else if (insn instanceof MethodInsnNode methodInsn) {
				if (methodInsn.getOpcode() == Opcodes.INVOKESTATIC) continue;
				if (!hierarchy.contains(methodInsn.owner)) continue;

				Type[] argTypes = Type.getArgumentTypes(methodInsn.desc);
				int slotsCount = 0;
				for (Type t : argTypes) slotsCount += t.getSize();

				if (isThisOnStack(frame, 1 + slotsCount)) {
					String callKey = methodInsn.owner + "." + methodInsn.name + methodInsn.desc;
					calls.add(callKey);
				}
			}
		}

		return new MethodAnalysis(reads, writes, calls);
	}

	/**
	 * Conservative fallback: treat all hierarchy field accesses as this-accesses.
	 */
	private static MethodAnalysis analyzeMethodConservative(
		String ownerInternal, MethodNode method, Set<String> hierarchy,
		Map<String, String> fieldDeclMap
	) {
		Set<String> reads = new LinkedHashSet<>();
		Set<String> writes = new LinkedHashSet<>();
		Set<String> calls = new LinkedHashSet<>();

		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof FieldInsnNode fieldInsn) {
				if (!hierarchy.contains(fieldInsn.owner)) continue;
				String fieldKey = fieldInsn.owner + "." + fieldInsn.name;
				if (fieldInsn.getOpcode() == Opcodes.GETFIELD) {
					reads.add(fieldKey);
				} else if (fieldInsn.getOpcode() == Opcodes.PUTFIELD) {
					writes.add(fieldKey);
				}
			} else if (insn instanceof MethodInsnNode methodInsn) {
				if (methodInsn.getOpcode() == Opcodes.INVOKESTATIC) continue;
				if (!hierarchy.contains(methodInsn.owner)) continue;
				calls.add(methodInsn.owner + "." + methodInsn.name + methodInsn.desc);
			}
		}

		return new MethodAnalysis(reads, writes, calls);
	}

	/**
	 * Check if the value at a given stack depth from the top is 'this'.
	 */
	private static boolean isThisOnStack(Frame<BasicValue> frame, int depthFromTop) {
		int index = frame.getStackSize() - depthFromTop;
		if (index < 0) return false;
		BasicValue value = frame.getStack(index);
		return value instanceof ThisValue tv && tv.isThis;
	}

	/**
	 * Build method resolution table: for each method signature, which class has the most-derived impl.
	 * Key: methodName + desc, Value: ownerInternal of most-derived implementation.
	 */
	private static Map<String, String> buildMethodResolution(Map<String, ClassNode> classNodes) {
		Map<String, String> resolution = new LinkedHashMap<>();
		// Process root to leaf — leaf overrides root
		for (String className : HIERARCHY_ROOT_TO_LEAF) {
			String internalName = toInternalName(className);
			ClassNode node = classNodes.get(internalName);
			for (MethodNode method : node.methods) {
				if ("<init>".equals(method.name) || "<clinit>".equals(method.name)) continue;
				if ((method.access & Opcodes.ACC_STATIC) != 0) continue;
				if (method.instructions == null || method.instructions.size() == 0) continue;
				resolution.put(method.name + method.desc, internalName);
			}
		}
		return resolution;
	}

	/**
	 * Compute effective reads and writes for each method by transitively including
	 * reads/writes from called methods. Uses fixed-point iteration for cycles.
	 */
	private static void computeTransitiveClosure(
		Map<String, MethodAnalysis> analyses,
		Map<String, String> methodResolution,
		Map<String, Set<String>> effectiveReads,
		Map<String, Set<String>> effectiveWrites,
		Set<String> hierarchy
	) {
		// Initialize with direct reads/writes
		for (var entry : analyses.entrySet()) {
			effectiveReads.put(entry.getKey(), new LinkedHashSet<>(entry.getValue().reads));
			effectiveWrites.put(entry.getKey(), new LinkedHashSet<>(entry.getValue().writes));
		}

		// Fixed-point iteration
		boolean changed = true;
		int iterations = 0;
		while (changed && iterations < 100) {
			changed = false;
			iterations++;
			for (var entry : analyses.entrySet()) {
				String methodKey = entry.getKey();
				MethodAnalysis analysis = entry.getValue();
				Set<String> reads = effectiveReads.get(methodKey);
				Set<String> writes = effectiveWrites.get(methodKey);

				for (String callKey : analysis.calls) {
					// Resolve the call to the actual implementation
					String resolved = resolveCall(callKey, methodResolution, analyses, hierarchy);
					if (resolved == null) continue;

					Set<String> callReads = effectiveReads.get(resolved);
					Set<String> callWrites = effectiveWrites.get(resolved);
					if (callReads != null) {
						for (String r : callReads) {
							if (reads.add(r)) changed = true;
						}
					}
					if (callWrites != null) {
						for (String w : callWrites) {
							if (writes.add(w)) changed = true;
						}
					}
				}
			}
		}
	}

	/**
	 * Resolve a call to its actual method key in the analysis map.
	 * Handles virtual dispatch by finding the most-derived implementation.
	 */
	private static String resolveCall(
		String callKey, Map<String, String> methodResolution,
		Map<String, MethodAnalysis> analyses, Set<String> hierarchy
	) {
		// callKey = "owner.nameDesc"
		// Try the call key directly first
		if (analyses.containsKey(callKey)) {
			return callKey;
		}

		// Extract name+desc from call key
		int dotIdx = callKey.indexOf('.');
		if (dotIdx < 0) return null;
		String nameDesc = callKey.substring(dotIdx + 1);

		// Resolve via method resolution table (most-derived implementation)
		String resolvedOwner = methodResolution.get(nameDesc);
		if (resolvedOwner != null) {
			String resolvedKey = resolvedOwner + "." + nameDesc;
			if (analyses.containsKey(resolvedKey)) {
				return resolvedKey;
			}
		}

		// Try each hierarchy class
		for (String className : HIERARCHY_ROOT_TO_LEAF) {
			String internalName = toInternalName(className);
			String candidateKey = internalName + "." + nameDesc;
			if (analyses.containsKey(candidateKey)) {
				return candidateKey;
			}
		}

		return null;
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

		InputStream input = FieldDependencyAnalyzer.class.getClassLoader().getResourceAsStream(entryName);
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

	// --- Data classes ---

	private record MethodAnalysis(Set<String> reads, Set<String> writes, Set<String> calls) {
	}

	// --- This-tracking interpreter ---

	private static final class ThisValue extends BasicValue {
		final boolean isThis;

		ThisValue(Type type, boolean isThis) {
			super(type);
			this.isThis = isThis;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof ThisValue other)) return false;
			return this.isThis == other.isThis && Objects.equals(getType(), other.getType());
		}

		@Override
		public int hashCode() {
			return Objects.hash(getType(), isThis);
		}
	}

	private static final class ThisTrackingInterpreter extends BasicInterpreter {
		ThisTrackingInterpreter() {
			super(Opcodes.ASM9);
		}

		@Override
		public ThisValue newValue(Type type) {
			if (type == null) return new ThisValue(null, false);
			if (type == Type.VOID_TYPE) return null;
			return new ThisValue(type, false);
		}

		@Override
		public ThisValue newParameterValue(boolean isInstanceMethod, int local, Type type) {
			return new ThisValue(type, isInstanceMethod && local == 0);
		}

		@Override
		public ThisValue newOperation(AbstractInsnNode insn) {
			try {
				BasicValue base = super.newOperation(insn);
				return wrap(base, false);
			} catch (AnalyzerException ex) {
				throw new IllegalStateException("Analyzer failure", ex);
			}
		}

		@Override
		public ThisValue copyOperation(AbstractInsnNode insn, BasicValue value) {
			ThisValue tv = (ThisValue) value;
			return new ThisValue(tv.getType(), tv.isThis);
		}

		@Override
		public ThisValue unaryOperation(AbstractInsnNode insn, BasicValue value) {
			try {
				BasicValue base = super.unaryOperation(insn, value);
				// GETFIELD result is never 'this' — it's a field value
				return wrap(base, false);
			} catch (AnalyzerException ex) {
				throw new IllegalStateException("Analyzer failure", ex);
			}
		}

		@Override
		public ThisValue binaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2) {
			try {
				BasicValue base = super.binaryOperation(insn, value1, value2);
				return wrap(base, false);
			} catch (AnalyzerException ex) {
				throw new IllegalStateException("Analyzer failure", ex);
			}
		}

		@Override
		public ThisValue ternaryOperation(AbstractInsnNode insn, BasicValue v1, BasicValue v2, BasicValue v3) {
			try {
				BasicValue base = super.ternaryOperation(insn, v1, v2, v3);
				return wrap(base, false);
			} catch (AnalyzerException ex) {
				throw new IllegalStateException("Analyzer failure", ex);
			}
		}

		@Override
		public ThisValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) {
			try {
				BasicValue base = super.naryOperation(insn, values);
				return wrap(base, false);
			} catch (AnalyzerException ex) {
				throw new IllegalStateException("Analyzer failure", ex);
			}
		}

		@Override
		public ThisValue merge(BasicValue value1, BasicValue value2) {
			ThisValue tv1 = (ThisValue) value1;
			ThisValue tv2 = (ThisValue) value2;
			BasicValue merged = super.merge(value1, value2);
			if (merged == null) return null;
			// Only 'this' if both paths are 'this'
			return new ThisValue(merged.getType(), tv1.isThis && tv2.isThis);
		}

		private static ThisValue wrap(BasicValue value, boolean isThis) {
			if (value == null) return null;
			return new ThisValue(value.getType(), isThis);
		}
	}

	// --- Seeds config ---

	private static final class SeedsConfig {
		Map<String, List<String>> seedFields;
		List<String> immutableTypes;

		static SeedsConfig load(Path path) throws IOException {
			try (Reader reader = Files.newBufferedReader(path)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				SeedsConfig config = new SeedsConfig();
				config.seedFields = new LinkedHashMap<>();
				config.immutableTypes = new ArrayList<>();

				if (root.has("seedFields")) {
					JsonObject seedObj = root.getAsJsonObject("seedFields");
					for (var entry : seedObj.entrySet()) {
						List<String> fields = new ArrayList<>();
						for (JsonElement el : entry.getValue().getAsJsonArray()) {
							fields.add(el.getAsString());
						}
						config.seedFields.put(entry.getKey(), fields);
					}
				}
				if (root.has("immutableTypes")) {
					for (JsonElement el : root.getAsJsonArray("immutableTypes")) {
						config.immutableTypes.add(el.getAsString());
					}
				}
				return config;
			}
		}
	}

	// --- CLI args ---

	private static final class Args {
		final Path seedsPath;
		final Path outputPath;
		final Path inputJar;

		Args(Path seedsPath, Path outputPath, Path inputJar) {
			this.seedsPath = seedsPath;
			this.outputPath = outputPath;
			this.inputJar = inputJar;
		}

		static Args parse(String[] args) {
			Path seedsPath = null;
			Path outputPath = null;
			Path inputJar = null;
			for (int i = 0; i < args.length; i++) {
				switch (args[i]) {
					case "--seeds" -> seedsPath = Paths.get(args[++i]);
					case "--output" -> outputPath = Paths.get(args[++i]);
					case "--input-jar" -> inputJar = Paths.get(args[++i]);
					default -> throw new IllegalArgumentException("Unknown arg: " + args[i]);
				}
			}
			if (seedsPath == null || outputPath == null) {
				throw new IllegalArgumentException("Missing required args: --seeds --output");
			}
			return new Args(seedsPath, outputPath, inputJar);
		}
	}
}
