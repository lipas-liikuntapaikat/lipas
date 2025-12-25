# Babashka Expert Assistant Guide

## Core Concept
Babashka is a scripting environment made with Clojure, compiled to native with GraalVM. It provides fast startup time and low memory consumption compared to JVM Clojure, making it ideal for scripting tasks.

## Key Characteristics
- **Fast startup**: Native compilation eliminates JVM startup penalty
- **Batteries included**: Ships with essential libraries (JSON, CLI parsing, HTTP, etc.)
- **Self-contained**: Single binary installation
- **Clojure subset**: Uses SCI (Small Clojure Interpreter) for execution
- **Compatible**: Can run many existing Clojure libraries

## Installation & Basic Usage

### Installation
- Download binary from releases page
- Available in package managers: brew (macOS/Linux), scoop (Windows)
- Executable is called `bb`

### Basic Execution
```bash
# Direct expression evaluation
bb -e '(+ 1 2 3)'  # or bb '(+ 1 2 3)'

# Run script file
bb script.clj      # or bb -f script.clj

# With shebang
#!/usr/bin/env bb
(println "Hello World")
```

## Command Line Interface

### Key Flags
- `-e, --eval <expr>`: Evaluate expression
- `-f, --file <path>`: Run file  
- `-m, --main <ns|var>`: Call main function
- `-x, --exec <var>`: Execute function with CLI parsing
- `--classpath`: Override classpath
- `--prn`: Print result via prn
- `-i`: Bind *input* to lazy seq of lines from stdin
- `-I`: Bind *input* to lazy seq of EDN values from stdin  
- `-o`: Write lines to stdout
- `-O`: Write EDN values to stdout

### Subcommands
- `repl`: Start REPL
- `socket-repl [addr]`: Start socket REPL
- `nrepl-server [addr]`: Start nREPL server
- `tasks`: List available tasks
- `run <task>`: Run specific task
- `uberscript <file>`: Create standalone script
- `uberjar <jar>`: Create standalone jar

## Project Structure (bb.edn)

### Basic Configuration
```clojure
{:paths ["src" "scripts"]           ; Source paths
 :deps {medley/medley {:mvn/version "1.3.0"}}  ; Dependencies
 :min-bb-version "0.4.0"            ; Minimum version requirement
 :tasks {...}}                      ; Task definitions
```

### Dependencies
- Only pure Clojure libraries supported (no Java libs)
- Can reference local deps.edn: `{:deps {your-org/your-project {:local/root "."}}}`
- Script-adjacent bb.edn files are automatically detected

## Task Runner System

### Task Definition
```clojure
{:tasks
 {:requires ([babashka.fs :as fs])   ; Global requires
  :init (def target-dir "target")    ; Initialization code
  :enter (println "Starting:" (:name (current-task)))  ; Before each task
  :leave (println "Finished:" (:name (current-task)))  ; After each task
  
  ; Simple task
  clean (fs/delete-tree "target")
  
  ; Task with metadata  
  build {:doc "Build the project"
         :task (do (run 'clean)
                   (shell "clj -T:build jar"))
         :depends [clean]}
         
  ; Private task (not shown in bb tasks)
  -helper-task (println "Internal task")}}
```

### Task Features
- **Dependencies**: `:depends [task1 task2]`
- **Parallel execution**: `bb run --parallel task`
- **Command line args**: Available via `*command-line-args*`
- **Function invocation**: `{task-name some.ns/function}`
- **Built-in functions**: `run`, `shell`, `clojure`, `exec`, `current-task`

### Shell Integration
```clojure
; Basic shell command
(shell "ls -la")

; With options
(shell {:dir "subproject" :out "output.txt"} "npm install")

; Capture output
(->> (shell {:out :string} "echo hello") :out str/trim)

; Continue on error
(shell {:continue true} "ls nonexistent")
```

## Built-in Libraries

### Core Clojure
- `clojure.core`, `clojure.string`, `clojure.set`
- `clojure.edn`, `clojure.data`, `clojure.walk`
- `clojure.java.io`, `clojure.java.shell`
- `clojure.test`, `clojure.pprint`

### Babashka-Specific
- `babashka.http-client`: HTTP requests
- `babashka.process`: Process management  
- `babashka.fs`: File system operations
- `babashka.cli`: Command line argument parsing
- `babashka.classpath`: Dynamic classpath manipulation
- `babashka.deps`: Dependency management

### Third-Party Libraries
- `cheshire.core` (JSON): `(json/parse-string s)`, `(json/generate-string m)`
- `clojure.tools.cli`: Command line parsing
- `clj-yaml.core`: YAML processing
- `org.httpkit.client/server`: HTTP client/server
- `hiccup.core`: HTML generation
- `selmer.parser`: Template processing

## Common Patterns

### Script Structure
```clojure
#!/usr/bin/env bb

(ns script-name
  (:require [clojure.string :as str]
            [babashka.fs :as fs]))

(defn main-logic [args]
  ; Implementation here
  )

(defn -main [& args]
  (main-logic args))

; Allow REPL loading
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
```

### Input/Output Handling
```clojure
; Read from stdin
(line-seq (io/reader *in*))           ; Lines
(edn/read *in*)                       ; Single EDN value
(slurp *in*)                          ; All text

; Command line args
*command-line-args*                   ; Vector of string args

; Environment variables  
(System/getenv "HOME")
```

### HTTP Requests
```clojure
(require '[babashka.http-client :as http])

; GET request
(http/get "https://api.example.com/data")

; POST with JSON
(http/post "https://api.example.com/submit" 
           {:headers {"content-type" "application/json"}
            :body (json/generate-string {:key "value"})})
```

### File Operations
```clojure
(require '[babashka.fs :as fs])

; File existence and properties
(fs/exists? "file.txt")
(fs/directory? "path")
(fs/size "file.txt")

; File operations
(fs/copy "src" "dest")
(fs/delete "file.txt")
(fs/create-dirs "path/to/dir")

; Globbing
(fs/glob "." "**/*.clj")
```

### Process Management
```clojure
(require '[babashka.process :as p])

; Simple process
(p/shell "ls -la")

; With options
(-> (p/process {:dir "project"} "npm" "install")
    p/check)  ; Throws on non-zero exit
```

## CLI Argument Parsing

### Using babashka.cli
```clojure
(require '[babashka.cli :as cli])

; Simple parsing
(def opts {:port {:default 8080 :coerce :long}
           :help {:coerce :boolean}})

(cli/parse-opts *command-line-args* {:spec opts})

; With exec integration
(defn my-function
  {:org.babashka/cli {:coerce {:port :int}
                      :alias {:p :port}}}
  [{:keys [port] :or {port 8080}}]
  (println "Starting on port:" port))

; Call with: bb -x my-function --port 3000
```

## Error Handling & Debugging

### Common Patterns
```clojure
; Exit with code
(System/exit 1)

; Exception handling
(try 
  (risky-operation)
  (catch Exception e
    (println "Error:" (.getMessage e))
    (System/exit 1)))

; Shutdown hooks
(-> (Runtime/getRuntime)
    (.addShutdownHook 
     (Thread. #(println "Cleanup on exit"))))
```

### Debugging
- Use `--debug` flag for internal stacktraces
- `(prn ...)` for debugging output
- REPL available: `bb repl`
- Socket REPL: `bb socket-repl 1666`

## Performance Considerations

### When to Use Babashka
- ✅ Scripts, automation, CI/CD tasks
- ✅ Quick data processing, file manipulation  
- ✅ HTTP requests, API interactions
- ✅ Command line tools
- ✅ System administration tasks

### When to Use JVM Clojure
- ❌ CPU-intensive computations with loops
- ❌ Long-running applications
- ❌ Heavy data processing (>few seconds runtime)
- ❌ Java interop requirements

## Limitations & Differences from JVM Clojure

### Not Supported
- `deftype`, `definterface`, unboxed math
- Adding Java classes at runtime
- Full `core.async/go` macro (maps to `thread`)
- `reify` with multiple classes

### Implementation Differences  
- `defprotocol`/`defrecord` use multimethods and maps internally
- Interpretation overhead affects performance
- Limited Java class access (pre-selected set only)

## Best Practices

### Code Organization
1. Use explicit namespace requires (avoid bare symbols)
2. Prefer `bb.edn` for project configuration over command-line classpath
3. Use task runner for complex workflows
4. Keep scripts focused and modular

### Error Handling
1. Always handle shell command failures explicitly
2. Use meaningful exit codes
3. Provide helpful error messages
4. Consider cleanup in shutdown hooks

### Performance
1. Minimize loops for large datasets
2. Use built-in functions when available
3. Consider uberscript for deployment
4. Profile with `time` for bottlenecks

## Integration Examples

### CI/CD Pipeline
```clojure
{:tasks 
 {test (clojure "-M:test")
  lint (clojure "-M:clj-kondo --lint src test")  
  build (clojure "-T:build jar")
  deploy {:depends [test lint build]
          :task (shell "aws s3 cp target/app.jar s3://releases/")}}}
```

### System Monitoring
```clojure
#!/usr/bin/env bb
(require '[babashka.http-client :as http]
         '[cheshire.core :as json])

(defn check-service [url]
  (try
    (let [resp (http/get url {:connect-timeout 5000})]
      {:url url :status (:status resp) :ok? (< (:status resp) 400)})
    (catch Exception e
      {:url url :status 0 :ok? false :error (.getMessage e)})))

(defn -main [& urls]
  (let [results (pmap check-service urls)
        failures (remove :ok? results)]
    (when (seq failures)
      (println "Failed services:")
      (doseq [f failures]
        (println "  " (:url f) "-" (or (:error f) (:status f))))
      (System/exit 1))))

(apply -main *command-line-args*)
```

This guide provides comprehensive coverage of babashka's capabilities and should enable an LLM to provide expert-level assistance with babashka scripting, project setup, and troubleshooting.
