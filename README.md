# DBMCP - MCP Server for Databases

An MCP (Model Context Protocol) server for database operations using Java, Maven, and JDBC. Supports almost every database type and integrates seamlessly with Claude Desktop for natural language database interactions.

## 🌟 Features

- **Natural language interface**: Ask your databases a question in Enhlish. Have a conversation with your tables.
- **Multi-database support**: Works with any JDBC-compatible database
- **Data visualization**: Generate charts and graphs from database data
- **Dual transport modes**: stdio (standard) and HTTP modes for flexible deployment
- **MCP Protocol compliant**: Implements listResources, readResource, listTools, and callTool
- **Query execution**: Execute SQL queries with well formatted, tabular results
- **Resource discovery**: Browse database structure (tables, views, schemas)
- **Metadata access**: Detailed information about database objects
- **Connection pooling**: HikariCP-based connection management
- **Security controls**: Read-only mode, query validation, and limits
- **Error handling**: Comprehensive error reporting and logging
- **Health monitoring**: Built-in health check endpoint (HTTP mode)
- **CORS support**: Ready for browser-based applications (HTTP mode)

## 🗃️ Supported Databases

### Default (Always Included)
- **H2** - In-memory and file-based database
- **SQLite** - Lightweight file-based database
- **PostgreSQL** - Advanced open-source database
- **CSV File** - Query any directory or ZIP file containing [RFC 4180](https://tools.ietf.org/html/rfc4180)
  compliant CSV or DBF files (needs extra config) to be accessed as though it were a database containing tables.

### Standard Databases (via `standard-databases` profile)
- **MySQL** - Popular open-source database
- **MariaDB** - MySQL-compatible database
- **ClickHouse** - Column-oriented analytics database
- **Apache Doris** - Use MySQL driver (it is 100% compatible with MySQL)

### Enterprise Databases (via `enterprise-databases` profile)
- **Oracle** - Enterprise database system
- **SQL Server** - Microsoft database system
- **IBM DB2** - IBM enterprise database

### Cloud Analytics (via `cloud-analytics` profile)
- **Amazon Redshift** - AWS data warehouse
- **Snowflake** - Cloud data platform
- **Google BigQuery** - Google's analytics database

### Big Data (via `big-data` profile)
- **Apache Hive** - Data warehouse software
- **MongoDB** - Document database (experimental)
- **Apache Cassandra** - NoSQL database
- **Apache Spark SQL** - Unified analytics engine

**IMPORTANT NOTE:** All JDBC drivers are subject to their own individual licences. It is your responsibility to ensure that you are compliant with all driver licensing requirements.

## 🚀 Transport Modes

### stdio Mode (Default)
Standard MCP communication over stdin/stdout for Claude Desktop integration.

```bash
java -jar target/dbmcp-1.0.0.jar
```

### HTTP Mode
HTTP REST API for web applications, testing, and remote access.

```bash
# Start HTTP server on default port 8080
java -jar target/dbmcp-1.0.0.jar --http_mode=true

# Start on custom port
java -jar target/dbmcp-1.0.0.jar --http_mode=true --http_port=9090

# Or using environment variables
export HTTP_MODE=true
export HTTP_PORT=8080
java -jar target/dbmcp-1.0.0.jar
```

#### HTTP Endpoints
- `POST /mcp` - MCP JSON-RPC requests
- `GET /health` - Health check and server status
- `OPTIONS /mcp` - CORS preflight support

#### HTTP Examples
```bash
# Health check
curl http://localhost:8080/health

# MCP request
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'

# Initialize protocol
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

## 🚀 Quick Start (Please read [INSTALL.md](INSTALL.md) for more details)

### 1. Build the Project
```bash
# Basic build (H2, SQLite, PostgreSQL)
mvn clean package

# With MySQL/MariaDB/ClickHouse support
mvn clean package -P standard-databases

# With enterprise database support
mvn clean package -P standard-databases,enterprise-databases
```

### 2. Configure Database Connection
```bash
# Environment variables
export DB_URL="jdbc:mysql://localhost:3306/mydb"
export DB_USER="username"
export DB_PASSWORD="password"
export DB_DRIVER="com.mysql.cj.jdbc.Driver"

# Or use system properties
java -Ddb.url="jdbc:mysql://localhost:3306/mydb" \
     -Ddb.user="username" \
     -Ddb.password="password" \
     -Ddb.driver="com.mysql.cj.jdbc.Driver" \
     -jar target/dbmcp-1.0.0.jar
```

### 3. Run the Server

#### For Claude Desktop (stdio mode)
```bash
java -jar target/dbmcp-1.0.0.jar
```

#### For HTTP/Web Applications
```bash
# Default port 8080
java -jar target/dbmcp-1.0.0.jar --http_mode=true

# Custom port
java -jar target/dbmcp-1.0.0.jar --http_mode=true --http_port=9090
```

### 4. Integration Options

#### Claude Desktop Integration

Add to your Claude Desktop configuration `claude_desktop_config.json`. You can add multiple databases if needed. Refer to [INSTALL.md](INSTALL.md) for more details:

```json
{
  "mcpServers": {
    "database-server": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/target/dbmcp-1.0.0.jar"],
      "env": {
        "DB_URL": "jdbc:mysql://localhost:3306/mydb",
        "DB_USER": "username",
        "DB_PASSWORD": "password",
        "DB_DRIVER": "com.mysql.cj.jdbc.Driver"
      }
    }
  }
}
```

#### Web Application Integration

For HTTP mode, integrate with any web application or API client:

```javascript
// JavaScript example
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
        sql: 'SELECT COUNT(*) FROM users',
        maxRows: 1000
      }
    }
  })
});
const result = await response.json();
```

```python
# Python example
import requests

response = requests.post('http://localhost:8080/mcp', json={
    'jsonrpc': '2.0',
    'id': 1,
    'method': 'tools/list',
    'params': {}
})
print(response.json())
```

## 📊 Data Visualization with Claude Desktop

The Database MCP Server enables Claude to create beautiful, interactive charts and graphs directly from your database data:

### Supported Visualizations
- **Line Charts** - Time series, trends, progression
- **Bar Charts** - Comparisons, categories, rankings
- **Pie Charts** - Proportions, distributions
- **Scatter Plots** - Relationships, correlations
- **Area Charts** - Cumulative data, stacked values
- **Heat Maps** - Pattern visualization, correlations
- **Histograms** - Data distribution, frequency
- **Box Plots** - Statistical summaries

## 🎨 Example Visualization Requests

### Sales Analytics
```
"Query our sales database and create a line chart showing monthly revenue for the last 12 months"

"Show me a bar chart of our top 10 customers by total order value"

"Create a pie chart showing the distribution of orders by product category"
```

### Business Intelligence
```
"Pull data from the users table and create a chart showing user signups by month over the past year"

"Generate a scatter plot showing the relationship between order value and customer age"

"Create a heat map showing sales performance by region and month"
```

### How It Works
1. **Natural Language Query** - Ask Claude to analyze your data
2. **Automatic SQL Generation** - Claude creates optimized queries
3. **Data Processing** - Results are processed and cleaned
4. **Interactive Charts** - Beautiful, responsive visualizations are generated
5. **Export & Share** - Charts can be exported or embedded

## 🛠️ Configuration Examples

### MySQL
```bash
export DB_URL="jdbc:mysql://localhost:3306/database_name"
export DB_USER="username"
export DB_PASSWORD="password"
export DB_DRIVER="com.mysql.cj.jdbc.Driver"
```

### PostgreSQL
```bash
export DB_URL="jdbc:postgresql://localhost:5432/mydb"
export DB_USER="username"
export DB_PASSWORD="password"
export DB_DRIVER="org.postgresql.Driver"
```

### Oracle
```bash
export DB_URL="jdbc:oracle:thin:@localhost:1521:xe"
export DB_USER="username"
export DB_PASSWORD="password"
export DB_DRIVER="oracle.jdbc.driver.OracleDriver"
```

### SQL Server
```bash
export DB_URL="jdbc:sqlserver://localhost:1433;databaseName=mydb"
export DB_USER="username"
export DB_PASSWORD="password"
export DB_DRIVER="com.microsoft.sqlserver.jdbc.SQLServerDriver"
```

### H2 (In-Memory)
```bash
export DB_URL="jdbc:h2:mem:testdb"
export DB_USER="sa"
export DB_PASSWORD=""
export DB_DRIVER="org.h2.Driver"
```

### CSV File/Dir/Zip (See [docs](https://github.com/simoc/csvjdbc/blob/master/docs/doc.md) here)
```bash
export DB_URL="jdbc:relique:csv:/path/to/file.txt?separator=;&fileExtension=.txt"
export DB_USER=""
export DB_PASSWORD=""
export DB_DRIVER="org.sqlite.JDBC"
```

### SQLite
```bash
export DB_URL="jdbc:sqlite:/path/to/database.db"
export DB_USER=""
export DB_PASSWORD=""
export DB_DRIVER="org.sqlite.JDBC"
```

## 🔧 MCP Protocol Support

### Tools

#### `query` Tool
Execute SQL queries on the database.

**Parameters:**
- `sql` (string, required): SQL query to execute
- `maxRows` (integer, optional): Maximum number of rows to return (default: 1000)

**Example:**
```json
{
  "method": "tools/call",
  "params": {
    "name": "query",
    "arguments": {
      "sql": "SELECT * FROM users WHERE status = 'active'",
      "maxRows": 100
    }
  }
}
```

### Resources

The server exposes database metadata as resources:

- `database://info` - Database connection and feature information
- `database://table/{tableName}` - Table structure and metadata
- `database://schema/{schemaName}` - Schema information (if supported)

## 💬 Natural Language Database Interactions

With Claude Desktop integration, you can interact with your database using natural language:

### Data Exploration
```
"What tables do we have in our database?"
"Show me the structure of the users table"
"How many customers do we have by region?"
"What are our top-selling products this month?"
```

### Analytics & Reporting
```
"Create a sales report for Q4 2024"
"Show me customer growth trends over the past year"
"Which products have the highest profit margins?"
"Generate a dashboard showing key business metrics"
```

### Data Quality & Investigation
```
"Are there any duplicate email addresses in our customer table?"
"Find customers who haven't placed orders in the last 6 months"
"Show me any unusual spikes in our daily transaction data"
"Identify potential data quality issues in our product catalog"
```

### Schema Understanding
```
"Explain the relationship between our orders and customers tables"
"What foreign keys exist in our database?"
"Show me all indexes on the products table"
"What are the primary keys for each table?"
```

## 🏗️ Project Structure

```
src/main/java/com/skanga/mcp/
├── McpServer.java           # Main MCP server implementation (stdio + HTTP)
├── ConfigParams.java        # Configuration parameter holder (record)
├── DatabaseService.java     # Database operations with HikariCP
├── DatabaseResource.java    # Resource representation (record)
└── QueryResult.java         # Query result holder (record)
```

### Key Components

- **McpServer.java** - Main entry point supporting both JSON-RPC over stdio and HTTP
- **DatabaseService.java** - Core database operations with connection pooling
- **ConfigParams.java** - Configuration management with validation
- **DatabaseResource.java** - Database object representation
- **QueryResult.java** - Structured query results

## ⚙️ Advanced Configuration

### Transport Mode Configuration
```bash
# HTTP mode settings
export HTTP_MODE="true"       # Enable HTTP mode
export HTTP_PORT="8080"       # HTTP server port

# Or via command line
java -jar target/dbmcp-1.0.0.jar --http_mode=true --http_port=8080
```

### Connection Pool Settings
```bash
export MAX_CONNECTIONS="20"
export CONNECTION_TIMEOUT_MS="30000"
export QUERY_TIMEOUT_SECONDS="60"
export IDLE_TIMEOUT_MS="600000"
export MAX_LIFETIME_MS="1800000"
export LEAK_DETECTION_THRESHOLD_MS="60000"
```

### Security Settings
```bash
export SELECT_ONLY="true"        # Enable read-only mode
export MAX_SQL_LENGTH="10000"    # Limit query size
export MAX_ROWS_LIMIT="1000"     # Limit result size
```

### Configuration Priority Order
1. Command line arguments: `--db_url=...`, `--http_mode=true`
2. Environment variables: `DB_URL`, `HTTP_MODE`
3. System properties: `-Ddb.url=...`, `-Dhttp.mode=true`
4. Default values

## 🛡️ Security Considerations

### Database Security
- Use read-only database users when possible
- Configure query timeouts to prevent long-running queries
- Limit maximum row counts to prevent memory issues
- Use connection pooling in production environments
- Validate SQL queries before execution
- Consider implementing query allowlists for production use

### MCP Security
- Server runs locally with no external network access (stdio mode)
- All data processing happens locally
- No data is sent to external services
- Environment variables are encrypted by the OS

### HTTP Mode Security
- **Local deployment recommended**: HTTP mode is designed for local development and testing
- **CORS enabled**: Allows browser-based applications (configure as needed)
- **No authentication**: Consider adding external authentication for production deployments
- **Health endpoint**: Monitor server status at `/health`
- **Network binding**: Server binds to localhost by default

## 📋 Usage Examples

### Initialize Connection
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2024-11-05",
    "capabilities": {},
    "clientInfo": {
      "name": "test-client",
      "version": "1.0.0"
    }
  }
}
```

### Execute SQL Query
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "query",
    "arguments": {
      "sql": "SELECT COUNT(*) as user_count FROM users",
      "maxRows": 1
    }
  }
}
```

### List Database Resources
```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "method": "resources/list",
  "params": {}
}
```

### Read Table Metadata
```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "method": "resources/read",
  "params": {
    "uri": "database://table/users"
  }
}
```

## 📊 Real-World Use Cases

### Business Intelligence
- **Sales Analytics**: Monthly revenue trends, top customers, product performance
- **Customer Insights**: Segmentation analysis, lifetime value, churn prediction
- **Operational Metrics**: Inventory levels, performance tracking, cost analysis

### Data Exploration
- **Schema Discovery**: Understanding database structure and relationships
- **Data Quality**: Finding duplicates, missing values, inconsistencies
- **Trend Analysis**: Identifying patterns and anomalies in data

### Reporting & Dashboards
- **Executive Summaries**: KPI tracking, growth metrics, performance indicators
- **Operational Reports**: Daily/weekly summaries, exception reports
- **Interactive Analysis**: Ad-hoc queries, drill-down capabilities

### Web Integration (HTTP Mode)
- **API Integration**: Embed database queries in web applications
- **Dashboard Development**: Build custom dashboards with real-time data
- **Microservices**: Use as a database microservice in larger architectures
- **Testing & Automation**: Automated database testing and validation

## 🚫 Limitations

- **Claude.ai website**: Does not support MCP servers (use Claude Desktop for stdio mode)
- **Mobile apps**: MCP is only available in Claude Desktop
- **Linux support**: Currently macOS and Windows (Linux in development)
- **Network access**: MCP servers run locally only (HTTP mode available for local web apps)
- **Performance**: Large queries may be slower due to communication overhead

## 📝 Logging

The server uses SLF4J for logging. Configure logging levels as needed:
- `INFO` - General server operations, HTTP requests
- `DEBUG` - Detailed request/response information
- `ERROR` - Error conditions

```bash
# Enable debug logging
java -Dlogging.level.root=DEBUG -jar target/dbmcp-1.0.0.jar

# Enable trace logging for database operations
java -Dlogging.level.com.skanga.mcp=TRACE -jar target/dbmcp-1.0.0.jar
```

## 🔧 Development

### Adding New Tools
1. Add tool definition in `handleListTools()`
2. Add tool execution in `handleCallTool()`
3. Implement the tool logic in `DatabaseService`

### Error Handling
The server provides detailed error responses:
- `invalid_request` - Invalid method or parameters
- `database_error` - SQL or connection errors
- `internal_error` - Unexpected server errors

## 🧪 Testing

### Automated Testing
```bash
mvn test                    # Unit tests
mvn verify                  # Integration tests
python3 test-mcp-protocol.py target/dbmcp-1.0.0.jar stdio    # stdio protocol tests
python3 test-mcp-protocol.py target/dbmcp-1.0.0.jar http     # HTTP protocol tests
python3 test-mcp-server.py                                   # server tests

```

### Manual Testing
```bash
# Test health endpoint
curl http://localhost:8080/health

# Test MCP protocol
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'

# Interactive testing (stdio mode)
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}' | java -jar target/dbmcp-1.0.0.jar

# Manual testing with debug output
java -Dlogging.level.root=DEBUG -jar target/dbmcp-1.0.0.jar
```

## 🎯 Getting Started Tips

1. **Start Simple**: Begin with H2 for initial testing
2. **Choose Your Mode**: Use stdio for Claude Desktop, HTTP for web applications
3. **Test Connection**: Verify database connectivity before MCP integration
4. **Start with Read-Only**: Use `SELECT_ONLY=true` for safety
5. **Monitor Performance**: Watch memory usage for large queries
6. **Use Health Checks**: Monitor `/health` endpoint in HTTP mode

## 📚 Additional Resources

- [Model Context Protocol Specification](https://modelcontextprotocol.io/)
- [Claude Desktop Download](https://claude.ai/download)
- [JDBC Documentation](https://docs.oracle.com/javase/tutorial/jdbc/)
- [HikariCP Connection Pool](https://github.com/brettwooldridge/HikariCP)
- [Maven Getting Started Guide](https://maven.apache.org/guides/getting-started/)

## 📄 License

This project is provided as-is for educational and development purposes under Apache 2.0 license.

---

## 🚀 Ready to Get Started?

1. **Install Prerequisites**: Java 17+, Maven 3.6+
2. **Build the Project**: `mvn clean package -P standard-databases` or similar
3. **Configure Database**: Set environment variables
4. **Choose Your Mode**:
   - **For Claude Desktop**: Run in stdio mode
   - **For Web Applications**: Run in HTTP mode
5. **Configure Integration**: Add server to Claude Desktop or integrate with your web app
6. **Start Querying**: Ask Claude about your data or make HTTP requests!

Transform your database interactions with natural language queries, automated visualizations, and intelligent analysis through the power of Claude Desktop and the Database MCP Server - now available in both stdio and HTTP modes for maximum flexibility.

## License

This project is provided as-is for educational and development purposes under Apache 2.0 license.
