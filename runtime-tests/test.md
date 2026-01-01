```ucm
scratch/main> builtins.merge
scratch/main> lib.install @unison/base/releases/3.18.0

scratch/main> load src/aws_json.u
scratch/main> add

scratch/main> load src/aws_json_bridge.u
scratch/main> add

scratch/main> load src/aws_json_test.u
scratch/main> add

scratch/main> load src/aws_json_bridge_test.u
scratch/main> add

scratch/main> run Aws.Json.Test.runAllTests
scratch/main> run Aws.Json.Bridge.Test.runAllTests
```
