# JSON Doclet Development Plan

## Phase 0 – Project Setup
- Initialize build system (Gradle with Java toolchain targeting latest supported Doclet API).
- Configure project structure: `src/main/java`, `src/test/java`, resources, and sample source fixtures.
- Add dependencies (JSON library, testing framework) and set up Spotless/checkstyle if desired.

## Phase 1 – Doclet Skeleton
- Implement `JsonDoclet` class implementing `Doclet` interface; wire `getName`, `run`, `init`.
- Register doclet via `META-INF/services/javax.tools.DocumentationTool$DocumentationTask$Doclet`.
- Parse command-line options (output directory, visibility filters, pretty-print flag).
- Hook into `DocletEnvironment` and `DocTrees` for element traversal.

## Phase 2 – Model Extraction
- Define internal model classes for packages, types, members, annotations, tags, and comments.
- Implement utilities to map `DocCommentTree` and `DocTree` nodes to structured representations.
- Traverse `PackageElement` and `TypeElement` hierarchies, capturing signatures, modifiers, type params, super types, and member details.
- Normalize block/inline tags (`@param`, `@return`, `@throws`, `@deprecated`, `@see`, etc.).

## Phase 3 – JSON Serialization
- Choose JSON library (e.g., Jackson) and create serializers for the internal models.
- Emit one JSON file per class/interface/enum/record, mirroring package directories.
- Emit `package-info.json` files for package-level documentation.
- Ensure deterministic ordering of members and fields for diff-friendly output.

## Phase 4 – Index Generation
- Build per-directory `index.json` summarizing contained package infos and type files (name, kind, relative path, basic metadata).
- Maintain index state while emitting files or perform post-pass aggregation over output tree.
- Validate that directories without documentation are skipped or stubbed per requirements.

## Phase 5 – Configuration & Extensibility
- Support options to include/exclude private members, filter packages, and toggle pretty-printing.
- Provide extension hooks for custom tag handlers or output transformations if needed.
- Integrate logging/reporting via `Reporter` to surface warnings.

## Phase 6 – Testing & Validation
- Add sample input sources under `src/test/resources/samples` covering packages, nested types, generics, annotations, and tags.
- Write integration tests invoking `com.sun.tools.javadoc.Main` (or `jdk.javadoc` API) against the doclet, asserting JSON output.
- Add unit tests for tag parsing and serialization utilities.
- Include golden-file assertions for index generation and formatting.

## Phase 7 – Tooling & Documentation
- Document usage in `README.md` (build, invocation examples, options).
- Provide Gradle task or script to run doclet against example sources.
- Optionally add CI workflow (GitHub Actions) running tests on push/PR.

## Deliverables & Next Steps
- Working doclet jar with documented options.
- Comprehensive test suite and sample outputs.
- Clear instructions for integrating doclet into existing Javadoc builds.
