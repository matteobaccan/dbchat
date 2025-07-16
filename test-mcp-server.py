#!/usr/bin/env python3
"""
Cross-platform test script for Database MCP Server
Supports Windows, macOS, and Linux
"""

import json
import subprocess
import sys
import os
import time
import platform
from typing import Dict, Any, Optional

class DatabaseMcpTester:
    def __init__(self):
        self.server_jar = "target/dbmcp-1.0.0.jar"
        # Use persistent H2 database for testing
        self.config = {
            "DB_URL": "jdbc:h2:./test-db/mcptest;AUTO_SERVER=TRUE",
            "DB_USER": "sa",
            "DB_PASSWORD": "",
            "DB_DRIVER": "org.h2.Driver"
        }
        self.test_results = []
        self.cleanup_required = False

    def print_header(self):
        """Print test header with system information"""
        print("=" * 60)
        print("Database MCP Server Test Script")
        print("=" * 60)
        print(f"Platform: {platform.system()} {platform.release()}")
        print(f"Python: {sys.version.split()[0]}")
        print(f"Java: {self.get_java_version()}")
        print("")

    def get_java_version(self) -> str:
        """Get Java version"""
        try:
            result = subprocess.run(['java', '-version'],
                                  capture_output=True, text=True, timeout=10)
            if result.stderr:
                # Java version info goes to stderr
                return result.stderr.split('\n')[0]
            return "Unknown"
        except Exception:
            return "Not found"

    def check_prerequisites(self) -> bool:
        """Check if all prerequisites are met"""
        print("Checking prerequisites...")

        # Check if JAR file exists
        if not os.path.exists(self.server_jar):
            print(f"❌ Error: Server JAR not found at {self.server_jar}")
            print("Please run 'mvn clean package' first")
            return False
        print(f"✅ Server JAR found: {self.server_jar}")

        # Check Java installation
        try:
            subprocess.run(['java', '-version'],
                         capture_output=True, timeout=10, check=True)
            print("✅ Java is installed and accessible")
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired, FileNotFoundError):
            print("❌ Java is not installed or not in PATH")
            return False

        print("")
        return True

    def print_config(self):
        """Print current configuration"""
        print("Configuration:")
        for key, value in self.config.items():
            # Mask password
            display_value = "***" if "PASSWORD" in key and value else value
            print(f"  {key}: {display_value}")
        print("Note: Using persistent H2 database for reliable testing")
        print("")

    def send_mcp_request(self, request: Dict[str, Any], timeout: int = 30) -> Optional[Dict[str, Any]]:
        """Send a single MCP request to the server"""
        try:
            # Set environment variables
            env = os.environ.copy()
            env.update(self.config)

            # Start the server process
            process = subprocess.Popen(
                ['java', '-jar', self.server_jar],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                env=env
            )

            # Send request and get response
            request_json = json.dumps(request)
            stdout, stderr = process.communicate(input=request_json, timeout=timeout)

            if stderr and "Starting Database MCP Server" not in stderr:
                print(f"Server stderr: {stderr}")

            if not stdout.strip():
                print("No response from server")
                return None

            # Parse the first line of response (should be JSON)
            response_line = stdout.strip().split('\n')[0]
            return json.loads(response_line)

        except subprocess.TimeoutExpired:
            print(f"Request timed out after {timeout} seconds")
            process.kill()
            return None
        except json.JSONDecodeError as e:
            print(f"Failed to parse JSON response: {e}")
            print(f"Raw response: {stdout}")
            return None
        except Exception as e:
            print(f"Error sending request: {e}")
            return None

    def run_test(self, test_name: str, request: Dict[str, Any],
                 expected_keys: list = None) -> bool:
        """Run a single test case"""
        print(f"=== {test_name} ===")
        print(f"Request: {json.dumps(request, indent=2)}")

        response = self.send_mcp_request(request)

        if response is None:
            print("❌ FAILED: No response received")
            self.test_results.append((test_name, False, "No response"))
            print("")
            return False

        print("Response:")
        print(json.dumps(response, indent=2))

        # Check for basic JSON-RPC structure
        success = True
        error_msg = ""

        if "jsonrpc" not in response:
            success = False
            error_msg = "Missing jsonrpc field"
        elif "error" in response:
            success = False
            error_msg = f"Server error: {response['error']}"
        elif "result" not in response:
            success = False
            error_msg = "Missing result field"
        elif expected_keys:
            result = response.get("result", {})
            for key in expected_keys:
                if key not in result:
                    success = False
                    error_msg = f"Missing expected key: {key} (found keys: {list(result.keys())})"
                    break

        status = "✅ PASSED" if success else "❌ FAILED"
        print(f"Status: {status}")
        if not success:
            print(f"Error: {error_msg}")

        # Track that we've created test data that needs cleanup
        if "CREATE TABLE test_table" in str(request):
            self.cleanup_required = True

        self.test_results.append((test_name, success, error_msg))
        print("")
        return success

    def cleanup_test_data(self):
        """Clean up test data and database files"""
        print("Cleaning up test data...")

        try:
            # Drop test table if it exists
            cleanup_request = {
                "jsonrpc": "2.0",
                "id": 999,
                "method": "tools/call",
                "params": {
                    "name": "query",
                    "arguments": {
                        "sql": "DROP TABLE IF EXISTS test_table"
                    }
                }
            }

            response = self.send_mcp_request(cleanup_request, timeout=10)
            if response:
                print("✅ Test table dropped successfully")
            else:
                print("⚠️  Could not drop test table (may not exist)")

        except Exception as e:
            print(f"⚠️  Error during database cleanup: {e}")

        # Clean up database files
        try:
            import glob
            db_files = glob.glob("test-db/mcptest*")
            for file_path in db_files:
                try:
                    os.remove(file_path)
                    print(f"✅ Removed database file: {file_path}")
                except Exception as e:
                    print(f"⚠️  Could not remove {file_path}: {e}")

            # Remove directory if empty
            try:
                os.rmdir("test-db")
                print("✅ Removed test-db directory")
            except OSError:
                pass  # Directory not empty or doesn't exist

        except Exception as e:
            print(f"⚠️  Error cleaning up database files: {e}")

        print("")

    def run_all_tests(self):
        """Run the complete test suite"""
        tests = [
            {
                "name": "Initialize Server",
                "request": {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "initialize",
                    "params": {
                        "protocolVersion": "2024-11-05",
                        "capabilities": {},
                        "clientInfo": {"name": "test-client", "version": "1.0.0"}
                    }
                },
                "expected_keys": ["protocolVersion", "capabilities", "serverInfo"]
            },
            {
                "name": "List Tools",
                "request": {
                    "jsonrpc": "2.0",
                    "id": 2,
                    "method": "tools/list",
                    "params": {}
                },
                "expected_keys": ["tools"]
            },
            {
                "name": "List Resources",
                "request": {
                    "jsonrpc": "2.0",
                    "id": 3,
                    "method": "resources/list",
                    "params": {}
                },
                "expected_keys": ["resources"]
            },
            {
                "name": "Read Database Info",
                "request": {
                    "jsonrpc": "2.0",
                    "id": 4,
                    "method": "resources/read",
                    "params": {"uri": "database://info"}
                },
                "expected_keys": ["contents"]
            },
            {
                "name": "Read Test Table Metadata",
                "request": {
                    "jsonrpc": "2.0",
                    "id": 9,
                    "method": "resources/read",
                    "params": {"uri": "database://table/TEST_TABLE"}
                },
                "expected_keys": ["contents"]
            },
            {
                "name": "Create Test Table",
                "request": {
                    "jsonrpc": "2.0",
                    "id": 5,
                    "method": "tools/call",
                    "params": {
                        "name": "query",
                        "arguments": {
                            "sql": "CREATE TABLE test_table (id INT PRIMARY KEY, name VARCHAR(50), created_date DATE)"
                        }
                    }
                },
                "expected_keys": ["content"]
            },
            {
                "name": "Insert Test Data",
                "request": {
                    "jsonrpc": "2.0",
                    "id": 6,
                    "method": "tools/call",
                    "params": {
                        "name": "query",
                        "arguments": {
                            "sql": "INSERT INTO test_table VALUES (1, 'John Doe', '2024-01-01'), (2, 'Jane Smith', '2024-01-02')"
                        }
                    }
                },
                "expected_keys": ["content"]
            },
            {
                "name": "Query Test Data",
                "request": {
                    "jsonrpc": "2.0",
                    "id": 7,
                    "method": "tools/call",
                    "params": {
                        "name": "query",
                        "arguments": {
                            "sql": "SELECT * FROM test_table ORDER BY id"
                        }
                    }
                },
                "expected_keys": ["content"]
            },
            {
                "name": "Read Test Table Metadata",
                "request": {
                    "jsonrpc": "2.0",
                    "id": 9,
                    "method": "resources/read",
                    "params": {"uri": "database://table/TEST_TABLE"}
                },
                "expected_keys": ["contents"]
            }
        ]

        print("Running test suite...")
        print("")

        for test in tests:
            self.run_test(
                test["name"],
                test["request"],
                test.get("expected_keys")
            )
            # Small delay between tests
            time.sleep(0.5)

    def print_summary(self):
        """Print test summary"""
        print("=" * 60)
        print("TEST SUMMARY")
        print("=" * 60)

        passed = sum(1 for _, success, _ in self.test_results if success)
        total = len(self.test_results)

        print(f"Total tests: {total}")
        print(f"Passed: {passed}")
        print(f"Failed: {total - passed}")
        print("")

        if total - passed > 0:
            print("Failed tests:")
            for name, success, error in self.test_results:
                if not success:
                    print(f"  ❌ {name}: {error}")
            print("")

        if passed == total:
            print("🎉 All tests passed!")
        else:
            print(f"⚠️  {total - passed} test(s) failed")

        print("")
        print("To run the server interactively:")
        print(f"java -jar {self.server_jar}")
        print("")
        print("Then send JSON-RPC requests via stdin, for example:")
        print('{"jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {}}')

    def run(self):
        """Main test runner"""
        self.print_header()

        if not self.check_prerequisites():
            sys.exit(1)

        self.print_config()

        try:
            # Create test-db directory if it doesn't exist
            os.makedirs("test-db", exist_ok=True)

            self.run_all_tests()
        except KeyboardInterrupt:
            print("\n\nTest interrupted by user")
        except Exception as e:
            print(f"\n\nUnexpected error during testing: {e}")
        finally:
            # Always cleanup, regardless of how tests ended
            if self.cleanup_required:
                self.cleanup_test_data()
            self.print_summary()

def main():
    """Main entry point"""
    # Allow configuration via environment variables
    tester = DatabaseMcpTester()

    # Override default config with environment variables if present
    for key in tester.config.keys():
        env_value = os.environ.get(key)
        if env_value is not None:
            tester.config[key] = env_value

    # Run the tests
    tester.run()

if __name__ == "__main__":
    main()