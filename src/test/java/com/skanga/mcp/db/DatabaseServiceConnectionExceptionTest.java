package com.skanga.mcp.db;

import com.skanga.mcp.config.ConfigParams;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests focused on connection pool and database connection exception handling
 */
@ExtendWith(MockitoExtension.class)
class DatabaseServiceConnectionExceptionTest {

    @Mock
    private HikariDataSource mockDataSource;
    
    @Mock
    private Connection mockConnection;
    
    @Mock
    private PreparedStatement mockStatement;
    
    private ConfigParams testConfig;
    private DatabaseService databaseService;

    @BeforeEach
    void setUp() {
        testConfig = new ConfigParams(
            "jdbc:h2:mem:testdb", "sa", "", "org.h2.Driver",
            10, 30000, 30, false, 10000, 10000, 600000, 1800000, 60000
        );
    }

    @Test
    void testGetConnection_PoolConnectionFailure() throws SQLException {
        databaseService = new DatabaseService(testConfig, mockDataSource);
        when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection pool exhausted"));

        SQLException exception = assertThrows(SQLException.class, () -> {
            databaseService.getConnection();
        });
        
        assertEquals("Connection pool exhausted", exception.getMessage());
    }

    @Test
    void testCreateConnection_PoolConnectionFailure() throws SQLException {
        databaseService = new DatabaseService(testConfig, mockDataSource);
        when(mockDataSource.getConnection()).thenThrow(new SQLException("Unable to create connection"));

        SQLException exception = assertThrows(SQLException.class, () -> {
            databaseService.createConnection();
        });
        
        assertEquals("Unable to create connection", exception.getMessage());
    }

    @Test
    void testExecuteQuery_ConnectionTimeoutException() throws SQLException {
        databaseService = new DatabaseService(testConfig, mockDataSource);
        when(mockDataSource.getConnection()).thenThrow(new SQLTimeoutException("Connection timeout"));

        SQLTimeoutException exception = assertThrows(SQLTimeoutException.class, () -> {
            databaseService.executeSql("SELECT * FROM test", 100);
        });
        
        assertEquals("Connection timeout", exception.getMessage());
    }

    @Test
    void testExecuteQuery_ConnectionClosedException() throws SQLException {
        databaseService = new DatabaseService(testConfig, mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.execute()).thenThrow(new SQLException("Connection is closed"));

        SQLException exception = assertThrows(SQLException.class, () -> {
            databaseService.executeSql("SELECT * FROM test", 100);
        });
        
        assertEquals("Connection is closed", exception.getMessage());
    }

    @Test
    void testExecuteQuery_StatementTimeoutException() throws SQLException {
        databaseService = new DatabaseService(testConfig, mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.execute()).thenThrow(new SQLTimeoutException("Query timeout"));

        SQLTimeoutException exception = assertThrows(SQLTimeoutException.class, () -> {
            databaseService.executeSql("SELECT * FROM test", 100);
        });
        
        assertEquals("Query timeout", exception.getMessage());
    }

    @Test
    void testExecuteQuery_StatementClosedException() throws SQLException {
        databaseService = new DatabaseService(testConfig, mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        doThrow(new SQLException("Statement is closed")).when(mockStatement).setMaxRows(anyInt());

        SQLException exception = assertThrows(SQLException.class, () -> {
            databaseService.executeSql("SELECT * FROM test", 100);
        });
        
        assertEquals("Statement is closed", exception.getMessage());
    }

    @Test
    void testExecuteQuery_QueryTimeoutSettingFailure() throws SQLException {
        databaseService = new DatabaseService(testConfig, mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        doThrow(new SQLException("Cannot set query timeout")).when(mockStatement).setQueryTimeout(anyInt());

        SQLException exception = assertThrows(SQLException.class, () -> {
            databaseService.executeSql("SELECT * FROM test", 100);
        });
        
        assertEquals("Cannot set query timeout", exception.getMessage());
    }

    @Test
    void testExecuteQuery_MaxRowsSettingFailure() throws SQLException {
        databaseService = new DatabaseService(testConfig, mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        doThrow(new SQLException("Cannot set max rows")).when(mockStatement).setMaxRows(anyInt());

        SQLException exception = assertThrows(SQLException.class, () -> {
            databaseService.executeSql("SELECT * FROM test", 100);
        });
        
        assertEquals("Cannot set max rows", exception.getMessage());
    }

    @Test
    void testExecuteQuery_ConnectionLeakageScenario() throws SQLException {
        databaseService = new DatabaseService(testConfig, mockDataSource);
        
        // First call succeeds to get connection
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.execute()).thenReturn(false);
        when(mockStatement.getUpdateCount()).thenReturn(1);
        
        // Second call fails with pool exhausted
        when(mockDataSource.getConnection())
            .thenReturn(mockConnection)
            .thenThrow(new SQLException("HikariPool connection leak detected"));

        // First query should succeed
        assertDoesNotThrow(() -> {
            databaseService.executeSql("INSERT INTO test VALUES(1)", 100);
        });

        // Second query should fail due to pool exhaustion
        SQLException exception = assertThrows(SQLException.class, () -> {
            databaseService.executeSql("INSERT INTO test VALUES(2)", 100);
        });
        
        assertTrue(exception.getMessage().contains("connection leak"));
    }

    @Test
    void testExecuteQuery_DatabaseUnavailableException() throws SQLException {
        databaseService = new DatabaseService(testConfig, mockDataSource);
        when(mockDataSource.getConnection()).thenThrow(new SQLException("Database is unavailable", "08S01"));

        SQLException exception = assertThrows(SQLException.class, () -> {
            databaseService.executeSql("SELECT * FROM test", 100);
        });
        
        assertEquals("Database is unavailable", exception.getMessage());
        assertEquals("08S01", exception.getSQLState());
    }

    @Test
    void testExecuteQuery_AuthenticationFailure() throws SQLException {
        databaseService = new DatabaseService(testConfig, mockDataSource);
        when(mockDataSource.getConnection()).thenThrow(new SQLException("Authentication failed", "28000"));

        SQLException exception = assertThrows(SQLException.class, () -> {
            databaseService.executeSql("SELECT * FROM test", 100);
        });
        
        assertEquals("Authentication failed", exception.getMessage());
        assertEquals("28000", exception.getSQLState());
    }

    @Test
    void testExecuteQuery_NetworkConnectionFailure() throws SQLException {
        databaseService = new DatabaseService(testConfig, mockDataSource);
        when(mockDataSource.getConnection()).thenThrow(new SQLException("Network connection failed", "08006"));

        SQLException exception = assertThrows(SQLException.class, () -> {
            databaseService.executeSql("SELECT * FROM test", 100);
        });
        
        assertEquals("Network connection failed", exception.getMessage());
        assertEquals("08006", exception.getSQLState());
    }

    @Test
    void testExecuteQuery_TransactionRollbackException() throws SQLException {
        databaseService = new DatabaseService(testConfig, mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.execute()).thenThrow(new SQLTransactionRollbackException("Transaction rolled back", "40001"));

        SQLTransactionRollbackException exception = assertThrows(SQLTransactionRollbackException.class, () -> {
            databaseService.executeSql("UPDATE test SET col=1", 100);
        });
        
        assertEquals("Transaction rolled back", exception.getMessage());
        assertEquals("40001", exception.getSQLState());
    }

    @Test
    void testExecuteQuery_DataIntegrityViolation() throws SQLException {
        databaseService = new DatabaseService(testConfig, mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.execute()).thenThrow(new SQLIntegrityConstraintViolationException("Unique constraint violation", "23000"));

        SQLIntegrityConstraintViolationException exception = assertThrows(SQLIntegrityConstraintViolationException.class, () -> {
            databaseService.executeSql("INSERT INTO test VALUES(1)", 100);
        });
        
        assertEquals("Unique constraint violation", exception.getMessage());
        assertEquals("23000", exception.getSQLState());
    }

    @Test
    void testExecuteQuery_FeatureNotSupportedException() throws SQLException {
        databaseService = new DatabaseService(testConfig, mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.execute()).thenThrow(new SQLFeatureNotSupportedException("Feature not supported", "0A000"));

        SQLException exception = assertThrows(SQLException.class, () -> {
            databaseService.executeSql("CALL unsupported_function()", 100);
        });

        assertTrue(exception instanceof SQLFeatureNotSupportedException);
        assertEquals("Feature not supported", exception.getMessage());
        assertEquals("0A000", exception.getSQLState());
    }

    @Test
    void testClose_DataSourceAlreadyClosed() {
        when(mockDataSource.isClosed()).thenReturn(true);
        databaseService = new DatabaseService(testConfig, mockDataSource);

        // Should not throw exception when data source is already closed
        assertDoesNotThrow(() -> {
            databaseService.close();
        });

        // Verify close() is not called on already closed data source
        verify(mockDataSource, never()).close();
    }

    @Test
    void testClose_DataSourceCloseException() {
        when(mockDataSource.isClosed()).thenReturn(false);
        doThrow(new RuntimeException("Error closing data source")).when(mockDataSource).close();
        databaseService = new DatabaseService(testConfig, mockDataSource);

        // Should not throw exception even if close fails
        assertDoesNotThrow(() -> {
            databaseService.close();
        });
    }

    @Test
    void testClose_NullDataSource() {
        // Test with null data source (edge case)
        databaseService = new DatabaseService(testConfig, null);

        // Should not throw exception with null data source
        assertDoesNotThrow(() -> {
            databaseService.close();
        });
    }

    @Test
    void testExecuteQuery_PrepareStatementResourceLeak() throws SQLException {
        databaseService = new DatabaseService(testConfig, mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        
        // First call succeeds to create prepared statement
        when(mockConnection.prepareStatement("SELECT * FROM test")).thenReturn(mockStatement);
        doThrow(new SQLException("Statement setup failed")).when(mockStatement).setMaxRows(anyInt());

        SQLException exception = assertThrows(SQLException.class, () -> {
            databaseService.executeSql("SELECT * FROM test", 100);
        });
        
        assertEquals("Statement setup failed", exception.getMessage());
        
        // Verify that the prepared statement would be closed in the try-with-resources
        // (This is handled automatically by the try-with-resources block)
    }

    @Test
    void testExecuteQuery_ConnectionInterrupted() throws SQLException {
        databaseService = new DatabaseService(testConfig, mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.execute()).thenThrow(new SQLException("Connection interrupted", "57014"));

        SQLException exception = assertThrows(SQLException.class, () -> {
            databaseService.executeSql("SELECT * FROM test", 100);
        });
        
        assertEquals("Connection interrupted", exception.getMessage());
        assertEquals("57014", exception.getSQLState());
    }

    @Test
    void testExecuteQuery_OutOfMemoryDuringExecution() throws SQLException {
        databaseService = new DatabaseService(testConfig, mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.execute()).thenThrow(new SQLException("Out of memory", "53200"));

        SQLException exception = assertThrows(SQLException.class, () -> {
            databaseService.executeSql("SELECT * FROM huge_table", 100);
        });
        
        assertEquals("Out of memory", exception.getMessage());
        assertEquals("53200", exception.getSQLState());
    }

    @Test
    void testExecuteQuery_DiskFullException() throws SQLException {
        databaseService = new DatabaseService(testConfig, mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.execute()).thenThrow(new SQLException("Disk full", "53100"));

        SQLException exception = assertThrows(SQLException.class, () -> {
            databaseService.executeSql("CREATE TABLE large_table AS SELECT * FROM huge_table", 100);
        });
        
        assertEquals("Disk full", exception.getMessage());
        assertEquals("53100", exception.getSQLState());
    }
}