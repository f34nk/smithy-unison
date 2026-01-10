```ucm
scratch/main> builtins.merge
scratch/main> lib.install @unison/base/releases/3.18.0
scratch/main> lib.install @unison/http/releases/8.0.0
scratch/main> lib.install @unison/json/releases/1.3.5
scratch/main> lib.install @systemfw/concurrent/releases/7.3.0

scratch/main> load generated/aws_http.u
scratch/main> add

scratch/main> load generated/aws_sigv4.u
scratch/main> add

scratch/main> load generated/aws_config.u
scratch/main> add

scratch/main> load generated/aws_credentials.u
scratch/main> add

scratch/main> load generated/aws_json.u
scratch/main> add

scratch/main> load generated/aws_json_bridge.u
scratch/main> add

scratch/main> load src/aws_http_bridge_stub.u
scratch/main> add

scratch/main> load generated/aws_kinesis_client.u
scratch/main> add

scratch/main> load src/main.u
scratch/main> add

scratch/main> run main
```
