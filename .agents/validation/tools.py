import os
import subprocess

def run_maven_tests() -> dict:
    """Compiles the Spring Boot project and executes the unit/integration tests using Maven.

    Returns:
        dict: A dictionary containing the status of the build ('success' or 'failed'),
              the return code, and the raw stdout/stderr output.
    """
    backend_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    mvn_path = os.path.join(backend_root, "maven", "apache-maven-3.9.6", "bin", "mvn.cmd")
    
    if not os.path.exists(mvn_path):
        return {
            "status": "failed",
            "error": f"Maven executable not found at: {mvn_path}"
        }
        
    try:
        print("Executing Maven compilation and tests...")
        result = subprocess.run(
            [mvn_path, "clean", "compile", "test-compile", "test"],
            cwd=backend_root,
            capture_output=True,
            text=True,
            timeout=300
        )
        status = "success" if result.returncode == 0 else "failed"
        return {
            "status": status,
            "returncode": result.returncode,
            "stdout": result.stdout,
            "stderr": result.stderr
        }
    except subprocess.TimeoutExpired:
        return {
            "status": "failed",
            "error": "Maven execution timed out after 300 seconds."
        }
    except Exception as e:
        return {
            "status": "failed",
            "error": str(e)
        }

def audit_code_changes() -> dict:
    """Scans the modified Java source files in the repository for security, validation, and styling issues.

    Returns:
        dict: A dictionary containing 'status' and a list of identified 'issues'.
    """
    backend_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    
    # Use git to find modified files in the working tree
    try:
        git_diff = subprocess.run(
            ["git", "diff", "--name-only"],
            cwd=backend_root,
            capture_output=True,
            text=True,
            check=True
        )
        modified_files = [
            os.path.join(backend_root, f.strip())
            for f in git_diff.stdout.splitlines()
            if f.strip().endswith(".java")
        ]
    except Exception as e:
        # Fallback: scan all java files in src/main/java if git fails
        modified_files = []
        src_dir = os.path.join(backend_root, "src", "main", "java")
        if os.path.exists(src_dir):
            for root, _, files in os.walk(src_dir):
                for file in files:
                    if file.endswith(".java"):
                        modified_files.append(os.path.join(root, file))

    issues = []
    for file_path in modified_files:
        if not os.path.exists(file_path):
            continue
        try:
            with open(file_path, "r", encoding="utf-8") as f:
                lines = f.readlines()
            
            relative_path = os.path.relpath(file_path, backend_root)
            
            # Simple checkguards
            in_multiline_comment = False
            for idx, line in enumerate(lines):
                line_num = idx + 1
                stripped = line.strip()
                
                # Handle multiline comments
                if "/*" in stripped:
                    in_multiline_comment = True
                if "*/" in stripped:
                    in_multiline_comment = False
                    continue
                if in_multiline_comment or stripped.startswith("//"):
                    continue
                
                # Check 1: Raw SQL/JPQL string concatenation (potential SQL injection)
                if ("SELECT" in stripped or "UPDATE" in stripped or "DELETE" in stripped) and "+" in stripped:
                    issues.append({
                        "file": relative_path,
                        "line": line_num,
                        "severity": "high",
                        "type": "SQL_INJECTION_RISK",
                        "message": "Potential SQL injection risk: String concatenation '+' detected in query string."
                    })
                
                # Check 2: Missing input validation on controller methods
                if "@PostMapping" in stripped or "@PutMapping" in stripped:
                    has_validation = False
                    for next_idx in range(idx + 1, min(idx + 5, len(lines))):
                        next_line = lines[next_idx].strip()
                        if "@Valid" in next_line or "@Validated" in next_line:
                            has_validation = True
                            break
                        if "{" in next_line or "public" in next_line:
                            break
                    if not has_validation and "login" not in relative_path.lower():
                        issues.append({
                            "file": relative_path,
                            "line": line_num,
                            "severity": "medium",
                            "type": "MISSING_INPUT_VALIDATION",
                            "message": "Endpoint method lacks explicit @Valid/@Validated validation for request bodies."
                        })
                        
                # Check 3: Hardcoded credentials or DB URLs
                if "jdbc:postgresql://" in stripped and "System.getenv" not in stripped and "properties" not in relative_path:
                    issues.append({
                        "file": relative_path,
                        "line": line_num,
                        "severity": "high",
                        "type": "HARDCODED_CREDENTIALS",
                        "message": "Hardcoded database connection string detected. Use environment variables instead."
                    })
        except Exception as e:
            issues.append({
                "file": os.path.basename(file_path),
                "line": 0,
                "severity": "low",
                "type": "FILE_READ_ERROR",
                "message": f"Could not audit file: {str(e)}"
            })

    return {
        "status": "success",
        "issues_found": len(issues),
        "issues": issues
    }
