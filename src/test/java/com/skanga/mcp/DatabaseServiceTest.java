package com.skanga.mcp;

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
        lenient().when(config.dbDriver()).thenReturn("org.h2.Driver");
        lenient().when(config.dbUrl()).thenReturn("jdbc:h2:mem:testdb");
        lenient().when(config.dbUser()).thenReturn("sa");
        lenient().when(config.dbPass()).thenReturn("");

        lenient().when(config.maxConnections()).thenReturn(10);
        lenient().when(config.connectionTimeoutMs()).thenReturn(30000);
        lenient().when(config.queryTimeoutSeconds()).thenReturn(30);

        // Load H2 driver manually to prevent ClassNotFoundException
        Class.forName("org.h2.Driver");

        // Mock the datasource to return our mock connection
        lenient().when(mockDataSource.getConnection()).thenReturn(connection);
        lenient().when(mockDataSource.isClosed()).thenReturn(false);

        // Create service that uses the mocked datasource
        service = new DatabaseService(config, mockDataSource);
    }

    @Test
    void testExecuteQuery_UsesConnectionPool() throws Exception {
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

        // Verify connection was obtained from pool
        verify(mockDataSource).getConnection();
        verify(connection).close(); // Connection should be returned to pool
    }

    @Test
    void testConnectionPoolExhaustion() throws Exception {
        // Simulate pool exhaustion
        when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection pool exhausted"));

        SQLException exception = assertThrows(SQLException.class, () -> {
            service.executeQuery("SELECT 1", 10);
        });

        assertEquals("Connection pool exhausted", exception.getMessage());
        verify(mockDataSource).getConnection();
    }

    @Test
    void testClose_ClosesDataSource() throws Exception {
        service.close();

        verify(mockDataSource).close();
    }

    @Test
    void testMultipleConcurrentQueries() throws Exception {
        // Test that multiple queries can get connections from pool
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
    void testListResources() throws Exception {
        when(connection.getMetaData()).thenReturn(metaData);

        when(metaData.getDatabaseProductName()).thenReturn("H2");
        when(metaData.getDatabaseProductVersion()).thenReturn("1.4");
        when(metaData.getDriverName()).thenReturn("H2 Driver");
        when(metaData.getDriverVersion()).thenReturn("1.4");
        when(metaData.isReadOnly()).thenReturn(false);
        when(metaData.getDefaultTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        when(metaData.supportsTransactions()).thenReturn(true);
        when(metaData.supportsStoredProcedures()).thenReturn(false);
        when(metaData.supportsMultipleResultSets()).thenReturn(true);
        when(metaData.supportsBatchUpdates()).thenReturn(true);

        // Fix: mock getConnection() inside metaData
        when(metaData.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);

        // Tables
        ResultSet tables = mock(ResultSet.class);
        when(metaData.getTables(null, null, "%", new String[]{"TABLE", "VIEW"})).thenReturn(tables);
        when(tables.next()).thenReturn(true, false);
        when(tables.getString("TABLE_NAME")).thenReturn("USERS");
        when(tables.getString("TABLE_TYPE")).thenReturn("TABLE");
        when(tables.getString("REMARKS")).thenReturn("User table");

        // Schemas
        ResultSet schemas = mock(ResultSet.class);
        when(metaData.getSchemas()).thenReturn(schemas);
        when(schemas.next()).thenReturn(true, false);
        when(schemas.getString("TABLE_SCHEM")).thenReturn("PUBLIC");

        List<DatabaseResource> resources = service.listResources();

        assertTrue(resources.stream().anyMatch(r -> r.uri().equals("database://info")));
        assertTrue(resources.stream().anyMatch(r -> r.uri().equals("database://table/USERS")));
        assertTrue(resources.stream().anyMatch(r -> r.uri().equals("database://schema/PUBLIC")));
    }

    @Test
    void testReadResource_Info() throws Exception {
        when(connection.getMetaData()).thenReturn(metaData);

        when(metaData.getDatabaseProductName()).thenReturn("H2");
        when(metaData.getDatabaseProductVersion()).thenReturn("1.4");
        when(metaData.getDriverName()).thenReturn("H2 Driver");
        when(metaData.getDriverVersion()).thenReturn("1.4");
        when(metaData.isReadOnly()).thenReturn(false);
        when(metaData.getDefaultTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        when(metaData.supportsTransactions()).thenReturn(true);
        when(metaData.supportsStoredProcedures()).thenReturn(false);
        when(metaData.supportsMultipleResultSets()).thenReturn(true);
        when(metaData.supportsBatchUpdates()).thenReturn(true);

        // Fix for NPE
        when(metaData.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);

        DatabaseResource info = service.readResource("database://info");

        assertNotNull(info);
        assertEquals("database://info", info.uri());
        assertTrue(info.content().contains("Database Information"));
    }

    @Test
    void testReadResource_Invalid() throws Exception {
        DatabaseResource resource = service.readResource("database://nonexistent");
        assertNull(resource);
    }

    @Test
    void testGetTableResource_TableDoesNotExist() throws Exception {
        when(connection.getMetaData()).thenReturn(metaData);
        ResultSet emptyTables = mock(ResultSet.class);
        when(metaData.getTables(null, null, "DOES_NOT_EXIST", new String[]{"TABLE", "VIEW"})).thenReturn(emptyTables);
        when(emptyTables.next()).thenReturn(false);

        DatabaseResource resource = service.readResource("database://table/DOES_NOT_EXIST");

        assertNull(resource);
    }

    @Test
    void testClose() throws Exception {
        // Verify that close() properly closes the HikariDataSource
        service.close();

        // Verify that the datasource close method was called
        verify(mockDataSource).close();
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
}
