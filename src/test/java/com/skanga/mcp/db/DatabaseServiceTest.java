package com.skanga.mcp.db;

import com.skanga.mcp.config.ConfigParams;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.*;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseServiceTest {
    @Mock
    ConfigParams config;

    @Mock
    HikariDataSource mockDataSource;

    @Mock
    Connection connection;

    @Mock
    PreparedStatement statement;

    @Mock
    ResultSet resultSet;

    @Mock
    DatabaseMetaData metaData;

    DatabaseService service;

    @BeforeEach
    void setUp() throws Exception {
        // Default config setup
        lenient().when(config.dbDriver()).thenReturn("org.h2.Driver");
        lenient().when(config.dbUrl()).thenReturn("jdbc:h2:mem:testdb");
        lenient().when(config.dbUser()).thenReturn("sa");
        lenient().when(config.dbPass()).thenReturn("");
        lenient().when(config.getDatabaseType()).thenReturn("h2");

        lenient().when(config.maxConnections()).thenReturn(10);
        lenient().when(config.connectionTimeoutMs()).thenReturn(30000);
        lenient().when(config.queryTimeoutSeconds()).thenReturn(30);
        lenient().when(config.selectOnly()).thenReturn(false);

        // Load H2 driver manually to prevent ClassNotFoundException
        Class.forName("org.h2.Driver");

        // Mock datasource to return our mock connection
        lenient().when(mockDataSource.getConnection()).thenReturn(connection);
        lenient().when(mockDataSource.isClosed()).thenReturn(false);

        // Create service that uses the mocked datasource
        service = new DatabaseService(config, mockDataSource);
    }

    // ========================================
    // BASIC FUNCTIONALITY TESTS
    // ========================================

    @Test
    void testExecuteQuery_Select() throws Exception {
        String sql = "SELECT id, name FROM users";

        when(connection.prepareStatement(sql)).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(resultSet);

        ResultSetMetaData rsMeta = mock(ResultSetMetaData.class);
        when(rsMeta.getColumnCount()).thenReturn(2);
        when(rsMeta.getColumnName(1)).thenReturn("id");
        when(rsMeta.getColumnName(2)).thenReturn("name");

        when(resultSet.getMetaData()).thenReturn(rsMeta);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getObject(1)).thenReturn(1);
        when(resultSet.getObject(2)).thenReturn("Alice");

        QueryResult result = service.executeQuery(sql, 10);

        assertEquals(1, result.rowCount());
        assertEquals(Arrays.asList("id", "name"), result.allColumns());
        assertEquals(List.of(List.of(1, "Alice")), result.allRows());

        verify(statement).setMaxRows(10);
        verify(statement).setQueryTimeout(30);
        verify(connection).close();
    }

    @Test
    void testExecuteQuery_Update() throws Exception {
        String sql = "UPDATE users SET name = 'Bob' WHERE id = 1";

        when(connection.prepareStatement(sql)).thenReturn(statement);
        when(statement.execute()).thenReturn(false);
        when(statement.getUpdateCount()).thenReturn(1);

        QueryResult result = service.executeQuery(sql, 10);

        assertEquals(1, result.rowCount());
        assertEquals(List.of("affected_rows"), result.allColumns());
        assertEquals(List.of(List.of(1)), result.allRows());
    }

    @Test
    void testExecuteQuery_WithMaxRowsLimit() throws Exception {
        String sql = "SELECT id FROM users";

        when(connection.prepareStatement(sql)).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(resultSet);

        ResultSetMetaData rsMeta = mock(ResultSetMetaData.class);
        when(rsMeta.getColumnCount()).thenReturn(1);
        when(rsMeta.getColumnName(1)).thenReturn("id");

        when(resultSet.getMetaData()).thenReturn(rsMeta);
        when(resultSet.next()).thenReturn(true, true, true, false);
        when(resultSet.getObject(1)).thenReturn(1, 2, 3);

        QueryResult result = service.executeQuery(sql, 2);

        assertEquals(2, result.rowCount());
        verify(statement).setMaxRows(2);
    }

    @Test
    void testExecuteQuery_WithNullValues() throws Exception {
        String sql = "SELECT id, name FROM users";

        when(connection.prepareStatement(sql)).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(resultSet);

        ResultSetMetaData rsMeta = mock(ResultSetMetaData.class);
        when(rsMeta.getColumnCount()).thenReturn(2);
        when(rsMeta.getColumnName(1)).thenReturn("id");
        when(rsMeta.getColumnName(2)).thenReturn("name");

        when(resultSet.getMetaData()).thenReturn(rsMeta);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getObject(1)).thenReturn(null);
        when(resultSet.getObject(2)).thenReturn(null);

        QueryResult result = service.executeQuery(sql, 10);

        assertEquals(1, result.rowCount());
        assertNotNull(result.allRows().get(0));
        assertNull(result.allRows().get(0).get(0));
        assertNull(result.allRows().get(0).get(1));
    }

    @Test
    void testExecuteQuery_SQLException() throws Exception {
        String sql = "SELECT * FROM nonexistent_table";

        when(connection.prepareStatement(sql)).thenReturn(statement);
        when(statement.execute()).thenThrow(new SQLException("Table not found"));

        SQLException exception = assertThrows(SQLException.class, () -> {
            service.executeQuery(sql, 10);
        });

        assertEquals("Table not found", exception.getMessage());
    }

    // ========================================
    // SELECT ONLY MODE TESTS
    // ========================================

    @Test
    void testExecuteQuery_SelectOnly_BlocksDangerousOperations() throws Exception {
        when(config.selectOnly()).thenReturn(true);

        String[] dangerousQueries = {
                "DROP TABLE users",
                "DELETE FROM users",
                "UPDATE users SET name = 'test'",
                "INSERT INTO users VALUES (1, 'test')",
                "CREATE TABLE test (id INT)",
                "ALTER TABLE users ADD COLUMN test VARCHAR(50)",
                "TRUNCATE TABLE users",
                "GRANT SELECT ON users TO public",
                "REVOKE SELECT ON users FROM public",
                "EXEC sp_test",
                "EXECUTE sp_test",
                "CALL procedure_test()"
        };

        for (String sql : dangerousQueries) {
            SQLException exception = assertThrows(SQLException.class, () -> {
                service.executeQuery(sql, 10);
            });
            assertTrue(exception.getMessage().contains("Operation not allowed") ||
                    exception.getMessage().contains("not allowed"));
        }
    }

    @Test
    void testExecuteQuery_SelectOnly_BlocksMultipleStatements() throws Exception {
        when(config.selectOnly()).thenReturn(true);

        SQLException exception = assertThrows(SQLException.class, () -> {
            service.executeQuery("SELECT * FROM users; DROP TABLE users;", 10);
        });

        assertTrue(exception.getMessage().contains("Multiple statements not allowed"));
    }

    @Test
    void testExecuteQuery_SelectOnly_BlocksComments() throws Exception {
        when(config.selectOnly()).thenReturn(true);

        String[] commentQueries = {
                "SELECT * FROM users -- comment",
                "SELECT * FROM users /* comment */",
                "SELECT * FROM users WHERE id = 1 -- malicious comment"
        };

        for (String sql : commentQueries) {
            SQLException exception = assertThrows(SQLException.class, () -> {
                service.executeQuery(sql, 10);
            });
            assertTrue(exception.getMessage().contains("SQL comments not allowed"));
        }
    }

    @Test
    void testExecuteQuery_SelectOnly_AllowsValidSelects() throws Exception {
        when(config.selectOnly()).thenReturn(true);

        String sql = "SELECT id, name FROM users WHERE active = 1";

        when(connection.prepareStatement(sql)).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(resultSet);

        ResultSetMetaData rsMeta = mock(ResultSetMetaData.class);
        when(rsMeta.getColumnCount()).thenReturn(2);
        when(rsMeta.getColumnName(1)).thenReturn("id");
        when(rsMeta.getColumnName(2)).thenReturn("name");

        when(resultSet.getMetaData()).thenReturn(rsMeta);
        when(resultSet.next()).thenReturn(false);

        QueryResult result = service.executeQuery(sql, 10);

        assertNotNull(result);
        verify(statement).setMaxRows(10);
        verify(statement).setQueryTimeout(30);
    }

    @Test
    void testExecuteQuery_EmptyQuery() throws Exception {
        when(config.selectOnly()).thenReturn(true);

        SQLException exception = assertThrows(SQLException.class, () -> {
            service.executeQuery("", 10);
        });

        assertTrue(exception.getMessage().contains("SQL query cannot be empty"));
    }

    @Test
    void testExecuteQuery_NullQuery() throws Exception {
        when(config.selectOnly()).thenReturn(true);

        SQLException exception = assertThrows(SQLException.class, () -> {
            service.executeQuery(null, 10);
        });

        assertTrue(exception.getMessage().contains("SQL query cannot be empty"));
    }

    // ========================================
    // CONNECTION POOL TESTS
    // ========================================

    @Test
    void testConnectionPoolUsage() throws Exception {
        String sql = "SELECT 1";

        when(connection.prepareStatement(sql)).thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(resultSet);

        ResultSetMetaData rsMeta = mock(ResultSetMetaData.class);
        when(rsMeta.getColumnCount()).thenReturn(1);
        when(rsMeta.getColumnName(1)).thenReturn("1");
        when(resultSet.getMetaData()).thenReturn(rsMeta);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getObject(1)).thenReturn(1);

        // Execute multiple queries
        service.executeQuery(sql, 10);
        service.executeQuery(sql, 10);
        service.executeQuery(sql, 10);

        // Verify pool was used 3 times
        verify(mockDataSource, times(3)).getConnection();
    }

    @Test
    void testConnectionPoolExhaustion() throws Exception {
        when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection pool exhausted"));

        SQLException exception = assertThrows(SQLException.class, () -> {
            service.executeQuery("SELECT 1", 10);
        });

        assertEquals("Connection pool exhausted", exception.getMessage());
        verify(mockDataSource).getConnection();
    }

    @Test
    void testGetConnection() throws Exception {
        Connection conn = service.getConnection();
        assertEquals(connection, conn);
        verify(mockDataSource).getConnection();
    }

    @Test
    void testCreateConnection() throws Exception {
        Connection conn = service.createConnection();
        assertEquals(connection, conn);
        verify(mockDataSource).getConnection();
    }

    // ========================================
    // RESOURCE LISTING TESTS
    // ========================================

    @Test
    void testListResources() throws Exception {
        when(connection.getMetaData()).thenReturn(metaData);
        setupH2MetaDataMocks();

        // Mock tables
        ResultSet tablesForListResources = mock(ResultSet.class);
        ResultSet tablesForDataDictionary = mock(ResultSet.class);

        when(metaData.getTables(null, null, "%", new String[]{"TABLE", "VIEW"}))
                .thenReturn(tablesForListResources)
                .thenReturn(tablesForDataDictionary);

        // First ResultSet for listResources
        lenient().when(tablesForListResources.next()).thenReturn(true, false);
        lenient().when(tablesForListResources.getString("TABLE_NAME")).thenReturn("USERS");
        lenient().when(tablesForListResources.getString("TABLE_TYPE")).thenReturn("TABLE");
        lenient().when(tablesForListResources.getString("REMARKS")).thenReturn("User table");

        // Second ResultSet for generateDataDictionary - need to add TABLE_SCHEM
        lenient().when(tablesForDataDictionary.next()).thenReturn(true, false);
        lenient().when(tablesForDataDictionary.getString("TABLE_NAME")).thenReturn("USERS");
        lenient().when(tablesForDataDictionary.getString("TABLE_SCHEM")).thenReturn("PUBLIC");
        lenient().when(tablesForDataDictionary.getString("TABLE_TYPE")).thenReturn("TABLE");

        // Schemas
        ResultSet schemas = mock(ResultSet.class);
        lenient().when(metaData.getSchemas()).thenReturn(schemas);
        lenient().when(schemas.next()).thenReturn(true, false);
        lenient().when(schemas.getString("TABLE_SCHEM")).thenReturn("PUBLIC");

        List<DatabaseResource> resources = service.listResources();

        assertTrue(resources.stream().anyMatch(r -> r.uri().equals("database://info")));
        assertTrue(resources.stream().anyMatch(r -> r.uri().equals("database://data-dictionary")));
        assertTrue(resources.stream().anyMatch(r -> r.uri().equals("database://table/USERS")));
        assertTrue(resources.stream().anyMatch(r -> r.uri().equals("database://schema/PUBLIC")));
    }

    @Test
    void testListResources_SchemaAccessException() throws Exception {
        when(connection.getMetaData()).thenReturn(metaData);
        setupH2MetaDataMocks();

        // Mock schema access to throw exception
        when(metaData.getSchemas()).thenThrow(new SQLException("Schemas not supported"));

        // Mock tables
        ResultSet tables = mock(ResultSet.class);
        when(metaData.getTables(null, null, "%", new String[]{"TABLE", "VIEW"}))
                .thenReturn(tables);
        when(tables.next()).thenReturn(false);

        List<DatabaseResource> resources = service.listResources();

        // Should still return resources even if schemas fail
        assertNotNull(resources);
        assertTrue(resources.stream().anyMatch(r -> r.uri().equals("database://info")));
    }

    // ========================================
    // RESOURCE READING TESTS
    // ========================================

    @Test
    void testReadResource_Info() throws Exception {
        when(connection.getMetaData()).thenReturn(metaData);
        setupH2MetaDataMocks();

        DatabaseResource info = service.readResource("database://info");

        assertNotNull(info);
        assertEquals("database://info", info.uri());
        assertTrue(info.content().contains("Database Information"));
        assertTrue(info.content().contains("Database Type: H2"));
        assertTrue(info.content().contains("Product: H2"));
    }

    @Test
    void testReadResource_Table() throws Exception {
        when(connection.getMetaData()).thenReturn(metaData);

        // Mock table exists check
        ResultSet tableCheck = mock(ResultSet.class);
        when(metaData.getTables(null, null, "USERS", new String[]{"TABLE", "VIEW"}))
                .thenReturn(tableCheck);
        when(tableCheck.next()).thenReturn(true);

        // Mock columns
        ResultSet columns = mock(ResultSet.class);
        when(metaData.getColumns(null, null, "USERS", null)).thenReturn(columns);
        when(columns.next()).thenReturn(true, false);
        when(columns.getString("COLUMN_NAME")).thenReturn("id");
        when(columns.getString("TYPE_NAME")).thenReturn("INTEGER");
        when(columns.getInt("COLUMN_SIZE")).thenReturn(11);
        when(columns.getString("IS_NULLABLE")).thenReturn("NO");
        when(columns.getString("COLUMN_DEF")).thenReturn(null);
        when(columns.getString("REMARKS")).thenReturn("Primary key");

        // Mock primary keys
        ResultSet primaryKeys = mock(ResultSet.class);
        when(metaData.getPrimaryKeys(null, null, "USERS")).thenReturn(primaryKeys);
        when(primaryKeys.next()).thenReturn(true, false);
        when(primaryKeys.getString("COLUMN_NAME")).thenReturn("id");

        // Mock foreign keys
        ResultSet foreignKeys = mock(ResultSet.class);
        when(metaData.getImportedKeys(null, null, "USERS")).thenReturn(foreignKeys);
        when(foreignKeys.next()).thenReturn(false);

        // Mock indexes
        ResultSet indexes = mock(ResultSet.class);
        when(metaData.getIndexInfo(null, null, "USERS", false, false)).thenReturn(indexes);
        when(indexes.next()).thenReturn(false);

        DatabaseResource resource = service.readResource("database://table/USERS");

        assertNotNull(resource);
        assertEquals("database://table/USERS", resource.uri());
        assertTrue(resource.content().contains("TABLE METADATA"));
        assertTrue(resource.content().contains("id"));
    }

    @Test
    void testReadResource_TableDoesNotExist() throws Exception {
        when(connection.getMetaData()).thenReturn(metaData);
        ResultSet emptyTables = mock(ResultSet.class);
        when(metaData.getTables(null, null, "DOES_NOT_EXIST", new String[]{"TABLE", "VIEW"}))
                .thenReturn(emptyTables);
        when(emptyTables.next()).thenReturn(false);

        DatabaseResource resource = service.readResource("database://table/DOES_NOT_EXIST");

        assertNull(resource);
    }

    @Test
    void testReadResource_Schema() throws Exception {
        when(connection.getMetaData()).thenReturn(metaData);

        // Mock schema exists check
        ResultSet schemaCheck = mock(ResultSet.class);
        when(metaData.getSchemas()).thenReturn(schemaCheck);
        when(schemaCheck.next()).thenReturn(true, false);
        when(schemaCheck.getString("TABLE_SCHEM")).thenReturn("PUBLIC");

        // Mock tables in schema
        ResultSet tables = mock(ResultSet.class);
        when(metaData.getTables(null, "PUBLIC", "%", new String[]{"TABLE", "VIEW"}))
                .thenReturn(tables);
        when(tables.next()).thenReturn(true, false);
        when(tables.getString("TABLE_NAME")).thenReturn("USERS");
        when(tables.getString("TABLE_TYPE")).thenReturn("TABLE");

        DatabaseResource resource = service.readResource("database://schema/PUBLIC");

        assertNotNull(resource);
        assertEquals("database://schema/PUBLIC", resource.uri());
        assertTrue(resource.content().contains("Schema: PUBLIC"));
    }

    @Test
    void testReadResource_DataDictionary() throws Exception {
        when(connection.getMetaData()).thenReturn(metaData);
        setupH2MetaDataMocks();

        // Mock tables for data dictionary
        ResultSet tables = mock(ResultSet.class);
        when(metaData.getTables(null, null, "%", new String[]{"TABLE", "VIEW"}))
                .thenReturn(tables);
        when(tables.next()).thenReturn(true, false);
        when(tables.getString("TABLE_NAME")).thenReturn("USERS");
        when(tables.getString("TABLE_SCHEM")).thenReturn("PUBLIC");
        when(tables.getString("TABLE_TYPE")).thenReturn("TABLE");

        DatabaseResource resource = service.readResource("database://data-dictionary");

        assertNotNull(resource);
        assertEquals("database://data-dictionary", resource.uri());
        assertTrue(resource.content().contains("DATA DICTIONARY"));
        assertTrue(resource.content().contains("H2"));
    }

    @Test
    void testReadResource_Invalid() throws Exception {
        DatabaseResource resource = service.readResource("database://nonexistent");
        assertNull(resource);
    }

    // ========================================
    // DATABASE-SPECIFIC TESTS - MYSQL
    // ========================================

    @Test
    void testMySQL_QueryExamplesAndDataTypes() throws Exception {
        // Override config for MySQL-specific values
        when(config.getDatabaseType()).thenReturn("mysql");
        when(config.dbUrl()).thenReturn("jdbc:mysql://localhost:3306/testdb");
        when(config.dbUser()).thenReturn("mysql_user");

        when(connection.getMetaData()).thenReturn(metaData);
        setupMySQLMetaDataMocks();
        mockMySQLSpecificQueries();

        DatabaseResource info = service.readResource("database://info");

        assertNotNull(info);
        String content = info.content();

        // Verify database identity
        assertTrue(content.contains("Database Type: MYSQL"));
        assertTrue(content.contains("Product: MySQL"));
        assertTrue(content.contains("URL: jdbc:mysql://localhost:3306/testdb"));
        assertTrue(content.contains("Username: mysql_user"));

        // Verify MySQL-specific content that would actually be in the output
        assertTrue(content.contains("Default Character Set: utf8mb4"));
        assertTrue(content.contains("Default Collation: utf8mb4_unicode_ci"));
        assertTrue(content.contains("Server Character Set: utf8mb4"));
        assertTrue(content.contains("MySQL/MariaDB supports per-column character sets"));
        assertTrue(content.contains("Server Timezone: Global=+00:00, Session=SYSTEM"));
        assertTrue(content.contains("Date Format: YYYY-MM-DD, DateTime Format: YYYY-MM-DD HH:MM:SS"));
        assertTrue(content.contains("TIMESTAMP columns are affected by timezone settings"));
        assertTrue(content.contains("Use backticks (`) for identifiers with spaces or keywords"));
        assertTrue(content.contains("DATE functions: NOW(), CURDATE(), DATE_ADD()"));
        assertTrue(content.contains("String functions: CONCAT(), SUBSTRING(), LENGTH()"));
        assertTrue(content.contains("Limit syntax: LIMIT offset, count"));
        assertTrue(content.contains("Auto-increment: AUTO_INCREMENT"));
    }

    @Test
    void testMySQL_DataDictionary() throws Exception {
        when(config.getDatabaseType()).thenReturn("mysql");
        when(connection.getMetaData()).thenReturn(metaData);
        setupMySQLMetaDataMocks();
        mockMySQLSpecificQueries();

        // Mock tables for data dictionary
        ResultSet tables = mock(ResultSet.class);
        when(metaData.getTables(null, null, "%", new String[]{"TABLE", "VIEW"}))
                .thenReturn(tables);
        when(tables.next()).thenReturn(true, false);
        when(tables.getString("TABLE_NAME")).thenReturn("users");
        when(tables.getString("TABLE_SCHEM")).thenReturn("testdb");
        when(tables.getString("TABLE_TYPE")).thenReturn("TABLE");

        DatabaseResource resource = service.readResource("database://data-dictionary");

        assertNotNull(resource);
        String content = resource.content();

        assertTrue(content.contains("DATA DICTIONARY & QUERY GUIDE"));
        assertTrue(content.contains("Database Type: MYSQL"));
        assertTrue(content.contains("COMMON QUERY PATTERNS FOR MYSQL"));
        assertTrue(content.contains("users (TABLE)"));
    }

    // ========================================
    // DATABASE-SPECIFIC TESTS - POSTGRESQL
    // ========================================
    @Test
    void testPostgreSQL_QueryExamplesAndDataTypes() throws Exception {
        // Override config for PostgreSQL-specific values
        when(config.getDatabaseType()).thenReturn("postgresql");
        when(config.dbUrl()).thenReturn("jdbc:postgresql://localhost:5432/testdb");
        when(config.dbUser()).thenReturn("postgres");

        when(connection.getMetaData()).thenReturn(metaData);
        setupPostgreSQLMetaDataMocks();
        mockPostgreSQLSpecificQueries();

        DatabaseResource info = service.readResource("database://info");

        assertNotNull(info);
        String content = info.content();

        // Verify database identity
        assertTrue(content.contains("Database Type: POSTGRESQL"));
        assertTrue(content.contains("Product: PostgreSQL"));
        assertTrue(content.contains("URL: jdbc:postgresql://localhost:5432/testdb"));
        assertTrue(content.contains("Username: postgres"));

        // Verify PostgreSQL-specific content that's actually in the output
        assertTrue(content.contains("Server Encoding: UTF8"));
        assertTrue(content.contains("Client Encoding: UTF8"));
        assertTrue(content.contains("PostgreSQL uses Unicode (UTF-8) by default"));
        assertTrue(content.contains("Server Timezone: UTC"));
        assertTrue(content.contains("Date Style: ISO, MDY"));
        assertTrue(content.contains("Use TIMESTAMPTZ for timezone-aware timestamps"));
        assertTrue(content.contains("Unquoted identifiers are folded to lowercase"));
        assertTrue(content.contains("Use double quotes (\") to preserve case in identifiers"));
        assertTrue(content.contains("DATE functions: NOW(), CURRENT_DATE, date + INTERVAL '1 day'"));
        assertTrue(content.contains("String functions: CONCAT(), SUBSTRING(), LENGTH()"));
        assertTrue(content.contains("Limit syntax: LIMIT count OFFSET offset"));
        assertTrue(content.contains("Sequences: SERIAL, BIGSERIAL"));
        assertTrue(content.contains("Arrays:"));
        assertTrue(content.contains("SELECT * FROM table WHERE column_name = ANY(ARRAY['val1', 'val2']);"));
    }

    // ========================================
    // DATABASE-SPECIFIC TESTS - SQL SERVER
    // ========================================
    @Test
    void testSQLServer_QueryExamplesAndDataTypes() throws Exception {
        // Override config for SQL Server-specific values
        when(config.getDatabaseType()).thenReturn("sqlserver");
        when(config.dbUrl()).thenReturn("jdbc:sqlserver://localhost:1433;databaseName=testdb");
        when(config.dbUser()).thenReturn("sa");

        when(connection.getMetaData()).thenReturn(metaData);
        setupSQLServerMetaDataMocks();
        mockSQLServerSpecificQueries();

        DatabaseResource info = service.readResource("database://info");

        assertNotNull(info);
        String content = info.content();

        // Verify database identity
        assertTrue(content.contains("Database Type: SQLSERVER"));
        assertTrue(content.contains("Product: Microsoft SQL Server"));
        assertTrue(content.contains("URL: jdbc:sqlserver://localhost:1433;databaseName=testdb"));
        assertTrue(content.contains("Username: sa"));

        // Verify SQL Server-specific content that would actually be in the output
        assertTrue(content.contains("Default Collation: SQL_Latin1_General_CP1_CI_AS"));
        assertTrue(content.contains("SQL Server uses UTF-16 internally for Unicode data"));
        assertTrue(content.contains("Server Timezone: Not explicitly stored (uses OS timezone)"));
        assertTrue(content.contains("Default Date Format: YYYY-MM-DD (ISO format)"));
        assertTrue(content.contains("DateTime Range: 1753-01-01 to 9999-12-31"));
        assertTrue(content.contains("Use DATETIMEOFFSET for timezone-aware timestamps"));
        assertTrue(content.contains("Use square brackets [] or double quotes \"\" for identifiers"));
        assertTrue(content.contains("DATE functions: GETDATE(), DATEADD(), DATEDIFF()"));
        assertTrue(content.contains("String functions: CONCAT(), SUBSTRING(), LEN()"));
        assertTrue(content.contains("Identity: IDENTITY(1,1)"));
        assertTrue(content.contains("ORDER BY is REQUIRED with OFFSET"));
        assertTrue(content.contains("SELECT * FROM table ORDER BY id OFFSET 0 ROWS FETCH NEXT 20 ROWS ONLY"));
    }

    @Test
    void testOracle_QueryExamplesAndDataTypes() throws Exception {
        // Override config for Oracle-specific values
        when(config.getDatabaseType()).thenReturn("oracle");
        when(config.dbUrl()).thenReturn("jdbc:oracle:thin:@localhost:1521:testdb");
        when(config.dbUser()).thenReturn("oracle_user");

        when(connection.getMetaData()).thenReturn(metaData);
        setupOracleMetaDataMocks();
        mockOracleSpecificQueries();

        DatabaseResource info = service.readResource("database://info");

        assertNotNull(info);
        String content = info.content();

        // Verify database identity
        assertTrue(content.contains("Database Type: ORACLE"));
        assertTrue(content.contains("Product: Oracle Database"));
        assertTrue(content.contains("URL: jdbc:oracle:thin:@localhost:1521:testdb"));
        assertTrue(content.contains("Username: oracle_user"));

        // Verify Oracle-specific charset info
        assertTrue(content.contains("Database Character Set: AL32UTF8"));
        assertTrue(content.contains("National Character Set: AL16UTF16"));

        // Verify Oracle-specific date/time info
        assertTrue(content.contains("Database Timezone: +00:00"));
        assertTrue(content.contains("Date Format: DD-MON-YY"));
        assertTrue(content.contains("Session Timezone: +00:00"));
        assertTrue(content.contains("Default Date Format: DD-MON-YY (can be changed with NLS_DATE_FORMAT)"));

        // Verify Oracle-specific SQL dialect guidance
        assertTrue(content.contains("Use double quotes (\") for case-sensitive identifiers"));
        assertTrue(content.contains("DATE functions: SYSDATE, TO_DATE(), ADD_MONTHS()"));
        assertTrue(content.contains("String functions: CONCAT(), SUBSTR(), LENGTH()"));
        assertTrue(content.contains("Sequences"));
        assertTrue(content.contains("SELECT sequence_name.NEXTVAL FROM DUAL"));
        assertTrue(content.contains("SELECT sequence_name.CURRVAL FROM DUAL"));
        assertTrue(content.contains("Pagination (Oracle 12c+)"));
        assertTrue(content.contains("SELECT * FROM table ORDER BY id OFFSET 0 ROWS FETCH NEXT 20 ROWS ONLY"));
        assertTrue(content.contains("Pagination (Oracle 11g and earlier)"));
        assertTrue(content.contains("SELECT * FROM (SELECT ROWNUM rn, t.* FROM table t WHERE ROWNUM <= 20) WHERE rn > 0"));
        assertTrue(content.contains("Pagination Using ROW_NUMBER()"));
    }

    // ========================================
    // ERROR HANDLING TESTS
    // ========================================
    @Test
    void testMySQL_CharsetQueryException() throws Exception {
        when(config.getDatabaseType()).thenReturn("mysql");
        when(connection.getMetaData()).thenReturn(metaData);
        setupMySQLMetaDataMocks();

        // Mock charset query to throw exception
        when(connection.prepareStatement("SELECT @@character_set_database, @@collation_database, @@character_set_server"))
                .thenThrow(new SQLException("Access denied"));

        // Still need to mock the timezone queries that will also be called
        PreparedStatement timezoneStmt = mock(PreparedStatement.class);
        ResultSet timezoneRs = mock(ResultSet.class);
        lenient().when(connection.prepareStatement("SELECT @@global.time_zone, @@session.time_zone"))
                .thenReturn(timezoneStmt);
        lenient().when(timezoneStmt.executeQuery()).thenReturn(timezoneRs);
        lenient().when(timezoneRs.next()).thenReturn(true);
        lenient().when(timezoneRs.getString(1)).thenReturn("+00:00");
        lenient().when(timezoneRs.getString(2)).thenReturn("SYSTEM");

        DatabaseResource info = service.readResource("database://info");

        assertNotNull(info);
        String content = info.content();

        // Should handle exception gracefully
        assertTrue(content.contains("Unable to retrieve (Access denied)"));
        assertTrue(content.contains("MySQL/MariaDB supports per-column character sets"));
    }

    @Test
    void testPostgreSQL_TimezoneQueryException() throws Exception {
        when(config.getDatabaseType()).thenReturn("postgresql");
        when(connection.getMetaData()).thenReturn(metaData);
        setupPostgreSQLMetaDataMocks();

        // Mock timezone query to throw exception
        when(connection.prepareStatement("SHOW timezone"))
                .thenThrow(new SQLException("Function not supported"));

        // Still need to mock the other PostgreSQL queries that will also be called
        PreparedStatement datestyleStmt = mock(PreparedStatement.class);
        ResultSet datestyleRs = mock(ResultSet.class);
        lenient().when(connection.prepareStatement("SHOW datestyle"))
                .thenReturn(datestyleStmt);
        lenient().when(datestyleStmt.executeQuery()).thenReturn(datestyleRs);
        lenient().when(datestyleRs.next()).thenReturn(true);
        lenient().when(datestyleRs.getString(1)).thenReturn("ISO, MDY");

        // Mock PostgreSQL encoding queries
        PreparedStatement serverEncodingStmt = mock(PreparedStatement.class);
        ResultSet serverEncodingRs = mock(ResultSet.class);
        lenient().when(connection.prepareStatement("SHOW server_encoding"))
                .thenReturn(serverEncodingStmt);
        lenient().when(serverEncodingStmt.executeQuery()).thenReturn(serverEncodingRs);
        lenient().when(serverEncodingRs.next()).thenReturn(true);
        lenient().when(serverEncodingRs.getString(1)).thenReturn("UTF8");

        PreparedStatement clientEncodingStmt = mock(PreparedStatement.class);
        ResultSet clientEncodingRs = mock(ResultSet.class);
        lenient().when(connection.prepareStatement("SHOW client_encoding"))
                .thenReturn(clientEncodingStmt);
        lenient().when(clientEncodingStmt.executeQuery()).thenReturn(clientEncodingRs);
        lenient().when(clientEncodingRs.next()).thenReturn(true);
        lenient().when(clientEncodingRs.getString(1)).thenReturn("UTF8");

        DatabaseResource info = service.readResource("database://info");

        assertNotNull(info);
        String content = info.content();

        // Should handle exception gracefully
        assertTrue(content.contains("Unable to retrieve"));
        assertTrue(content.contains("Use TIMESTAMPTZ for timezone-aware timestamps"));
    }

    // ========================================
    // CLEANUP TESTS
    // ========================================

    @Test
    void testClose() throws Exception {
        service.close();
        verify(mockDataSource).close();
    }

    @Test
    void testClose_AlreadyClosed() throws Exception {
        when(mockDataSource.isClosed()).thenReturn(true);

        service.close();

        verify(mockDataSource, never()).close();
    }

    @Test
    void testClose_ThrowsException() throws Exception {
        doThrow(new RuntimeException("Close failed")).when(mockDataSource).close();

        // Should not throw exception
        assertDoesNotThrow(() -> service.close());

        verify(mockDataSource).close();
    }

    @Test
    void testGetDatabaseConfig() {
        ConfigParams result = service.getDatabaseConfig();
        assertEquals(config, result);
    }

    @Test
    void testConstructor_ThrowsIfDriverNotFound() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            ConfigParams badConfig = mock(ConfigParams.class);
            when(badConfig.dbDriver()).thenReturn("non.existent.Driver");

            new DatabaseService(badConfig);
        });

        assertTrue(exception.getMessage().contains("Database driver not found"));
    }

    // ========================================
    // HELPER METHODS
    // ========================================
    private void setupH2MetaDataMocks() throws SQLException {
        lenient().when(metaData.getDatabaseProductName()).thenReturn("H2");
        lenient().when(metaData.getDatabaseProductVersion()).thenReturn("1.4");
        lenient().when(metaData.getDriverName()).thenReturn("H2 Driver");
        lenient().when(metaData.getDriverVersion()).thenReturn("1.4");
        lenient().when(metaData.isReadOnly()).thenReturn(false);
        lenient().when(metaData.supportsTransactions()).thenReturn(true);
        lenient().when(metaData.supportsStoredProcedures()).thenReturn(false);
        lenient().when(metaData.supportsMultipleResultSets()).thenReturn(true);
        lenient().when(metaData.supportsBatchUpdates()).thenReturn(true);
        lenient().when(metaData.getConnection()).thenReturn(connection);

        // Mock H2 compatibility mode query
        PreparedStatement h2ModeStmt = mock(PreparedStatement.class);
        ResultSet h2ModeRs = mock(ResultSet.class);
        lenient().when(connection.prepareStatement("SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME = 'MODE'"))
                .thenReturn(h2ModeStmt);
        lenient().when(h2ModeStmt.executeQuery()).thenReturn(h2ModeRs);
        lenient().when(h2ModeRs.next()).thenReturn(true);
        lenient().when(h2ModeRs.getString(1)).thenReturn("REGULAR");
    }

    private void setupMySQLMetaDataMocks() throws SQLException {
        lenient().when(metaData.getDatabaseProductName()).thenReturn("MySQL");
        lenient().when(metaData.getDatabaseProductVersion()).thenReturn("8.0.28");
        lenient().when(metaData.getDriverName()).thenReturn("MySQL Connector/J");
        lenient().when(metaData.getDriverVersion()).thenReturn("8.0.28");
        lenient().when(metaData.isReadOnly()).thenReturn(false);
        lenient().when(metaData.supportsTransactions()).thenReturn(true);
        lenient().when(metaData.supportsStoredProcedures()).thenReturn(true);
        lenient().when(metaData.supportsMultipleResultSets()).thenReturn(true);
        lenient().when(metaData.supportsBatchUpdates()).thenReturn(true);
        lenient().when(metaData.getConnection()).thenReturn(connection);
    }

    private void setupPostgreSQLMetaDataMocks() throws SQLException {
        lenient().when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        lenient().when(metaData.getDatabaseProductVersion()).thenReturn("14.5");
        lenient().when(metaData.getDriverName()).thenReturn("PostgreSQL JDBC Driver");
        lenient().when(metaData.getDriverVersion()).thenReturn("42.5.0");
        lenient().when(metaData.isReadOnly()).thenReturn(false);
        lenient().when(metaData.supportsTransactions()).thenReturn(true);
        lenient().when(metaData.supportsStoredProcedures()).thenReturn(true);
        lenient().when(metaData.supportsMultipleResultSets()).thenReturn(true);
        lenient().when(metaData.supportsBatchUpdates()).thenReturn(true);
        lenient().when(metaData.getConnection()).thenReturn(connection);
    }

    private void setupSQLServerMetaDataMocks() throws SQLException {
        lenient().when(metaData.getDatabaseProductName()).thenReturn("Microsoft SQL Server");
        lenient().when(metaData.getDatabaseProductVersion()).thenReturn("15.00.2000");
        lenient().when(metaData.getDriverName()).thenReturn("Microsoft JDBC Driver for SQL Server");
        lenient().when(metaData.getDriverVersion()).thenReturn("9.4.1");
        lenient().when(metaData.isReadOnly()).thenReturn(false);
        lenient().when(metaData.supportsTransactions()).thenReturn(true);
        lenient().when(metaData.supportsStoredProcedures()).thenReturn(true);
        lenient().when(metaData.supportsMultipleResultSets()).thenReturn(true);
        lenient().when(metaData.supportsBatchUpdates()).thenReturn(true);
        lenient().when(metaData.getConnection()).thenReturn(connection);
    }

    private void setupOracleMetaDataMocks() throws SQLException {
        lenient().when(metaData.getDatabaseProductName()).thenReturn("Oracle Database");
        lenient().when(metaData.getDatabaseProductVersion()).thenReturn("19.0.0.0.0");
        lenient().when(metaData.getDriverName()).thenReturn("Oracle JDBC Driver");
        lenient().when(metaData.getDriverVersion()).thenReturn("19.0.0.0.0");
        lenient().when(metaData.isReadOnly()).thenReturn(false);
        lenient().when(metaData.supportsTransactions()).thenReturn(true);
        lenient().when(metaData.supportsStoredProcedures()).thenReturn(true);
        lenient().when(metaData.supportsMultipleResultSets()).thenReturn(true);
        lenient().when(metaData.supportsBatchUpdates()).thenReturn(true);
        lenient().when(metaData.getConnection()).thenReturn(connection);
    }

    private void mockMySQLSpecificQueries() throws SQLException {
        // Mock MySQL charset queries
        PreparedStatement charsetStmt = mock(PreparedStatement.class);
        ResultSet charsetRs = mock(ResultSet.class);
        lenient().when(connection.prepareStatement("SELECT @@character_set_database, @@collation_database, @@character_set_server"))
                .thenReturn(charsetStmt);
        lenient().when(charsetStmt.executeQuery()).thenReturn(charsetRs);
        lenient().when(charsetRs.next()).thenReturn(true);
        lenient().when(charsetRs.getString(1)).thenReturn("utf8mb4");
        lenient().when(charsetRs.getString(2)).thenReturn("utf8mb4_unicode_ci");
        lenient().when(charsetRs.getString(3)).thenReturn("utf8mb4");

        // Mock MySQL timezone queries
        PreparedStatement timezoneStmt = mock(PreparedStatement.class);
        ResultSet timezoneRs = mock(ResultSet.class);
        lenient().when(connection.prepareStatement("SELECT @@global.time_zone, @@session.time_zone"))
                .thenReturn(timezoneStmt);
        lenient().when(timezoneStmt.executeQuery()).thenReturn(timezoneRs);
        lenient().when(timezoneRs.next()).thenReturn(true);
        lenient().when(timezoneRs.getString(1)).thenReturn("+00:00");
        lenient().when(timezoneRs.getString(2)).thenReturn("SYSTEM");
    }

    private void mockPostgreSQLSpecificQueries() throws SQLException {
        // Mock PostgreSQL timezone queries
        PreparedStatement timezoneStmt = mock(PreparedStatement.class);
        ResultSet timezoneRs = mock(ResultSet.class);
        lenient().when(connection.prepareStatement("SHOW timezone"))
                .thenReturn(timezoneStmt);
        lenient().when(timezoneStmt.executeQuery()).thenReturn(timezoneRs);
        lenient().when(timezoneRs.next()).thenReturn(true);
        lenient().when(timezoneRs.getString(1)).thenReturn("UTC");

        // Mock PostgreSQL datestyle queries
        PreparedStatement datestyleStmt = mock(PreparedStatement.class);
        ResultSet datestyleRs = mock(ResultSet.class);
        lenient().when(connection.prepareStatement("SHOW datestyle"))
                .thenReturn(datestyleStmt);
        lenient().when(datestyleStmt.executeQuery()).thenReturn(datestyleRs);
        lenient().when(datestyleRs.next()).thenReturn(true);
        lenient().when(datestyleRs.getString(1)).thenReturn("ISO, MDY");

        // Mock PostgreSQL encoding queries
        PreparedStatement serverEncodingStmt = mock(PreparedStatement.class);
        ResultSet serverEncodingRs = mock(ResultSet.class);
        lenient().when(connection.prepareStatement("SHOW server_encoding"))
                .thenReturn(serverEncodingStmt);
        lenient().when(serverEncodingStmt.executeQuery()).thenReturn(serverEncodingRs);
        lenient().when(serverEncodingRs.next()).thenReturn(true);
        lenient().when(serverEncodingRs.getString(1)).thenReturn("UTF8");

        PreparedStatement clientEncodingStmt = mock(PreparedStatement.class);
        ResultSet clientEncodingRs = mock(ResultSet.class);
        lenient().when(connection.prepareStatement("SHOW client_encoding"))
                .thenReturn(clientEncodingStmt);
        lenient().when(clientEncodingStmt.executeQuery()).thenReturn(clientEncodingRs);
        lenient().when(clientEncodingRs.next()).thenReturn(true);
        lenient().when(clientEncodingRs.getString(1)).thenReturn("UTF8");
    }

    private void mockSQLServerSpecificQueries() throws SQLException {
        // Mock SQL Server collation query
        PreparedStatement collationStmt = mock(PreparedStatement.class);
        ResultSet collationRs = mock(ResultSet.class);
        lenient().when(connection.prepareStatement("SELECT SERVERPROPERTY('Collation')"))
                .thenReturn(collationStmt);
        lenient().when(collationStmt.executeQuery()).thenReturn(collationRs);
        lenient().when(collationRs.next()).thenReturn(true);
        lenient().when(collationRs.getString(1)).thenReturn("SQL_Latin1_General_CP1_CI_AS");
    }

    private void mockOracleSpecificQueries() throws SQLException {
        // Mock Oracle charset queries
        PreparedStatement charsetStmt = mock(PreparedStatement.class);
        ResultSet charsetRs = mock(ResultSet.class);
        lenient().when(connection.prepareStatement("SELECT value FROM nls_database_parameters WHERE parameter = 'NLS_CHARACTERSET'"))
                .thenReturn(charsetStmt);
        lenient().when(charsetStmt.executeQuery()).thenReturn(charsetRs);
        lenient().when(charsetRs.next()).thenReturn(true);
        lenient().when(charsetRs.getString(1)).thenReturn("AL32UTF8");

        PreparedStatement ncharsetStmt = mock(PreparedStatement.class);
        ResultSet ncharsetRs = mock(ResultSet.class);
        lenient().when(connection.prepareStatement("SELECT value FROM nls_database_parameters WHERE parameter = 'NLS_NCHAR_CHARACTERSET'"))
                .thenReturn(ncharsetStmt);
        lenient().when(ncharsetStmt.executeQuery()).thenReturn(ncharsetRs);
        lenient().when(ncharsetRs.next()).thenReturn(true);
        lenient().when(ncharsetRs.getString(1)).thenReturn("AL16UTF16");

        // Mock Oracle timezone queries
        PreparedStatement dbTimezoneStmt = mock(PreparedStatement.class);
        ResultSet dbTimezoneRs = mock(ResultSet.class);
        lenient().when(connection.prepareStatement("SELECT DBTIMEZONE FROM DUAL"))
                .thenReturn(dbTimezoneStmt);
        lenient().when(dbTimezoneStmt.executeQuery()).thenReturn(dbTimezoneRs);
        lenient().when(dbTimezoneRs.next()).thenReturn(true);
        lenient().when(dbTimezoneRs.getString(1)).thenReturn("+00:00");

        PreparedStatement dateFormatStmt = mock(PreparedStatement.class);
        ResultSet dateFormatRs = mock(ResultSet.class);
        lenient().when(connection.prepareStatement("SELECT value FROM nls_session_parameters WHERE parameter = 'NLS_DATE_FORMAT'"))
                .thenReturn(dateFormatStmt);
        lenient().when(dateFormatStmt.executeQuery()).thenReturn(dateFormatRs);
        lenient().when(dateFormatRs.next()).thenReturn(true);
        lenient().when(dateFormatRs.getString(1)).thenReturn("DD-MON-YY");

        PreparedStatement sessionTimezoneStmt = mock(PreparedStatement.class);
        ResultSet sessionTimezoneRs = mock(ResultSet.class);
        lenient().when(connection.prepareStatement("SELECT SESSIONTIMEZONE FROM DUAL"))
                .thenReturn(sessionTimezoneStmt);
        lenient().when(sessionTimezoneStmt.executeQuery()).thenReturn(sessionTimezoneRs);
        lenient().when(sessionTimezoneRs.next()).thenReturn(true);
        lenient().when(sessionTimezoneRs.getString(1)).thenReturn("+00:00");
    }
}