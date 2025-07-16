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
from typing import Dict, Any, Optional, List

class DatabaseMcpTester:
    def __init__(self):
        self.server_jar = "target/dbmcp-1.0.0.jar"
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

    def send_mcp_requests_batch(self, requests: List[Dict[str, Any]], timeout: int = 30) -> List[Optional[Dict[str, Any]]]:
        """Send multiple MCP requests to a single server instance"""
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

            # Send all requests as separate lines
            input_data = ""
            for request in requests:
                input_data += json.dumps(request) + "\n"

            stdout, stderr = process.communicate(input=input_data, timeout=timeout)

            if stderr and "Starting Database MCP Server" not in stderr:
                print(f"Server stderr: {stderr}")

            if not stdout.strip():
                print("No response from server")
                return [None] * len(requests)

            # Parse each line of response
            response_lines = stdout.strip().split('\n')
            responses = []

            for i, line in enumerate(response_lines):
                if line.strip():
                    try:
                        responses.append(json.loads(line.strip()))
                    except json.JSONDecodeError as e:
                        print(f"Failed to parse JSON response line {i+1}: {e}")
                        print(f"Raw line: {line}")
                        responses.append(None)
                else:
                    responses.append(None)

            # Pad with None if we got fewer responses than requests
            while len(responses) < len(requests):
                responses.append(None)

            return responses

        except subprocess.TimeoutExpired:
            print(f"Batch request timed out after {timeout} seconds")
            process.kill()
            return [None] * len(requests)
        except Exception as e:
            print(f"Error sending batch request: {e}")
            return [None] * len(requests)

    def send_mcp_request(self, request: Dict[str, Any], timeout: int = 30) -> Optional[Dict[str, Any]]:
        """Send a single MCP request to the server"""
        responses = self.send_mcp_requests_batch([request], timeout)
        return responses[0] if responses else None

    def run_test(self, test_name: str, request: Dict[str, Any],
                 expected_keys: list = None, allow_error: bool = False) -> bool:
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
        elif "error" in response and not allow_error:
            success = False
            error_msg = f"Server error: {response['error']}"
        elif "result" not in response and not allow_error:
            success = False
            error_msg = "Missing result field"
        elif expected_keys and "result" in response:
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

        self.test_results.append((test_name, success, error_msg))
        print("")
        return success

    def run_batch_test(self, test_name: str, requests: List[Dict[str, Any]],
                      expected_keys_list: List[List[str]] = None,
                      allow_errors: List[bool] = None) -> bool:
        """Run multiple requests in a single server session"""
        print(f"=== {test_name} ===")
        print(f"Sending {len(requests)} requests in batch...")

        responses = self.send_mcp_requests_batch(requests)

        if not responses or all(r is None for r in responses):
            print("❌ FAILED: No responses received")
            self.test_results.append((test_name, False, "No responses"))
            print("")
            return False

        overall_success = True
        error_messages = []

        for i, (request, response) in enumerate(zip(requests, responses)):
            print(f"\n--- Request {i+1} ---")
            print(f"Request: {json.dumps(request, indent=2)}")

            if response is None:
                print("❌ No response")
                overall_success = False
                error_messages.append(f"Request {i+1}: No response")
                continue

            print("Response:")
            print(json.dumps(response, indent=2))

            # Get expected keys and error allowance for this request
            expected_keys = expected_keys_list[i] if expected_keys_list and i < len(expected_keys_list) else []
            allow_error = allow_errors[i] if allow_errors and i < len(allow_errors) else False

            # Check response
            success = True
            error_msg = ""

            if "jsonrpc" not in response:
                success = False
                error_msg = "Missing jsonrpc field"
            elif "error" in response and not allow_error:
                success = False
                error_msg = f"Server error: {response['error']}"
            elif "result" not in response and not allow_error:
                success = False
                error_msg = "Missing result field"
            elif expected_keys and "result" in response:
                result = response.get("result", {})
                for key in expected_keys:
                    if key not in result:
                        success = False
                        error_msg = f"Missing expected key: {key}"
                        break

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

        # Test 1: Basic MCP protocol tests (single requests)
        basic_tests = [
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
            }
        ]

        for test in basic_tests:
            self.run_test(
                test["name"],
                test["request"],
                test.get("expected_keys")
            )
            time.sleep(0.5)

        # Test 2: Database operations in a single session
        db_requests = [
            {
                "jsonrpc": "2.0",
                "id": 10,
                "method": "tools/call",
                "params": {
                    "name": "query",
                    "arguments": {
                        "sql": "CREATE TABLE test_table (id INT PRIMARY KEY, name VARCHAR(50), created_date DATE)"
                    }
                }
            },
            {
                "jsonrpc": "2.0",
                "id": 11,
                "method": "tools/call",
                "params": {
                    "name": "query",
                    "arguments": {
                        "sql": "INSERT INTO test_table VALUES (1, 'John Doe', '2024-01-01'), (2, 'Jane Smith', '2024-01-02')"
                    }
                }
            },
            {
                "jsonrpc": "2.0",
                "id": 12,
                "method": "tools/call",
                "params": {
                    "name": "query",
                    "arguments": {
                        "sql": "SELECT * FROM test_table ORDER BY id"
                    }
                }
            },
            {
                "jsonrpc": "2.0",
                "id": 13,
                "method": "resources/read",
                "params": {"uri": "database://table/TEST_TABLE"}  # Use uppercase as H2 stores it
            }
        ]

        db_expected_keys = [
            ["content"],    # CREATE TABLE
            ["content"],    # INSERT
            ["content"],    # SELECT
            ["contents"]    # READ TABLE METADATA
        ]

        self.run_batch_test(
            "Database Operations (Single Session)",
            db_requests,
            db_expected_keys
        )

        # Test 3: Error handling test
        error_test = {
            "name": "Test Non-existent Table (Should Fail)",
            "request": {
                "jsonrpc": "2.0",
                "id": 20,
                "method": "resources/read",
                "params": {"uri": "database://table/nonexistent_table"}
            },
            "allow_error": True
        }

        self.run_test(
            error_test["name"],
            error_test["request"],
            [],
            error_test.get("allow_error", False)
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
        print("Then send JSON-RPC requests via stdin, for example:")
        print('{"jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {}}')

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