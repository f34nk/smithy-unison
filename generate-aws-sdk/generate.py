#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = [
#   "jq"
# ]
# [[tool.uv.index]]
# name = "pipy"
# url = "https://pypi.org/simple/"
# ///

import os
import concurrent.futures
from functools import partial
import glob
import jq
import json
import re
import shutil
import subprocess
import time

INPUT_PATH = "api-models-aws-main/models"
OUTPUT_PATH = "output"
MODEL_DIRNAME = "model"
GENERATED_DIRNAME = "generated"
BUILD_DIRNAME = "build"


def generate(sdk_id: str, index: int = 0, total: int = 0):
    result = {}

    start_time = time.time()
    result["sdk_id"] = sdk_id
    result["process"] = f"{index}/{total}"

    def build(path: str):
        print(f"{index}/{total} - building: {sdk_id}")
        command = f"cd {path} && smithy build"
        build_log = f"{path}/smithy-build.log"
        with open(build_log, "w+") as file:
            process = subprocess.Popen(command, shell=True, stdout=file, stderr=file)
            process.wait()  # Wait inside with block to keep file open
        return process.returncode, build_log

    def exec(command: str):
        result = subprocess.run(
            command, encoding="utf-8", shell=True, text=True, capture_output=True
        )
        return result.returncode, result.stdout, result.stderr

    def snake_case(x):
        pattern = re.compile(r"(?<!^)(?=[A-Z])")
        return (
            pattern.sub("_", x)
            .lower()
            .replace("a_w_s", "aws")
            .replace("e_c2", "ec2")
            .replace("_d_b", "_db")
        )

    path = os.path.join(INPUT_PATH, sdk_id)
    json_files = glob.glob(f"{path}/**/*.json", recursive=True)
    if not json_files:
        print(f"No JSON files found for {sdk_id}")
        return

    source_path = json_files[0]
    with open(source_path, "r") as file:
        data = json.load(file)

        query = '.shapes | to_entries[] | select(.value.type == "service")|.key'
        key = jq.compile(query).input_value(data).first()
        result["model"] = source_path
        result["key"] = key

        # model_name = key.split("#")[1].split("_")[0]
        model_name = key.split("#")[0].split(".")[-1]
        module_name = snake_case(model_name)
        namespace = "aws." + module_name

        build_json = {
            "version": "1.0",
            "sources": [MODEL_DIRNAME],
            "maven": {
                "dependencies": [
                    "software.amazon.smithy:smithy-aws-traits:1.64.0",
                    "software.amazon.smithy:smithy-aws-endpoints:1.64.0",
                    "software.amazon.smithy:smithy-aws-smoke-test-model:1.64.0",
                    "software.amazon.smithy:smithy-aws-iam-traits:1.64.0",
                    "io.smithy.unison:smithy-unison:0.1.0",
                ],
                "repositories": [
                    {"url": "https://repo1.maven.org/maven2"},
                    {"url": "file://${user.home}/.m2/repository"},
                ],
            },
            "plugins": {
                "unison-codegen": {
                    "service": key,
                    "name": model_name,
                    "namespace": namespace,
                    "outputDir": GENERATED_DIRNAME,
                }
            },
        }

        output_path = f"{OUTPUT_PATH}/{sdk_id}"
        build_json_path = f"{output_path}/smithy-build.json"
        model_path = f"{output_path}/{MODEL_DIRNAME}"
        build_path = f"{output_path}/{BUILD_DIRNAME}"

        result["path"] = output_path

        os.makedirs(output_path, exist_ok=True)
        os.makedirs(model_path, exist_ok=True)
        os.makedirs(build_path, exist_ok=True)
        shutil.copy(source_path, model_path)
        json.dump(build_json, open(build_json_path, "w"), indent=2)

    build_result, build_log = build(result["path"])
    result["build"] = "success" if build_result == 0 else "failed"

    if build_result != 0:
        _, output, _ = exec(
            "|".join(
                [
                    f'ag "ERROR" -B 1 -A 12 --max-count 0 {build_log}',
                    # "sed 's/\u2500//g'",
                    # "sed 's/--//g'",
                ]
            )
        )
        with open(f'{result["path"]}/errors.txt', "w") as file:
            file.write(output)

    shutil.rmtree(f'{result["path"]}/build')

    end_time = time.time()
    result["time"] = f"{end_time - start_time:.2f} seconds"
    print(json.dumps(result, indent=2))


def main():
    start_time = time.time()

    # sdks = sorted(os.listdir(INPUT_PATH))
    # sdks = ["s3", "dynamodb", "ec2", "lambda", "sns", "sqs", "iam", "sts", "route53", "cloudfront", "waf", "organizations"]
    sdks = ["s3", "dynamodb", "ec2"]
    # sdks = ["s3"]

    # This sets the worker count to the minimum of:
    # Number of SDKs to process
    # 4× CPU cores
    # Hard cap of 64 (to avoid memory issues from too many JVM processes)
    max_workers = min(len(sdks), os.cpu_count() * 4, 64)

    with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as executor:
        # Use partial to bind total, then pass sdk and index per task
        total = len(sdks)
        gen = partial(generate, total=total)
        futures = {executor.submit(gen, sdk, i + 1): sdk for i, sdk in enumerate(sdks)}
        for future in concurrent.futures.as_completed(futures):
            sdk = futures[future]
            try:
                future.result()
            except Exception as e:
                print(f"ERROR: Failed to generate {sdk}: {e}")

    end_time = time.time()
    print(f"Processing {len(sdks)} SDKs")
    print(
        f"Using {max_workers} workers ({os.cpu_count()} cores available, max {64} workers)"
    )
    print(f"Total time: {end_time - start_time:.2f} seconds")


if __name__ == "__main__":
    main()
