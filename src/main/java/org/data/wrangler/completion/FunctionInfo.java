package org.data.wrangler.completion;

import java.util.List;

/** Compact view of a {@code duckdb_functions()} row. */
public record FunctionInfo(String name, String type, List<String> parameters, String returnType, String description) {

 public String signature() {
 return name + "(" + String.join(", ", parameters) + ")";
    }
}
