```ucm
scratch/main> builtins.merge
scratch/main> lib.install @unison/base/releases/3.18.0
scratch/main> lib.install @unison/json/releases/1.3.5
scratch/main> lib.install @unison/xml/releases/1.2.0
scratch/main> lib.install @unison/http/releases/8.0.0

scratch/main> load main/resources/runtime/aws_config.u
scratch/main> add
scratch/main> load test/resources/runtime/aws_config_test.u
scratch/main> add
scratch/main> run aws.config.test.runAllTests

scratch/main> load main/resources/runtime/aws_credentials.u
scratch/main> add

scratch/main> load main/resources/runtime/aws_sigv4.u
scratch/main> add
scratch/main> load test/resources/runtime/aws_sigv4_test.u
scratch/main> add
scratch/main> run aws.sigv4.test.runAllTests

scratch/main> load main/resources/runtime/aws_json.u
scratch/main> add
scratch/main> load test/resources/runtime/aws_json_test.u
scratch/main> add
scratch/main> run aws.json.test.runAllTests

scratch/main> load main/resources/runtime/aws_json_bridge.u
scratch/main> add
scratch/main> load test/resources/runtime/aws_json_bridge_test.u
scratch/main> add
scratch/main> run aws.json.bridge.test.runAllTests

scratch/main> load main/resources/runtime/aws_xml.u
scratch/main> add
scratch/main> load test/resources/runtime/aws_xml_test.u
scratch/main> add
scratch/main> run aws.xml.test.runAllTests

scratch/main> load test/resources/runtime/aws_xml_parse_test.u
scratch/main> add
scratch/main> run aws.xml.parse.test.testXmlParsing

scratch/main> load test/resources/runtime/aws_xml_parse_test2.u
scratch/main> add
scratch/main> run aws.xml.parse.test2.runParseTests

scratch/main> load test/resources/runtime/aws_xml_parse_test3.u
scratch/main> add
scratch/main> run aws.xml.parse.test3.runAllTests

scratch/main> load main/resources/runtime/aws_http.u
scratch/main> add
scratch/main> load test/resources/runtime/aws_http_test.u
scratch/main> add
scratch/main> run aws.http.test.runAllTests

scratch/main> load main/resources/runtime/aws_xml_bridge.u
scratch/main> add
scratch/main> load test/resources/runtime/aws_xml_bridge_test.u
scratch/main> add
scratch/main> run aws.xml.bridge.test.runAllTests

scratch/main> load main/resources/runtime/aws_query.u
scratch/main> add
scratch/main> load test/resources/runtime/aws_query_test.u
scratch/main> add
scratch/main> run aws.query.test.runAllTests

scratch/main> load main/resources/runtime/aws_restjson.u
scratch/main> add
scratch/main> load test/resources/runtime/aws_restjson_test.u
scratch/main> add
scratch/main> run aws.restjson.test.runAllTests

scratch/main> load main/resources/runtime/aws_env.u
scratch/main> add
scratch/main> load test/resources/runtime/aws_env_test.u
scratch/main> add
scratch/main> run aws.env.test.runAllTests

```
