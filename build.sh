#!/bin/bash

# build.sh - Local build script for DBMCP variants
# Usage: ./build.sh [variant] [options]

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

print_status() { echo -e "${GREEN}[INFO]${NC} $1"; }
print_warning() { echo -e "${YELLOW}[WARN]${NC} $1"; }
print_error() { echo -e "${RED}[ERROR]${NC} $1"; }

show_usage() {
    echo "Usage: $0 [variant] [options]"
    echo ""
    echo "Variants:"
    echo "  basic           - H2, SQLite, PostgreSQL (default)"
    echo "  standard        - + MySQL, MariaDB, ClickHouse"
    echo "  enterprise      - + Oracle, SQL Server, IBM DB2"
    echo "  cloud-analytics - + Redshift, Snowflake, BigQuery"
    echo "  all             - All database drivers (400MB+)"
    echo "  test-all        - Test all variants"
    echo "  build-all       - Build all variants"
    echo ""
    echo "Options:"
    echo "  --skip-tests    - Skip running tests"
    echo "  --clean         - Clean before build"
    echo "  --show-size     - Show file sizes after build"
    echo "  --help          - Show this help"
    echo ""
    echo "Examples:"
    echo "  $0                          # Build basic variant"
    echo "  $0 standard                 # Build standard variant"
    echo "  $0 all --skip-tests         # Build all variant, skip tests"
    echo "  $0 build-all --show-size    # Build all variants and show sizes"
    echo "  $0 test-all                 # Test all variants"
    exit 1
}

# Default values
VARIANT="basic"
SKIP_TESTS=false
CLEAN=false
SHOW_SIZE=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        basic|standard|enterprise|cloud-analytics|all|test-all|build-all)
            VARIANT="$1"
            shift
            ;;
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        --clean)
            CLEAN=true
            shift
            ;;
        --show-size)
            SHOW_SIZE=true
            shift
            ;;
        --help)
            show_usage
            ;;
        *)
            print_error "Unknown option: $1"
            show_usage
            ;;
    esac
done

# Get current version
VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null)
print_status "Current version: $VERSION"

# Define profiles for each variant
get_profiles() {
    case $1 in
        basic) echo "" ;;
        standard) echo "-P standard-databases" ;;
        enterprise) echo "-P standard-databases,enterprise-databases" ;;
        cloud-analytics) echo "-P standard-databases,cloud-analytics" ;;
        all) echo "-P standard-databases,enterprise-databases,cloud-analytics,big-data" ;;
    esac
}

get_description() {
    case $1 in
        basic) echo "H2, SQLite, PostgreSQL" ;;
        standard) echo "MySQL, MariaDB, ClickHouse" ;;
        enterprise) echo "Oracle, SQL Server, IBM DB2" ;;
        cloud-analytics) echo "Redshift, Snowflake, BigQuery" ;;
        all) echo "All database drivers (400MB+)" ;;
    esac
}

# Clean if requested
if [ "$CLEAN" == "true" ]; then
    print_status "Cleaning..."
    mvn clean
fi

# Set up Maven goals
if [ "$SKIP_TESTS" == "true" ]; then
    MAVEN_GOAL="package -DskipTests"
    print_warning "Skipping tests"
else
    MAVEN_GOAL="package"
fi

# Handle special variants
case $VARIANT in
    test-all)
        print_status "Testing all variants..."
        variants=("basic" "standard" "enterprise" "cloud-analytics" "all")

        for var in "${variants[@]}"; do
            profiles=$(get_profiles "$var")
            desc=$(get_description "$var")

            print_status "Testing $var variant ($desc)..."
            mvn clean test $profiles

            if [ $? -eq 0 ]; then
                print_status "✓ $var variant tests passed"
            else
                print_error "✗ $var variant tests failed"
                exit 1
            fi
        done

        print_status "All variant tests completed successfully!"
        exit 0
        ;;

    build-all)
        print_status "Building all variants..."
        mkdir -p target/releases

        variants=("basic" "standard" "enterprise" "cloud-analytics" "all")

        for var in "${variants[@]}"; do
            profiles=$(get_profiles "$var")
            desc=$(get_description "$var")

            print_status "Building $var variant ($desc)..."
            mvn clean $MAVEN_GOAL $profiles

            if [ $? -eq 0 ]; then
                # Rename artifact
                cp target/dbmcp-$VERSION.jar target/releases/dbmcp-$VERSION-$var.jar
                print_status "✓ $var variant built successfully"
            else
                print_error "✗ $var variant build failed"
                exit 1
            fi
        done

        if [ "$SHOW_SIZE" == "true" ]; then
            print_status "Build artifacts:"
            ls -lh target/releases/
        fi

        print_status "All variants built successfully!"
        print_status "Artifacts saved in target/releases/"
        exit 0
        ;;
esac

# Build single variant
profiles=$(get_profiles "$VARIANT")
desc=$(get_description "$VARIANT")

print_status "Building $VARIANT variant ($desc)..."
print_status "Profiles: ${profiles:-none}"

# Set memory for large builds
if [ "$VARIANT" == "all" ] || [ "$VARIANT" == "cloud-analytics" ]; then
    export MAVEN_OPTS="${MAVEN_OPTS} -Xmx4g -XX:MaxMetaspaceSize=1g"
    print_warning "Using increased memory for large build"
fi

# Run Maven build
mvn clean $MAVEN_GOAL $profiles

if [ $? -eq 0 ]; then
    print_status "✓ Build completed successfully"

    # Show file size if requested
    if [ "$SHOW_SIZE" == "true" ]; then
        print_status "Artifact size:"
        ls -lh target/dbmcp-$VERSION.jar
    fi

    # Rename artifact to include variant name
    if [ "$VARIANT" != "basic" ]; then
        cp target/dbmcp-$VERSION.jar target/dbmcp-$VERSION-$VARIANT.jar
        print_status "Artifact renamed to: dbmcp-$VERSION-$VARIANT.jar"
    fi

    print_status "Build artifact: target/dbmcp-$VERSION$([ "$VARIANT" != "basic" ] && echo "-$VARIANT").jar"
else
    print_error "✗ Build failed"
    exit 1
fi

# Quick verification
print_status "Verifying JAR contents..."
jar -tf target/dbmcp-$VERSION$([ "$VARIANT" != "basic" ] && echo "-$VARIANT").jar | head -5

print_status "Build completed! 🎉"
print_status ""
print_status "To run:"
print_status "  java -jar target/dbmcp-$VERSION$([ "$VARIANT" != "basic" ] && echo "-$VARIANT").jar"