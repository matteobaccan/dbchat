package com.skanga.init;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.mockito.Mockito;

public class ClientConfigInteractiveTest {
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUpStreams() {
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    public void restoreStreams() {
        System.setOut(originalOut);
    }

    private void provideInput(String data) {
        ByteArrayInputStream testIn = new ByteArrayInputStream(data.getBytes());
        System.setIn(testIn);
    }

    @Test
    void testMainWorkflow_SuccessfulRun() throws Exception {
        // Simulate user input for the entire process
        String input = "jdbc:h2:mem:test\n" + // DB URL
                "org.h2.Driver\n" +           // DB Driver
                "sa\n" +                      // DB User
                "\n" +                        // DB Password (empty)
                "my-test-server\n" +          // Server Name
                "1\n" +                       // Client Choice (Cursor)
                "n\n";                        // No to direct installation

        provideInput(input);

        String output = tapSystemOut(() -> {
            assertDoesNotThrow(() -> ClientConfig.main(new String[]{}));
        });

        // Verify the output contains expected prompts and messages
        assertTrue(output.contains("=== DBChat MCP Configuration Generator ==="));
        assertTrue(output.contains("Please provide your database connection details"));
        assertTrue(output.contains("Database JDBC URL"));
        assertTrue(output.contains("Successfully connected to database."));
        assertTrue(output.contains("Supported MCP Clients:"));
        assertTrue(output.contains("Cursor configuration generated!"));
        assertTrue(output.contains("Manual installation: Copy the generated file"));
    }

    @Test
    void testMainWorkflow_DatabaseConnectionFailure_Retry() throws Exception {
        String input = "invalid-url\n" +             // Invalid DB URL
                "org.h2.Driver\n" +           // DB Driver
                "sa\n" +                      // DB User
                "\n" +                        // DB Password
                "y\n" +                       // Retry connection
                "jdbc:h2:mem:test\n" +        // Correct DB URL
                "org.h2.Driver\n" +
                "sa\n" +
                "\n" +
                "my-test-server\n" +
                "1\n" +
                "n\n";

        provideInput(input);

        String output = tapSystemOut(() -> {
            assertDoesNotThrow(() -> ClientConfig.main(new String[]{}));
        });

        assertTrue(output.contains("Failed to connect to the database"));
        assertTrue(output.contains("Would you like to retry with different credentials?"));
        assertTrue(output.contains("Successfully connected to database."));
        assertTrue(output.contains("Cursor configuration generated!"));
    }

    @Test
    void testMainWorkflow_InvalidClientChoice() throws Exception {
        String input = "jdbc:h2:mem:test\n" +
                "org.h2.Driver\n" +
                "sa\n" +
                "\n" +
                "my-test-server\n" +
                "99\n" +                       // Invalid choice
                "1\n" +                        // Valid choice
                "n\n";

        provideInput(input);

        String output = tapSystemOut(() -> {
            assertDoesNotThrow(() -> ClientConfig.main(new String[]{}));
        });

        assertTrue(output.contains("Please enter a number between 1 and 9."));
        assertTrue(output.contains("Cursor configuration generated!"));
    }

    @Test
    void testGenerateAllConfigurations() throws Exception {
        String input = "jdbc:h2:mem:test\n" +
                "org.h2.Driver\n" +
                "sa\n" +
                "\n" +
                "my-test-server\n" +
                "9\n" + 
                "n\n" + "n\n" + "n\n" + "n\n" + "n\n" + "n\n"; 

        provideInput(input);

        String output = tapSystemOut(() -> {
            assertDoesNotThrow(() -> ClientConfig.main(new String[]{}));
        });

        assertTrue(output.contains("All configurations generated successfully!"));
        assertTrue(output.contains("Cursor configuration generated!"));
        assertTrue(output.contains("Windsurf configuration generated!"));
        assertTrue(output.contains("Zed configuration generated!"));
        assertTrue(!output.contains("Claude Code command generated!"));
    }

    @Test
    void testDirectInstallation() throws Exception {
        // Create a dummy config file to test backup and merge
        Path configDir = tempDir.resolve(".config").resolve("cursor");
        Files.createDirectories(configDir);
        Path configFile = configDir.resolve("mcp.json");
        Files.writeString(configFile, "{\"mcpServers\": {}}");

        // Mock static methods to provide a controlled test environment
        try (var mockedStatic = org.mockito.Mockito.mockStatic(ClientConfig.class)) {
            // Mock environment-dependent methods
            mockedStatic.when(ClientConfig::getJar).thenReturn("dummy.jar");
            mockedStatic.when(ClientConfig::getJava).thenReturn("dummy/java");
            mockedStatic.when(() -> ClientConfig.getConfigPath("cursor")).thenReturn(configFile.toString());

            // Explicitly call real methods for the main workflow
            mockedStatic.when(() -> ClientConfig.main(new String[]{})).thenCallRealMethod();
            mockedStatic.when(() -> ClientConfig.collectDatabaseInfo(Mockito.any())).thenCallRealMethod();
            mockedStatic.when(() -> ClientConfig.displayClientOptions()).thenCallRealMethod();
            mockedStatic.when(() -> ClientConfig.getClientChoice(Mockito.any())).thenCallRealMethod();
            mockedStatic.when(() -> ClientConfig.generateConfiguration(Mockito.any(), Mockito.anyInt(), Mockito.any())).thenCallRealMethod();
            mockedStatic.when(() -> ClientConfig.generateClientConfig(Mockito.any(), Mockito.any(), Mockito.any())).thenCallRealMethod();
            mockedStatic.when(() -> ClientConfig.offerDirectInstallation(Mockito.any(), Mockito.any(), Mockito.any())).thenCallRealMethod();
            mockedStatic.when(() -> ClientConfig.saveConfigurationDirect(Mockito.any(), Mockito.any())).thenCallRealMethod();
            mockedStatic.when(() -> ClientConfig.createBackup(Mockito.anyString())).thenCallRealMethod();
            mockedStatic.when(() -> ClientConfig.readExistingConfig(Mockito.anyString())).thenCallRealMethod();
            mockedStatic.when(() -> ClientConfig.mergeConfiguration(Mockito.anyString(), Mockito.any(), Mockito.any())).thenCallRealMethod();
            mockedStatic.when(() -> ClientConfig.generateMcpServersJson(Mockito.any())).thenCallRealMethod();
            mockedStatic.when(() -> ClientConfig.formatClientName(Mockito.any())).thenCallRealMethod();
            mockedStatic.when(() -> ClientConfig.createServerConfig(Mockito.any())).thenCallRealMethod();
            mockedStatic.when(() -> ClientConfig.mergeStandardMcpConfig(Mockito.any(), Mockito.any())).thenCallRealMethod();
            mockedStatic.when(() -> ClientConfig.saveConfiguration(Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any())).thenCallRealMethod();
            mockedStatic.when(() -> ClientConfig.provideInstallationInstructions(Mockito.any())).thenCallRealMethod();


            String input = "jdbc:h2:mem:test\n" +
                    "org.h2.Driver\n" +
                    "sa\n" +
                    "\n" +
                    "my-test-server\n" +
                    "1\n" + // Cursor
                    "y\n"; // Direct install

            provideInput(input);

            String output = tapSystemOut(() -> {
                assertDoesNotThrow(() -> ClientConfig.main(new String[]{}));
            });

            assertTrue(output.contains("Configuration installed to:"), "Output must show confirmation message. Current output: " + output);
            assertTrue(output.contains("Restart " + ClientConfig.formatClientName(ClientConfig.ClientType.CURSOR) + " to use the new configuration"));

            // Verify that the file was written and contains the new server
            String content = Files.readString(configFile);
            assertTrue(content.contains("my-test-server"));

            // Verify that a backup was created
            try (var files = Files.list(configDir)) {
                assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("mcp_backup_")));
            }
        }
    }
}
