package kaptainwutax.tungsten.simulation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SimMetadata {
	public static final class FieldSpec {
		public String name;
		public String desc;

		public FieldSpec() {
		}

		public FieldSpec(String name, String desc) {
			this.name = name;
			this.desc = desc;
		}
	}

	public List<String> hierarchyClasses = new ArrayList<>();
	public Map<String, String> classMap = new LinkedHashMap<>();
	public Map<String, List<FieldSpec>> fieldMap = new LinkedHashMap<>();
	public Set<String> immutableTypes = new LinkedHashSet<>();
	/** Maps hierarchy class dot-name → set of whitelisted field names. */
	public Map<String, Set<String>> whitelistedFields = new LinkedHashMap<>();

	public static SimMetadata loadResource(String resourcePath) throws IOException {
		try (InputStream input = SimMetadata.class.getResourceAsStream(resourcePath)) {
			if (input == null) {
				throw new IOException("Metadata resource not found: " + resourcePath);
			}
			try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
				return gson().fromJson(reader, SimMetadata.class);
			}
		}
	}

	public static SimMetadata load(Path path) throws IOException {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return gson().fromJson(reader, SimMetadata.class);
		}
	}

	public void save(Path path) throws IOException {
		Objects.requireNonNull(path, "path");
		Files.createDirectories(path.getParent());
		String json = gson().toJson(this);
		Files.writeString(path, json, StandardCharsets.UTF_8);
	}

	private static Gson gson() {
		return new GsonBuilder().setPrettyPrinting().create();
	}
}
