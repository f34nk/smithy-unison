```ucm
scratch/main> builtins.merge
scratch/main> lib.install @unison/base/releases/3.18.0
scratch/main> lib.install @unison/xml/releases/1.2.0

scratch/main> load main/resources/runtime/aws_json.u
scratch/main> add

scratch/main> load main/resources/runtime/aws_json_bridge.u
scratch/main> add

scratch/main> load main/resources/runtime/aws_xml.u
scratch/main> add

scratch/main> load main/resources/runtime/aws_xml_bridge.u
scratch/main> add

scratch/main> load main/resources/runtime/aws_http.u
scratch/main> add

scratch/main> load main/resources/runtime/aws_restjson.u
scratch/main> add

scratch/main> load test/resources/runtime/aws_json_test.u
scratch/main> add

scratch/main> load test/resources/runtime/aws_json_bridge_test.u
scratch/main> add

scratch/main> load test/resources/runtime/aws_xml_test.u
scratch/main> add

scratch/main> load test/resources/runtime/aws_xml_bridge_test.u
scratch/main> add

scratch/main> load test/resources/runtime/aws_http_test.u
scratch/main> add

scratch/main> load test/resources/runtime/aws_restjson_test.u
scratch/main> add

scratch/main> run aws.http.test.runAllTests
scratch/main> run aws.restjson.test.runAllTests
scratch/main> run aws.json.test.runAllTests
scratch/main> run aws.json.bridge.test.runAllTests
scratch/main> run aws.xml.test.runAllTests
scratch/main> run aws.xml.bridge.test.runAllTests
```
