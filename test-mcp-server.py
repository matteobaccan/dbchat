#!/usr/bin/env python3
"""
Simple working MCP test that maintains a persistent server process
"""

import json
import subprocess
import sys
import time
import os
import signal
import socket
import platform
import glob
from typing import Dict, Any, Optional

class SimpleMCPTester:
    def __init__(self, jar_path: str):
        self.jar_path = jar_path
        self.java_path = "java"
        self.process = None
        
    def setup_env(self):
        """Setup environment variables"""
        env = os.environ.copy()
        env.update({
            'DB_URL': 'jdbc:h2:file:./test-db/mcptest;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1;CACHE_SIZE=65536',
            'DB_USER': 'sa',
            'DB_PASSWORD': '',
            'DB_DRIVER': 'org.h2.Driver',
            'SELECT_ONLY': 'False'
        })
        return env

    def start_process(self):
        """Start the MCP server process"""
        if self.process:
            return
            
        cmd = [self.java_path, "-jar", self.jar_path]
        env = self.setup_env()
        
        self.process = subprocess.Popen(
            cmd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            env=env
        )
        
    def stop_process(self):
        """Stop the MCP server process"""
        if self.process:
            try:
                self.process.stdin.close()
                self.process.terminate()
                self.process.wait(timeout=5)
            except:
                if self.process.poll() is None:
                    self.process.kill()
            finally:
                self.process = None

    def send_request(self, request: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """Send a request to the persistent process"""
        if not self.process:
            return None
            
        try:
            input_data = json.dumps(request) + "\n"
            self.process.stdin.write(input_data)
            self.process.stdin.flush()
            
            # Notifications (no 'id' field) don't expect a response
            if 'id' not in request:
                return None
            
            # Read response for requests with id
            response_line = self.process.stdout.readline().strip()
            if not response_line:
                return None
                
            return json.loads(response_line)
            
        except json.JSONDecodeError as e:
            print(f"JSON parse error: {e}")
            print(f"Raw output: {response_line}")
            return None
        except Exception as e:
            print(f"Error sending request: {e}")
            return None

    def test_http_mode(self) -> bool:
        """Test HTTP mode"""
        try:
            import requests
            
            # Start HTTP server
            env = self.setup_env()
            env['HTTP_MODE'] = 'true'
            env['HTTP_PORT'] = '8080'
            
            process = subprocess.Popen([self.java_path, "-jar", self.jar_path], env=env)
            
            # Wait for server
            for i in range(20):
                try:
                    response = requests.get("http://localhost:8080/health", timeout=1)
                    if response.status_code == 200:
                        break
                except:
                    time.sleep(0.5)
                    continue
            else:
                process.terminate()
                return False
            
            # Initialize the HTTP server first
            init_request = {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "initialize",
                "params": {
                    "protocolVersion": "2025-06-18",
                    "capabilities": {"tools": {}, "resources": {}},
                    "clientInfo": {"name": "http-test", "version": "1.0"}
                }
            }
            
            init_response = requests.post("http://localhost:8080/mcp", json=init_request, timeout=10)
            if init_response.status_code != 200 or not init_response.json().get("result"):
                process.terminate()
                return False
            
            # Send initialized notification
            init_notification = {
                "jsonrpc": "2.0",
                "method": "notifications/initialized",
                "params": {}
            }
            
            requests.post("http://localhost:8080/mcp", json=init_notification, timeout=10)
            
            # Test basic functionality
            test = {
                "jsonrpc": "2.0",
                "id": 2,
                "method": "tools/list",
                "params": {}
            }
            
            response = requests.post("http://localhost:8080/mcp", json=test, timeout=10)
            success = response.status_code == 200 and response.json().get("result")
            
            process.terminate()
            process.wait(timeout=5)
            return success
            
        except Exception as e:
            print(f"HTTP test failed: {e}")
            return False

    def cleanup_test_data(self):
        """Clean up test database files"""
        try:
            import glob
            import os
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

    def run_tests(self) -> bool:
        """Run all tests"""
        print("=" * 50)
        print("COMPREHENSIVE MCP TEST SUITE")
        print("=" * 50)
        
        # Clean any existing database files first
        try:
            import glob
            import os
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
        
        tests = [
            # Initialize
            {
                "name": "Initialize",
                "request": {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "initialize",
                    "params": {
                        "protocolVersion": "2025-06-18",
                        "capabilities": {"tools": {}, "resources": {}},
                        "clientInfo": {"name": "test", "version": "1.0"}
                    }
                }
            },
            # Initialized notification (required after initialize)
            {
                "name": "Initialized Notification",
                "request": {
                    "jsonrpc": "2.0",
                    "method": "notifications/initialized",
                    "params": {}
                }
            },
            # List tools
            {
                "name": "List Tools",
                "request": {
                    "jsonrpc": "2.0",
                    "id": 2,
                    "method": "tools/list",
                    "params": {}
                }
            },
            # List resources
            {
                "name": "List Resources",
                "request": {
                    "jsonrpc": "2.0",
                    "id": 3,
                    "method": "resources/list",
                    "params": {}
                }
            },
            # Read database info resource
            {
                "name": "Read Database Info",
                "request": {
                    "jsonrpc": "2.0",
                    "id": 4,
                    "method": "resources/read",
                    "params": {"uri": "database://info"}
                }
            },
            # Create table
            {
                "name": "Create Table",
                "request": {
                    "jsonrpc": "2.0",
                    "id": 5,
                    "method": "tools/call",
                    "params": {
                        "name": "run_sql",
                        "arguments": {
                            "sql": "CREATE TABLE test_table (id INT PRIMARY KEY, name VARCHAR(50), created_date DATE)"
                        }
                    }
                }
            },
            # Insert data
            {
                "name": "Insert Data",
                "request": {
                    "jsonrpc": "2.0",
                    "id": 6,
                    "method": "tools/call",
                    "params": {
                        "name": "run_sql",
                        "arguments": {
                            "sql": "INSERT INTO test_table VALUES (1, 'John Doe', '2024-01-01'), (2, 'Jane Smith', '2024-01-02')"
                        }
                    }
                }
            },
            # Select data
            {
                "name": "Select Data",
                "request": {
                    "jsonrpc": "2.0",
                    "id": 7,
                    "method": "tools/call",
                    "params": {
                        "name": "run_sql",
                        "arguments": {
                            "sql": "SELECT * FROM test_table ORDER BY id"
                        }
                    }
                }
            },
            # Describe table
            {
                "name": "Describe Table",
                "request": {
                    "jsonrpc": "2.0",
                    "id": 8,
                    "method": "tools/call",
                    "params": {
                        "name": "describe_table",
                        "arguments": {
                            "table_name": "test_table"
                        }
                    }
                }
            },
            # Read table metadata resource
            {
                "name": "Read Table Metadata",
                "request": {
                    "jsonrpc": "2.0",
                    "id": 9,
                    "method": "resources/read",
                    "params": {"uri": "database://table/TEST_TABLE"}
                }
            },
            # Parameterized insert
            {
                "name": "Parameterized Insert",
                "request": {
                    "jsonrpc": "2.0",
                    "id": 10,
                    "method": "tools/call",
                    "params": {
                        "name": "run_sql",
                        "arguments": {
                            "sql": "INSERT INTO test_table VALUES (?, ?, ?)",
                            "params": [3, "Alice Brown", "2024-01-03"]
                        }
                    }
                }
            },
            # Parameterized select (single param)
            {
                "name": "Parameterized Select (Single)",
                "request": {
                    "jsonrpc": "2.0",
                    "id": 11,
                    "method": "tools/call",
                    "params": {
                        "name": "run_sql",
                        "arguments": {
                            "sql": "SELECT * FROM test_table WHERE id = ?",
                            "params": [3]
                        }
                    }
                }
            },
            # Parameterized select (multiple params)
            {
                "name": "Parameterized Select (Multiple)",
                "request": {
                    "jsonrpc": "2.0",
                    "id": 12,
                    "method": "tools/call",
                    "params": {
                        "name": "run_sql",
                        "arguments": {
                            "sql": "SELECT * FROM test_table WHERE name LIKE ? AND id > ?",
                            "params": ["%e%", 1]
                        }
                    }
                }
            },
            # Parameterized select (range)
            {
                "name": "Parameterized Select (Range)",
                "request": {
                    "jsonrpc": "2.0",
                    "id": 13,
                    "method": "tools/call",
                    "params": {
                        "name": "run_sql",
                        "arguments": {
                            "sql": "SELECT * FROM test_table WHERE id BETWEEN ? AND ? ORDER BY id",
                            "params": [2, 4]
                        }
                    }
                }
            },
            # Empty params array (backward compatibility)
            {
                "name": "Empty Params Array",
                "request": {
                    "jsonrpc": "2.0",
                    "id": 14,
                    "method": "tools/call",
                    "params": {
                        "name": "run_sql",
                        "arguments": {
                            "sql": "SELECT COUNT(*) as total_count FROM test_table",
                            "params": []
                        }
                    }
                }
            }
        ]
        
        # Start persistent process
        self.start_process()
        
        try:
            passed = 0
            total = len(tests)
            
            for test in tests:
                print(f"Testing {test['name']}...")
                response = self.send_request(test['request'])
                
                # Notifications don't expect a response
                is_notification = 'id' not in test['request']
                
                if is_notification:
                    # For notifications, success is no error response
                    if not response or 'error' not in response:
                        print(f"✅ PASS")
                        passed += 1
                    else:
                        print(f"❌ FAIL")
                        print(f"   Error: {response['error']}")
                elif response and 'result' in response:
                    print(f"✅ PASS")
                    passed += 1
                else:
                    print(f"❌ FAIL")
                    if response and 'error' in response:
                        print(f"   Error: {response['error']}")
                    elif response:
                        print(f"   Response: {response}")
            
            # Test HTTP mode
            print(f"\nTesting HTTP mode...")
            http_ok = self.test_http_mode()
            print(f"HTTP test: {'✅ PASS' if http_ok else '❌ FAIL'}")
            
            print(f"\nResults: {passed}/{total} STDIO tests passed")
            overall_success = passed == total and http_ok
            print(f"Overall: {'✅ SUCCESS' if overall_success else '❌ SOME FAILED'}")
            
            # Print detailed summary
            if passed < total:
                print(f"\nFailed tests:")
                for i, test in enumerate(tests):
                    if i < len(tests) and not (i == 0 or (i > 0 and passed > i-1)):
                        continue  # This is a simple check, could be improved
                
            return overall_success
            
        finally:
            self.stop_process()
            # Cleanup test data
            try:
                self.cleanup_test_data()
            except Exception as e:
                print(f"Error during cleanup: {e}")

def main():
    # Find JAR file
    jars = glob.glob("target/dbchat-*.jar")
    if not jars:
        print("❌ Error: No dbchat JAR file found in target/. Please run 'mvn clean package'.")
        sys.exit(1)
    
    jar_path = jars[0]
    print(f"Using JAR: {jar_path}")
    
    # Check if Java is available
    try:
        subprocess.run(['java', '-version'], capture_output=True, timeout=10, check=True)
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired, FileNotFoundError):
        print("❌ Error: Java is not installed or not in PATH")
        sys.exit(1)
    
    tester = SimpleMCPTester(jar_path)
    
    try:
        success = tester.run_tests()
        
        if success:
            print("\n🎉 All tests passed! DBChat MCP server is working correctly.")
        else:
            print("\n⚠️  Some tests failed. Check the output above for details.")
            
        sys.exit(0 if success else 1)
        
    except KeyboardInterrupt:
        print("\n\nTest interrupted by user")
        sys.exit(1)
    except Exception as e:
        print(f"\n\nUnexpected error during testing: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()