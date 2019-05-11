#!/bin/bash
set -e
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
rlwrap java -jar "${script_dir}/fileshare.jar" "${@}"
