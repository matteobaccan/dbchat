#!/usr/bin/env python3
"""
MCP Protocol Test Script for Windows
Tests the Database MCP Server for protocol compliance and validates responses
"""

import json
import subprocess
import sys
import time
import os
from typing import Dict, Any, List, Optional
from dataclasses import dataclass
from enum import Enum

class TestResult(Enum):
    PASS = "PASS"
    FAIL = "FAIL"
    ERROR = "ERROR"

@dataclass
class TestCase:
    name: str
    request: Dict[str, Any]
    expected_fields: List[str]
    result: TestResult = TestResult.ERROR
    response: Optional[Dict[str, Any]] = None
    error_message: Optional[str] = None
    execution_time: float = 0.0

class MCPTester:
    def __init__(self, jar_path: str, java_path: str = "java"):
        self.jar_path = jar_path
        self.java_path = java_path
        self.test_cases = []
        self.setup_environment()

    def setup_environment(self):
        """Setup environment variables for H2 database"""
        os.environ['DB_URL'] = 'jdbc:h2:mem:testdb'
        os.environ['DB_USER'] = 'sa'
        os.environ['DB_PASSWORD'] = ''
        os.environ['DB_DRIVER'] = 'org.h2.Driver'

    def add_test_case(self, name: str, request: Dict[str, Any], expected_fields: List[str]):
        """Add a test case to the test suite"""
        test_case = TestCase(name=name, request=request, expected_fields=expected_fields)
        self.test_cases.append(test_case)

    def create_standard_test_cases(self):
        """Create standard MCP protocol test cases"""

        # Test 1: Initialize
        self.add_test_case(
            name="Initialize Protocol",
            request={
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
            },
            expected_fields=["jsonrpc", "id", "result"]
        )

        # Test 2: Initialize WITHOUT ID (notification style - this is the key test for your bug)
        self.add_test_case(
            name="Initialize Protocol (No ID - Notification Style)",
            request={
                "jsonrpc": "2.0",
                "method": "initialize",
                "params": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {},
                    "clientInfo": {
                        "name": "test-client",
                        "version": "1.0.0"
                    }
                }
            },
            expected_fields=["jsonrpc", "result"]  # Note: NO "id" expected in response
        )

        # Test 3: List Tools
        self.add_test_case(
            name="List Tools",
            request={
                "jsonrpc": "2.0",
                "id": 2,
                "method": "tools/list",
                "params": {}
            },
            expected_fields=["jsonrpc", "id", "result", "result.tools"]
        )

        # Test 4: List Tools WITHOUT ID (notification style)
        self.add_test_case(
            name="List Tools (No ID - Notification Style)",
            request={
                "jsonrpc": "2.0",
                "method": "tools/list",
                "params": {}
            },
            expected_fields=["jsonrpc", "result", "result.tools"]  # Note: NO "id" expected
        )

        # Test 5: List Resources
        self.add_test_case(
            name="List Resources",
            request={
                "jsonrpc": "2.0",
                "id": 3,
                "method": "resources/list",
                "params": {}
            },
            expected_fields=["jsonrpc", "id", "result", "result.resources"]
        )

        # Test 6: Call Query Tool
        self.add_test_case(
            name="Call Query Tool",
            request={
                "jsonrpc": "2.0",
                "id": 4,
                "method": "tools/call",
                "params": {
                    "name": "query",
                    "arguments": {
                        "sql": "SELECT 1 as test_value, 'Hello' as message",
                        "maxRows": 10
                    }
                }
            },
            expected_fields=["jsonrpc", "id", "result", "result.content"]
        )

        # Test 7: Call Query Tool WITHOUT ID (notification style)
        self.add_test_case(
            name="Call Query Tool (No ID - Notification Style)",
            request={
                "jsonrpc": "2.0",
                "method": "tools/call",
                "params": {
                    "name": "query",
                    "arguments": {
                        "sql": "SELECT 1 as test_value, 'Hello' as message",
                        "maxRows": 10
                    }
                }
            },
            expected_fields=["jsonrpc", "result", "result.content"]  # Note: NO "id" expected
        )

        # Test 8: Invalid Method (should return error)
        self.add_test_case(
            name="Invalid Method (Error Test)",
            request={
                "jsonrpc": "2.0",
                "id": 5,
                "method": "invalid/method",
                "params": {}
            },
            expected_fields=["jsonrpc", "id", "error", "error.code", "error.message"]
        )

        # Test 9: Invalid Method WITHOUT ID (notification style error)
        self.add_test_case(
            name="Invalid Method (No ID - Notification Style Error)",
            request={
                "jsonrpc": "2.0",
                "method": "invalid/method",
                "params": {}
            },
            expected_fields=["jsonrpc", "error", "error.code", "error.message"]  # Note: NO "id" expected
        )

        # Test 10: Call Tool with Empty SQL (should return error)
        self.add_test_case(
            name="Empty SQL Query (Error Test)",
            request={
                "jsonrpc": "2.0",
                "id": 6,
                "method": "tools/call",
                "params": {
                    "name": "query",
                    "arguments": {
                        "sql": "",
                        "maxRows": 10
                    }
                }
            },
            expected_fields=["jsonrpc", "id", "error", "error.code", "error.message"]
        )

        # Test 11: Read Database Info Resource
        self.add_test_case(
            name="Read Database Info Resource",
            request={
                "jsonrpc": "2.0",
                "id": 7,
                "method": "resources/read",
                "params": {
                    "uri": "database://info"
                }
            },
            expected_fields=["jsonrpc", "id", "result", "result.contents"]
        )

        # Test 12: Read Database Info Resource WITHOUT ID
        self.add_test_case(
            name="Read Database Info Resource (No ID - Notification Style)",
            request={
                "jsonrpc": "2.0",
                "method": "resources/read",
                "params": {
                    "uri": "database://info"
                }
            },
            expected_fields=["jsonrpc", "result", "result.contents"]  # Note: NO "id" expected
        )

    def run_mcp_server(self, input_data: str) -> tuple[str, str, int]:
        """Run the MCP server with input data and return stdout, stderr, return_code"""
        try:
            cmd = [self.java_path, "-jar", self.jar_path]

            process = subprocess.Popen(
                cmd,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                env=os.environ.copy()
            )

            stdout, stderr = process.communicate(input=input_data, timeout=30)
            return stdout, stderr, process.returncode

        except subprocess.TimeoutExpired:
            process.kill()
            return "", "Process timed out after 30 seconds", 1
        except Exception as e:
            return "", f"Failed to run process: {str(e)}", 1

    def validate_json_rpc(self, response: Dict[str, Any], expect_id: bool = True) -> List[str]:
        """Validate JSON-RPC 2.0 compliance"""
        errors = []

        # Check required fields
        if response.get("jsonrpc") != "2.0":
            errors.append("Missing or invalid 'jsonrpc' field")

        # Check ID field based on expectation
        has_id = "id" in response
        if expect_id and not has_id:
            errors.append("Missing 'id' field")
        elif not expect_id and has_id:
            errors.append("Unexpected 'id' field in notification response")

        # Validate ID field if present
        if has_id:
            id_value = response.get("id")
            if id_value is None:
                errors.append("ID field cannot be null")
            elif isinstance(id_value, str) and id_value == "":
                errors.append("ID field cannot be empty string")

        # Must have either result or error, but not both
        has_result = "result" in response
        has_error = "error" in response

        if not has_result and not has_error:
            errors.append("Response must have either 'result' or 'error'")
        elif has_result and has_error:
            errors.append("Response cannot have both 'result' and 'error'")

        # Validate error structure if present
        if has_error:
            error = response["error"]
            if not isinstance(error, dict):
                errors.append("Error field must be an object")
            else:
                if "code" not in error:
                    errors.append("Error object missing 'code' field")
                elif not isinstance(error["code"], int):
                    errors.append("Error code must be an integer")

                if "message" not in error:
                    errors.append("Error object missing 'message' field")
                elif not isinstance(error["message"], str):
                    errors.append("Error message must be a string")

        return errors

    def validate_expected_fields(self, response: Dict[str, Any], expected_fields: List[str]) -> List[str]:
        """Validate that expected fields are present in the response"""
        errors = []

        for field_path in expected_fields:
            current = response
            parts = field_path.split('.')

            for part in parts:
                if isinstance(current, dict) and part in current:
                    current = current[part]
                else:
                    errors.append(f"Missing expected field: {field_path}")
                    break

        return errors

    def validate_mcp_specific(self, test_case: TestCase, response: Dict[str, Any]) -> List[str]:
        """Validate MCP-specific requirements"""
        errors = []

        if test_case.name.startswith("Initialize Protocol") and "result" in response:
            result = response["result"]

            # Check required initialize response fields
            required_fields = ["protocolVersion", "capabilities", "serverInfo"]
            for field in required_fields:
                if field not in result:
                    errors.append(f"Initialize response missing '{field}'")

            # Validate capabilities structure
            if "capabilities" in result:
                caps = result["capabilities"]
                if not isinstance(caps, dict):
                    errors.append("capabilities must be an object")

        elif test_case.name.startswith("List Tools") and "result" in response:
            result = response["result"]

            if "tools" not in result:
                errors.append("tools/list response missing 'tools' array")
            elif not isinstance(result["tools"], list):
                errors.append("'tools' must be an array")
            else:
                # Validate each tool
                for i, tool in enumerate(result["tools"]):
                    if not isinstance(tool, dict):
                        errors.append(f"Tool {i} must be an object")
                        continue

                    required_tool_fields = ["name", "description"]
                    for field in required_tool_fields:
                        if field not in tool:
                            errors.append(f"Tool {i} missing '{field}'")

        elif test_case.name.startswith("Call Query Tool") and "result" in response:
            result = response["result"]

            if "content" not in result:
                errors.append("tools/call response missing 'content'")
            elif not isinstance(result["content"], list):
                errors.append("'content' must be an array")

        return errors

    def run_test_case(self, test_case: TestCase) -> TestCase:
        """Run a single test case and validate the response"""
        start_time = time.time()

        try:
            # Prepare input
            input_line = json.dumps(test_case.request) + "\n"

            # Run server
            stdout, stderr, return_code = self.run_mcp_server(input_line)

            test_case.execution_time = time.time() - start_time

            # Check if server started successfully
            if return_code != 0:
                test_case.result = TestResult.ERROR
                test_case.error_message = f"Server failed to start: {stderr}"
                return test_case

            # Parse response
            lines = stdout.strip().split('\n')
            if not lines or not lines[0].strip():
                test_case.result = TestResult.ERROR
                test_case.error_message = "No response from server"
                return test_case

            try:
                response_line = lines[0].strip()
                response = json.loads(response_line)
                test_case.response = response
            except json.JSONDecodeError as e:
                test_case.result = TestResult.ERROR
                test_case.error_message = f"Invalid JSON response: {str(e)}"
                return test_case

            # Validate response
            errors = []

            # Determine if we expect an ID based on the test case
            expect_id = "id" in test_case.request

            # JSON-RPC validation (with ID expectation)
            errors.extend(self.validate_json_rpc(response, expect_id))

            # Expected fields validation
            errors.extend(self.validate_expected_fields(response, test_case.expected_fields))

            # MCP-specific validation
            errors.extend(self.validate_mcp_specific(test_case, response))

            # Additional validation: Check for undefined/null ID values
            if "id" in response:
                id_value = response["id"]
                if id_value is None:
                    errors.append("Response ID cannot be null")
                elif isinstance(id_value, str) and id_value.strip() == "":
                    errors.append("Response ID cannot be empty string")

            # Set result
            if errors:
                test_case.result = TestResult.FAIL
                test_case.error_message = "; ".join(errors)
            else:
                test_case.result = TestResult.PASS

        except Exception as e:
            test_case.result = TestResult.ERROR
            test_case.error_message = f"Unexpected error: {str(e)}"
            test_case.execution_time = time.time() - start_time

        return test_case

    def run_all_tests(self) -> Dict[str, Any]:
        """Run all test cases and return results summary"""
        if not self.test_cases:
            self.create_standard_test_cases()

        print("MCP Protocol Test Suite")
        print("=" * 50)
        print(f"Java Path: {self.java_path}")
        print(f"JAR Path: {self.jar_path}")
        print(f"Database: {os.environ.get('DB_URL', 'Not set')}")
        print()

        results = {
            "total": len(self.test_cases),
            "passed": 0,
            "failed": 0,
            "errors": 0,
            "test_cases": []
        }

        for i, test_case in enumerate(self.test_cases, 1):
            print(f"Test {i}/{len(self.test_cases)}: {test_case.name}...")

            result = self.run_test_case(test_case)
            results["test_cases"].append(result)

            # Update counters
            if result.result == TestResult.PASS:
                results["passed"] += 1
                print(f"  ✅ PASS ({result.execution_time:.2f}s)")
            elif result.result == TestResult.FAIL:
                results["failed"] += 1
                print(f"  ❌ FAIL ({result.execution_time:.2f}s)")
                print(f"     Error: {result.error_message}")
            else:
                results["errors"] += 1
                print(f"  🔥 ERROR ({result.execution_time:.2f}s)")
                print(f"     Error: {result.error_message}")

            # Show response summary for successful tests
            if result.result == TestResult.PASS and result.response:
                if "result" in result.response:
                    print(f"     Response: Success")
                elif "error" in result.response:
                    error = result.response["error"]
                    print(f"     Response: Error {error.get('code', 'unknown')} - {error.get('message', 'no message')}")

            # Show ID handling for debugging
            if result.response:
                has_id = "id" in result.response
                expects_id = "id" in test_case.request
                if expects_id and has_id:
                    print(f"     ID: {result.response['id']} (expected)")
                elif not expects_id and not has_id:
                    print(f"     ID: None (expected for notification)")
                elif expects_id and not has_id:
                    print(f"     ID: Missing (ERROR - expected)")
                elif not expects_id and has_id:
                    print(f"     ID: {result.response['id']} (ERROR - unexpected)")

            print()

        return results

    def print_summary(self, results: Dict[str, Any]):
        """Print test results summary"""
        print("Test Results Summary")
        print("=" * 50)
        print(f"Total Tests: {results['total']}")
        print(f"✅ Passed: {results['passed']}")
        print(f"❌ Failed: {results['failed']}")
        print(f"🔥 Errors: {results['errors']}")

        success_rate = (results['passed'] / results['total']) * 100 if results['total'] > 0 else 0
        print(f"Success Rate: {success_rate:.1f}%")

        if results['failed'] > 0 or results['errors'] > 0:
            print("\nFailed/Error Test Details:")
            print("-" * 30)
            for test_case in results['test_cases']:
                if test_case.result != TestResult.PASS:
                    print(f"❌ {test_case.name}: {test_case.error_message}")

        # Show specific information about ID handling tests
        print("\nID Handling Test Results:")
        print("-" * 30)
        for test_case in results['test_cases']:
            if "No ID" in test_case.name:
                status = "✅" if test_case.result == TestResult.PASS else "❌"
                print(f"{status} {test_case.name}")
                if test_case.response and "id" in test_case.response:
                    print(f"    WARNING: Unexpected ID in response: {test_case.response['id']}")

        print()

    def save_detailed_report(self, results: Dict[str, Any], filename: str = "mcp_test_report.json"):
        """Save detailed test results to JSON file"""
        # Convert test cases to serializable format
        serializable_results = {
            "total": results["total"],
            "passed": results["passed"],
            "failed": results["failed"],
            "errors": results["errors"],
            "test_cases": []
        }

        for test_case in results["test_cases"]:
            serializable_results["test_cases"].append({
                "name": test_case.name,
                "result": test_case.result.value,
                "execution_time": test_case.execution_time,
                "error_message": test_case.error_message,
                "request": test_case.request,
                "response": test_case.response,
                "expected_fields": test_case.expected_fields
            })

        with open(filename, 'w', encoding='utf-8') as f:
            json.dump(serializable_results, f, indent=2, ensure_ascii=False)

        print(f"Detailed report saved to: {filename}")

def main():
    """Main function"""
    if len(sys.argv) < 2:
        print("Usage: python test-mcp-protocol.py <path_to_jar> [java_path]")
        print("Example: python test-mcp-protocol.py C:/Users/skanga/IdeaProjects/dbmcp/target/dbmcp-1.0.0.jar")
        print("Example: python test-mcp-protocol.py C:/Users/skanga/IdeaProjects/dbmcp/target/dbmcp-1.0.0.jar c:/java/jdk-17.0.13/bin/java.exe")
        sys.exit(1)

    jar_path = sys.argv[1]
    java_path = sys.argv[2] if len(sys.argv) > 2 else "java"

    # Check if JAR file exists
    if not os.path.exists(jar_path):
        print(f"Error: JAR file not found: {jar_path}")
        sys.exit(1)

    # Create tester and run tests
    tester = MCPTester(jar_path=jar_path, java_path=java_path)
    results = tester.run_all_tests()

    # Print summary
    tester.print_summary(results)

    # Save detailed report
    tester.save_detailed_report(results)

    # Exit with error code if any tests failed
    if results['failed'] > 0 or results['errors'] > 0:
        sys.exit(1)
    else:
        print("🎉 All tests passed!")
        sys.exit(0)

if __name__ == "__main__":
    main()