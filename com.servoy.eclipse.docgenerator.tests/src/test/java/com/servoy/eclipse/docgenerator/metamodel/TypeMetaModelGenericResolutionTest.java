package com.servoy.eclipse.docgenerator.metamodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("TypeMetaModel - Generic Type Resolution")
class TypeMetaModelGenericResolutionTest
{
	private MetaModelHolder holder;

	@TempDir
	Path tempDir;

	private static final String BASE_BUILDER_SOURCE = """
		package com.example;
		public abstract class BaseChatBuilder<T extends BaseChatBuilder<T>> {
		    public T useBuiltInTools(boolean b) { return (T)this; }
		    public T maxMemoryTokens(Integer tokens) { return (T)this; }
		    public abstract ChatClient build();
		}
		""";

	private static final String GEMINI_BUILDER_SOURCE = """
		package com.example;
		public class GeminiChatBuilder extends BaseChatBuilder<GeminiChatBuilder> {
		}
		""";

	private static final String OPENAI_BUILDER_SOURCE = """
		package com.example;
		public class OpenAiChatBuilder extends BaseChatBuilder<OpenAiChatBuilder> {
		}
		""";

	private static final String CHAT_CLIENT_SOURCE = """
		package com.example;
		public class ChatClient {
		    public void close() {}
		}
		""";

	private static final String MCP_CLIENT_BUILDER_SOURCE = """
		package com.example;
		public class MCPClientBuilder<T extends BaseChatBuilder<T>> {
		    public T build() { return null; }
		}
		""";

	private static final String TOOL_BUILDER_SOURCE = """
		package com.example;
		public class ToolBuilder<T extends BaseChatBuilder<T>> {
		    public T build() { return null; }
		}
		""";

	private static final String NON_GENERIC_BASE_SOURCE = """
		package com.example;
		public class NonGenericBase {
		    public String getValue() { return null; }
		}
		""";

	private static final String NON_GENERIC_CHILD_SOURCE = """
		package com.example;
		public class NonGenericChild extends NonGenericBase {
		}
		""";

	private static final String GENERIC_INTERFACE_SOURCE = """
		package com.example;
		public interface Builder<T> {
		    T self();
		}
		""";

	private static final String INTERFACE_IMPL_SOURCE = """
		package com.example;
		public class ConcreteBuilder implements Builder<ConcreteBuilder> {
		}
		""";

	@BeforeEach
	void setUp()
	{
		holder = new MetaModelHolder();
	}

	private void parseAndBuildMetaModel(Map<String, String> sourceFiles) throws IOException
	{
		Path pkgDir = tempDir.resolve("com").resolve("example");
		Files.createDirectories(pkgDir);

		List<String> filePaths = new ArrayList<>();
		List<String> encodings = new ArrayList<>();

		for (Map.Entry<String, String> entry : sourceFiles.entrySet())
		{
			Path file = pkgDir.resolve(entry.getKey());
			Files.writeString(file, entry.getValue());
			filePaths.add(file.toAbsolutePath().toString());
			encodings.add("UTF-8");
		}

		ASTParser parser = ASTParser.newParser(AST.JLS21);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(true);
		parser.setEnvironment(new String[0], new String[]{ tempDir.toAbsolutePath().toString() }, new String[]{ "UTF-8" }, true);

		Map<String, String> options = new HashMap<>();
		options.put("org.eclipse.jdt.core.compiler.source", "21");
		options.put("org.eclipse.jdt.core.compiler.compliance", "21");
		options.put("org.eclipse.jdt.core.compiler.codegen.targetPlatform", "21");
		parser.setCompilerOptions(options);

		parser.createASTs(
			filePaths.toArray(new String[0]),
			encodings.toArray(new String[0]),
			new String[0],
			new FileASTRequestor()
			{
				@Override
				public void acceptAST(String sourceFilePath, CompilationUnit cu)
				{
					buildMetaModel(cu);
				}
			},
			null);
	}

	private void buildMetaModel(CompilationUnit cu)
	{
		cu.accept(new org.eclipse.jdt.core.dom.ASTVisitor()
		{
			@Override
			public boolean visit(TypeDeclaration node)
			{
				String pkg = "";
				if (cu.getPackage() != null)
				{
					pkg = cu.getPackage().getName().getFullyQualifiedName();
				}
				boolean isInterface = node.isInterface();
				TypeMetaModel tmm = new TypeMetaModel(pkg, Collections.emptyList(), node, isInterface);
				tmm.setAnnotations(new AnnotationsList());

				for (MethodDeclaration method : node.getMethods())
				{
					MethodMetaModel mmm = new MethodMetaModel(tmm.getName().getQualifiedName(), method);
					mmm.setAnnotations(new AnnotationsList());
					tmm.addMember(mmm.getIndexSignature(), mmm);
				}

				holder.addType(tmm.getName().getBaseBinaryName(), tmm);
				return false;
			}
		});
	}

	private IMemberMetaModel findMember(Collection<IMemberMetaModel> members, String methodName)
	{
		return members.stream()
			.filter(m -> m.getName().equals(methodName))
			.findFirst()
			.orElse(null);
	}

	@Nested
	@DisplayName("Supertype type argument resolution")
	class SupertypeResolution
	{
		@Test
		@DisplayName("AC1: inherited methods resolve generic return type T to concrete subclass GeminiChatBuilder")
		void testInheritedMethodsResolveGenericReturnType() throws IOException
		{
			parseAndBuildMetaModel(Map.of(
				"ChatClient.java", CHAT_CLIENT_SOURCE,
				"BaseChatBuilder.java", BASE_BUILDER_SOURCE,
				"GeminiChatBuilder.java", GEMINI_BUILDER_SOURCE));

			TypeMetaModel geminiBuilder = holder.getType("com.example.GeminiChatBuilder");
			assertNotNull(geminiBuilder, "GeminiChatBuilder should be in the holder");

			Collection<IMemberMetaModel> allMembers = geminiBuilder.getMembers(holder);

			IMemberMetaModel useBuiltInTools = findMember(allMembers, "useBuiltInTools");
			assertNotNull(useBuiltInTools, "useBuiltInTools should be inherited");
			assertEquals("com.example.GeminiChatBuilder", useBuiltInTools.getType().getQualifiedName(),
				"Return type of useBuiltInTools should be resolved to GeminiChatBuilder");

			IMemberMetaModel maxMemoryTokens = findMember(allMembers, "maxMemoryTokens");
			assertNotNull(maxMemoryTokens, "maxMemoryTokens should be inherited");
			assertEquals("com.example.GeminiChatBuilder", maxMemoryTokens.getType().getQualifiedName(),
				"Return type of maxMemoryTokens should be resolved to GeminiChatBuilder");
		}

		@Test
		@DisplayName("AC2: inherited methods resolve to OpenAiChatBuilder for that subclass")
		void testOpenAiBuilderResolvesCorrectly() throws IOException
		{
			parseAndBuildMetaModel(Map.of(
				"ChatClient.java", CHAT_CLIENT_SOURCE,
				"BaseChatBuilder.java", BASE_BUILDER_SOURCE,
				"OpenAiChatBuilder.java", OPENAI_BUILDER_SOURCE));

			TypeMetaModel openAiBuilder = holder.getType("com.example.OpenAiChatBuilder");
			assertNotNull(openAiBuilder, "OpenAiChatBuilder should be in the holder");

			Collection<IMemberMetaModel> allMembers = openAiBuilder.getMembers(holder);

			IMemberMetaModel useBuiltInTools = findMember(allMembers, "useBuiltInTools");
			assertNotNull(useBuiltInTools, "useBuiltInTools should be inherited");
			assertEquals("com.example.OpenAiChatBuilder", useBuiltInTools.getType().getQualifiedName(),
				"Return type of useBuiltInTools should be resolved to OpenAiChatBuilder");

			IMemberMetaModel maxMemoryTokens = findMember(allMembers, "maxMemoryTokens");
			assertNotNull(maxMemoryTokens, "maxMemoryTokens should be inherited");
			assertEquals("com.example.OpenAiChatBuilder", maxMemoryTokens.getType().getQualifiedName(),
				"Return type of maxMemoryTokens should be resolved to OpenAiChatBuilder");
		}
	}

	@Nested
	@DisplayName("Non-generic supertype")
	class NonGenericSupertype
	{
		@Test
		@DisplayName("non-generic supertype passes null for type arguments (no regression)")
		void testNonGenericSupertypePassesNull() throws IOException
		{
			parseAndBuildMetaModel(Map.of(
				"NonGenericBase.java", NON_GENERIC_BASE_SOURCE,
				"NonGenericChild.java", NON_GENERIC_CHILD_SOURCE));

			TypeMetaModel child = holder.getType("com.example.NonGenericChild");
			assertNotNull(child, "NonGenericChild should be in the holder");

			Collection<IMemberMetaModel> allMembers = child.getMembers(holder);

			IMemberMetaModel getValue = findMember(allMembers, "getValue");
			assertNotNull(getValue, "getValue should be inherited from NonGenericBase");
			assertEquals("java.lang.String", getValue.getType().getQualifiedName(),
				"Return type of getValue should remain String (no generic substitution)");
		}
	}

	@Nested
	@DisplayName("Own-class type parameters")
	class OwnClassTypeParameters
	{
		@Test
		@DisplayName("AC3: MCPClientBuilder resolves own type parameter T to its bound BaseChatBuilder")
		void testMCPClientBuilderResolvesToBound() throws IOException
		{
			parseAndBuildMetaModel(Map.of(
				"ChatClient.java", CHAT_CLIENT_SOURCE,
				"BaseChatBuilder.java", BASE_BUILDER_SOURCE,
				"MCPClientBuilder.java", MCP_CLIENT_BUILDER_SOURCE));

			TypeMetaModel mcpBuilder = holder.getType("com.example.MCPClientBuilder");
			assertNotNull(mcpBuilder, "MCPClientBuilder should be in the holder");

			Collection<IMemberMetaModel> allMembers = mcpBuilder.getMembers(holder);

			IMemberMetaModel build = findMember(allMembers, "build");
			assertNotNull(build, "build method should exist");
			assertEquals("com.example.BaseChatBuilder", build.getType().getQualifiedName(),
				"Return type of build() should resolve to the type bound BaseChatBuilder");
		}

		@Test
		@DisplayName("AC4: ToolBuilder resolves own type parameter T to its bound BaseChatBuilder")
		void testToolBuilderResolvesToBound() throws IOException
		{
			parseAndBuildMetaModel(Map.of(
				"ChatClient.java", CHAT_CLIENT_SOURCE,
				"BaseChatBuilder.java", BASE_BUILDER_SOURCE,
				"ToolBuilder.java", TOOL_BUILDER_SOURCE));

			TypeMetaModel toolBuilder = holder.getType("com.example.ToolBuilder");
			assertNotNull(toolBuilder, "ToolBuilder should be in the holder");

			Collection<IMemberMetaModel> allMembers = toolBuilder.getMembers(holder);

			IMemberMetaModel build = findMember(allMembers, "build");
			assertNotNull(build, "build method should exist");
			assertEquals("com.example.BaseChatBuilder", build.getType().getQualifiedName(),
				"Return type of build() should resolve to the type bound BaseChatBuilder");
		}
	}

	@Nested
	@DisplayName("Interface type argument resolution")
	class InterfaceResolution
	{
		@Test
		@DisplayName("AC5: methods inherited from generic interface resolve type parameter")
		void testInterfaceTypeArgumentResolution() throws IOException
		{
			parseAndBuildMetaModel(Map.of(
				"Builder.java", GENERIC_INTERFACE_SOURCE,
				"ConcreteBuilder.java", INTERFACE_IMPL_SOURCE));

			TypeMetaModel concreteBuilder = holder.getType("com.example.ConcreteBuilder");
			assertNotNull(concreteBuilder, "ConcreteBuilder should be in the holder");

			Collection<IMemberMetaModel> allMembers = concreteBuilder.getMembers(holder);

			IMemberMetaModel self = findMember(allMembers, "self");
			assertNotNull(self, "self should be inherited from Builder interface");
			assertEquals("com.example.ConcreteBuilder", self.getType().getQualifiedName(),
				"Return type of self() should be resolved to ConcreteBuilder");
		}
	}

	@Nested
	@DisplayName("Null safety")
	class NullSafety
	{
		@Test
		@DisplayName("getMembers with holder does not NPE when supertype is null")
		void testNullSupertypeNoNPE() throws IOException
		{
			parseAndBuildMetaModel(Map.of(
				"ChatClient.java", CHAT_CLIENT_SOURCE));

			TypeMetaModel chatClient = holder.getType("com.example.ChatClient");
			assertNotNull(chatClient, "ChatClient should be in the holder");

			Collection<IMemberMetaModel> allMembers = chatClient.getMembers(holder);
			assertNotNull(allMembers, "getMembers should not return null");

			IMemberMetaModel close = findMember(allMembers, "close");
			assertNotNull(close, "close method should exist");
		}

		@Test
		@DisplayName("getMembers with null holder returns own members only")
		void testNullHolderReturnsOwnMembers() throws IOException
		{
			parseAndBuildMetaModel(Map.of(
				"ChatClient.java", CHAT_CLIENT_SOURCE));

			TypeMetaModel chatClient = holder.getType("com.example.ChatClient");
			assertNotNull(chatClient, "ChatClient should be in the holder");

			Collection<IMemberMetaModel> ownMembers = chatClient.getMembers(null);
			assertNotNull(ownMembers, "getMembers(null) should not return null");
			assertTrue(ownMembers.size() > 0, "Should have at least the close method");
		}
	}

	@Nested
	@DisplayName("Non-regression")
	class NonRegression
	{
		@Test
		@DisplayName("AC7: methods that do not return a type parameter are unchanged")
		void testNonGenericReturnTypeUnchanged() throws IOException
		{
			parseAndBuildMetaModel(Map.of(
				"ChatClient.java", CHAT_CLIENT_SOURCE,
				"BaseChatBuilder.java", BASE_BUILDER_SOURCE,
				"GeminiChatBuilder.java", GEMINI_BUILDER_SOURCE));

			TypeMetaModel geminiBuilder = holder.getType("com.example.GeminiChatBuilder");
			assertNotNull(geminiBuilder, "GeminiChatBuilder should be in the holder");

			Collection<IMemberMetaModel> allMembers = geminiBuilder.getMembers(holder);

			IMemberMetaModel build = findMember(allMembers, "build");
			assertNotNull(build, "build should be inherited from BaseChatBuilder");
			assertEquals("com.example.ChatClient", build.getType().getQualifiedName(),
				"Return type of build() should remain ChatClient (not affected by generic resolution)");
		}

		@Test
		@DisplayName("AC7: multiple concrete subclasses resolve independently")
		void testMultipleSubclassesResolveIndependently() throws IOException
		{
			parseAndBuildMetaModel(Map.of(
				"ChatClient.java", CHAT_CLIENT_SOURCE,
				"BaseChatBuilder.java", BASE_BUILDER_SOURCE,
				"GeminiChatBuilder.java", GEMINI_BUILDER_SOURCE,
				"OpenAiChatBuilder.java", OPENAI_BUILDER_SOURCE));

			TypeMetaModel gemini = holder.getType("com.example.GeminiChatBuilder");
			TypeMetaModel openai = holder.getType("com.example.OpenAiChatBuilder");

			IMemberMetaModel geminiUseBuiltInTools = findMember(gemini.getMembers(holder), "useBuiltInTools");
			IMemberMetaModel openaiUseBuiltInTools = findMember(openai.getMembers(holder), "useBuiltInTools");

			assertEquals("com.example.GeminiChatBuilder", geminiUseBuiltInTools.getType().getQualifiedName());
			assertEquals("com.example.OpenAiChatBuilder", openaiUseBuiltInTools.getType().getQualifiedName());
		}
	}
}
