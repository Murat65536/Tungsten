package kaptainwutax.tungsten.simulation.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class WhitelistConfig {
	static final class FieldSpec {
		private final String name;
		private final String desc;

		FieldSpec(String name, String desc) {
			this.name = name;
			this.desc = desc;
		}

		boolean matches(String fieldName, String fieldDesc) {
			return this.name.equals(fieldName) && (this.desc == null || this.desc.equals(fieldDesc));
		}

		String key() {
			return this.desc == null ? this.name : this.name + ":" + this.desc;
		}
	}

	private final Map<String, List<FieldSpec>> classFields;
	private final Set<String> immutableTypes;
	private final Map<String, Set<String>> stubbedMethods;

	private WhitelistConfig(Map<String, List<FieldSpec>> classFields, Set<String> immutableTypes, Map<String, Set<String>> stubbedMethods) {
		this.classFields = classFields;
		this.immutableTypes = immutableTypes;
		this.stubbedMethods = stubbedMethods;
	}

	static WhitelistConfig load(Path path) throws IOException {
		try (Reader reader = Files.newBufferedReader(path)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			Map<String, List<FieldSpec>> classFields = new LinkedHashMap<>();
			Set<String> immutableTypes = new LinkedHashSet<>();
			if (root.has("immutableTypes")) {
				JsonArray array = root.getAsJsonArray("immutableTypes");
				for (JsonElement element : array) {
					immutableTypes.add(element.getAsString());
				}
			}

			for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
				if ("immutableTypes".equals(entry.getKey())) {
					continue;
				}
				JsonObject classObj = entry.getValue().getAsJsonObject();
				JsonArray fields = classObj.getAsJsonArray("fields");
				List<FieldSpec> specs = new ArrayList<>();
				for (JsonElement element : fields) {
					String raw = element.getAsString();
					String[] parts = raw.split(":", 2);
					String name = parts[0];
					String desc = parts.length > 1 ? parts[1] : null;
					specs.add(new FieldSpec(name, desc));
				}
				classFields.put(toInternalName(entry.getKey()), Collections.unmodifiableList(specs));
			}

			Map<String, Set<String>> stubbedMethods = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
				if ("immutableTypes".equals(entry.getKey())) {
					continue;
				}
				JsonObject classObj = entry.getValue().getAsJsonObject();
				if (classObj.has("stubbedMethods")) {
					JsonArray methods = classObj.getAsJsonArray("stubbedMethods");
					Set<String> methodSet = new LinkedHashSet<>();
					for (JsonElement element : methods) {
						methodSet.add(element.getAsString());
					}
					stubbedMethods.put(toInternalName(entry.getKey()), Collections.unmodifiableSet(methodSet));
				}
			}

			return new WhitelistConfig(classFields, Collections.unmodifiableSet(immutableTypes), stubbedMethods);
		}
	}

	boolean hasClass(String internalName) {
		return classFields.containsKey(internalName);
	}

	List<FieldSpec> getFields(String internalName) {
		return classFields.getOrDefault(internalName, List.of());
	}

	boolean isWhitelistedField(String ownerInternalName, String name, String desc) {
		List<FieldSpec> specs = classFields.get(ownerInternalName);
		if (specs == null) {
			return false;
		}
		for (FieldSpec spec : specs) {
			if (spec.matches(name, desc)) {
				return true;
			}
		}
		return false;
	}

	Set<String> immutableTypes() {
		return immutableTypes;
	}

	Set<String> expectedFieldKeys(String internalName) {
		List<FieldSpec> specs = classFields.getOrDefault(internalName, List.of());
		Set<String> keys = new LinkedHashSet<>();
		for (FieldSpec spec : specs) {
			keys.add(spec.key());
		}
		return keys;
	}

	boolean isStubbedMethod(String ownerInternalName, String methodName) {
		Set<String> methods = stubbedMethods.get(ownerInternalName);
		return methods != null && methods.contains(methodName);
	}

	/**
	 * Checks if a method is stubbed in the given class OR any of its ancestors.
	 * This ensures that if Entity stubs "fall", LivingEntity's override of "fall"
	 * is also stubbed.
	 */
	boolean isStubbedInHierarchy(String methodName, List<String> hierarchy) {
		for (String className : hierarchy) {
			Set<String> methods = stubbedMethods.get(className);
			if (methods != null && methods.contains(methodName)) {
				return true;
			}
		}
		return false;
	}

	private static String toInternalName(String name) {
		Objects.requireNonNull(name, "name");
		return name.indexOf('/') >= 0 ? name : name.replace('.', '/');
	}
}
