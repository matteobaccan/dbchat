#!/usr/bin/env python3
"""
Cross-platform test script for Database MCP Server
Supports Windows, macOS, and Linux
Follows proper MCP initialization lifecycle
Run a DBChat server with a file based H2 database and execute a basic MCP lifecycle
"""

import os
import sys
import time
import glob
import json
import platform
import subprocess
from typing import Dict, Any, Optional, List

class DatabaseMcpTester:
    def __init__(self):
        jars = glob.glob("target/dbchat-*.jar")
        if not jars:
            print("❌ Error: No dbchat JAR file found in target/. Please run 'mvn clean package'.")
            sys.exit(1)
        self.server_jar = jars[0]  # Use the first matching file
        # Use persistent H2 database for testing
        self.config = {
            "DB_URL": "jdbc:h2:file:./test-db/mcptest;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1;CACHE_SIZE=65536",
            "DB_USER": "sa",
            "DB_PASSWORD": "",
            "DB_DRIVER": "org.h2.Driver",
            "SELECT_ONLY": "False"
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

    def send_mcp_requests_session(self, requests: List[Dict[str, Any]], timeout: int = 30) -> List[Optional[Dict[str, Any]]]:
        """Send multiple MCP requests to a single server instance with proper session handling"""
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

            responses = []

            # Send each request individually and read responses
            for i, request in enumerate(requests):
                request_json = json.dumps(request) + "\n"
                process.stdin.write(request_json)
                process.stdin.flush()

                # Handle notifications (no id field) - expect no response
                if "id" not in request:
                    responses.append(None)
                    continue

                # Read response for requests with id
                try:
                    # Read one line of response
                    response_line = process.stdout.readline()
                    if response_line.strip():
                        response = json.loads(response_line.strip())
                        responses.append(response)
                    else:
                        responses.append(None)
                except json.JSONDecodeError as e:
                    print(f"Failed to parse JSON response for request {i+1}: {e}")
                    print(f"Raw response: {response_line}")
                    responses.append(None)
                except Exception as e:
                    print(f"Error reading response for request {i+1}: {e}")
                    responses.append(None)

            # Close stdin and wait for process to finish
            process.stdin.close()

            # Wait for process to complete
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()

            return responses

        except subprocess.TimeoutExpired:
            print(f"Session request timed out after {timeout} seconds")
            if process:
                process.kill()
            return [None] * len(requests)
        except Exception as e:
            print(f"Error in session request: {e}")
            return [None] * len(requests)

    def send_mcp_request(self, request: Dict[str, Any], timeout: int = 30) -> Optional[Dict[str, Any]]:
        """Send a single MCP request to the server"""
        responses = self.send_mcp_requests_session([request], timeout)
        return responses[0] if responses else None

    def validate_response(self, response: Dict[str, Any], expected_id: Any = None,
                         expected_keys: List[str] = None, allow_error: bool = False) -> tuple[bool, str]:
        """Validate MCP response format and content"""
        if not response:
            return False, "No response received"

        # Check JSON-RPC 2.0 format
        if response.get("jsonrpc") != "2.0":
            return False, "Invalid or missing jsonrpc field"

        # Check ID matches if expected
        if expected_id is not None:
            actual_id = response.get("id")
            if actual_id != expected_id:
                return False, f"ID mismatch: expected {expected_id}, got {actual_id}"

        # Check for result or error
        has_result = "result" in response
        has_error = "error" in response

        if not has_result and not has_error:
            return False, "Response missing both result and error fields"

        if has_result and has_error:
            return False, "Response has both result and error fields"

        # Handle error responses
        if has_error:
            if not allow_error:
                error_info = response["error"]
                if isinstance(error_info, dict):
                    return False, f"Server error: {error_info.get('message', 'Unknown error')}"
                else:
                    return False, f"Server error: {error_info}"
            else:
                return True, ""  # Error was expected

        # Validate expected keys in result
        if expected_keys and has_result:
            result = response["result"]
            for key in expected_keys:
                if key not in result:
                    return False, f"Missing expected key in result: {key}"

        return True, ""

    def run_test(self, test_name: str, request: Dict[str, Any],
                 expected_keys: list = None, allow_error: bool = False) -> bool:
        """Run a single test case"""
        print(f"=== {test_name} ===")
        print(f"Request: {json.dumps(request, indent=2)}")

        response = self.send_mcp_request(request)

        if response is None and "id" not in request:
            # This is a notification - no response expected
            print("✅ PASSED (notification - no response expected)")
            self.test_results.append((test_name, True, ""))
            print("")
            return True

        if response:
            print("Response:")
            print(json.dumps(response, indent=2))

        expected_id = request.get("id")
        success, error_msg = self.validate_response(response, expected_id, expected_keys, allow_error)

        status = "✅ PASSED" if success else "❌ FAILED"
        print(f"Status: {status}")
        if not success:
            print(f"Error: {error_msg}")

        self.test_results.append((test_name, success, error_msg))
        print("")
        return success

    def run_session_test(self, test_name: str, requests: List[Dict[str, Any]],
                        expected_keys_list: List[List[str]] = None,
                        allow_errors: List[bool] = None) -> bool:
        """Run multiple requests in a single server session"""
        print(f"=== {test_name} ===")
        print(f"Sending {len(requests)} requests in session...")

        responses = self.send_mcp_requests_session(requests)

        overall_success = True
        error_messages = []

        for i, (request, response) in enumerate(zip(requests, responses)):
            print(f"\n--- Request {i+1} ---")
            print(f"Request: {json.dumps(request, indent=2)}")

            # Handle notifications
            if "id" not in request:
                if response is None:
                    print("✅ PASSED (notification - no response expected)")
                    continue
                else:
                    print("❌ FAILED (notification should not have response)")
                    overall_success = False
                    error_messages.append(f"Request {i+1}: Unexpected response for notification")
                    continue

            if response:
                print("Response:")
                print(json.dumps(response, indent=2))

            # Get expected keys and error allowance for this request
            expected_keys = expected_keys_list[i] if expected_keys_list and i < len(expected_keys_list) else []
            allow_error = allow_errors[i] if allow_errors and i < len(allow_errors) else False

            expected_id = request.get("id")
            success, error_msg = self.validate_response(response, expected_id, expected_keys, allow_error)

            status = "✅ PASSED" if success else "❌ FAILED"
            print(f"Status: {status}")
            if not success:
                print(f"Error: {error_msg}")
                overall_success = False
                error_messages.append(f"Request {i+1}: {error_msg}")

        final_status = "✅ PASSED" if overall_success else "❌ FAILED"
        print(f"\nOverall Status: {final_status}")

        final_error = "; ".join(error_messages) if error_messages else ""
        self.test_results.append((test_name, overall_success, final_error))
        print("")
        return overall_success

    def cleanup_test_data(self):
        """Clean up test data and database files"""
        print("Cleaning up test data...")

        try:
            # Clean up database files
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
        print("Running test suite...")
        print("")

        # Clean any existing database files first
        try:
            import glob
            db_files = glob.glob("test-db/mcptest*")
            for file_path in db_files:
                try:
                    os.remove(file_path)
                except Exception:
                    pass
        except Exception:
            pass

        # Create test-db directory
        os.makedirs("test-db", exist_ok=True)

        # Test 1: Proper MCP Lifecycle in a single session
        lifecycle_requests = [
            # Step 1: Initialize
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "initialize",
                "params": {
                    "protocolVersion": "2025-06-18",  # Updated protocol version
                    "capabilities": {
                        "tools": {},
                        "resources": {}
                    },
                    "clientInfo": {"name": "test-client", "version": "1.0.0"}
                }
            },
            # Step 2: Send initialized notification
            {
                "jsonrpc": "2.0",
                "method": "notifications/initialized"
            },
            # Step 3: Now we can use other methods
            {
                "jsonrpc": "2.0",
                "id": 3,
                "method": "tools/list",
                "params": {}
            },
            {
                "jsonrpc": "2.0",
                "id": 4,
                "method": "resources/list",
                "params": {}
            },
            {
                "jsonrpc": "2.0",
                "id": 5,
                "method": "resources/read",
                "params": {"uri": "database://info"}
            }
        ]

        lifecycle_expected_keys = [
            ["protocolVersion", "capabilities", "serverInfo"],  # initialize
            [],  # notification (no response)
            ["tools"],  # tools/list
            ["resources"],  # resources/list
            ["contents"]  # resources/read
        ]

        self.run_session_test(
            "MCP Lifecycle and Basic Operations",
            lifecycle_requests,
            lifecycle_expected_keys
        )

        # Test 2: Database operations in a single session
        db_requests = [
            # Initialize first
            {
                "jsonrpc": "2.0",
                "id": 10,
                "method": "initialize",
                "params": {
                    "protocolVersion": "2025-06-18",
                    "capabilities": {
                        "tools": {},
                        "resources": {}
                    },
                    "clientInfo": {"name": "test-client", "version": "1.0.0"}
                }
            },
            # Send initialized notification
            {
                "jsonrpc": "2.0",
                "method": "notifications/initialized"
            },
            # Create table
            {
                "jsonrpc": "2.0",
                "id": 12,
                "method": "tools/call",
                "params": {
                    "name": "run_sql",
                    "arguments": {
                        "sql": "CREATE TABLE test_table (id INT PRIMARY KEY, name VARCHAR(50), created_date DATE)"
                    }
                }
            },
            # Insert data
            {
                "jsonrpc": "2.0",
                "id": 13,
                "method": "tools/call",
                "params": {
                    "name": "run_sql",
                    "arguments": {
                        "sql": "INSERT INTO test_table VALUES (1, 'John Doe', '2024-01-01'), (2, 'Jane Smith', '2024-01-02')"
                    }
                }
            },
            # Select data
            {
                "jsonrpc": "2.0",
                "id": 14,
                "method": "tools/call",
                "params": {
                    "name": "run_sql",
                    "arguments": {
                        "sql": "SELECT * FROM test_table ORDER BY id"
                    }
                }
            },
            # Describe table (different tool call)
            {
                "jsonrpc": "2.0",
                "id": 15,
                "method": "tools/call",
                "params": {
                    "name": "describe_table",
                    "arguments": {
                        "table_name": "test_table"
                    }
                }
            },
            # Read table metadata
            {
                "jsonrpc": "2.0",
                "id": 16,
                "method": "resources/read",
                "params": {"uri": "database://table/TEST_TABLE"}  # Use uppercase as H2 stores it
            }
        ]

        db_expected_keys = [
            ["protocolVersion", "capabilities", "serverInfo"],  # Req 1: initialize
            [],                                                 # Req 2: notification (no response)
            ["content"],                                        # Req 3: run_sql (CREATE)
            ["content"],                                        # Req 4: run_sql (INSERT)
            ["content"],                                        # Req 5: run_sql (SELECT)
            ["content"],                                        # Req 6: describe_table call
            ["contents"]                                        # Req 7: resources/read call
        ]

        self.run_session_test(
            "Database Operations (Single Session)",
            db_requests,
            db_expected_keys
        )

        # Test 3: Error handling test
        error_requests = [
            # Initialize first
            {
                "jsonrpc": "2.0",
                "id": 20,
                "method": "initialize",
                "params": {
                    "protocolVersion": "2025-06-18",
                    "capabilities": {
                        "tools": {},
                        "resources": {}
                    },
                    "clientInfo": {"name": "test-client", "version": "1.0.0"}
                }
            },
            # Send initialized notification
            {
                "jsonrpc": "2.0",
                "method": "notifications/initialized"
            },
            # Try to read non-existent table
            {
                "jsonrpc": "2.0",
                "id": 22,
                "method": "resources/read",
                "params": {"uri": "database://table/nonexistent_table"}
            }
        ]

        error_expected_keys = [
            ["protocolVersion", "capabilities", "serverInfo"],  # initialize
            [],  # notification
            []   # error case - no expected keys
        ]

        error_allow_list = [False, False, True]  # Only allow error for the third request

        self.run_session_test(
            "Error Handling Test",
            error_requests,
            error_expected_keys,
            error_allow_list
        )

        self.cleanup_required = True

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
        print("Example MCP session:")
        print('{"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {"protocolVersion": "2025-03-26", "capabilities": {}, "clientInfo": {"name": "client", "version": "1.0"}}}')
        print('{"jsonrpc": "2.0", "method": "notifications/initialized"}')
        print('{"jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {}}')

    def run(self):
        """Main test runner"""
        self.print_header()

        if not self.check_prerequisites():
            sys.exit(1)

        self.print_config()

        try:
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
    