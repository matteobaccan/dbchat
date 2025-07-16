# DBMCP - MCP Server for Databases
## Installation and Usage Guide

## 📋 Prerequisites

Before you begin, ensure you have the following installed on your system:

### Required Software
- **Java 17 or higher** - Download from [OpenJDK](https://openjdk.org/) or [Oracle](https://www.oracle.com/java/technologies/downloads/)
- **Maven 3.6 or higher** - Download from [Apache Maven](https://maven.apache.org/download.cgi)
- **Git** - Download from [git-scm.com](https://git-scm.com/)

### Verify Installation
Open a terminal/command prompt and run:

```bash
# Check Java version (must be 17+)
java -version

# Check Maven version (must be 3.6+)
mvn -version

# Check Git
git --version
```

## 🚀 Step 1: Download and Build the Project

### Clone the Repository
```bash
git clone https://github.com/skanga/dbmcp
cd dbmcp
```

### Choose Your Database Profile and Build

The project supports different database sets through Maven profiles:

#### Option A: Basic Build (H2, SQLite, PostgreSQL)
```bash
mvn clean package
```

#### Option B: Standard Databases (includes MySQL, MariaDB, ClickHouse)
```bash
mvn clean package -P standard-databases
```

#### Option C: Enterprise Databases (includes Oracle, SQL Server, IBM DB2)
```bash
mvn clean package -P standard-databases,enterprise-databases
```

#### Option D: Cloud Analytics (includes Redshift, Snowflake, BigQuery)
```bash
mvn clean package -P standard-databases,cloud-analytics
```

#### Option E: Everything (creates 400MB+ JAR)
```bash
mvn clean package -P standard-databases,enterprise-databases,cloud-analytics,big-data
```

### Verify Build Success
```bash
# Check that the JAR file was created
ls target/dbmcp-1.0.0.jar
```

## 🗄️ Step 2: Database Setup Options

### Option A: Quick Test with H2 (Recommended for First Run)
H2 is an in-memory database perfect for testing - no additional setup required!

```bash
export DB_URL="jdbc:h2:mem:testdb"
export DB_USER="sa"
export DB_PASSWORD=""
export DB_DRIVER="org.h2.Driver"
```

### Option B: MySQL Database
```bash
# 1. Install MySQL or use Docker
docker run -d -p 3306:3306 -e MYSQL_ROOT_PASSWORD=password mysql:8.0

# 2. Create database and user
mysql -u root -p
CREATE DATABASE testdb;
CREATE USER 'mcpuser'@'%' IDENTIFIED BY 'mcppassword';
GRANT ALL PRIVILEGES ON testdb.* TO 'mcpuser'@'%';
FLUSH PRIVILEGES;
EXIT;

# 3. Set environment variables
export DB_URL="jdbc:mysql://localhost:3306/testdb"
export DB_USER="mcpuser"
export DB_PASSWORD="mcppassword"
export DB_DRIVER="com.mysql.cj.jdbc.Driver"
```

### Option C: PostgreSQL Database
```bash
# 1. Install PostgreSQL or use Docker
docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=password postgres:15

# 2. Create database and user
psql -U postgres
CREATE DATABASE testdb;
CREATE USER mcpuser WITH PASSWORD 'mcppassword';
GRANT ALL PRIVILEGES ON DATABASE testdb TO mcpuser;
\q

# 3. Set environment variables
export DB_URL="jdbc:postgresql://localhost:5432/testdb"
export DB_USER="mcpuser"
export DB_PASSWORD="mcppassword"
export DB_DRIVER="org.postgresql.Driver"
```

### Option D: SQLite (File-based)
```bash
export DB_URL="jdbc:sqlite:./testdb.sqlite"
export DB_USER=""
export DB_PASSWORD=""
export DB_DRIVER="org.sqlite.JDBC"
```

### Option E: Oracle Database
```bash
export DB_URL="jdbc:oracle:thin:@localhost:1521:xe"
export DB_USER="username"
export DB_PASSWORD="password"
export DB_DRIVER="oracle.jdbc.driver.OracleDriver"
```

### Option F: SQL Server
```bash
export DB_URL="jdbc:sqlserver://localhost:1433;databaseName=mydb"
export DB_USER="username"
export DB_PASSWORD="password"
export DB_DRIVER="com.microsoft.sqlserver.jdbc.SQLServerDriver"
```

## ▶️ Step 3: Choose Your Transport Mode and Start the Server

The DBMCP server supports two transport modes:

### **stdio Mode** - For Claude Desktop Integration (Default)
Standard MCP communication over stdin/stdout for Claude Desktop.

### **HTTP Mode** - For Web Applications and Testing
HTTP REST API for web applications, remote access, and easier testing.

---

### 🖥️ stdio Mode (Claude Desktop)

#### Method 1: Using Environment Variables
```bash
java -jar target/dbmcp-1.0.0.jar
```

#### Method 2: Using Java System Properties
```bash
java -Ddb.url="jdbc:h2:mem:testdb" \
     -Ddb.user="sa" \
     -Ddb.password="" \
     -Ddb.driver="org.h2.Driver" \
     -jar target/dbmcp-1.0.0.jar
```

#### Method 3: Using Command Line Arguments
```bash
java -jar target/dbmcp-1.0.0.jar \
     --db_url="jdbc:h2:mem:testdb" \
     --db_user="sa" \
     --db_password="" \
     --db_driver="org.h2.Driver"
```

#### Expected Output (stdio mode)
```
[main] INFO com.skanga.mcp.McpServer - Starting Database MCP Server in stdio mode...
```

The server is now waiting for JSON-RPC requests on stdin.

---

### 🌐 HTTP Mode (Web Applications)

#### Method 1: Using Environment Variables
```bash
export HTTP_MODE="true"
export HTTP_PORT="8080"  # Optional, defaults to 8080
java -jar target/dbmcp-1.0.0.jar
```

#### Method 2: Using Command Line Arguments
```bash
# Default port 8080
java -jar target/dbmcp-1.0.0.jar --http_mode=true

# Custom port
java -jar target/dbmcp-1.0.0.jar --http_mode=true --http_port=9090
```

#### Method 3: Using Java System Properties
```bash
java -Dhttp.mode=true \
     -Dhttp.port=8080 \
     -Ddb.url="jdbc:h2:mem:testdb" \
     -jar target/dbmcp-1.0.0.jar
```

#### Expected Output (HTTP mode)
```
[main] INFO com.skanga.mcp.McpServer - Starting Database MCP Server in HTTP mode on port 8080...
[main] INFO com.skanga.mcp.McpServer - Database MCP Server HTTP mode started on port 8080
[main] INFO com.skanga.mcp.McpServer - MCP endpoint: http://localhost:8080/mcp
[main] INFO com.skanga.mcp.McpServer - Health check: http://localhost:8080/health
```

#### HTTP Endpoints
- **`POST /mcp`** - MCP JSON-RPC requests
- **`GET /health`** - Health check and server status
- **`OPTIONS /mcp`** - CORS preflight support

## 🧪 Step 4: Test the Server

### Test stdio Mode

#### Quick Test with H2
```bash
# In a new terminal, create some test data
echo '{"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {"protocolVersion": "2024-11-05", "capabilities": {}, "clientInfo": {"name": "test-client", "version": "1.0.0"}}}' | java -jar target/dbmcp-1.0.0.jar

# Create a test table
echo '{"jsonrpc": "2.0", "id": 2, "method": "tools/call", "params": {"name": "query", "arguments": {"sql": "CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100), email VARCHAR(100))"}}}' | java -jar target/dbmcp-1.0.0.jar

# Insert test data
echo '{"jsonrpc": "2.0", "id": 3, "method": "tools/call", "params": {"name": "query", "arguments": {"sql": "INSERT INTO users VALUES (1, '\''John Doe'\'', '\''john@example.com'\''), (2, '\''Jane Smith'\'', '\''jane@example.com'\'')"}}}' | java -jar target/dbmcp-1.0.0.jar

# Query the data
echo '{"jsonrpc": "2.0", "id": 4, "method": "tools/call", "params": {"name": "query", "arguments": {"sql": "SELECT * FROM users ORDER BY id"}}}' | java -jar target/dbmcp-1.0.0.jar
```

### Test HTTP Mode

Start the server in HTTP mode first:
```bash
java -jar target/dbmcp-1.0.0.jar --http_mode=true
```

Then in another terminal:

#### Health Check
```bash
curl http://localhost:8080/health
```

#### Initialize Protocol
```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "capabilities": {},
      "clientInfo": {"name": "http-client", "version": "1.0.0"}
    }
  }'
```

#### List Tools
```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
```

#### Execute Query
```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "query",
      "arguments": {
        "sql": "SELECT 1 as test_value, '\''Hello World'\'' as message",
        "maxRows": 10
      }
    }
  }'
```

## 🔧 Step 5: Configure with Claude Desktop

### Download Claude Desktop
1. Go to [claude.ai/download](https://claude.ai/download)
2. Install the desktop application
3. Sign in with your Claude account

⚠️ **Important**: MCP is NOT supported on the Claude.ai website. You must use Claude Desktop.

### Configure Claude Desktop

#### Find Configuration File Location:
- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`

#### Access Through Claude Desktop:
1. Open Claude Desktop
2. Click **Claude** menu (macOS) or settings icon
3. Select **Settings...**
4. Click **Developer** in the left sidebar
5. Click **Edit Config**

#### Add Database Server Configuration:

```json
{
  "mcpServers": {
    "database-server": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/your/target/dbmcp-1.0.0.jar"
      ],
      "env": {
        "DB_URL": "jdbc:h2:mem:testdb",
        "DB_USER": "sa",
        "DB_PASSWORD": "",
        "DB_DRIVER": "org.h2.Driver"
      }
    }
  }
}
```

#### Configuration Examples for Different Databases:

**MySQL:**
```json
{
  "mcpServers": {
    "mysql-server": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/dbmcp-1.0.0.jar"],
      "env": {
        "DB_URL": "jdbc:mysql://localhost:3306/your_database",
        "DB_USER": "your_username",
        "DB_PASSWORD": "your_password",
        "DB_DRIVER": "com.mysql.cj.jdbc.Driver"
      }
    }
  }
}
```

**PostgreSQL:**
```json
{
  "mcpServers": {
    "postgres-server": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/dbmcp-1.0.0.jar"],
      "env": {
        "DB_URL": "jdbc:postgresql://localhost:5432/your_database",
        "DB_USER": "your_username",
        "DB_PASSWORD": "your_password",
        "DB_DRIVER": "org.postgresql.Driver"
      }
    }
  }
}
```

#### Multiple Database Connections:
```json
{
  "mcpServers": {
    "production-db": {
      "command": "java",
      "args": ["-jar", "/path/to/dbmcp-1.0.0.jar"],
      "env": {
        "DB_URL": "jdbc:mysql://prod-server:3306/proddb",
        "DB_USER": "readonly_user",
        "DB_PASSWORD": "secure_password",
        "DB_DRIVER": "com.mysql.cj.jdbc.Driver"
      }
    },
    "analytics-db": {
      "command": "java",
      "args": ["-jar", "/path/to/dbmcp-1.0.0.jar"],
      "env": {
        "DB_URL": "jdbc:postgresql://analytics:5432/analytics",
        "DB_USER": "analyst",
        "DB_PASSWORD": "password",
        "DB_DRIVER": "org.postgresql.Driver"
      }
    }
  }
}
```

### Important Configuration Notes:
1. **Use Absolute Paths**: Always use complete file paths
2. **Java Path**: If `java` isn't in PATH, use full path like `"c:/java/jdk-17/bin/java.exe"`
3. **Windows Paths**: Use forward slashes: `"c:/path/to/file"`
4. **Security**: Environment variables are encrypted by the OS
5. **Restart Required**: Restart Claude Desktop after configuration changes

## 🔧 Step 5B: Integrate with Web Applications (HTTP mode)

### JavaScript/Node.js Integration
```javascript
// Example: Query database from a web application
async function queryDatabase(sql, maxRows = 1000) {
  const response = await fetch('http://localhost:8080/mcp', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      jsonrpc: '2.0',
      id: Date.now(),
      method: 'tools/call',
      params: {
        name: 'query',
        arguments: { sql, maxRows }
      }
    })
  });
  
  return await response.json();
}

// Usage
const result = await queryDatabase('SELECT COUNT(*) FROM users');
console.log(result);
```

### Python Integration
```python
import requests

def query_database(sql, max_rows=1000):
    response = requests.post('http://localhost:8080/mcp', json={
        'jsonrpc': '2.0',
        'id': 1,
        'method': 'tools/call',
        'params': {
            'name': 'query',
            'arguments': {
                'sql': sql,
                'maxRows': max_rows
            }
        }
    })
    return response.json()

# Usage
result = query_database('SELECT * FROM users LIMIT 10')
print(result)
```

### cURL Examples
```bash
# Health check
curl http://localhost:8080/health

# List available tools
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'

# Execute a query
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "query",
      "arguments": {
        "sql": "SELECT COUNT(*) as user_count FROM users",
        "maxRows": 1
      }
    }
  }'

# List database resources
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"resources/list","params":{}}'
```

## 🚀 Step 6: Using the Database MCP Server

### Verify Connection
After restarting Claude Desktop:
1. Look for the MCP icon (slider/plug) in the message input box
2. Click to see your database server listed and connected

### Example Queries
```
"Show me all tables in the database"
"Query the users table and show me the first 10 records"
"What's the structure of the orders table?"
"Run this SQL: SELECT COUNT(*) FROM customers WHERE status = 'active'"
"Create a chart showing monthly sales from the orders table"
```

### For Web Applications (HTTP mode)

#### Health Monitoring
Monitor server status:
```bash
curl http://localhost:8080/health
```

Expected response:
```json
{
  "status": "healthy",
  "server": "Database MCP Server",
  "timestamp": 1703123456789,
  "database": "connected"
}
```

#### API Integration Examples

**Dashboard Application:**
```html
<!DOCTYPE html>
<html>
<head>
    <title>Database Dashboard</title>
</head>
<body>
    <script>
    async function loadUserCount() {
        const response = await fetch('http://localhost:8080/mcp', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                jsonrpc: '2.0',
                id: 1,
                method: 'tools/call',
                params: {
                    name: 'query',
                    arguments: {
                        sql: 'SELECT COUNT(*) as count FROM users',
                        maxRows: 1
                    }
                }
            })
        });
        
        const result = await response.json();
        document.getElementById('userCount').textContent = 
            result.result.content[0].text;
    }
    
    loadUserCount();
    </script>
    
    <h1>User Count: <span id="userCount">Loading...</span></h1>
</body>
</html>
```

## 🛠️ Advanced Configuration

### Transport Mode Selection
```bash
# Environment variable approach
export HTTP_MODE="true"      # Enable HTTP mode
export HTTP_PORT="8080"      # Set HTTP port

# Command line approach
java -jar target/dbmcp-1.0.0.jar --http_mode=true --http_port=9090

# System properties approach
java -Dhttp.mode=true -Dhttp.port=8080 -jar target/dbmcp-1.0.0.jar
```

### Connection Pool Settings
Set environment variables for fine-tuning:

```bash
export MAX_CONNECTIONS="20"
export CONNECTION_TIMEOUT_MS="30000"
export QUERY_TIMEOUT_SECONDS="60"
export SELECT_ONLY="false"
export MAX_SQL_LENGTH="50000"
export MAX_ROWS_LIMIT="50000"
```

### Security Settings
```bash
# Enable read-only mode
export SELECT_ONLY="true"

# Limit query size
export MAX_SQL_LENGTH="10000"

# Limit result size
export MAX_ROWS_LIMIT="1000"
```

### Debug Mode
Enable debug logging for troubleshooting:

```bash
# stdio mode
java -Dlogging.level.root=DEBUG -jar target/dbmcp-1.0.0.jar
```

# HTTP mode
java -Dlogging.level.root=DEBUG -jar target/dbmcp-1.0.0.jar --http_mode=true
```

### Configuration Priority Order
1. **Command line arguments**: `--http_mode=true`, `--http_port=8080`
2. **Environment variables**: `HTTP_MODE`, `HTTP_PORT`
3. **System properties**: `-Dhttp.mode=true`, `-Dhttp.port=8080`
4. **Default values**: stdio mode, port 8080

## 🔍 Troubleshooting

### Common Issues

#### "ClassNotFoundException" for database driver
**Solution**: Ensure you built with the correct Maven profile for your database.

#### "Connection refused" or "Access denied"
**Solutions**:
- Verify database server is running
- Check connection parameters
- Ensure database user has necessary permissions
- Check firewall settings

#### Server starts but doesn't respond (stdio mode)
**Solutions**:
- Verify JSON-RPC request format
- Check server logs for errors
- Ensure proper stdin/stdout communication

#### HTTP server doesn't start
**Solutions**:
- Check if port is already in use: `netstat -an | grep 8080`
- Try different port: `--http_port=9090`
- Check for permission issues on the port
- Verify firewall settings

#### Claude Desktop doesn't show the server
**Solutions**:
- Validate JSON configuration syntax
- Check absolute file paths
- Verify Java is accessible
- Restart Claude Desktop

#### HTTP requests fail with CORS errors
**Solutions**:
- Server includes CORS headers by default
- For browser testing, ensure you're making requests from `http://localhost`
- Check browser developer tools for specific CORS errors

### Mode-Specific Troubleshooting

#### stdio Mode Issues
```bash
# Test JSON-RPC communication
echo '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' | java -jar target/dbmcp-1.0.0.jar

# Debug mode for detailed logging
java -Dlogging.level.root=DEBUG -jar target/dbmcp-1.0.0.jar
```

#### HTTP Mode Issues
```bash
# Test server startup
java -Dlogging.level.root=DEBUG -jar target/dbmcp-1.0.0.jar --http_mode=true

# Test health endpoint
curl -v http://localhost:8080/health

# Test MCP endpoint
curl -v -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'

# Check what's running on the port
netstat -an | grep 8080
```

### Validation Commands
```bash
# Test Java version
java -version

# Validate JSON configuration (Claude Desktop)
cat claude_desktop_config.json | python -m json.tool

# Test database connection manually (stdio)
java -jar target/dbmcp-1.0.0.jar < test_query.json

# Test database connection manually (HTTP)
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'

```

## 📚 Additional Resources

- [Model Context Protocol Documentation](https://modelcontextprotocol.io/)
- [Claude Desktop Documentation](https://support.anthropic.com)
- [JDBC Documentation](https://docs.oracle.com/javase/tutorial/jdbc/)
- [Maven Documentation](https://maven.apache.org/guides/getting-started/)
- [HTTP/REST API Best Practices](https://restfulapi.net/)

## 🎯 Next Steps

Once installed, you can:

### With Claude Desktop (stdio mode):
- Query your database using natural language through Claude
- Generate SQL queries with Claude's help
- Create data visualizations and charts
- Explore database schemas and relationships
- Build reports and analytics dashboards

### With Web Applications (HTTP mode):
- Integrate database queries into web applications
- Build custom dashboards and analytics tools
- Create APIs that leverage the MCP protocol
- Automate database testing and validation
- Monitor database health and performance

### Choosing the Right Mode:

**Use stdio mode when:**
- Integrating with Claude Desktop
- Building conversational database interfaces
- Working with natural language queries
- Creating interactive data exploration experiences

**Use HTTP mode when:**
- Building web applications or APIs
- Creating custom dashboards
- Integrating with existing web infrastructure
- Automating database operations
- Testing and development workflows

For detailed usage examples and advanced features, see the README.md file.
