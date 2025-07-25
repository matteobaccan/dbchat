# DBChat MCP Installation Guide

This guide explains how to install and configure DBChat with various MCP (Model Context Protocol) clients.

## Prerequisites

- Java Runtime Environment (JRE) installed
- DBChat JAR file downloaded (e.g., `dbchat-x.y.z.jar`)
- Database connection details (URL, driver, username, password)

## Configuration Parameters

Before configuring any client, prepare the following information:

- `DB_URL`: Your database JDBC URL
- `DB_DRIVER`: Your database JDBC driver class name
- `DB_USER`: Your database username
- `DB_PASSWORD`: Your database password
- JAR file path: Absolute path to your DBChat JAR file

## Client Configuration

### 1. Cursor

#### Open MCP Configuration

Navigate to your Cursor MCP configuration file:

- **macOS**: `~/.cursor/mcp.json`
- **Windows**: `%APPDATA%\Cursor\mcp.json`
- **Linux**: `~/.config/cursor/mcp.json`

#### Add DBChat Configuration

```json
{
  "mcpServers": {
    "my-database": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/dbchat-x.y.z.jar"],
      "env": {
        "DB_URL": "your_database_jdbc_url",
        "DB_DRIVER": "your_database_jdbc_driver",
        "DB_USER": "your_database_username",
        "DB_PASSWORD": "your_database_password"
      }
    }
  }
}
```

### 2. Windsurf

#### Open MCP Configuration

Navigate to your Windsurf MCP configuration file:

- **macOS**: `~/.windsurf/mcp.json`
- **Windows**: `%APPDATA%\Windsurf\mcp.json`
- **Linux**: `~/.config/windsurf/mcp.json`

#### Add DBChat Configuration

```json
{
  "mcpServers": {
    "my-database": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/dbchat-x.y.z.jar"],
      "env": {
        "DB_URL": "your_database_jdbc_url",
        "DB_DRIVER": "your_database_jdbc_driver",
        "DB_USER": "your_database_username",
        "DB_PASSWORD": "your_database_password"
      }
    }
  }
}
```

### 3. Claude Desktop

#### Open Configuration File

Edit your Claude Desktop configuration file:

- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`

#### Add DBChat Configuration

```json
{
  "mcpServers": {
    "my-database": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/dbchat-x.y.z.jar"],
      "env": {
        "DB_URL": "your_database_jdbc_url",
        "DB_DRIVER": "your_database_jdbc_driver",
        "DB_USER": "your_database_username",
        "DB_PASSWORD": "your_database_password"
      }
    }
  }
}
```

### 4. Continue

#### Open Continue Configuration

Edit your `~/.continue/config.json` file.

#### Add DBChat Configuration

```json
{
  "models": [...],
  "mcpServers": [
    {
      "name": "my-database",
      "command": "java",
      "args": ["-jar", "/absolute/path/to/dbchat-x.y.z.jar"],
      "env": {
        "DB_URL": "your_database_jdbc_url",
        "DB_DRIVER": "your_database_jdbc_driver",
        "DB_USER": "your_database_username",
        "DB_PASSWORD": "your_database_password"
      }
    }
  ]
}
```

### 5. VS Code

#### Open VS Code Settings

Open your VS Code settings and locate the MCP configuration section, or create a `.vscode/settings.json` file in your workspace.

#### Add DBChat Configuration

```json
{
  "mcp": {
    "servers": {
      "my-database": {
        "command": "java",
        "args": ["-jar", "/absolute/path/to/dbchat-x.y.z.jar"],
        "env": {
          "DB_URL": "your_database_jdbc_url",
          "DB_DRIVER": "your_database_jdbc_driver",
          "DB_USER": "your_database_username",
          "DB_PASSWORD": "your_database_password"
        }
      }
    }
  }
}
```

### 6. Claude Code

For Claude Code, use the command line interface:

```bash
claude mcp add my-database \
  -e DB_URL=your_database_jdbc_url \
  -e DB_DRIVER=your_database_jdbc_driver \
  -e DB_USER=your_database_username \
  -e DB_PASSWORD=your_database_password \
  -- java -jar /absolute/path/to/dbchat-x.y.z.jar
```

## Configuration Notes

- Replace `my-database` with a meaningful name for your database connection
- Ensure the JAR file path is absolute and accessible
- Verify that Java is installed and available in your system PATH
- Test database connectivity before configuring the MCP server
- Some clients may require restarting after configuration changes

## Troubleshooting

- **Java not found**: Ensure Java is installed and in your system PATH
- **JAR file not found**: Verify the absolute path to the DBChat JAR file
- **Database connection issues**: Test your database credentials and URL separately
- **Configuration not loading**: Check JSON syntax and restart your client

## Security Considerations

- Store sensitive database credentials securely
- Consider using environment variables for production deployments
- Ensure your database user has appropriate permissions for the intended operations