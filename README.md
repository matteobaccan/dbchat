# DBMCP - MCP Server for Databases

A generic MCP (Model Context Protocol) server for database operations using Java, Maven, and JDBC. Supports multiple database types and integrates seamlessly with Claude Desktop for natural language database interactions.

## 🌟 Features

- **Multi-database support**: Works with any JDBC-compatible database
- **MCP Protocol compliant**: Implements listResources, readResource, listTools, and callTool
- **Query execution**: Execute SQL queries with formatted, tabular results
- **Resource discovery**: Browse database structure (tables, views, schemas)
- **Metadata access**: Detailed information about database objects
- **Connection pooling**: HikariCP-based connection management
- **Security controls**: Read-only mode, query validation, and limits
- **Natural language interface**: Use Claude Desktop to query databases conversationally
- **Data visualization**: Generate charts and graphs from database data
- **Error handling**: Comprehensive error reporting and logging

## 🗃️ Supported Databases

### Default (Always Included)
- **H2** - In-memory and file-based database
- **SQLite** - Lightweight file-based database
- **PostgreSQL** - Advanced open-source database
- **CSV File** - Query any directory or ZIP file containing   [RFC 4180](https://tools.ietf.org/html/rfc4180).
  compliant CSV or DBF files to be accessed as though it were a database containing tables.

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
```bash
java -jar target/dbmcp-1.0.0.jar
```

### 4. Integrate with Claude Desktop

Add to your Claude Desktop configuration (`claude_desktop_config.json`):

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
├── McpServer.java            # Main MCP server implementation
├── ConfigParams.java        # Configuration parameter holder (record)
├── DatabaseService.java     # Database operations with HikariCP
├── DatabaseResource.java    # Resource representation (record)
└── QueryResult.java         # Query result holder (record)
```

### Key Components

- **McpServer.java** - Main entry point implementing JSON-RPC over stdio
- **DatabaseService.java** - Core database operations with connection pooling
- **ConfigParams.java** - Configuration management with validation
- **DatabaseResource.java** - Database object representation
- **QueryResult.java** - Structured query results

## ⚙️ Advanced Configuration

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
1. Command line arguments: `--db_url=...`
2. Environment variables: `DB_URL`
3. System properties: `-Ddb.url=...`
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
- Server runs locally with no external network access
- All data processing happens locally
- No data is sent to external services
- Environment variables are encrypted by the OS

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

## 🚫 Limitations

- **Claude.ai website**: Does not support MCP servers (use Claude Desktop)
- **Mobile apps**: MCP is only available in Claude Desktop
- **Linux support**: Currently macOS and Windows (Linux in development)
- **Network access**: MCP servers run locally only
- **Performance**: Large queries may be slower due to communication overhead

## 📝 Logging

The server uses SLF4J for logging. Configure logging levels as needed:
- `INFO` - General server operations
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

### Testing
```bash
# Run automated tests
python test-mcp-server.py

# Manual testing with debug output
java -Dlogging.level.root=DEBUG -jar target/dbmcp-1.0.0.jar
```

## 🎯 Getting Started Tips

1. **Start Simple**: Begin with H2 for initial testing
2. **Use Claude Desktop**: Download and configure the desktop app
3. **Test Connection**: Verify database connectivity before MCP integration
4. **Start with Read-Only**: Use `SELECT_ONLY=true` for safety
5. **Monitor Performance**: Watch memory usage for large queries

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
4. **Download Claude Desktop**: From claude.ai/download
5. **Configure MCP**: Add server to Claude Desktop config
6. **Start Querying**: Ask Claude about your data!

Transform your database interactions with natural language queries, automated visualizations, and intelligent analysis through the power of Claude Desktop and the Database MCP Server.

## License

This project is provided as-is for educational and development purposes under Apache 2.0 license.
